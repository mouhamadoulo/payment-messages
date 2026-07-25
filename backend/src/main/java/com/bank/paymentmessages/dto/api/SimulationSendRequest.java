package com.bank.paymentmessages.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande de dépôt de messages de test sur la file d'entrée.
 * <p>
 * La destination n'est <b>pas</b> un paramètre : les messages partent toujours sur
 * {@code ibm.mq.queue}, la seule file que l'application consomme. Laisser l'appelant la
 * choisir aurait ouvert l'écriture sur les autres destinations du gestionnaire de files,
 * pour un besoin qui n'existe pas — un dépôt ailleurs ne produirait rien d'observable.
 * <p>
 * Le payload part <b>tel quel</b> sur la file : c'est le consommateur applicatif qui le
 * désérialise et le valide. Un payload volontairement illisible est donc un cas d'usage
 * légitime — il permet d'observer le rejet définitif en {@code FAILED}.
 */
@Schema(description = "Dépôt de messages de test sur la file d'entrée IBM MQ")
public record SimulationSendRequest(

        @NotBlank(message = "payload obligatoire")
        @Size(max = 65_536, message = "payload limité à 64 Ko")
        @Schema(description = "Corps du message publié tel quel sur la file")
        String payload,

        @Min(value = 1, message = "count doit valoir au moins 1")
        @Schema(description = "Nombre de copies à publier", defaultValue = "1")
        Integer count,

        @Min(value = 1, message = "ratePerSecond doit valoir au moins 1")
        @Schema(description = "Cadence de publication en messages par seconde", defaultValue = "20")
        Integer ratePerSecond,

        @Schema(description = "Réécrit le champ messageId de chaque copie. Sans cela l'ingestion "
                + "traite les copies comme des redélivrances du même message et n'en conserve "
                + "qu'une seule (idempotence sur messageId).", defaultValue = "true")
        Boolean uniqueIds) {

    /** Valeurs par défaut : un champ absent du corps JSON arrive à {@code null}. */
    public SimulationSendRequest {
        count = count == null ? 1 : count;
        ratePerSecond = ratePerSecond == null ? 20 : ratePerSecond;
        uniqueIds = uniqueIds == null || uniqueIds;
    }
}
