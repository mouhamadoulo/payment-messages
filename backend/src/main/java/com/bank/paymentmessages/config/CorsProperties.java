package com.bank.paymentmessages.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Origines autorisées à appeler l'API depuis un navigateur ({@code app.cors}).
 * Par défaut le front local ; en conteneur le proxy nginx rend l'appel de même origine.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(

        @DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
}
