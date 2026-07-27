package com.bank.paymentmessages.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Attache un identifiant de corrélation à chaque requête HTTP.
 * <p>
 * L'identifiant est repris de l'en-tête {@code X-Request-Id} s'il est fourni (chaîne
 * d'appels traversant plusieurs services), sinon généré. Il est publié dans le MDC —
 * donc dans toutes les lignes de log de la requête (cf. {@code logging.pattern.level}) —
 * renvoyé au client dans le même en-tête, et repris comme {@code correlationId} des
 * réponses 500 : c'est ce qui permet de relier un incident signalé par un utilisateur
 * aux logs correspondants sans exposer le détail technique de l'erreur.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** Clé MDC ; reprise telle quelle dans le motif de log. */
    public static final String REQUEST_ID = "requestId";

    /** Au-delà, l'en-tête est ignoré : un client ne doit pas pouvoir polluer les logs. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = sanitize(request.getHeader(HEADER));

        MDC.put(REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Les threads du conteneur sont recyclés : un MDC non purgé fuiterait
            // l'identifiant de la requête précédente.
            MDC.remove(REQUEST_ID);
        }
    }

    /** @return l'identifiant fourni s'il est exploitable, un identifiant neuf sinon */
    public static String currentOrNew() {
        String current = MDC.get(REQUEST_ID);
        return current != null ? current : UUID.randomUUID().toString();
    }

    private static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        String cleaned = candidate.replaceAll("[^A-Za-z0-9._-]", "");
        return cleaned.isEmpty() ? UUID.randomUUID().toString() : cleaned;
    }
}
