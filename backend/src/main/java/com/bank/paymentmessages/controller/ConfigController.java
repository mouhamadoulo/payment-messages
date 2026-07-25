package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.MqConfigDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expose la configuration MQ non sensible (noms de files, gestionnaire, canal)
 * pour affichage dans l'IHM. Aucun identifiant ni mot de passe n'est retourné.
 */
@RestController
@RequestMapping("/api/v1/config")
@Tag(name = "Configuration", description = "Configuration MQ exposée à l'IHM (sans secret)")
@SecurityRequirement(name = "bearerAuth")
public class ConfigController {

    private final MqConfigDto mqConfig;

    public ConfigController(
            @Value("${ibm.mq.queue}") String queue,
            @Value("${ibm.mq.dlq-queue}") String dlqQueue,
            @Value("${ibm.mq.queue-manager}") String queueManager,
            @Value("${ibm.mq.channel}") String channel) {
        this.mqConfig = new MqConfigDto(queue, dlqQueue, queueManager, channel);
    }

    @GetMapping
    @Operation(summary = "Retourne la configuration MQ utile à l'IHM (sans secret)")
    public MqConfigDto getMqConfig() {
        return mqConfig;
    }
}
