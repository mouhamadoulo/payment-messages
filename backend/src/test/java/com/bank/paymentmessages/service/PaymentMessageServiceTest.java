package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.mq.DeadLetterPublisher;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PaymentMessageServiceTest {

    private static final int MAX_RETRIES = 3;

    @Mock
    private PaymentMessageRepository repository;

    @Mock
    private DeadLetterPublisher deadLetterPublisher;

    private PaymentMessageService service;

    @Captor
    private ArgumentCaptor<PaymentMessage> messageCaptor;

    @BeforeEach
    void setUp() {
        service = new PaymentMessageService(repository, deadLetterPublisher, MAX_RETRIES);
    }

    @Test
    void shouldSaveMessageWithReceivedStatus() {
        String rawPayload = "{\"amount\":250}";
        PaymentMessageEvent event = PaymentMessageEvent.builder()
                .messageId("event-uuid")
                .reference("REF-001")
                .messageType("PAYMENT_REQUEST")
                .status(PaymentMessageStatus.RECEIVED)
                .build();
        PaymentMessage savedEntity = PaymentMessage.builder()
                .id(1L)
                .messageId("event-uuid")
                .payload(rawPayload)
                .status(PaymentMessageStatus.RECEIVED)
                .build();
        when(repository.save(any(PaymentMessage.class))).thenReturn(savedEntity);

        PaymentMessage result = service.saveMessage(event, rawPayload);

        verify(repository).save(messageCaptor.capture());
        PaymentMessage captured = messageCaptor.getValue();
        assertThat(captured.getPayload()).isEqualTo(rawPayload);
        assertThat(captured.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(captured.getMessageId()).isNotNull();
        assertThat(captured.getReceivedAt()).isNotNull();
        assertThat(result).isEqualTo(savedEntity);
    }

    @Test
    void shouldReturnAllMessages() {
        PaymentMessage m1 = PaymentMessage.builder().id(1L).messageId("m1").reference("R1").status(PaymentMessageStatus.RECEIVED).build();
        PaymentMessage m2 = PaymentMessage.builder().id(2L).messageId("m2").reference("R2").status(PaymentMessageStatus.PROCESSED).build();
        Page<PaymentMessage> page = new PageImpl<>(List.of(m1, m2));
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        Page<PaymentMessageDto> result = service.findAll(Pageable.unpaged());

        assertThat(result).hasSize(2);
        assertThat(result.getContent().get(0).getMessageId()).isEqualTo("m1");
        assertThat(result.getContent().get(1).getMessageId()).isEqualTo("m2");
    }

    @Test
    void shouldFindByIdWhenExists() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.PROCESSED).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        PaymentMessageDto dto = service.findById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getMessageId()).isEqualTo("m1");
    }

    @Test
    void shouldThrowWhenFindByIdNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void shouldThrowWhenFindByIdNull() {
        assertThatThrownBy(() -> service.findById(null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void retryShouldIncrementCountAndReturnToReceived() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED)
                .errorMessage("timeout")
                .retryCount(1)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PaymentMessage.class))).thenAnswer(i -> i.getArgument(0));

        PaymentMessageDto dto = service.retry(1L);

        assertThat(dto.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(dto.getRetryCount()).isEqualTo(2);
        assertThat(dto.getErrorMessage()).isNull();
        verify(deadLetterPublisher, never()).publish(any());
    }

    @Test
    void retryShouldMoveToDeadLetterBeyondMaxRetries() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED)
                .retryCount(MAX_RETRIES)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PaymentMessage.class))).thenAnswer(i -> i.getArgument(0));

        PaymentMessageDto dto = service.retry(1L);

        assertThat(dto.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        assertThat(dto.getRetryCount()).isEqualTo(MAX_RETRIES + 1);
        assertThat(dto.getErrorMessage()).contains(String.valueOf(MAX_RETRIES));
        verify(deadLetterPublisher).publish(entity);
    }

    @Test
    void retryShouldRejectMessageThatIsNotFailed() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.PROCESSED)
                .retryCount(0)
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.retry(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROCESSED");
        verify(deadLetterPublisher, never()).publish(any());
    }

    @Test
    void batchRetryFailedShouldReplayEveryFailedMessage() {
        PaymentMessage m1 = PaymentMessage.builder().id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED).retryCount(0).build();
        PaymentMessage m2 = PaymentMessage.builder().id(2L).messageId("m2").reference("R2")
                .status(PaymentMessageStatus.FAILED).retryCount(MAX_RETRIES).build();
        when(repository.findAllByStatus(PaymentMessageStatus.FAILED)).thenReturn(List.of(m1, m2));

        int affected = service.batchRetryFailed();

        assertThat(affected).isEqualTo(2);
        assertThat(m1.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(m2.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        verify(deadLetterPublisher).publish(m2);
        verify(repository).saveAll(List.of(m1, m2));
    }

    @Test
    void updateStatusShouldPublishOnceWhenEnteringDeadLetter() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED).retryCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PaymentMessage.class))).thenAnswer(i -> i.getArgument(0));

        PaymentMessageDto dto = service.updateStatus(1L, PaymentMessageStatus.DEAD_LETTER);

        assertThat(dto.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        verify(deadLetterPublisher).publish(entity);

        service.updateStatus(1L, PaymentMessageStatus.DEAD_LETTER);

        verify(deadLetterPublisher).publish(entity);
    }
}
