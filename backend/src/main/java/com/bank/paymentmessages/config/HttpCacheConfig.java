package com.bank.paymentmessages.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Validation conditionnelle des réponses de lecture par {@code ETag}.
 * <p>
 * Le dashboard rappelle {@code /stats} et la liste après chaque action ; sans validateur,
 * chaque rappel retransmet un corps identique. Le filtre calcule une empreinte de la
 * réponse et répond 304 sans corps quand le client renvoie le même {@code If-None-Match}.
 * <p>
 * Volontairement limité aux routes de messages : le filtre met la réponse en tampon
 * mémoire avant de l'empreinter, coût inutile sur les autres routes (et incompatible avec
 * un flux d'événements).
 */
@Configuration
public class HttpCacheConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {

        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());

        registration.addUrlPatterns("/api/v1/messages", "/api/v1/messages/*");
        registration.setName("etagFilter");
        return registration;
    }
}
