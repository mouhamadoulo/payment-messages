package com.bank.paymentmessages.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Politique de partage entre origines pour {@code /api/**}.
 * <p>
 * En développement comme en conteneur, le front est servi derrière un proxy de même origine
 * (proxy du dev-server Angular, bloc {@code location /api/} de nginx) : le navigateur n'émet
 * alors aucune requête préalable. La politique reste néanmoins déclarée pour un appel direct
 * au port de l'API depuis une autre origine, et borne explicitement les origines admises.
 * <p>
 * La source de configuration n'est volontairement <b>pas</b> exposée en bean : le
 * {@code HandlerMappingIntrospector} de Spring MVC implémente lui aussi
 * {@code CorsConfigurationSource} et rendrait l'injection ambiguë.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    /**
     * Filtre plutôt qu'annotation : {@code @CrossOrigin} ne couvrirait pas les requêtes
     * préalables {@code OPTIONS}, et la politique resterait dispersée sur les contrôleurs.
     */
    @Bean
    public CorsFilter corsFilter(CorsProperties properties) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "ETag"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsFilter(source);
    }
}
