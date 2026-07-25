package com.bank.paymentmessages.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Configuration de la sécurité applicative ({@code app.security}).
 * <p>
 * Les comptes sont déclarés en configuration : ce projet n'a pas de référentiel
 * d'identités, l'authentification sert à fermer l'API, pas à gérer des utilisateurs.
 * Un annuaire réel (LDAP, OAuth2 externe) se substituerait au {@code UserDetailsService}
 * sans toucher au reste de la chaîne.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(

        Jwt jwt,

        /** Comptes autorisés. Sans compte déclaré, aucune authentification n'est possible. */
        @DefaultValue List<User> users,

        /**
         * Laisse Swagger UI et {@code /v3/api-docs} accessibles sans jeton. Pratique en
         * développement, à passer à {@code false} en production : le contrat exposé
         * renseigne un attaquant sur toute la surface de l'API.
         */
        @DefaultValue("true") boolean publicDocs,

        /** Origines autorisées en CORS. Le front passe par un proxy en dev, donc même origine. */
        @DefaultValue("http://localhost:4200") List<String> allowedOrigins) {

    /**
     * Jeton signé en HMAC-SHA256 : le service émet et vérifie lui-même ses jetons, sans
     * serveur d'autorisation externe. Le secret est donc une donnée sensible à part entière.
     */
    public record Jwt(

            /**
             * Secret de signature, 32 octets minimum (contrainte HS256). Fourni par
             * l'environnement, jamais versionné.
             */
            String secret,

            @DefaultValue("1h") Duration expiration,

            @DefaultValue("payment-messages") String issuer) {
    }

    /** Mot de passe préfixé par l'algorithme ({@code {bcrypt}…}, {@code {noop}…} en dev). */
    public record User(String username, String password, @DefaultValue("USER") List<String> roles) {
    }
}
