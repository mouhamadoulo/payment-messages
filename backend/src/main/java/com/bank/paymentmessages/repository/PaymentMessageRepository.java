package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentMessageRepository  extends JpaRepository<PaymentMessage,Long> {

    Optional<PaymentMessage> findByMessageId(String messageId);

    Optional<PaymentMessage> findByReference(String reference);
}
