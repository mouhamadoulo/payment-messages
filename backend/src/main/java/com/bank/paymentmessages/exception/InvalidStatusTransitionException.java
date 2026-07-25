package com.bank.paymentmessages.exception;

import com.bank.paymentmessages.entity.PaymentMessageStatus;

/**
 * Changement de statut refusé par la machine à états ({@link PaymentMessageStatus}).
 * <p>
 * La requête est syntaxiquement valide — l'enum existe — mais incohérente avec l'état
 * courant du message : elle est rejetée en 422, pas en 400.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    private final PaymentMessageStatus from;
    private final PaymentMessageStatus to;

    public InvalidStatusTransitionException(PaymentMessageStatus from, PaymentMessageStatus to) {
        super("Transition de statut interdite : " + from + " → " + to
                + (from.isTerminal()
                ? " (" + from + " est un statut terminal)"
                : " (transitions autorisées : " + from.allowedTransitions() + ")"));
        this.from = from;
        this.to = to;
    }

    public PaymentMessageStatus getFrom() {
        return from;
    }

    public PaymentMessageStatus getTo() {
        return to;
    }
}
