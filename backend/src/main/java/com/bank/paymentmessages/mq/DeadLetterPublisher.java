package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.entity.PaymentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;


@Component
public class DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final String deadLetterQueue;


    public DeadLetterPublisher(JmsTemplate jmsTemplate, @Value("${ibm.mq.dlq-queue}") String deadLetterQueue) {
        this.jmsTemplate = jmsTemplate;
        this.deadLetterQueue = deadLetterQueue;
    }


    public void publish(PaymentMessage message) {

        try {

            jmsTemplate.send(deadLetterQueue, session -> {

                jakarta.jms.TextMessage jmsMessage = session.createTextMessage(message.getPayload());
                jmsMessage.setStringProperty("originalMessageId", message.getMessageId());
                jmsMessage.setStringProperty("reference", message.getReference());
                jmsMessage.setIntProperty("retryCount", message.getRetryCount());

                if (message.getErrorMessage() != null) {
                    jmsMessage.setStringProperty("errorMessage", message.getErrorMessage());
                }

                return jmsMessage;
            });

            log.warn("Message envoyé en Dead Letter Queue {} : {}", deadLetterQueue, message.getMessageId());

        } catch (Exception e) {

            // Le statut DEAD_LETTER reste posé en base même si la republication échoue.
            log.error("Echec de publication sur la Dead Letter Queue {} pour {}",
                    deadLetterQueue, message.getMessageId(), e);
        }
    }
}
