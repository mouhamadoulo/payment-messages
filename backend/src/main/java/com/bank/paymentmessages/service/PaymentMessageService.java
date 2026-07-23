package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.PaymentMessageDto;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.mapper.PaymentMessageMapper;
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

    public List<PaymentMessageDto> findAll() {

        return repository.findAll()
                .stream()
                .map(PaymentMessageMapper::toDto)
                .toList();
    }

    public PaymentMessageDto findById(Long id) {

        return repository.findById(id)
                .map(PaymentMessageMapper::toDto)
                .orElseThrow(() ->
                        new RuntimeException("Message introuvable"));
    }

}