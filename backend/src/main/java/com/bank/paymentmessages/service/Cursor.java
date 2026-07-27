package com.bank.paymentmessages.service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Curseur de pagination keyset : le couple {@code (receivedAt, id)} de la dernière ligne
 * rendue, sérialisé en Base64 pour rester opaque côté client.
 */
public record Cursor(OffsetDateTime receivedAt, Long id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = receivedAt + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param cursor curseur reçu du client, {@code null} ou vide pour la première page
     * @throws IllegalArgumentException si le curseur est illisible (→ 400 plutôt que 500)
     */
    public static Cursor decode(String cursor) {

        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(SEPARATOR);
            if (separator < 0) {
                throw new IllegalArgumentException("Curseur de pagination invalide");
            }
            return new Cursor(
                    OffsetDateTime.parse(raw.substring(0, separator)),
                    Long.valueOf(raw.substring(separator + 1)));

        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new IllegalArgumentException("Curseur de pagination invalide");
        }
    }
}
