package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterDispatcherTest {

    @Mock
    private PaymentMessageRepository repository;

    @Mock
    private DeadLetterPublisher publisher;

    private DeadLetterDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new DeadLetterDispatcher(repository, publisher);
    }

    @Test
    void shouldStampPublicationOnlyWhenBrokerAccepts() {
        PaymentMessage message = deadLetter();
        when(repository.findById(1L)).thenReturn(Optional.of(message));
        when(publisher.publish(message)).thenReturn(true);

        dispatcher.onDeadLetterRequested(new DeadLetterRequestedEvent(1L));

        assertThat(message.getDlqPublishedAt()).isNotNull();
        verify(repository).save(message);
    }

    @Test
    void shouldLeaveRowEligibleForRecoveryWhenPublicationFails() {
        PaymentMessage message = deadLetter();
        when(repository.findById(1L)).thenReturn(Optional.of(message));
        when(publisher.publish(message)).thenReturn(false);

        dispatcher.onDeadLetterRequested(new DeadLetterRequestedEvent(1L));

        // Sans dlqPublishedAt, DeadLetterRecoveryJob reprendra la ligne.
        assertThat(message.getDlqPublishedAt()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotPublishTwiceForAnAlreadyConfirmedMessage() {
        PaymentMessage message = deadLetter();
        message.setDlqPublishedAt(OffsetDateTime.now());
        when(repository.findById(1L)).thenReturn(Optional.of(message));

        dispatcher.onDeadLetterRequested(new DeadLetterRequestedEvent(1L));

        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldIgnoreMessageDeletedBeforePublication() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        dispatcher.onDeadLetterRequested(new DeadLetterRequestedEvent(1L));

        verify(publisher, never()).publish(any());
    }

    private static PaymentMessage deadLetter() {
        return PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.DEAD_LETTER)
                .retryCount(4)
                .build();
    }
}
