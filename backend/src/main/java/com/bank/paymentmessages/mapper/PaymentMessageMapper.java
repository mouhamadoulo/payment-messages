package com.bank.paymentmessages.mapper;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.repository.PaymentMessageSummary;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

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
                .payloadSize(entity.getPayloadSize())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .receivedAt(entity.getReceivedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** Vue de liste : la projection ne porte pas le payload, seulement sa taille. */
    public static PaymentMessageSummaryDto toSummaryDto(PaymentMessageSummary summary) {

        return PaymentMessageSummaryDto.builder()
                .id(summary.id())
                .messageId(summary.messageId())
                .reference(summary.reference())
                .messageType(summary.messageType())
                .status(summary.status())
                .payloadSize(summary.payloadSize())
                .retryCount(summary.retryCount())
                .errorMessage(summary.errorMessage())
                .receivedAt(summary.receivedAt())
                .updatedAt(summary.updatedAt())
                .build();
    }

    public static PaymentMessage toEntity(PaymentMessageEvent event, String rawPayload) {

        OffsetDateTime now = OffsetDateTime.now();

        return PaymentMessage.builder()
                .messageId(event.getMessageId())
                .reference(event.getReference())
                .messageType(event.getMessageType())
                .status(event.getStatus() != null ? event.getStatus() : PaymentMessageStatus.RECEIVED)
                .payload(rawPayload)
                .payloadSize(payloadSize(rawPayload))
                .retryCount(0)
                .receivedAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Entité représentant un message rejeté définitivement (payload illisible ou
     * validation en échec) : le payload brut est conservé pour permettre le rejeu.
     */
    public static PaymentMessage toFailedEntity(String messageId, String reference, String messageType,
                                                String rawPayload, String errorMessage) {

        OffsetDateTime now = OffsetDateTime.now();

        return PaymentMessage.builder()
                .messageId(messageId)
                .reference(reference)
                .messageType(messageType)
                .status(PaymentMessageStatus.FAILED)
                .payload(rawPayload)
                .payloadSize(payloadSize(rawPayload))
                .errorMessage(errorMessage)
                .retryCount(0)
                .receivedAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Taille en octets du payload brut, mesurée une fois à l'ingestion : ni la base ni le
     * client n'ont besoin du payload complet pour l'afficher dans une liste.
     */
    private static int payloadSize(String rawPayload) {
        return rawPayload == null ? 0 : rawPayload.getBytes(StandardCharsets.UTF_8).length;
    }
}
