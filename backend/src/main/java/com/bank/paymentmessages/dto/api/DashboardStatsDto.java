package com.bank.paymentmessages.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Agrégats du tableau de bord, calculés en SQL.
 * <p>
 * Le dashboard dérivait toutes ses visualisations d'un échantillon de 200 messages
 * complets : quelques mégaoctets transférés pour afficher une vingtaine de nombres, et
 * surtout des chiffres <b>faux</b> dès que la table dépassait 200 lignes. Cette réponse
 * pèse quelques centaines d'octets et porte des comptages exacts.
 *
 * @param windowFrom     début de la fenêtre glissante du volume horaire (heure pleine)
 * @param windowTo       fin de la fenêtre (heure pleine suivant l'instant courant)
 * @param windowTotal    nombre de messages reçus sur la fenêtre
 * @param lastReceivedAt réception la plus récente en base, tous statuts
 * @param hourly         24 tranches horaires consécutives, toujours présentes même à zéro
 * @param types          répartition par type de message, sur toute la table
 * @param retries        répartition par nombre de tentatives, sur toute la table
 * @param recentFailures derniers messages FAILED / DEAD_LETTER, sans leur payload
 */
public record DashboardStatsDto(
        OffsetDateTime windowFrom,
        OffsetDateTime windowTo,
        long windowTotal,
        OffsetDateTime lastReceivedAt,
        List<HourlyBucket> hourly,
        List<TypeCount> types,
        RetryBuckets retries,
        List<PaymentMessageSummaryDto> recentFailures) {

    /**
     * @param bucketStart instant de début de la tranche, fuseau inclus : le client affiche
     *                    l'heure sans avoir à deviner celle du serveur
     * @param hour        heure du jour correspondante côté serveur (0–23)
     */
    @Schema(description = "Tranche horaire du volume de réception")
    public record HourlyBucket(OffsetDateTime bucketStart, int hour, long count) {
    }

    @Schema(description = "Nombre de messages pour un type donné")
    public record TypeCount(String messageType, long count) {
    }

    /**
     * Tentatives de rejeu regroupées comme le dashboard les affiche : au-delà de deux, le
     * détail n'apporte rien, seul compte le fait qu'un message s'acharne.
     */
    @Schema(description = "Répartition des messages par nombre de tentatives")
    public record RetryBuckets(long none, long one, long two, long threeOrMore) {
    }
}
