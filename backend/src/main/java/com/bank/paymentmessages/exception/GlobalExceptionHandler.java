package com.bank.paymentmessages.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.OffsetDateTime;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentMessageNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(PaymentMessageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("status", 404, "error", "Not Found", "message", ex.getMessage(), "timestamp", OffsetDateTime.now())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                Map.of("status", 400, "error", "Bad Request", "message", ex.getMessage(), "timestamp", OffsetDateTime.now())
        );
    }

    /**
     * Verrou optimiste perdu : un autre appel a modifié la même ligne entre-temps.
     * Le client doit recharger le message et rejouer son action plutôt que d'écraser
     * silencieusement la modification concurrente.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of("status", 409, "error", "Conflict",
                        "message", "Le message a été modifié par une autre opération, rechargez-le",
                        "timestamp", OffsetDateTime.now())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of("status", 500, "error", "Internal Server Error", "message", ex.getMessage(), "timestamp", OffsetDateTime.now())
        );
    }
}
