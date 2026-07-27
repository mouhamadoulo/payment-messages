package com.bank.paymentmessages.service;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * État d'un rejeu massif. Le rejeu s'exécute en tâche de fond : l'appelant HTTP reçoit un
 * {@code 202 Accepted} avec un identifiant de tâche et suit l'avancement, au lieu de tenir
 * une requête ouverte plusieurs minutes.
 */
@Schema(description = "État d'un rejeu massif des messages FAILED")
public record BatchRetryTask(
        String taskId,
        State state,
        @Schema(description = "Nombre de messages rejoués à cet instant") int processed,
        @Schema(description = "Vrai si le plafond de sécurité a été atteint avant la fin") boolean truncated,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String error) {

    public enum State { RUNNING, COMPLETED, FAILED }

    public static BatchRetryTask running(String taskId) {
        return new BatchRetryTask(taskId, State.RUNNING, 0, false, OffsetDateTime.now(), null, null);
    }

    public BatchRetryTask progress(int processed) {
        return new BatchRetryTask(taskId, State.RUNNING, processed, false, startedAt, null, null);
    }

    public BatchRetryTask completed(int processed, boolean truncated) {
        return new BatchRetryTask(taskId, State.COMPLETED, processed, truncated, startedAt, OffsetDateTime.now(), null);
    }

    public BatchRetryTask failed(int processed, String error) {
        return new BatchRetryTask(taskId, State.FAILED, processed, false, startedAt, OffsetDateTime.now(), error);
    }
}
