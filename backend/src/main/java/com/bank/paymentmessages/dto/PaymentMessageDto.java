package com.bank.paymentmessages.dto;

import com.bank.paymentmessages.entity.PaymentMessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Message de paiement")
public class PaymentMessageDto {

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
                    "VALIDATING",
                    "PROCESSING",
                    "PROCESSED",
                    "FAILED",
                    "RETRY_PENDING",
                    "REJECTED",
                    "DEAD_LETTER"
            })
    private PaymentMessageStatus status;

    @Schema(example = "{\"amount\":100}")
    private String payload;

    @Schema(example = "0")
    private Integer retryCount;

    @Schema(example = "null")
    private String errorMessage;

    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;

    private LocalDateTime updatedAt;
}