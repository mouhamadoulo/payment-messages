package com.bank.paymentmessages.service;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * État d'un envoi de test. La publication est cadencée : un envoi de 1 000 messages à
 * 20 msg/s dure près d'une minute, l'appelant HTTP reçoit donc un {@code 202 Accepted}
 * avec un identifiant de suivi plutôt qu'une requête tenue ouverte.
 * <p>
 * Les compteurs portent sur la <b>publication</b>, pas sur le traitement : {@code published}
 * signifie que le broker a accepté le message. Son sort applicatif (persisté, rejeté)
 * s'observe ensuite dans la liste des messages.
 */
@Schema(description = "État d'un envoi de test sur une file IBM MQ")
public record SimulationTask(
        String taskId,
        State state,
        @Schema(description = "File visée") String destination,
        @Schema(description = "Nombre de messages demandés") int total,
        @Schema(description = "Messages traités à cet instant") int sent,
        @Schema(description = "Messages acceptés par le broker") int published,
        @Schema(description = "Publications en échec") int failed,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String error) {

    public enum State { RUNNING, COMPLETED, FAILED }

    public static SimulationTask running(String taskId, String destination, int total) {
        return new SimulationTask(taskId, State.RUNNING, destination, total, 0, 0, 0,
                OffsetDateTime.now(), null, null);
    }

    public SimulationTask progress(int sent, int published, int failed) {
        return new SimulationTask(taskId, State.RUNNING, destination, total, sent, published, failed,
                startedAt, null, null);
    }

    public SimulationTask completed(int sent, int published, int failed) {
        return new SimulationTask(taskId, State.COMPLETED, destination, total, sent, published, failed,
                startedAt, OffsetDateTime.now(), null);
    }

    public SimulationTask failed(int sent, int published, int failed, String error) {
        return new SimulationTask(taskId, State.FAILED, destination, total, sent, published, failed,
                startedAt, OffsetDateTime.now(), error);
    }
}
