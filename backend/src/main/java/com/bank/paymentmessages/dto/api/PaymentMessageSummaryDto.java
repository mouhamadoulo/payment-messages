package com.bank.paymentmessages.dto.api;

import com.bank.paymentmessages.entity.PaymentMessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Contrat de sortie des listes : mêmes métadonnées que {@link PaymentMessageDto} mais
 * <b>sans le payload</b>, remplacé par sa taille en octets. Le payload complet est servi
 * par {@code GET /api/v1/messages/{id}}.
 */
@Data
@Builder
@Schema(description = "Message de paiement, vue de liste (sans payload)")
public class PaymentMessageSummaryDto {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "8db6d2b4-df72-4b5b-a2cf-f9983f55d901")
    private String messageId;

    @Schema(example = "PAY-20260723-001")
    private String reference;

    @Schema(example = "PAYMENT_REQUEST")
    private String messageType;

    @Schema(description = "Statut du traitement",
            example = "RECEIVED",
            allowableValues = {
                    "RECEIVED",
                    "PROCESSED",
                    "FAILED",
                    "DEAD_LETTER"
            })
    private PaymentMessageStatus status;

    @Schema(description = "Taille du payload brut en octets", example = "412")
    private Integer payloadSize;

    @Schema(example = "0")
    private Integer retryCount;

    @Schema(example = "null")
    private String errorMessage;

    private OffsetDateTime receivedAt;

    private OffsetDateTime updatedAt;
}
