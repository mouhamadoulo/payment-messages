package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.PaymentMessageDto;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMessageServiceTest {

    @Mock
    private PaymentMessageRepository repository;

    @InjectMocks
    private PaymentMessageService service;

    @Captor
    private ArgumentCaptor<PaymentMessage> messageCaptor;

    @Test
    void shouldSaveMessageWithReceivedStatus() {
        String payload = "{\"amount\":250}";
        PaymentMessage savedEntity = PaymentMessage.builder()
                .id(1L)
                .messageId("generated-uuid")
                .payload(payload)
                .status(PaymentMessageStatus.RECEIVED)
                .build();
        when(repository.save(any(PaymentMessage.class))).thenReturn(savedEntity);

        PaymentMessage result = service.saveMessage(payload);

        verify(repository).save(messageCaptor.capture());
        PaymentMessage captured = messageCaptor.getValue();
        assertThat(captured.getPayload()).isEqualTo(payload);
        assertThat(captured.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(captured.getMessageId()).isNotNull();
        assertThat(captured.getReceivedAt()).isNotNull();
        assertThat(result).isEqualTo(savedEntity);
    }

    @Test
    void shouldReturnAllMessages() {
        PaymentMessage m1 = PaymentMessage.builder().id(1L).messageId("m1").reference("R1").status(PaymentMessageStatus.RECEIVED).build();
        PaymentMessage m2 = PaymentMessage.builder().id(2L).messageId("m2").reference("R2").status(PaymentMessageStatus.PROCESSED).build();
        when(repository.findAll()).thenReturn(List.of(m1, m2));

        List<PaymentMessageDto> dtos = service.findAll();

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getMessageId()).isEqualTo("m1");
        assertThat(dtos.get(1).getMessageId()).isEqualTo("m2");
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
                .hasMessageContaining("Message introuvable");
    }

    @Test
    void shouldThrowWhenFindByIdNull() {
        assertThatThrownBy(() -> service.findById(null))
                .isInstanceOf(RuntimeException.class);
    }
}
