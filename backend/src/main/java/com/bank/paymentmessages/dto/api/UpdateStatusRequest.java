package com.bank.paymentmessages.dto.api;

import com.bank.paymentmessages.entity.PaymentMessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /{id}/status}.
 * <p>
 * Un objet et non une chaîne JSON brute : le statut est validé par {@code @Valid}, le
 * champ {@code reason} documente une intervention manuelle, et le contrat reste extensible
 * sans casser les appelants.
 */
@Schema(description = "Changement de statut d'un message")
public record UpdateStatusRequest(

        @NotNull(message = "Le statut cible est obligatoire")
        @Schema(description = "Statut cible, compatible avec la machine à états", example = "PROCESSED")
        PaymentMessageStatus status,

        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
        @Schema(description = "Motif de l'intervention. Conservé comme message d'erreur pour un "
                + "statut d'échec, tracé dans les logs dans tous les cas.",
                example = "Rejeu manuel après correction du référentiel bénéficiaire")
        String reason) {
}
