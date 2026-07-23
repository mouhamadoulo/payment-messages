package com.bank.paymentmessages.exception;

public class PaymentMessageNotFoundException extends RuntimeException {

    public PaymentMessageNotFoundException(Long id) {

        super("Message introuvable avec l'id : " + id);
    }
}
