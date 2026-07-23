package com.bank.paymentmessages.mapper;

import com.bank.paymentmessages.dto.PaymentMessageDto;
import com.bank.paymentmessages.entity.PaymentMessage;

public final class PaymentMessageMapper {

    private PaymentMessageMapper() {
    }

    public static PaymentMessageDto toDto(PaymentMessage entity) {

        return PaymentMessageDto.builder()
                .id(entity.getId())
                .messageId(entity.getMessageId())
                .reference(entity.getReference())
                .status(entity.getStatus())
                .payload(entity.getPayload())
                .receivedAt(entity.getReceivedAt())
                .processedAt(entity.getProcessedAt())
                .build();
    }
}