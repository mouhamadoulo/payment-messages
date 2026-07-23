package com.bank.paymentmessages.mapper;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMessageMapperTest {

    @Test
    void shouldMapEntityToDto() {
        LocalDateTime receivedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
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
                .processedAt(null)
                .build();

        PaymentMessageDto dto = PaymentMessageMapper.toDto(entity);

        assertThat(dto.getId()).isEqualTo(2L);
        assertThat(dto.getPayload()).isNull();
        assertThat(dto.getReceivedAt()).isNull();
        assertThat(dto.getProcessedAt()).isNull();
        assertThat(dto.getUpdatedAt()).isNull();
    }
}
