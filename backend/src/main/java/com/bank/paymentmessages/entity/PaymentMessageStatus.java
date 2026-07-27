package com.bank.paymentmessages.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cycle de vie d'un message, transitions comprises.
 * <p>
 * Le graphe est déclaré ici plutôt que dispersé dans le service : c'est la seule
 * référence de ce qui est autorisé, et le point d'appui du rejet des changements de
 * statut incohérents ({@code PUT /{id}/status}).
 * <pre>
 *   RECEIVED ──▶ PROCESSED (terminal)
 *      │
 *      └──▶ FAILED ──▶ RECEIVED    (rejeu)
 *                 ├──▶ PROCESSED   (résolution manuelle)
 *                 └──▶ DEAD_LETTER (tentatives épuisées, terminal)
 * </pre>
 * {@code PROCESSED} et {@code DEAD_LETTER} sont terminaux : un message traité ne
 * redevient pas en attente, et un abandon reste tracé — la reprise d'un
 * {@code DEAD_LETTER} passe par la Dead Letter Queue, pas par un retour en base.
 */
public enum PaymentMessageStatus {
    RECEIVED,     // Message consommé depuis MQ et sauvegardé, en attente de traitement
    PROCESSED,    // Traitement terminé avec succès
    FAILED,       // Erreur technique ou métier, rejouable via /retry
    DEAD_LETTER;  // Abandonné après trop de tentatives, republié sur la Dead Letter Queue

    private static final Map<PaymentMessageStatus, Set<PaymentMessageStatus>> ALLOWED =
            new EnumMap<>(PaymentMessageStatus.class);

    static {
        ALLOWED.put(RECEIVED, EnumSet.of(PROCESSED, FAILED));
        ALLOWED.put(FAILED, EnumSet.of(RECEIVED, PROCESSED, DEAD_LETTER));
        ALLOWED.put(PROCESSED, EnumSet.noneOf(PaymentMessageStatus.class));
        ALLOWED.put(DEAD_LETTER, EnumSet.noneOf(PaymentMessageStatus.class));
    }

    /**
     * Un statut identique est accepté et traité comme une absence de changement : rejouer
     * la même commande ne doit pas produire d'erreur.
     */
    public boolean canTransitionTo(PaymentMessageStatus target) {
        return this == target || ALLOWED.get(this).contains(target);
    }

    /** Statuts atteignables depuis celui-ci, hors transition neutre. */
    public Set<PaymentMessageStatus> allowedTransitions() {
        return Set.copyOf(ALLOWED.get(this));
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
