package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentMessageRepository  extends JpaRepository<PaymentMessage,Long> {

    /**
     * Prédicat commun à la liste, à son {@code COUNT(*)} et à ses compteurs par statut.
     * <p>
     * Un paramètre nul neutralise sa clause : une seule requête couvre les seize
     * combinaisons de filtres, là où des méthodes dérivées en auraient demandé seize.
     * Le groupe de recherche texte est parenthésé — {@code AND} lie plus fort que
     * {@code OR}, sans quoi un fragment recherché annulerait les autres filtres.
     */
    String FILTERS = """
            WHERE (:status IS NULL OR p.status = :status)
              AND (:receivedAfter IS NULL OR p.receivedAt > :receivedAfter)
              AND (:type IS NULL OR p.messageType = :type)
              AND (:text IS NULL
                   OR lower(p.reference) LIKE :text
                   OR lower(p.messageId) LIKE :text
                   OR lower(p.messageType) LIKE :text)
            """;

    /** Projection de liste : les colonnes du tableau, jamais le payload. */
    String SELECT_SUMMARY = """
            SELECT new com.bank.paymentmessages.repository.PaymentMessageSummary(
                       p.id, p.messageId, p.reference, p.messageType, p.status,
                       p.payloadSize, p.retryCount, p.errorMessage, p.receivedAt, p.updatedAt)
            FROM PaymentMessage p
            """;

    Optional<PaymentMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    Optional<PaymentMessage> findByReference(String reference);

    // --- Listes paginées : projections sans payload (cf. PaymentMessageSummary) ---

    /**
     * Liste non filtrée : requête dérivée, sans les prédicats {@code IS NULL OR} de
     * {@link #search} — c'est le chemin le plus fréquent et le plus simple à planifier.
     */
    Page<PaymentMessageSummary> findAllProjectedBy(Pageable pageable);

    /** Liste filtrée (statut, date, type, texte), triée par le {@link Pageable}. */
    @Query(value = SELECT_SUMMARY + FILTERS,
           countQuery = "SELECT COUNT(p) FROM PaymentMessage p " + FILTERS)
    Page<PaymentMessageSummary> search(@Param("status") PaymentMessageStatus status,
                                       @Param("receivedAfter") OffsetDateTime receivedAfter,
                                       @Param("type") String type,
                                       @Param("text") String text,
                                       Pageable pageable);

    /**
     * Pagination par curseur (<i>keyset</i>) : pas de {@code COUNT(*)}, pas d'{@code OFFSET},
     * donc un coût constant quelle que soit la profondeur de navigation. Le curseur est le
     * couple {@code (receivedAt, id)} de la dernière ligne rendue ; {@code null} sur la
     * première page.
     * <p>
     * Le tri est figé sur {@code received_at DESC, id DESC} : c'est ce qui rend le curseur
     * total (l'{@code id} départage deux messages reçus au même instant) et ce qui permet à
     * l'index {@code idx_pm_status_received_at} de servir la requête.
     */
    @Query(SELECT_SUMMARY + FILTERS + """
              AND (:cursorReceivedAt IS NULL
                   OR p.receivedAt < :cursorReceivedAt
                   OR (p.receivedAt = :cursorReceivedAt AND p.id < :cursorId))
            ORDER BY p.receivedAt DESC, p.id DESC
            """)
    List<PaymentMessageSummary> findNextPage(@Param("status") PaymentMessageStatus status,
                                             @Param("receivedAfter") OffsetDateTime receivedAfter,
                                             @Param("type") String type,
                                             @Param("text") String text,
                                             @Param("cursorReceivedAt") OffsetDateTime cursorReceivedAt,
                                             @Param("cursorId") Long cursorId,
                                             Pageable pageable);

    /**
     * Derniers messages en échec, pour le bandeau d'alertes du dashboard. La projection
     * évite d'en rapatrier les payloads, et le {@code Pageable} borne le lot.
     */
    @Query(SELECT_SUMMARY + """
            WHERE p.status IN :statuses
            ORDER BY p.receivedAt DESC, p.id DESC
            """)
    List<PaymentMessageSummary> findRecentByStatusIn(@Param("statuses") List<PaymentMessageStatus> statuses,
                                                     Pageable pageable);

    // --- Traitements par lots : entités complètes ---

    /** Lot borné de messages dans un statut donné, pour un traitement itératif. */
    Page<PaymentMessage> findAllByStatus(PaymentMessageStatus status, Pageable pageable);

    /**
     * Messages passés en DEAD_LETTER dont la republication sur la DLQ n'a jamais été
     * confirmée. Alimente la reprise planifiée qui rattrape les divergences base / broker.
     */
    List<PaymentMessage> findByStatusAndDlqPublishedAtIsNull(PaymentMessageStatus status, Pageable pageable);

    /** Identifiants purgeables par la rétention : lot borné pour éviter un DELETE massif. */
    @Query("SELECT p.id FROM PaymentMessage p WHERE p.status = :status AND p.receivedAt < :cutoff")
    List<Long> findPurgeableIds(@Param("status") PaymentMessageStatus status,
                                @Param("cutoff") OffsetDateTime cutoff,
                                Pageable pageable);

    // --- Agrégats ---

    @Query("SELECT p.status, COUNT(p) FROM PaymentMessage p GROUP BY p.status")
    List<Object[]> countByStatus();

    /**
     * Compteurs par statut sous les filtres actifs (le statut lui-même est exclu du
     * prédicat : chaque pastille doit afficher ce que donnerait un clic dessus).
     */
    @Query("""
            SELECT p.status, COUNT(p) FROM PaymentMessage p
            WHERE (:receivedAfter IS NULL OR p.receivedAt > :receivedAfter)
              AND (:type IS NULL OR p.messageType = :type)
              AND (:text IS NULL
                   OR lower(p.reference) LIKE :text
                   OR lower(p.messageId) LIKE :text
                   OR lower(p.messageType) LIKE :text)
            GROUP BY p.status
            """)
    List<Object[]> countByStatusFiltered(@Param("receivedAfter") OffsetDateTime receivedAfter,
                                         @Param("type") String type,
                                         @Param("text") String text);

    /** Types présents en base, pour alimenter le sélecteur de la barre de filtres. */
    @Query("""
            SELECT DISTINCT p.messageType FROM PaymentMessage p
            WHERE p.messageType IS NOT NULL
            ORDER BY p.messageType
            """)
    List<String> findDistinctMessageTypes();

    /**
     * Volume horaire sur une fenêtre glissante, agrégé <b>en SQL</b> : le dashboard
     * téléchargeait 200 messages complets pour recompter côté client une vingtaine de
     * nombres — et fournissait des chiffres faux dès que la table dépassait 200 lignes.
     * <p>
     * L'heure est extraite dans le fuseau de la session base de données (celui de la JVM
     * via le pilote) ; la fenêtre couvrant 24 heures consécutives, chaque heure du jour
     * n'apparaît qu'une fois et le service peut lui réassocier son instant absolu.
     */
    @Query("""
            SELECT extract(hour from p.receivedAt), COUNT(p) FROM PaymentMessage p
            WHERE p.receivedAt >= :from
            GROUP BY extract(hour from p.receivedAt)
            """)
    List<Object[]> countByHourSince(@Param("from") OffsetDateTime from);

    /** Instant de réception le plus récent, servi par l'index sur {@code received_at}. */
    @Query("SELECT MAX(p.receivedAt) FROM PaymentMessage p")
    OffsetDateTime findLastReceivedAt();

    /** Répartition par type sur toute la table (cohérente avec l'agrégat par statut). */
    @Query("SELECT p.messageType, COUNT(p) FROM PaymentMessage p GROUP BY p.messageType ORDER BY COUNT(p) DESC")
    List<Object[]> countByMessageType();

    /**
     * Répartition par nombre de tentatives. Le regroupement final (0 / 1 / 2 / 3+) est fait
     * en Java : la cardinalité de {@code retry_count} est bornée par {@code max-retries}.
     */
    @Query("SELECT p.retryCount, COUNT(p) FROM PaymentMessage p GROUP BY p.retryCount")
    List<Object[]> countByRetryCount();
}
