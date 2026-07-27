package com.bank.paymentmessages.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Ajuste le {@code JsonMapper} auto-configuré par Spring Boot au lieu de le remplacer :
 * tous les réglages du framework (dates ISO, modules détectés, {@code JacksonProperties})
 * restent en vigueur.
 * <p>
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} est désactivé : sur un contrat d'entrée bancaire
 * amené à évoluer, un champ ajouté par un émetteur amont ne doit pas faire échouer la
 * désérialisation et envoyer le message en rejet définitif.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer paymentJsonMapperCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
