package com.bank.paymentmessages.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * File visée et bornes de la simulation d'envoi, pour que l'IHM ne les redéclare pas.
 * Le serveur reste l'autorité : une demande hors bornes est refusée en 400.
 */
@Schema(description = "Configuration de la simulation d'envoi exposée à l'IHM")
public record SimulationConfigDto(

        @Schema(description = "Faux si la simulation est désactivée sur cet environnement")
        boolean enabled,

        @Schema(description = "File d'entrée sur laquelle les messages de test sont déposés. "
                + "Elle n'est pas choisie par l'appelant : c'est celle que l'application "
                + "consomme (ibm.mq.queue).")
        String queue,

        @Schema(description = "Nombre maximal de messages par envoi") int maxCount,

        @Schema(description = "Cadence maximale, en messages par seconde") int maxRate) {}
