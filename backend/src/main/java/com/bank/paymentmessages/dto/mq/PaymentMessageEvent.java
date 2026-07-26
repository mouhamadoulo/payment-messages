package com.bank.paymentmessages.dto.mq;

import com.bank.paymentmessages.entity.PaymentMessageStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Contrat d'entrée de la file de paiements.
 * <p>
 * Les bornes de taille ne sont pas décoratives : {@code message_id}, {@code reference} et
 * {@code message_type} sont des {@code VARCHAR(255)}. Sans elles, une valeur plus longue
 * passe la validation puis casse à l'{@code INSERT} — une violation d'intégrité que
 * l'ingestion ne sait pas distinguer d'une panne, donc traitée comme transitoire :
 * rollback de session et redélivrance en boucle. Les borner ici fait de ce cas un rejet
 * <b>définitif</b>, persisté en {@code FAILED} et acquitté.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMessageEvent {

    /** Longueur des colonnes texte correspondantes (cf. {@code db/migration/V1}). */
    public static final int MAX_LENGTH = 255;

    @NotBlank(message = "messageId obligatoire")
    @Size(max = MAX_LENGTH, message = "messageId limité à " + MAX_LENGTH + " caractères")
    private String messageId;

    @NotBlank(message = "messageType obligatoire")
    @Size(max = MAX_LENGTH, message = "messageType limité à " + MAX_LENGTH + " caractères")
    private String messageType;

    @NotBlank(message = "reference obligatoire")
    @Size(max = MAX_LENGTH, message = "reference limitée à " + MAX_LENGTH + " caractères")
    private String reference;

    /**
     * {@code @Valid} déclenche la cascade : sans elle, les contraintes déclarées dans
     * {@link Payment} ne seraient jamais évaluées et un bloc vide ou un montant négatif
     * entrerait en base.
     */
    @NotNull
    @Valid
    private Payment payment;

    private Debtor debtor;

    private Creditor creditor;

    @NotNull(message = "status obligatoire")
    private PaymentMessageStatus status;

    private LocalDateTime createdAt;
}