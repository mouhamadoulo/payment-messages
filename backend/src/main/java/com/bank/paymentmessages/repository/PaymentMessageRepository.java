package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentMessageRepository  extends JpaRepository<PaymentMessage,Long> {

    Optional<PaymentMessage> findByMessageId(String messageId);

    Optional<PaymentMessage> findByReference(String reference);

    Page<PaymentMessage> findByStatus(PaymentMessageStatus status, Pageable pageable);

    Page<PaymentMessage> findByReceivedAtAfter(LocalDateTime receivedAfter, Pageable pageable);

    Page<PaymentMessage> findByStatusAndReceivedAtAfter(PaymentMessageStatus status, LocalDateTime receivedAfter, Pageable pageable);
}
