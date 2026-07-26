package com.bank.paymentmessages.mapper;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.repository.PaymentMessageSummary;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMessageMapperTest {

    @Test
    void shouldMapEntityToDto() {
        OffsetDateTime receivedAt = OffsetDateTime.of(2026, 7, 23, 10, 0, 0, 0, ZoneOffset.UTC);
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L)
                .messageId("msg-123")
                .reference("REF-001")
                .messageType("PAYMENT_REQUEST")
                .status(PaymentMessageStatus.RECEIVED)
                .payload("{\"amount\":100}")
                .retryCount(0)
                .receivedAt(receivedAt)
                .build();

        PaymentMessageDto dto = PaymentMessageMapper.toDto(entity);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getMessageId()).isEqualTo("msg-123");
        assertThat(dto.getReference()).isEqualTo("REF-001");
        assertThat(dto.getMessageType()).isEqualTo("PAYMENT_REQUEST");
        assertThat(dto.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(dto.getPayload()).isEqualTo("{\"amount\":100}");
        assertThat(dto.getReceivedAt()).isEqualTo(receivedAt);
    }

    @Test
    void shouldMapEntityWithNullFields() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(2L)
                .messageId("msg-456")
                .reference("REF-002")
                .status(PaymentMessageStatus.PROCESSED)
                .payload(null)
                .receivedAt(null)
                .build();

        PaymentMessageDto dto = PaymentMessageMapper.toDto(entity);

        assertThat(dto.getId()).isEqualTo(2L);
        assertThat(dto.getPayload()).isNull();
        assertThat(dto.getReceivedAt()).isNull();
        assertThat(dto.getUpdatedAt()).isNull();
    }

    @Test
    void shouldMeasurePayloadSizeAtIngestion() {
        PaymentMessageEvent event = PaymentMessageEvent.builder()
                .messageId("msg-789").reference("REF-003").messageType("PAYMENT_REQUEST")
                .status(PaymentMessageStatus.RECEIVED).build();

        // 'é' occupe deux octets en UTF-8 : la taille est mesurée en octets, pas en caractères.
        PaymentMessage entity = PaymentMessageMapper.toEntity(event, "{\"label\":\"é\"}");

        assertThat(entity.getPayloadSize()).isEqualTo(14);
    }

    @Test
    void shouldMeasurePayloadSizeOfRejectedMessage() {
        PaymentMessage entity = PaymentMessageMapper.toFailedEntity(
                "msg-790", "REF-004", "PAYMENT_REQUEST", "{not-json", "Payload JSON illisible");

        assertThat(entity.getPayloadSize()).isEqualTo(9);
        assertThat(entity.getStatus()).isEqualTo(PaymentMessageStatus.FAILED);
    }

    /**
     * Le rejet d'un message trop long ne doit pas casser à son tour sur la longueur des
     * colonnes : ce serait une violation d'intégrité, donc une redélivrance en boucle
     * pour un message qu'aucun rejeu ne peut sauver.
     */
    @Test
    void shouldTruncateOverlongIdentifiersOfRejectedMessage() {
        String tropLong = "X".repeat(300);

        PaymentMessage entity = PaymentMessageMapper.toFailedEntity(
                tropLong, tropLong, tropLong, "{}", "Validation en échec");

        assertThat(entity.getMessageId()).hasSize(255).isEqualTo(tropLong.substring(0, 255));
        assertThat(entity.getReference()).hasSize(255);
        assertThat(entity.getMessageType()).hasSize(255);
    }

    /**
     * Troncature déterministe, sans suffixe aléatoire : une redélivrance du même message
     * doit retomber sur le même identifiant pour que l'idempotence l'acquitte au lieu
     * d'insérer une ligne par tentative.
     */
    @Test
    void shouldTruncateDeterministicallySoThatRedeliveryStaysIdempotent() {
        String tropLong = "X".repeat(300);

        assertThat(PaymentMessageMapper.toFailedEntity(tropLong, "R", "T", "{}", "ko").getMessageId())
                .isEqualTo(PaymentMessageMapper.toFailedEntity(tropLong, "R", "T", "{}", "ko").getMessageId());
    }

    @Test
    void shouldMapSummaryWithoutPayload() {
        OffsetDateTime receivedAt = OffsetDateTime.of(2026, 7, 23, 10, 0, 0, 0, ZoneOffset.UTC);
        PaymentMessageSummary summary = new PaymentMessageSummary(
                3L, "msg-3", "REF-005", "PAYMENT_REQUEST", PaymentMessageStatus.FAILED,
                412, 2, "timeout", receivedAt, receivedAt);

        PaymentMessageSummaryDto dto = PaymentMessageMapper.toSummaryDto(summary);

        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getPayloadSize()).isEqualTo(412);
        assertThat(dto.getRetryCount()).isEqualTo(2);
        assertThat(dto.getErrorMessage()).isEqualTo("timeout");
        assertThat(dto.getReceivedAt()).isEqualTo(receivedAt);
    }
}
