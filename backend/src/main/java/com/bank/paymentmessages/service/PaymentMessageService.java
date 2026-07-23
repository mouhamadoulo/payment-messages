package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.mapper.PaymentMessageMapper;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;


@Service
public class PaymentMessageService {

    private final PaymentMessageRepository repository;

    public PaymentMessageService(PaymentMessageRepository repository){
        this.repository = repository;
    }

    public PaymentMessage saveMessage(PaymentMessageEvent event, String rawPayload) {

        PaymentMessage message = PaymentMessageMapper.toEntity(event, rawPayload);
        return repository.save(message);
    }

    public Page<PaymentMessageDto> findAll(Pageable pageable) {

        return repository.findAll(pageable).map(PaymentMessageMapper::toDto);
    }

    public Page<PaymentMessageDto> search(PaymentMessageStatus status, LocalDateTime receivedAfter, Pageable pageable) {
        if (status != null && receivedAfter != null) {
            return repository.findByStatusAndReceivedAtAfter(status, receivedAfter, pageable)
                    .map(PaymentMessageMapper::toDto);
        }
        if (status != null) {
            return repository.findByStatus(status, pageable)
                    .map(PaymentMessageMapper::toDto);
        }
        if (receivedAfter != null) {
            return repository.findByReceivedAtAfter(receivedAfter, pageable)
                    .map(PaymentMessageMapper::toDto);
        }
        return findAll(pageable);
    }

    public PaymentMessageDto findById(Long id) {

        return repository.findById(id)
                .map(PaymentMessageMapper::toDto)
                .orElseThrow(() ->
                        new PaymentMessageNotFoundException(id));
    }

}