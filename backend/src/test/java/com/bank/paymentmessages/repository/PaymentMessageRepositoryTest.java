package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PaymentMessageRepositoryTest {

    @Autowired
    private PaymentMessageRepository repository;

    @Test
    void shouldSaveAndFindById() {
        PaymentMessage message = PaymentMessage.builder()
                .messageId("uuid-1")
                .reference("REF-001")
                .messageType("PAYMENT_REQUEST")
                .status(PaymentMessageStatus.RECEIVED)
                .payload("{\"amount\":500}")
                .retryCount(0)
                .build();

        PaymentMessage saved = repository.save(message);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMessageId()).isEqualTo("uuid-1");
    }

    @Test
    void shouldFindByMessageId() {
        repository.save(PaymentMessage.builder()
                .messageId("uuid-2").reference("REF-002")
                .status(PaymentMessageStatus.RECEIVED).build());

        Optional<PaymentMessage> found = repository.findByMessageId("uuid-2");

        assertThat(found).isPresent();
        assertThat(found.get().getReference()).isEqualTo("REF-002");
    }

    @Test
    void shouldFindByReference() {
        repository.save(PaymentMessage.builder()
                .messageId("uuid-3").reference("REF-003")
                .status(PaymentMessageStatus.PROCESSED).build());

        Optional<PaymentMessage> found = repository.findByReference("REF-003");

        assertThat(found).isPresent();
        assertThat(found.get().getMessageId()).isEqualTo("uuid-3");
    }

    @Test
    void shouldReturnAllMessages() {
        repository.save(PaymentMessage.builder().messageId("u1").reference("R1").status(PaymentMessageStatus.RECEIVED).build());
        repository.save(PaymentMessage.builder().messageId("u2").reference("R2").status(PaymentMessageStatus.PROCESSED).build());

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        Optional<PaymentMessage> found = repository.findByMessageId("non-existent");
        assertThat(found).isEmpty();
    }
}
