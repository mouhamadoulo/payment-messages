package com.bank.paymentmessages.dto.api;

/**
 * Exposition en lecture seule de la configuration MQ utile à l'IHM.
 * Ne contient volontairement aucun secret (ni user ni password).
 */
public record MqConfigDto(
        String queue,
        String dlqQueue,
        String queueManager,
        String channel
) {}
