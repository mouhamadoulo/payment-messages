package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.service.PaymentMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rattrape les messages passés en {@code DEAD_LETTER} en base dont la republication
 * sur la DLQ a échoué. Sans cette reprise, la base et le broker divergent
 * silencieusement : le message est marqué abandonné mais n'existe nulle part côté MQ.
 */
@Component
@ConditionalOnProperty(prefix = "ibm.mq.dlq-recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRecoveryJob.class);

    private final PaymentMessageService service;
    private final int batchSize;

    public DeadLetterRecoveryJob(PaymentMessageService service,
                                 @Value("${ibm.mq.dlq-recovery.batch-size:100}") int batchSize) {
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ibm.mq.dlq-recovery.interval:60000}",
               initialDelayString = "${ibm.mq.dlq-recovery.initial-delay:60000}")
    public void republishPending() {

        try {
            int republished = service.republishPendingDeadLetters(batchSize);
            if (republished > 0) {
                log.info("Reprise Dead Letter Queue : {} message(s) republié(s)", republished);
            }
        } catch (Exception e) {
            // Le job est périodique : l'échec sera réessayé au prochain déclenchement.
            log.error("Echec de la reprise Dead Letter Queue", e);
        }
    }
}
