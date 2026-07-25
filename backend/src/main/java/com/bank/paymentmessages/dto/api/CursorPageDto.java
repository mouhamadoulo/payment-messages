package com.bank.paymentmessages.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Page issue d'une pagination par curseur : ni {@code COUNT(*)} ni {@code OFFSET}, donc un
 * coût constant quelle que soit la profondeur. {@code nextCursor} est opaque et se repasse
 * tel quel dans l'appel suivant.
 */
@Schema(description = "Page de messages paginée par curseur (keyset)")
public record CursorPageDto<T>(
        List<T> content,
        @Schema(description = "Curseur à repasser pour obtenir la page suivante", example = "MjAyNi0wNy0yNVQxMDoxNTozMFo=|42")
        String nextCursor,
        @Schema(description = "Vrai s'il reste des messages après cette page")
        boolean hasNext) {
}
