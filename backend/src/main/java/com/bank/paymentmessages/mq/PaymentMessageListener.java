package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * Consommateur JMS de la file d'entrée des paiements.
 * <p>
 * La session étant transactée ({@code spring.jms.listener.session.transacted}), le
 * traitement distingue deux familles d'erreurs :
 * <ul>
 *   <li><b>définitives</b> (payload illisible, validation en échec) : le rejeu ne
 *       servirait à rien. Le message est persisté en {@code FAILED} avec son payload
 *       brut, puis acquitté — il reste rejouable depuis l'API ;</li>
 *   <li><b>transitoires</b> (base indisponible, timeout, deadlock) : l'exception est
 *       laissée remonter pour que la session effectue un rollback et que le broker
 *       redélivre le message. Le garde-fou anti-message-empoisonné est posé côté
 *       queue manager ({@code BOTHRESH} / {@code BOQNAME}).</li>
 * </ul>
 * Dans les deux cas, aucun message n'est perdu.
 */
@Component
public class PaymentMessageListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageListener.class);

    /** Valeur de repli quand le payload est illisible : les colonnes concernées sont non nulles. */
    private static final String UNKNOWN = "UNKNOWN";

    private final PaymentMessageService service;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final Counter rejectedCounter;
    private final Counter duplicateCounter;


    public PaymentMessageListener(PaymentMessageService service, JsonMapper jsonMapper,
                                  Validator validator, MeterRegistry meterRegistry) {
        this.service = service;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.rejectedCounter = Counter.builder("payment.mq.messages.rejected")
                .description("Messages rejetés définitivement et persistés en FAILED")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("payment.mq.messages.duplicates")
                .description("Messages déjà présents en base, ignorés lors d'une redélivrance")
                .register(meterRegistry);
    }


    @JmsListener(destination = "${ibm.mq.queue}")
    public void receive(String payload) {

        PaymentMessageEvent event;

        try {
            event = jsonMapper.readValue(payload, PaymentMessageEvent.class);
        } catch (JacksonException e) {
            // Erreur définitive : un rejeu produirait exactement la même erreur.
            rejectPermanently(UNKNOWN + "-" + UUID.randomUUID(), UNKNOWN, UNKNOWN, payload,
                    "Payload JSON illisible : " + e.getMessage());
            return;
        }

        Set<ConstraintViolation<PaymentMessageEvent>> violations = validator.validate(event);

        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + " : " + v.getMessage())
                    .collect(Collectors.joining(", "));

            rejectPermanently(
                    hasText(event.getMessageId()) ? event.getMessageId() : UNKNOWN + "-" + UUID.randomUUID(),
                    hasText(event.getReference()) ? event.getReference() : UNKNOWN,
                    hasText(event.getMessageType()) ? event.getMessageType() : UNKNOWN,
                    payload,
                    "Validation en échec : " + errors);
            return;
        }

        // Toute exception d'ici est considérée comme transitoire : elle remonte,
        // la session JMS effectue un rollback et le broker redélivre le message.
        if (service.saveMessage(event, payload)) {
            log.info("Message paiement sauvegardé : {}", event.getMessageId());
        } else {
            duplicateCounter.increment();
            log.info("Message paiement déjà traité, redélivrance ignorée : {}", event.getMessageId());
        }
    }


    /**
     * Persiste le rejet et acquitte. Si la persistance elle-même échoue (base
     * indisponible), l'exception remonte : le message sera redélivré plutôt que perdu.
     */
    private void rejectPermanently(String messageId, String reference, String messageType,
                                   String payload, String errorMessage) {

        log.error("Message IBM MQ rejeté définitivement ({}) : {}", messageId, errorMessage);

        if (service.savePermanentFailure(messageId, reference, messageType, payload, errorMessage)) {
            rejectedCounter.increment();
        } else {
            duplicateCounter.increment();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
