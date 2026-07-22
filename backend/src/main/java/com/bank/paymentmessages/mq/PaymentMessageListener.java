package com.bank.paymentmessages.mq;


import com.bank.paymentmessages.service.PaymentMessageService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;


@Component
public class PaymentMessageListener {

    private final PaymentMessageService service;

    public PaymentMessageListener(PaymentMessageService service){
        this.service = service;
    }

    @JmsListener(destination = "PAYMENT.IN")
    public void receive(String message){
        service.saveMessage(message);
    }

}