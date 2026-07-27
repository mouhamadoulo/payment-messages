package com.bank.paymentmessages.service;

import com.bank.paymentmessages.entity.PaymentMessageStatus;

import java.time.OffsetDateTime;

/**
 * Critères de recherche d'une liste de messages.
 * <p>
 * Les quatre filtres sont désormais appliqués <b>côté serveur</b> : le front filtrait le
 * texte et le type sur la page affichée, alors que les compteurs de statut venaient de
 * l'agrégat global — l'utilisateur lisait « FAILED 1 240 » puis trois lignes après filtrage.
 * Regrouper les critères ici évite de propager quatre paramètres nus dans le contrôleur,
 * le service et le repository, et garantit que la liste et ses compteurs sont calculés sur
 * exactement le même prédicat.
 *
 * @param status        statut exact, {@code null} = tous
 * @param receivedAfter borne basse de réception exclusive, {@code null} = pas de borne
 * @param type          type de message exact, {@code null} = tous
 * @param text          fragment recherché dans la référence, le messageId ou le type
 */
public record MessageQuery(PaymentMessageStatus status,
                           OffsetDateTime receivedAfter,
                           String type,
                           String text) {

    /**
     * Normalise les entrées HTTP : une chaîne vide ou blanche vaut « pas de filtre », et la
     * recherche texte est passée en minuscules une seule fois — la requête compare
     * {@code lower(colonne)} au motif, qui doit donc déjà l'être.
     */
    public static MessageQuery of(PaymentMessageStatus status, OffsetDateTime receivedAfter,
                                  String type, String text) {
        return new MessageQuery(status, receivedAfter, trimToNull(type), lowerOrNull(text));
    }

    /** Aucun critère : la liste peut emprunter le chemin non filtré, et l'agrégat son cache. */
    public boolean isEmpty() {
        return status == null && receivedAfter == null && type == null && text == null;
    }

    /** Vrai si un critère autre que le statut est actif (cas des compteurs par statut). */
    public boolean hasNonStatusCriteria() {
        return receivedAfter != null || type != null || text != null;
    }

    /**
     * Motif {@code LIKE} de la recherche texte, {@code null} si elle est absente.
     * Recherche « contient » : sans index trigramme, elle coûte un parcours — acceptable
     * ici, et de toute façon inévitable pour un fragment en milieu de chaîne.
     */
    public String textPattern() {
        return text == null ? null : "%" + text + "%";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String lowerOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }
}
