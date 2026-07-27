package com.bank.paymentmessages.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Purge planifiée des messages {@code PROCESSED} au-delà de la durée de rétention.
 * <p>
 * Sans rétention, la table et ses index croissent indéfiniment et les temps de réponse
 * avec. La purge procède par lots bornés pour ne pas tenir un verrou long sur la table.
 * <p>
 * Désactivée par défaut ({@code app.retention.enabled}) : la suppression de données est
 * irréversible et relève d'une décision d'exploitation. Sur de très gros volumes, lui
 * préférer un partitionnement natif PostgreSQL par mois sur {@code received_at}, où
 * l'archivage se fait par {@code DETACH PARTITION} — sans DELETE ni VACUUM.
 */
@Component
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true")
public class MessageRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MessageRetentionJob.class);

    private final PaymentMessageService service;
    private final int retentionDays;
    private final int batchSize;
    private final int maxDeletionsPerRun;

    public MessageRetentionJob(PaymentMessageService service,
                               @Value("${app.retention.processed-days:90}") int retentionDays,
                               @Value("${app.retention.batch-size:500}") int batchSize,
                               @Value("${app.retention.max-per-run:50000}") int maxDeletionsPerRun) {
        this.service = service;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.maxDeletionsPerRun = maxDeletionsPerRun;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    public void purge() {

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int deleted = 0;

        try {
            int batch;
            do {
                batch = service.purgeProcessedBefore(cutoff, batchSize);
                deleted += batch;
            } while (batch == batchSize && deleted < maxDeletionsPerRun);

            if (deleted > 0) {
                log.info("Rétention : {} message(s) PROCESSED antérieurs au {} supprimé(s)", deleted, cutoff);
            }

        } catch (Exception e) {
            // Job périodique : la purge reprendra au prochain déclenchement.
            log.error("Echec de la purge de rétention (supprimés avant l'erreur : {})", deleted, e);
        }
    }
}
