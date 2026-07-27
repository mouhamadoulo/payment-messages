package com.bank.paymentmessages.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestration du rejeu massif des messages {@code FAILED}.
 * <p>
 * Le rejeu ne charge plus toute la table en mémoire : il enchaîne des lots bornés, chacun
 * dans sa propre transaction ({@link PaymentMessageService#retryFailedBatch(int)}). Il
 * s'exécute en tâche de fond et l'API rend un identifiant de suivi, plutôt qu'une requête
 * HTTP qui durerait plusieurs minutes.
 * <p>
 * Un seul rejeu peut être en vol à la fois : deux exécutions concurrentes se disputeraient
 * les mêmes lignes et sursoliciteraient la base.
 */
@Service
public class BatchRetryService {

    private static final Logger log = LoggerFactory.getLogger(BatchRetryService.class);

    /** Historique conservé pour la consultation ; au-delà, les tâches terminées les plus anciennes sont oubliées. */
    private static final int MAX_TRACKED_TASKS = 50;

    private final PaymentMessageService service;
    private final TaskExecutor executor;
    private final int batchSize;
    private final int maxMessages;

    private final Map<String, BatchRetryTask> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BatchRetryService(PaymentMessageService service,
                             @Qualifier("batchRetryExecutor") TaskExecutor executor,
                             @Value("${app.batch-retry.batch-size:500}") int batchSize,
                             @Value("${app.batch-retry.max-messages:100000}") int maxMessages) {
        this.service = service;
        this.executor = executor;
        this.batchSize = batchSize;
        this.maxMessages = maxMessages;
    }

    /**
     * Démarre un rejeu, ou rend la tâche déjà en cours si un rejeu est en vol.
     *
     * @return l'état initial de la tâche
     */
    public BatchRetryTask start() {

        if (!running.compareAndSet(false, true)) {
            return tasks.values().stream()
                    .filter(t -> t.state() == BatchRetryTask.State.RUNNING)
                    .findFirst()
                    .orElseGet(() -> BatchRetryTask.running("unknown"));
        }

        BatchRetryTask task = BatchRetryTask.running(UUID.randomUUID().toString());
        tasks.put(task.taskId(), task);
        evictOldestFinished();

        executor.execute(() -> run(task.taskId()));
        return task;
    }

    public Optional<BatchRetryTask> find(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    private void run(String taskId) {

        int processed = 0;

        try {
            int batch;
            do {
                batch = service.retryFailedBatch(batchSize);
                processed += batch;
                int done = processed;
                tasks.computeIfPresent(taskId, (id, task) -> task.progress(done));
            } while (batch == batchSize && processed < maxMessages);

            boolean truncated = processed >= maxMessages && batch == batchSize;
            int total = processed;
            tasks.computeIfPresent(taskId, (id, task) -> task.completed(total, truncated));

            if (truncated) {
                log.warn("Rejeu massif interrompu au plafond de {} messages, relancer pour poursuivre", maxMessages);
            } else {
                log.info("Rejeu massif terminé : {} message(s)", processed);
            }

        } catch (Exception e) {
            log.error("Echec du rejeu massif {}", taskId, e);
            int total = processed;
            tasks.computeIfPresent(taskId, (id, task) -> task.failed(total, e.getMessage()));

        } finally {
            running.set(false);
        }
    }

    private void evictOldestFinished() {
        if (tasks.size() <= MAX_TRACKED_TASKS) {
            return;
        }
        tasks.values().stream()
                .filter(t -> t.finishedAt() != null)
                .min(Comparator.comparing(BatchRetryTask::finishedAt))
                .ifPresent(t -> tasks.remove(t.taskId()));
    }
}
