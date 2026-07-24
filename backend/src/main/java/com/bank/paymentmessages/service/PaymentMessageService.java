package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.mapper.PaymentMessageMapper;
import com.bank.paymentmessages.mq.DeadLetterPublisher;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class PaymentMessageService {

    private final PaymentMessageRepository repository;
    private final DeadLetterPublisher deadLetterPublisher;
    private final int maxRetries;

    public PaymentMessageService(PaymentMessageRepository repository,
                                 DeadLetterPublisher deadLetterPublisher,
                                 @Value("${ibm.mq.max-retries}") int maxRetries){
        this.repository = repository;
        this.deadLetterPublisher = deadLetterPublisher;
        this.maxRetries = maxRetries;
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

    public Map<PaymentMessageStatus, Long> getStats() {
        List<Object[]> results = repository.countByStatus();
        Map<PaymentMessageStatus, Long> stats = new LinkedHashMap<>();
        for (PaymentMessageStatus status : PaymentMessageStatus.values()) {
            stats.put(status, 0L);
        }
        for (Object[] row : results) {
            stats.put((PaymentMessageStatus) row[0], (Long) row[1]);
        }
        return stats;
    }

    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw new PaymentMessageNotFoundException(id);
        }
        repository.deleteById(id);
    }

    public int batchRetryFailed() {
        List<PaymentMessage> messages = repository.findAllByStatus(PaymentMessageStatus.FAILED);
        for (PaymentMessage message : messages) {
            applyRetry(message);
        }
        repository.saveAll(messages);
        return messages.size();
    }

    public PaymentMessageDto retry(Long id) {
        PaymentMessage message = repository.findById(id)
                .orElseThrow(() -> new PaymentMessageNotFoundException(id));
        if (message.getStatus() != PaymentMessageStatus.FAILED) {
            throw new IllegalArgumentException(
                    "Seuls les messages FAILED sont rejouables, statut actuel : " + message.getStatus());
        }
        applyRetry(message);
        return PaymentMessageMapper.toDto(repository.save(message));
    }

    public PaymentMessageDto updateStatus(Long id, PaymentMessageStatus newStatus) {
        PaymentMessage message = repository.findById(id)
                .orElseThrow(() -> new PaymentMessageNotFoundException(id));
        boolean entersDeadLetter = newStatus == PaymentMessageStatus.DEAD_LETTER
                && message.getStatus() != PaymentMessageStatus.DEAD_LETTER;
        message.setStatus(newStatus);
        message.setUpdatedAt(LocalDateTime.now());
        PaymentMessageDto dto = PaymentMessageMapper.toDto(repository.save(message));
        if (entersDeadLetter) {
            deadLetterPublisher.publish(message);
        }
        return dto;
    }

    /**
     * Incrémente le compteur de tentatives et remet le message en attente de traitement.
     * Au-delà du seuil configuré, le message part en Dead Letter Queue.
     */
    private void applyRetry(PaymentMessage message) {
        int attempt = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
        message.setRetryCount(attempt);
        message.setUpdatedAt(LocalDateTime.now());

        if (attempt > maxRetries) {
            message.setStatus(PaymentMessageStatus.DEAD_LETTER);
            message.setErrorMessage("Abandonné après " + maxRetries + " tentatives");
            deadLetterPublisher.publish(message);
            return;
        }

        message.setStatus(PaymentMessageStatus.RECEIVED);
        message.setErrorMessage(null);
    }

}
