package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.support.JmsHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
 * <p>
 * Observabilité : chaque traitement alimente le MDC ({@code messageId}, {@code reference},
 * {@code jmsMessageId}) — toutes les lignes de log d'un message sont donc corrélables — et
 * un chronomètre {@code payment.mq.processing} ventilé par issue.
 */
@Component
public class PaymentMessageListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageListener.class);

    /** Valeur de repli quand le payload est illisible : les colonnes concernées sont non nulles. */
    private static final String UNKNOWN = "UNKNOWN";

    private static final String TIMER = "payment.mq.processing";

    /** Clés MDC reprises par le motif de log (cf. {@code logging.pattern.level}). */
    private static final String MDC_MESSAGE_ID = "messageId";
    private static final String MDC_REFERENCE = "reference";
    private static final String MDC_JMS_MESSAGE_ID = "jmsMessageId";

    private static final String OUTCOME_PERSISTED = "persisted";
    private static final String OUTCOME_DUPLICATE = "duplicate";
    private static final String OUTCOME_REJECTED = "rejected";
    private static final String OUTCOME_ERROR = "error";

    private final PaymentMessageService service;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final MeterRegistry meterRegistry;
    private final Counter receivedCounter;
    private final Counter rejectedCounter;
    private final Counter duplicateCounter;


    public PaymentMessageListener(PaymentMessageService service, JsonMapper jsonMapper,
                                  Validator validator, MeterRegistry meterRegistry) {
        this.service = service;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.meterRegistry = meterRegistry;
        this.receivedCounter = Counter.builder("payment.mq.messages.received")
                .description("Messages consommés et persistés depuis la file d'entrée")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("payment.mq.messages.rejected")
                .description("Messages rejetés définitivement et persistés en FAILED")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("payment.mq.messages.duplicates")
                .description("Messages déjà présents en base, ignorés lors d'une redélivrance")
                .register(meterRegistry);
    }


    @JmsListener(destination = "${ibm.mq.queue}")
    public void receive(String payload,
                        @Header(name = JmsHeaders.MESSAGE_ID, required = false) String jmsMessageId) {

        long startedAt = System.nanoTime();
        String outcome = OUTCOME_ERROR;

        MDC.put(MDC_JMS_MESSAGE_ID, jmsMessageId == null ? UNKNOWN : jmsMessageId);

        try {
            outcome = process(payload);
        } finally {
            // La durée est enregistrée même en échec : un rollback lent est un symptôme utile.
            meterRegistry.timer(TIMER, "outcome", outcome)
                    .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);

            // Les threads du conteneur JMS sont réutilisés : sans purge, le message suivant
            // hériterait des identifiants du précédent.
            MDC.remove(MDC_JMS_MESSAGE_ID);
            MDC.remove(MDC_MESSAGE_ID);
            MDC.remove(MDC_REFERENCE);
        }
    }


    /** @return l'issue du traitement, utilisée comme étiquette du chronomètre */
    private String process(String payload) {

        PaymentMessageEvent event;

        try {
            event = jsonMapper.readValue(payload, PaymentMessageEvent.class);
        } catch (JacksonException e) {
            // Erreur définitive : un rejeu produirait exactement la même erreur.
            return rejectPermanently(UNKNOWN + "-" + UUID.randomUUID(), UNKNOWN, UNKNOWN, payload,
                    "Payload JSON illisible : " + e.getMessage());
        }

        MDC.put(MDC_MESSAGE_ID, hasText(event.getMessageId()) ? event.getMessageId() : UNKNOWN);
        MDC.put(MDC_REFERENCE, hasText(event.getReference()) ? event.getReference() : UNKNOWN);

        Set<ConstraintViolation<PaymentMessageEvent>> violations = validator.validate(event);

        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + " : " + v.getMessage())
                    .collect(Collectors.joining(", "));

            return rejectPermanently(
                    hasText(event.getMessageId()) ? event.getMessageId() : UNKNOWN + "-" + UUID.randomUUID(),
                    hasText(event.getReference()) ? event.getReference() : UNKNOWN,
                    hasText(event.getMessageType()) ? event.getMessageType() : UNKNOWN,
                    payload,
                    "Validation en échec : " + errors);
        }

        // Toute exception d'ici est considérée comme transitoire : elle remonte,
        // la session JMS effectue un rollback et le broker redélivre le message.
        if (service.saveMessage(event, payload)) {
            receivedCounter.increment();
            log.info("Message paiement sauvegardé : {}", event.getMessageId());
            return OUTCOME_PERSISTED;
        }

        duplicateCounter.increment();
        log.info("Message paiement déjà traité, redélivrance ignorée : {}", event.getMessageId());
        return OUTCOME_DUPLICATE;
    }


    /**
     * Persiste le rejet et acquitte. Si la persistance elle-même échoue (base
     * indisponible), l'exception remonte : le message sera redélivré plutôt que perdu.
     */
    private String rejectPermanently(String messageId, String reference, String messageType,
                                     String payload, String errorMessage) {

        log.error("Message IBM MQ rejeté définitivement ({}) : {}", messageId, errorMessage);

        if (service.savePermanentFailure(messageId, reference, messageType, payload, errorMessage)) {
            rejectedCounter.increment();
            return OUTCOME_REJECTED;
        }

        duplicateCounter.increment();
        return OUTCOME_DUPLICATE;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
