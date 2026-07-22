package com.bank.paymentmessages.service;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Service
public class PaymentMessageService {

    private final PaymentMessageRepository repository;

    public PaymentMessageService(PaymentMessageRepository repository){
        this.repository = repository;
    }

    public PaymentMessage saveMessage(String payload){
        PaymentMessage message = PaymentMessage.builder()
                        .messageId(UUID.randomUUID().toString())
                        .payload(payload)
                        .status("RECEIVED")
                        .receivedAt(LocalDateTime.now())
                        .build();


        return repository.save(message);
    }

    public List<PaymentMessage> findAll(){
        return repository.findAll();
    }

    public PaymentMessage findById(Long id){
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message introuvable")
                );
    }

}