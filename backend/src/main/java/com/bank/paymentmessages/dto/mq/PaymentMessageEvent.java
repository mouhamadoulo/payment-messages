package com.bank.paymentmessages.dto.mq;

import com.bank.paymentmessages.entity.PaymentMessageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMessageEvent {

    @NotBlank(message = "messageId obligatoire")
    private String messageId;

    @NotBlank(message = "messageType obligatoire")
    private String messageType;

    @NotBlank(message = "reference obligatoire")
    private String reference;

    @NotNull
    private Payment payment;

    private Debtor debtor;

    private Creditor creditor;

    @NotNull(message = "status obligatoire")
    private PaymentMessageStatus status;

    private LocalDateTime createdAt;
}