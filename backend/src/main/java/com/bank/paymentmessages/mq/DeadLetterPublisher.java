package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.entity.PaymentMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;


@Component
public class DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final String deadLetterQueue;
    private final Counter publishFailures;


    public DeadLetterPublisher(JmsTemplate jmsTemplate,
                               @Value("${ibm.mq.dlq-queue}") String deadLetterQueue,
                               MeterRegistry meterRegistry) {
        this.jmsTemplate = jmsTemplate;
        this.deadLetterQueue = deadLetterQueue;
        this.publishFailures = Counter.builder("payment.dlq.publish.failures")
                .description("Echecs de republication sur la Dead Letter Queue applicative")
                .register(meterRegistry);
    }


    /**
     * Republie le payload brut sur la DLQ applicative.
     *
     * @return {@code true} si le broker a accepté le message. Un {@code false} signale
     *         une divergence base / broker : l'appelant ne doit pas confirmer la
     *         publication, la reprise planifiée réessaiera.
     */
    public boolean publish(PaymentMessage message) {

        try {

            jmsTemplate.send(deadLetterQueue, session -> {

                jakarta.jms.TextMessage jmsMessage = session.createTextMessage(message.getPayload());
                jmsMessage.setStringProperty("originalMessageId", message.getMessageId());
                jmsMessage.setStringProperty("reference", message.getReference());
                jmsMessage.setIntProperty("retryCount", Objects.requireNonNullElse(message.getRetryCount(), 0));

                if (message.getErrorMessage() != null) {
                    jmsMessage.setStringProperty("errorMessage", message.getErrorMessage());
                }

                return jmsMessage;
            });

            log.warn("Message envoyé en Dead Letter Queue {} : {}", deadLetterQueue, message.getMessageId());
            return true;

        } catch (Exception e) {

            publishFailures.increment();

            // Le statut DEAD_LETTER est posé en base sans confirmation de publication :
            // la ligne reste sans dlqPublishedAt et sera reprise par DeadLetterRecoveryJob.
            log.error("Echec de publication sur la Dead Letter Queue {} pour {}",
                    deadLetterQueue, message.getMessageId(), e);
            return false;
        }
    }
}
