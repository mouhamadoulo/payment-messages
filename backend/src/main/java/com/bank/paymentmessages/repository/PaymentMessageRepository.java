package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentMessageRepository  extends JpaRepository<PaymentMessage,Long> {

    Optional<PaymentMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    Optional<PaymentMessage> findByReference(String reference);

    Page<PaymentMessage> findByStatus(PaymentMessageStatus status, Pageable pageable);

    Page<PaymentMessage> findByReceivedAtAfter(LocalDateTime receivedAfter, Pageable pageable);

    Page<PaymentMessage> findByStatusAndReceivedAtAfter(PaymentMessageStatus status, LocalDateTime receivedAfter, Pageable pageable);

    List<PaymentMessage> findAllByStatus(PaymentMessageStatus status);

    /**
     * Messages passés en DEAD_LETTER dont la republication sur la DLQ n'a jamais été
     * confirmée. Alimente la reprise planifiée qui rattrape les divergences base / broker.
     */
    List<PaymentMessage> findByStatusAndDlqPublishedAtIsNull(PaymentMessageStatus status, Pageable pageable);

    @Query("SELECT p.status, COUNT(p) FROM PaymentMessage p GROUP BY p.status")
    List<Object[]> countByStatus();
}
