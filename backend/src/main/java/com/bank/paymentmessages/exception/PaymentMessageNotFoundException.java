package com.bank.paymentmessages.exception;

public class PaymentMessageNotFoundException extends RuntimeException {

    public PaymentMessageNotFoundException(Long id) {

        super("Message introuvable avec l'id : " + id);
    }

    private PaymentMessageNotFoundException(String message) {

        super(message);
    }

    /** Tâche de rejeu massif inconnue ou déjà oubliée de l'historique. */
    public static PaymentMessageNotFoundException forBatchRetryTask(String taskId) {

        return new PaymentMessageNotFoundException("Tâche de rejeu introuvable : " + taskId);
    }
}
