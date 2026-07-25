package com.bank.paymentmessages.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare le schéma d'authentification dans le contrat OpenAPI, pour que Swagger UI
 * propose le bouton « Authorize » et transmette le jeton obtenu via
 * {@code POST /api/v1/auth/login}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentMessagesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Messages API")
                        .version("1.0.0")
                        .description("Consultation et gestion des messages de paiement consommés depuis IBM MQ. "
                                + "Tous les appels exigent un jeton obtenu sur POST /api/v1/auth/login."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton émis par POST /api/v1/auth/login")));
    }
}
