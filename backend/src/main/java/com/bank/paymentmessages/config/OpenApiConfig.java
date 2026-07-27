package com.bank.paymentmessages.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Métadonnées du contrat OpenAPI exposé par Swagger UI ({@code /swagger-ui.html}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentMessagesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Messages API")
                        .version("1.0.0")
                        .description("Consultation et gestion des messages de paiement consommés depuis IBM MQ."));
    }
}
