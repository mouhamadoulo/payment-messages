package com.bank.paymentmessages.mq;


import com.bank.paymentmessages.service.PaymentMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;


@Component
public class PaymentMessageListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentMessageListener.class);

    private final PaymentMessageService service;

    public PaymentMessageListener(PaymentMessageService service){
        this.service = service;
    }

    @JmsListener(destination = "${ibm.mq.queue}", concurrency = "5-10")
    public void receive(String message){
        log.info("Message IBM MQ reçu : {}", message);

        service.saveMessage(message);
    }

}