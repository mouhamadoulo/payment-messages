package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.SimulationConfigDto;
import com.bank.paymentmessages.dto.api.SimulationSendRequest;
import com.bank.paymentmessages.exception.SimulationDisabledException;
import com.bank.paymentmessages.mq.SimulationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulation d'envoi : dépose des messages de test sur la file d'entrée, en tenant le rôle
 * des applications de back-office du flux réel. Rien n'est écrit en base ici — les messages
 * repassent par le consommateur applicatif comme n'importe quel autre, avec la même
 * validation et les mêmes rejets.
 * <p>
 * La destination n'est pas un paramètre : c'est {@code ibm.mq.queue}, la seule file que
 * l'application consomme. Laisser l'appelant la choisir aurait ouvert l'écriture sur les
 * autres destinations du gestionnaire de files, pour un besoin qui n'existe pas.
 * <p>
 * Restent deux garde-fous, côté serveur puisque l'IHM n'est pas une frontière de confiance :
 * le nombre de messages et la cadence sont bornés ({@code app.simulation.max-*}), et un seul
 * envoi peut être en vol — deux envois concurrents ne tiendraient plus aucune des deux
 * cadences demandées.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    /** Historique conservé pour la consultation ; au-delà, les envois terminés les plus anciens sont oubliés. */
    private static final int MAX_TRACKED_TASKS = 20;

    /** Champ réécrit à chaque copie : l'ingestion est idempotente sur lui. */
    private static final String MESSAGE_ID_FIELD = "messageId";

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final SimulationPublisher publisher;
    private final JsonMapper jsonMapper;
    private final TaskExecutor executor;

    private final boolean enabled;
    private final int maxCount;
    private final int maxRate;
    /** Unique destination possible : la file consommée par l'application. */
    private final String queue;

    private final Map<String, SimulationTask> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SimulationService(SimulationPublisher publisher,
                             JsonMapper jsonMapper,
                             @Qualifier("simulationExecutor") TaskExecutor executor,
                             @Value("${app.simulation.enabled:true}") boolean enabled,
                             @Value("${app.simulation.max-count:1000}") int maxCount,
                             @Value("${app.simulation.max-rate:200}") int maxRate,
                             @Value("${ibm.mq.queue}") String queue) {
        this.publisher = publisher;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
        this.enabled = enabled;
        this.maxCount = maxCount;
        this.maxRate = maxRate;
        this.queue = queue;
    }

    public SimulationConfigDto config() {
        return new SimulationConfigDto(enabled, queue, maxCount, maxRate);
    }

    /**
     * Démarre un envoi, ou rend l'envoi déjà en vol le cas échéant — comme le rejeu massif,
     * un seul à la fois.
     *
     * @return l'état initial de la tâche, à suivre via {@link #find(String)}
     */
    public SimulationTask start(SimulationSendRequest request) {

        if (!enabled) {
            throw new SimulationDisabledException();
        }

        int count = request.count();
        int rate = request.ratePerSecond();

        if (count > maxCount) {
            throw new IllegalArgumentException(
                    "count dépasse le plafond autorisé (" + maxCount + ")");
        }
        if (rate > maxRate) {
            throw new IllegalArgumentException(
                    "ratePerSecond dépasse le plafond autorisé (" + maxRate + ")");
        }

        if (!running.compareAndSet(false, true)) {
            return tasks.values().stream()
                    .filter(t -> t.state() == SimulationTask.State.RUNNING)
                    .findFirst()
                    .orElseGet(() -> SimulationTask.running("unknown", queue, count));
        }

        SimulationTask task = SimulationTask.running(UUID.randomUUID().toString(), queue, count);
        tasks.put(task.taskId(), task);
        evictOldestFinished();

        String payload = request.payload();
        boolean uniqueIds = request.uniqueIds();

        try {
            executor.execute(() -> run(task.taskId(), payload, count, rate, uniqueIds));
        } catch (RuntimeException e) {
            // File d'attente de l'exécuteur saturée : sans cela le verrou resterait pris et
            // plus aucun envoi ne serait accepté jusqu'au redémarrage.
            running.set(false);
            tasks.computeIfPresent(task.taskId(), (id, t) -> t.failed(0, 0, 0, e.getMessage()));
            throw e;
        }

        return task;
    }

    public Optional<SimulationTask> find(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * Boucle de publication cadencée. Les échéances sont calculées à partir de l'instant de
     * départ et non cumulées d'attente en attente : sur un envoi long, l'erreur d'une
     * publication lente ne se reporte pas sur toutes les suivantes.
     */
    private void run(String taskId, String payload, int count, int rate, boolean uniqueIds) {

        String runId = taskId.substring(0, 8);
        long stepNanos = NANOS_PER_SECOND / rate;
        long startedAt = System.nanoTime();

        int sent = 0;
        int published = 0;
        int failed = 0;

        try {
            for (int index = 1; index <= count; index++) {

                if (Thread.currentThread().isInterrupted()) {
                    log.warn("Envoi de test {} interrompu après {} message(s)", taskId, sent);
                    break;
                }

                if (publisher.publish(queue, bodyFor(payload, uniqueIds, messageId(runId, index)))) {
                    published++;
                } else {
                    failed++;
                }
                sent++;

                int currentSent = sent;
                int currentPublished = published;
                int currentFailed = failed;
                tasks.computeIfPresent(taskId,
                        (id, task) -> task.progress(currentSent, currentPublished, currentFailed));

                if (index < count && !sleepUntil(startedAt + index * stepNanos)) {
                    log.warn("Envoi de test {} interrompu après {} message(s)", taskId, sent);
                    break;
                }
            }

            int totalSent = sent;
            int totalPublished = published;
            int totalFailed = failed;
            tasks.computeIfPresent(taskId,
                    (id, task) -> task.completed(totalSent, totalPublished, totalFailed));

            log.info("Envoi de test terminé sur {} : {} publié(s), {} échec(s)",
                    queue, published, failed);

        } catch (Exception e) {
            log.error("Echec de l'envoi de test {}", taskId, e);
            int totalSent = sent;
            int totalPublished = published;
            int totalFailed = failed;
            tasks.computeIfPresent(taskId,
                    (id, task) -> task.failed(totalSent, totalPublished, totalFailed, e.getMessage()));

        } finally {
            running.set(false);
        }
    }

    /**
     * Réécrit {@code messageId} pour que chaque copie soit un message distinct. Un payload
     * qui n'est pas un objet JSON — le cas volontaire du test de rejet — part inchangé :
     * c'est précisément ce qu'on veut faire consommer.
     */
    private String bodyFor(String payload, boolean uniqueIds, String messageId) {

        if (!uniqueIds) {
            return payload;
        }

        try {
            JsonNode node = jsonMapper.readTree(payload);
            if (!node.isObject()) {
                return payload;
            }
            ((ObjectNode) node).put(MESSAGE_ID_FIELD, messageId);
            return node.toString();

        } catch (JacksonException e) {
            return payload;
        }
    }

    private static String messageId(String runId, int index) {
        return "SIM-" + runId + "-" + String.format(Locale.ROOT, "%05d", index);
    }

    /** @return {@code false} si l'attente a été interrompue (arrêt de l'application) */
    private static boolean sleepUntil(long deadlineNanos) {

        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            return true;
        }

        try {
            TimeUnit.NANOSECONDS.sleep(remaining);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void evictOldestFinished() {
        if (tasks.size() <= MAX_TRACKED_TASKS) {
            return;
        }
        tasks.values().stream()
                .filter(t -> t.finishedAt() != null)
                .min(Comparator.comparing(SimulationTask::finishedAt))
                .ifPresent(t -> tasks.remove(t.taskId()));
    }
}
