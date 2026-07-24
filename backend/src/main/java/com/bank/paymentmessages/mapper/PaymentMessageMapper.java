package com.bank.paymentmessages.mapper;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;

import java.time.LocalDateTime;

public final class PaymentMessageMapper {

    private PaymentMessageMapper() {
    }

    public static PaymentMessageDto toDto(PaymentMessage entity) {

        return PaymentMessageDto.builder()
                .id(entity.getId())
                .messageId(entity.getMessageId())
                .reference(entity.getReference())
                .messageType(entity.getMessageType())
                .status(entity.getStatus())
                .payload(entity.getPayload())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .receivedAt(entity.getReceivedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static PaymentMessage toEntity(PaymentMessageEvent event, String rawPayload) {

        return PaymentMessage.builder()
                .messageId(event.getMessageId())
                .reference(event.getReference())
                .messageType(event.getMessageType())
                .status(event.getStatus() != null ? event.getStatus() : PaymentMessageStatus.RECEIVED)
                .payload(rawPayload)
                .retryCount(0)
                .receivedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}