package com.bank.paymentmessages.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Active le cache applicatif. Le seul cache déclaré est {@code messageStats} : l'agrégat
 * par statut est la requête la plus coûteuse du système et la plus souvent appelée par le
 * front (dashboard, liste, après chaque action unitaire). Il est mis en cache quelques
 * secondes ({@code spring.cache.caffeine.spec}) et invalidé par toute écriture, ce qui
 * borne l'incohérence à la fenêtre de TTL en cas d'écriture par une autre instance.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
