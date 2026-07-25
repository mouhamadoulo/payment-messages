package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.config.JacksonConfig;
import com.bank.paymentmessages.dto.mq.Payment;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMessageListenerTest {

    /** Identifiant technique du message JMS, alimente le MDC. */
    private static final String JMS_MESSAGE_ID = "ID:414d5120514d31";

    @Mock
    private PaymentMessageService service;

    /** Mapper construit avec le customizer applicatif, comme celui injecté en production. */
    private final JsonMapper jsonMapper = paymentJsonMapper();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private Validator validator;
    private PaymentMessageListener listener;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
        listener = new PaymentMessageListener(service, jsonMapper, validator, meterRegistry);
    }

    @Test
    void shouldPersistValidMessage() {
        String payload = validPayload("uuid-1");
        when(service.saveMessage(any(PaymentMessageEvent.class), eq(payload))).thenReturn(true);

        listener.receive(payload, JMS_MESSAGE_ID);

        verify(service).saveMessage(any(PaymentMessageEvent.class), eq(payload));
        verify(service, never()).savePermanentFailure(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldPersistUnreadablePayloadAsFailedInsteadOfDroppingIt() {
        when(service.savePermanentFailure(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        listener.receive("{ ceci n'est pas du JSON", JMS_MESSAGE_ID);

        verify(service, never()).saveMessage(any(), anyString());
        verify(service).savePermanentFailure(anyString(), anyString(), anyString(),
                eq("{ ceci n'est pas du JSON"), anyString());
        assertThat(meterRegistry.counter("payment.mq.messages.rejected").count()).isEqualTo(1d);
    }

    @Test
    void shouldPersistInvalidMessageAsFailedInsteadOfDroppingIt() {
        // reference et messageType manquants : la validation echoue.
        String payload = """
                {"messageId":"uuid-2","status":"RECEIVED","payment":{"amount":10}}
                """;
        when(service.savePermanentFailure(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        listener.receive(payload, JMS_MESSAGE_ID);

        verify(service, never()).saveMessage(any(), anyString());
        verify(service).savePermanentFailure(eq("uuid-2"), eq("UNKNOWN"), eq("UNKNOWN"),
                eq(payload), anyString());
    }

    @Test
    void shouldPropagateTransientFailureSoThatMessageIsRedelivered() {
        String payload = validPayload("uuid-3");
        when(service.saveMessage(any(PaymentMessageEvent.class), eq(payload)))
                .thenThrow(new DataAccessResourceFailureException("base indisponible"));

        assertThatThrownBy(() -> listener.receive(payload, JMS_MESSAGE_ID))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void shouldPropagateFailureWhenRejectCannotBePersisted() {
        when(service.savePermanentFailure(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("base indisponible"));

        assertThatThrownBy(() -> listener.receive("{ ceci n'est pas du JSON", JMS_MESSAGE_ID))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void shouldIgnoreRedeliveredDuplicate() {
        String payload = validPayload("uuid-4");
        when(service.saveMessage(any(PaymentMessageEvent.class), eq(payload))).thenReturn(false);

        listener.receive(payload, JMS_MESSAGE_ID);

        assertThat(meterRegistry.counter("payment.mq.messages.duplicates").count()).isEqualTo(1d);
    }

    @Test
    void shouldTolerateUnknownFieldsAddedByUpstreamEmitter() {
        String payload = validPayload("uuid-5").replace("\"status\"", "\"champInconnu\":\"x\",\"status\"");
        when(service.saveMessage(any(PaymentMessageEvent.class), eq(payload))).thenReturn(true);

        listener.receive(payload, JMS_MESSAGE_ID);

        verify(service).saveMessage(any(PaymentMessageEvent.class), eq(payload));
    }

    @Test
    void shouldRecordProcessingDurationPerOutcome() {
        String payload = validPayload("uuid-6");
        when(service.saveMessage(any(PaymentMessageEvent.class), eq(payload))).thenReturn(true);

        listener.receive(payload, JMS_MESSAGE_ID);

        assertThat(meterRegistry.timer("payment.mq.processing", "outcome", "persisted").count()).isEqualTo(1L);
        assertThat(meterRegistry.counter("payment.mq.messages.received").count()).isEqualTo(1d);
    }

    @Test
    void shouldRecordProcessingDurationEvenWhenTreatmentFails() {
        String payload = validPayload("uuid-7");
        when(service.saveMessage(any(PaymentMessageEvent.class), eq(payload)))
                .thenThrow(new DataAccessResourceFailureException("base indisponible"));

        assertThatThrownBy(() -> listener.receive(payload, JMS_MESSAGE_ID))
                .isInstanceOf(DataAccessResourceFailureException.class);

        // Un rollback lent est un symptôme utile : la durée est mesurée en échec aussi.
        assertThat(meterRegistry.timer("payment.mq.processing", "outcome", "error").count()).isEqualTo(1L);
    }

    /**
     * Les threads du conteneur JMS sont réutilisés : un MDC non purgé attacherait les
     * identifiants de ce message aux logs du suivant.
     */
    @Test
    void shouldClearCorrelationContextAfterProcessing() {
        String payload = validPayload("uuid-8");
        when(service.saveMessage(any(PaymentMessageEvent.class), eq(payload))).thenReturn(true);

        listener.receive(payload, JMS_MESSAGE_ID);

        assertThat(MDC.get("messageId")).isNull();
        assertThat(MDC.get("reference")).isNull();
        assertThat(MDC.get("jmsMessageId")).isNull();
    }

    private static JsonMapper paymentJsonMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JacksonConfig().paymentJsonMapperCustomizer().customize(builder);
        return builder.build();
    }

    private static String validPayload(String messageId) {
        PaymentMessageEvent event = PaymentMessageEvent.builder()
                .messageId(messageId)
                .reference("REF-001")
                .messageType("PAYMENT_REQUEST")
                .status(PaymentMessageStatus.RECEIVED)
                .payment(Payment.builder().build())
                .build();
        return paymentJsonMapper().writeValueAsString(event);
    }
}
