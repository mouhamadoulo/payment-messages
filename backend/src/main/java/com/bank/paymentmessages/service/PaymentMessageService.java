package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.DashboardStatsDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.InvalidStatusTransitionException;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.mapper.PaymentMessageMapper;
import com.bank.paymentmessages.mq.DeadLetterPublisher;
import com.bank.paymentmessages.mq.DeadLetterRequestedEvent;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import com.bank.paymentmessages.repository.PaymentMessageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Logique métier des messages de paiement.
 * <p>
 * La classe est transactionnelle en lecture seule par défaut ; chaque écriture est
 * annotée explicitement. Deux exceptions notables :
 * <ul>
 *   <li>les insertions d'ingestion s'exécutent <b>hors</b> transaction englobante
 *       ({@link Propagation#NOT_SUPPORTED}) pour que la violation de contrainte d'un
 *       doublon reste confinée à la transaction interne de {@code repository.save} ;</li>
 *   <li>les publications DLQ sont émises sous forme d'événement et exécutées
 *       <b>après commit</b> (cf. {@code DeadLetterDispatcher}).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PaymentMessageService {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageService.class);

    /** Nom du cache des statistiques (cf. {@code spring.cache}). */
    public static final String STATS_CACHE = "messageStats";

    /** Cache des agrégats du dashboard (volume horaire, types, tentatives, alertes). */
    public static final String DASHBOARD_CACHE = "dashboardStats";

    /** Cache de la liste des types présents en base (sélecteur de la barre de filtres). */
    public static final String TYPES_CACHE = "messageTypes";

    /** Tranches du volume horaire : 24 heures glissantes, alignées sur l'heure pleine. */
    private static final int HOURLY_WINDOW_HOURS = 24;

    /** Nombre d'alertes remontées au dashboard. */
    private static final int RECENT_FAILURES = 5;

    private final PaymentMessageRepository repository;
    private final DeadLetterPublisher deadLetterPublisher;
    private final ApplicationEventPublisher events;
    private final int maxRetries;

    public PaymentMessageService(PaymentMessageRepository repository,
                                 DeadLetterPublisher deadLetterPublisher,
                                 ApplicationEventPublisher events,
                                 @Value("${ibm.mq.max-retries}") int maxRetries){
        this.repository = repository;
        this.deadLetterPublisher = deadLetterPublisher;
        this.events = events;
        this.maxRetries = maxRetries;
    }

    /**
     * Persiste un message entrant de façon idempotente.
     * <p>
     * La contrainte d'unicité sur {@code messageId} arbitre les redélivrances JMS :
     * un doublon se solde par un {@code false} — le message peut être acquitté sans
     * erreur — au lieu de remonter une exception qui provoquerait un rollback puis
     * une boucle de redélivrance infinie.
     *
     * @return {@code true} si la ligne a été créée, {@code false} si le message était déjà en base
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CacheEvict(cacheNames = STATS_CACHE, allEntries = true)
    public boolean saveMessage(PaymentMessageEvent event, String rawPayload) {

        PaymentMessage message = PaymentMessageMapper.toEntity(event, rawPayload);
        return insertIfAbsent(message);
    }

    /**
     * Persiste un message rejeté définitivement (payload illisible, validation en échec).
     * Le rejeu reste possible depuis l'API au lieu de perdre le message.
     *
     * @return {@code true} si la ligne a été créée, {@code false} si le message était déjà en base
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @CacheEvict(cacheNames = STATS_CACHE, allEntries = true)
    public boolean savePermanentFailure(String messageId, String reference, String messageType,
                                        String rawPayload, String errorMessage) {

        return insertIfAbsent(
                PaymentMessageMapper.toFailedEntity(messageId, reference, messageType, rawPayload, errorMessage));
    }

    /**
     * {@code repository.save} s'exécute dans sa propre transaction : la violation de
     * contrainte n'invalide donc pas de transaction englobante et peut être requalifiée
     * ici en « déjà traité ». Toute autre violation d'intégrité est relancée : c'est une
     * anomalie réelle, qui doit provoquer un rollback JMS et une redélivrance.
     */
    private boolean insertIfAbsent(PaymentMessage message) {

        if (repository.existsByMessageId(message.getMessageId())) {
            return false;
        }

        try {
            repository.save(message);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (repository.existsByMessageId(message.getMessageId())) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Rattrape les messages marqués {@code DEAD_LETTER} en base dont la republication
     * sur la DLQ n'a jamais abouti, pour supprimer la divergence base / broker.
     * <p>
     * Volontairement hors transaction englobante : la publication précède l'écriture de
     * {@code dlqPublishedAt}, qui n'est posé que sur accusé du broker.
     *
     * @return le nombre de messages effectivement republiés
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int republishPendingDeadLetters(int batchSize) {

        List<PaymentMessage> pending = repository.findByStatusAndDlqPublishedAtIsNull(
                PaymentMessageStatus.DEAD_LETTER, PageRequest.of(0, batchSize));

        List<PaymentMessage> republished = new ArrayList<>();
        for (PaymentMessage message : pending) {
            if (deadLetterPublisher.publish(message)) {
                message.setDlqPublishedAt(OffsetDateTime.now());
                republished.add(message);
            }
        }

        repository.saveAll(republished);
        return republished.size();
    }

    public Page<PaymentMessageSummaryDto> findAll(Pageable pageable) {

        return repository.findAllProjectedBy(pageable).map(PaymentMessageMapper::toSummaryDto);
    }

    /**
     * Liste filtrée. Les quatre critères sont appliqués en base : le front filtrait le texte
     * et le type sur la seule page affichée, ce qui contredisait les compteurs globaux et
     * rendait toute recherche aveugle au-delà de la page courante.
     */
    public Page<PaymentMessageSummaryDto> search(MessageQuery query, Pageable pageable) {
        if (query.isEmpty()) {
            return findAll(pageable);
        }
        return repository.search(query.status(), query.receivedAfter(), query.type(),
                        query.textPattern(), pageable)
                .map(PaymentMessageMapper::toSummaryDto);
    }

    /**
     * Navigation séquentielle par curseur : ni {@code COUNT(*)} ni {@code OFFSET}, donc un
     * coût indépendant de la profondeur. Le tri est figé sur {@code receivedAt DESC, id DESC}.
     *
     * @param cursor curseur opaque rendu par l'appel précédent, {@code null} pour la première page
     */
    public CursorPageDto<PaymentMessageSummaryDto> searchByCursor(MessageQuery query,
                                                                  String cursor,
                                                                  int size) {

        Cursor position = Cursor.decode(cursor);

        // Une ligne de plus que demandé : sa présence signale qu'il reste une page.
        List<PaymentMessageSummary> rows = repository.findNextPage(
                query.status(),
                query.receivedAfter(),
                query.type(),
                query.textPattern(),
                position == null ? null : position.receivedAt(),
                position == null ? null : position.id(),
                PageRequest.ofSize(size + 1));

        boolean hasNext = rows.size() > size;
        List<PaymentMessageSummary> page = hasNext ? rows.subList(0, size) : rows;

        String nextCursor = null;
        if (hasNext) {
            PaymentMessageSummary last = page.get(page.size() - 1);
            nextCursor = new Cursor(last.receivedAt(), last.id()).encode();
        }

        return new CursorPageDto<>(
                page.stream().map(PaymentMessageMapper::toSummaryDto).toList(),
                nextCursor,
                hasNext);
    }

    public PaymentMessageDto findById(Long id) {

        return repository.findById(id)
                .map(PaymentMessageMapper::toDto)
                .orElseThrow(() ->
                        new PaymentMessageNotFoundException(id));
    }

    /**
     * Agrégat par statut : requête la plus coûteuse du système et la plus souvent appelée
     * par le front. Le résultat est mis en cache quelques secondes et invalidé par toute
     * écriture, plutôt que recalculé à chaque appel.
     */
    @Cacheable(STATS_CACHE)
    public Map<PaymentMessageStatus, Long> getStats() {
        return toStatusMap(repository.countByStatus());
    }

    /**
     * Compteurs par statut <b>sous les filtres actifs</b>, le statut excepté : les pastilles
     * annoncent ce que donnerait un clic dessus, au lieu d'un total global sans rapport avec
     * la liste affichée.
     * <p>
     * Sans critère, l'appel retombe sur la variante mise en cache. Les variantes filtrées ne
     * sont volontairement pas mises en cache : la clé contiendrait un texte libre, donc un
     * nombre non borné d'entrées.
     */
    public Map<PaymentMessageStatus, Long> getStats(MessageQuery query) {
        if (!query.hasNonStatusCriteria()) {
            return getStats();
        }
        return toStatusMap(repository.countByStatusFiltered(
                query.receivedAfter(), query.type(), query.textPattern()));
    }

    /** Types de messages présents en base, pour le sélecteur de la barre de filtres. */
    @Cacheable(TYPES_CACHE)
    public List<String> getMessageTypes() {
        return repository.findDistinctMessageTypes();
    }

    /**
     * Agrégats du dashboard, calculés en SQL sur la fenêtre voulue.
     * <p>
     * Remplace le téléchargement d'un échantillon de 200 messages complets que le client
     * recomptait : le volume réseau tombe de plusieurs mégaoctets à quelques centaines
     * d'octets, et les comptages portent sur toute la table au lieu de l'échantillon.
     * <p>
     * Mis en cache comme l'agrégat par statut ({@code STATS_CACHE_TTL}) : cinq requêtes
     * d'agrégation à chaque affichage seraient la lecture la plus coûteuse du système.
     * L'ingestion ne l'invalide pas — sous un flux soutenu, le cache serait vidé à chaque
     * message et ne servirait plus à rien ; le TTL borne la fraîcheur à quelques secondes.
     */
    @Cacheable(DASHBOARD_CACHE)
    public DashboardStatsDto getDashboardStats() {

        // Fenêtre alignée sur l'heure pleine : 24 tranches consécutives, donc chaque heure
        // du jour n'y figure qu'une fois — c'est ce qui permet de réassocier une tranche à
        // son instant absolu (au décalage près d'un changement d'heure, où deux tranches
        // partagent la même heure locale et voient leurs comptages fusionner).
        OffsetDateTime to = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1);
        OffsetDateTime from = to.minusHours(HOURLY_WINDOW_HOURS);

        Map<Integer, Long> perHour = new HashMap<>();
        for (Object[] row : repository.countByHourSince(from)) {
            perHour.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        List<DashboardStatsDto.HourlyBucket> hourly = new ArrayList<>(HOURLY_WINDOW_HOURS);
        long windowTotal = 0;
        for (int i = 0; i < HOURLY_WINDOW_HOURS; i++) {
            OffsetDateTime start = from.plusHours(i);
            long count = perHour.getOrDefault(start.getHour(), 0L);
            hourly.add(new DashboardStatsDto.HourlyBucket(start, start.getHour(), count));
            windowTotal += count;
        }

        List<DashboardStatsDto.TypeCount> types = new ArrayList<>();
        for (Object[] row : repository.countByMessageType()) {
            String type = row[0] == null ? "INCONNU" : (String) row[0];
            types.add(new DashboardStatsDto.TypeCount(type, ((Number) row[1]).longValue()));
        }

        List<PaymentMessageSummaryDto> recentFailures = repository.findRecentByStatusIn(
                        List.of(PaymentMessageStatus.FAILED, PaymentMessageStatus.DEAD_LETTER),
                        PageRequest.ofSize(RECENT_FAILURES))
                .stream()
                .map(PaymentMessageMapper::toSummaryDto)
                .toList();

        return new DashboardStatsDto(from, to, windowTotal, repository.findLastReceivedAt(),
                hourly, types, retryBuckets(), recentFailures);
    }

    /** Regroupe {@code retry_count} en 0 / 1 / 2 / 3+, comme le dashboard l'affiche. */
    private DashboardStatsDto.RetryBuckets retryBuckets() {
        long[] buckets = new long[4];
        for (Object[] row : repository.countByRetryCount()) {
            int retries = row[0] == null ? 0 : ((Number) row[0]).intValue();
            buckets[Math.min(retries, 3)] += ((Number) row[1]).longValue();
        }
        return new DashboardStatsDto.RetryBuckets(buckets[0], buckets[1], buckets[2], buckets[3]);
    }

    /** Complète l'agrégat des statuts absents de la requête, pour un contrat stable. */
    private static Map<PaymentMessageStatus, Long> toStatusMap(List<Object[]> rows) {
        Map<PaymentMessageStatus, Long> stats = new LinkedHashMap<>();
        for (PaymentMessageStatus status : PaymentMessageStatus.values()) {
            stats.put(status, 0L);
        }
        for (Object[] row : rows) {
            stats.put((PaymentMessageStatus) row[0], (Long) row[1]);
        }
        return stats;
    }

    @Transactional
    @CacheEvict(cacheNames = {STATS_CACHE, DASHBOARD_CACHE}, allEntries = true)
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new PaymentMessageNotFoundException(id);
        }
        repository.deleteById(id);
    }

    /**
     * Rejoue un lot borné de messages {@code FAILED}.
     * <p>
     * Le rejeu global ne charge plus toute la table en mémoire : l'orchestration
     * ({@code BatchRetryService}) rappelle cette méthode tant qu'elle renvoie un lot plein,
     * chaque lot ayant sa propre transaction.
     *
     * @return le nombre de messages traités dans ce lot
     */
    @Transactional
    @CacheEvict(cacheNames = {STATS_CACHE, DASHBOARD_CACHE}, allEntries = true)
    public int retryFailedBatch(int batchSize) {

        Page<PaymentMessage> batch = repository.findAllByStatus(
                PaymentMessageStatus.FAILED, PageRequest.of(0, batchSize));

        List<PaymentMessage> messages = batch.getContent();
        for (PaymentMessage message : messages) {
            applyRetry(message);
        }
        repository.saveAll(messages);
        return messages.size();
    }

    @Transactional
    @CacheEvict(cacheNames = {STATS_CACHE, DASHBOARD_CACHE}, allEntries = true)
    public PaymentMessageDto retry(Long id) {
        PaymentMessage message = repository.findById(id)
                .orElseThrow(() -> new PaymentMessageNotFoundException(id));
        if (message.getStatus() != PaymentMessageStatus.FAILED) {
            throw new IllegalArgumentException(
                    "Seuls les messages FAILED sont rejouables, statut actuel : " + message.getStatus());
        }
        applyRetry(message);
        return PaymentMessageMapper.toDto(repository.save(message));
    }

    /**
     * Applique un changement de statut demandé par l'API.
     * <p>
     * La transition est confrontée à la machine à états ({@link PaymentMessageStatus}) :
     * sans ce contrôle, n'importe quelle valeur d'enum était acceptée et un message
     * pouvait passer directement de {@code RECEIVED} à {@code DEAD_LETTER} sans qu'aucune
     * tentative n'ait eu lieu. Le verrou optimiste ({@code @Version}) arbitre par ailleurs
     * deux changements concurrents : le second échoue au lieu d'écraser le premier.
     *
     * @param reason motif facultatif de l'intervention manuelle
     * @throws InvalidStatusTransitionException si la transition n'est pas autorisée
     */
    @Transactional
    @CacheEvict(cacheNames = {STATS_CACHE, DASHBOARD_CACHE}, allEntries = true)
    public PaymentMessageDto updateStatus(Long id, PaymentMessageStatus newStatus, String reason) {

        PaymentMessage message = repository.findById(id)
                .orElseThrow(() -> new PaymentMessageNotFoundException(id));

        PaymentMessageStatus currentStatus = message.getStatus();
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(currentStatus, newStatus);
        }

        // Statut inchangé : la commande est neutre, on ne consomme pas de version.
        if (currentStatus == newStatus) {
            return PaymentMessageMapper.toDto(message);
        }

        log.info("Changement de statut du message {} : {} -> {}{}",
                id, currentStatus, newStatus, reason == null ? "" : " (motif : " + reason + ")");

        boolean entersDeadLetter = newStatus == PaymentMessageStatus.DEAD_LETTER;
        message.setStatus(newStatus);
        message.setUpdatedAt(OffsetDateTime.now());
        applyReason(message, newStatus, reason);

        PaymentMessage saved = repository.save(message);
        if (entersDeadLetter) {
            requestDeadLetterPublication(saved);
        }
        return PaymentMessageMapper.toDto(saved);
    }

    /**
     * Le motif tient lieu de message d'erreur sur un statut d'échec ; sur un retour à un
     * statut sain, l'erreur précédente est effacée pour ne pas laisser un diagnostic
     * obsolète attaché au message.
     */
    private static void applyReason(PaymentMessage message, PaymentMessageStatus newStatus, String reason) {
        boolean failureStatus = newStatus == PaymentMessageStatus.FAILED
                || newStatus == PaymentMessageStatus.DEAD_LETTER;

        if (failureStatus) {
            if (reason != null && !reason.isBlank()) {
                message.setErrorMessage(reason);
            }
            return;
        }
        message.setErrorMessage(null);
    }

    /**
     * Purge un lot borné de messages {@code PROCESSED} antérieurs à la date donnée.
     * Sans rétention, la table et ses index croissent indéfiniment.
     *
     * @return le nombre de lignes supprimées dans ce lot
     */
    @Transactional
    @CacheEvict(cacheNames = {STATS_CACHE, DASHBOARD_CACHE}, allEntries = true)
    public int purgeProcessedBefore(OffsetDateTime cutoff, int batchSize) {

        List<Long> ids = repository.findPurgeableIds(
                PaymentMessageStatus.PROCESSED, cutoff, PageRequest.ofSize(batchSize));

        if (ids.isEmpty()) {
            return 0;
        }

        repository.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    /**
     * Incrémente le compteur de tentatives et remet le message en attente de traitement.
     * Au-delà du seuil configuré, le message part en Dead Letter Queue.
     */
    private void applyRetry(PaymentMessage message) {
        int attempt = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
        message.setRetryCount(attempt);
        message.setUpdatedAt(OffsetDateTime.now());

        if (attempt > maxRetries) {
            message.setStatus(PaymentMessageStatus.DEAD_LETTER);
            message.setErrorMessage("Abandonné après " + maxRetries + " tentatives");
            requestDeadLetterPublication(message);
            return;
        }

        message.setStatus(PaymentMessageStatus.RECEIVED);
        message.setErrorMessage(null);
    }

    /**
     * L'envoi effectif est différé après le commit : tant que la transaction n'est pas
     * confirmée, rien ne doit atterrir sur la DLQ.
     */
    private void requestDeadLetterPublication(PaymentMessage message) {
        events.publishEvent(new DeadLetterRequestedEvent(message.getId()));
    }

}
