package com.bank.paymentmessages.dto.mq;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bloc de paiement du message entrant.
 * <p>
 * Ces contraintes ne sont évaluées que parce que {@link PaymentMessageEvent#getPayment()}
 * porte {@code @Valid} : Bean Validation ne descend pas dans un objet imbriqué sans cascade
 * explicite.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @NotBlank(message = "transactionId obligatoire")
    private String transactionId;

    @NotNull(message = "amount obligatoire")
    @Positive(message = "amount doit être strictement positif")
    private BigDecimal amount;

    @NotBlank(message = "currency obligatoire")
    private String currency;

    @NotNull(message = "executionDate obligatoire")
    private LocalDate executionDate;
}