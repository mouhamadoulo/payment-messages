package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessageStatus;

import java.time.OffsetDateTime;

/**
 * Projection de liste : toutes les colonnes utiles à un tableau, <b>sans le payload</b>.
 * <p>
 * Spring Data traduit ce type en {@code select new ...(p.id, p.messageId, …)} : la colonne
 * {@code payload} ({@code TEXT}) n'est plus lue pour une page de liste. Une page de 100
 * lignes ne transporte donc plus 100 payloads depuis la base ni sur le réseau ; le payload
 * complet reste servi par {@code GET /messages/{id}}. La taille affichée vient de la
 * colonne {@code payload_size} calculée à l'ingestion.
 */
public record PaymentMessageSummary(
        Long id,
        String messageId,
        String reference,
        String messageType,
        PaymentMessageStatus status,
        Integer payloadSize,
        Integer retryCount,
        String errorMessage,
        OffsetDateTime receivedAt,
        OffsetDateTime updatedAt) {
}
