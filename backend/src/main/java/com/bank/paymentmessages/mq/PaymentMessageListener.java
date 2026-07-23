package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.service.PaymentMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.Set;


@Component
public class PaymentMessageListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageListener.class);

    private final PaymentMessageService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;


    public PaymentMessageListener(PaymentMessageService service, ObjectMapper objectMapper, Validator validator) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }


    @JmsListener(destination = "${ibm.mq.queue}", concurrency = "5-10")
    public void receive(String payload) {

        log.info("Message IBM MQ reçu");

        try {

            PaymentMessageEvent event = objectMapper.readValue(payload, PaymentMessageEvent.class);

            Set<ConstraintViolation<PaymentMessageEvent>> violations = validator.validate(event);

            if (!violations.isEmpty()) {
                String errors = violations.stream().map(v ->
                                v.getPropertyPath() + " : " + v.getMessage()).reduce("",
                                (a,b) -> a + "\n" + b);

                log.error("Message IBM MQ invalide : {}", errors);

                return;
            }

            service.saveMessage(event, payload);

            log.info("Message paiement sauvegardé : {}", event.getMessageId());

        } catch (Exception e) {

            log.error("Erreur traitement message IBM MQ", e);

        }
    }
}