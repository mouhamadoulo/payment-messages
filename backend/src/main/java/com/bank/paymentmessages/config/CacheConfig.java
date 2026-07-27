package com.bank.paymentmessages.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Active le cache applicatif. Trois caches, tous des agrégats de lecture mis en cache
 * quelques secondes ({@code spring.cache.caffeine.spec}) :
 * <ul>
 *   <li>{@code messageStats} — comptage par statut, requête la plus coûteuse du système et la
 *       plus souvent appelée par le front (dashboard, liste, après chaque action unitaire) ;</li>
 *   <li>{@code dashboardStats} — volume horaire, répartitions et alertes du tableau de bord,
 *       soit cinq agrégations qui seraient sinon rejouées à chaque affichage ;</li>
 *   <li>{@code messageTypes} — types distincts, pour le sélecteur de la barre de filtres.</li>
 * </ul>
 * Les deux premiers sont invalidés par les écritures de l'API, ce qui borne l'incohérence à la
 * fenêtre de TTL en cas d'écriture par une autre instance. {@code dashboardStats} n'est
 * volontairement <b>pas</b> invalidé par l'ingestion : sous un flux soutenu, il serait vidé à
 * chaque message et ne servirait plus à rien.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
