package com.bank.paymentmessages.dto;

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

    @Schema(example = "RECEIVED")
    private String status;

    @Schema(example = "{\"amount\":100}")
    private String payload;

    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}