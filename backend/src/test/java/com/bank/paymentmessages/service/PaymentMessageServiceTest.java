package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.InvalidStatusTransitionException;
import com.bank.paymentmessages.mq.DeadLetterPublisher;
import com.bank.paymentmessages.mq.DeadLetterRequestedEvent;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import com.bank.paymentmessages.repository.PaymentMessageSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Mock
    private ApplicationEventPublisher events;

    private PaymentMessageService service;

    @Captor
    private ArgumentCaptor<PaymentMessage> messageCaptor;

    @Captor
    private ArgumentCaptor<DeadLetterRequestedEvent> deadLetterCaptor;

    @BeforeEach
    void setUp() {
        service = new PaymentMessageService(repository, deadLetterPublisher, events, MAX_RETRIES);
    }

    @Test
    void shouldSaveMessageWithReceivedStatus() {
        String rawPayload = "{\"amount\":250}";
        PaymentMessageEvent event = event("event-uuid");
        when(repository.existsByMessageId("event-uuid")).thenReturn(false);

        boolean inserted = service.saveMessage(event, rawPayload);

        assertThat(inserted).isTrue();
        verify(repository).save(messageCaptor.capture());
        PaymentMessage captured = messageCaptor.getValue();
        assertThat(captured.getPayload()).isEqualTo(rawPayload);
        assertThat(captured.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(captured.getMessageId()).isEqualTo("event-uuid");
        assertThat(captured.getReceivedAt()).isNotNull();
    }

    @Test
    void saveMessageShouldIgnoreAlreadyProcessedMessage() {
        when(repository.existsByMessageId("event-uuid")).thenReturn(true);

        boolean inserted = service.saveMessage(event("event-uuid"), "{\"amount\":250}");

        assertThat(inserted).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void saveMessageShouldTreatConcurrentDuplicateAsAlreadyProcessed() {
        when(repository.existsByMessageId("event-uuid")).thenReturn(false, true);
        when(repository.save(any(PaymentMessage.class)))
                .thenThrow(new DataIntegrityViolationException("unique messageId"));

        boolean inserted = service.saveMessage(event("event-uuid"), "{\"amount\":250}");

        assertThat(inserted).isFalse();
    }

    @Test
    void saveMessageShouldRethrowIntegrityViolationThatIsNotADuplicate() {
        when(repository.existsByMessageId("event-uuid")).thenReturn(false, false);
        when(repository.save(any(PaymentMessage.class)))
                .thenThrow(new DataIntegrityViolationException("reference must not be null"));

        assertThatThrownBy(() -> service.saveMessage(event("event-uuid"), "{\"amount\":250}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savePermanentFailureShouldKeepRawPayloadAndReason() {
        when(repository.existsByMessageId("bad-uuid")).thenReturn(false);

        boolean inserted = service.savePermanentFailure(
                "bad-uuid", "REF-001", "PAYMENT_REQUEST", "{not-json", "Payload JSON illisible");

        assertThat(inserted).isTrue();
        verify(repository).save(messageCaptor.capture());
        PaymentMessage captured = messageCaptor.getValue();
        assertThat(captured.getStatus()).isEqualTo(PaymentMessageStatus.FAILED);
        assertThat(captured.getPayload()).isEqualTo("{not-json");
        assertThat(captured.getErrorMessage()).isEqualTo("Payload JSON illisible");
        assertThat(captured.getRetryCount()).isZero();
    }

    private static PaymentMessageEvent event(String messageId) {
        return PaymentMessageEvent.builder()
                .messageId(messageId)
                .reference("REF-001")
                .messageType("PAYMENT_REQUEST")
                .status(PaymentMessageStatus.RECEIVED)
                .build();
    }

    @Test
    void shouldReturnAllMessagesWithoutPayload() {
        Page<PaymentMessageSummary> page = new PageImpl<>(List.of(summary(1L, "m1"), summary(2L, "m2")));
        when(repository.findAllProjectedBy(any(Pageable.class))).thenReturn(page);

        Page<PaymentMessageSummaryDto> result = service.findAll(Pageable.unpaged());

        assertThat(result).hasSize(2);
        assertThat(result.getContent().get(0).getMessageId()).isEqualTo("m1");
        assertThat(result.getContent().get(1).getMessageId()).isEqualTo("m2");
        assertThat(result.getContent().get(0).getPayloadSize()).isEqualTo(120);
    }

    @Test
    void cursorPageShouldRenderRequestedSizeAndExposeNextCursor() {
        // Le service demande une ligne de plus que la taille voulue pour savoir s'il reste une page.
        when(repository.findNextPage(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(summary(1L, "m1"), summary(2L, "m2"), summary(3L, "m3")));

        CursorPageDto<PaymentMessageSummaryDto> page = service.searchByCursor(null, null, null, 2);

        assertThat(page.content()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void cursorPageShouldStopWhenNoFurtherRow() {
        when(repository.findNextPage(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(summary(1L, "m1")));

        CursorPageDto<PaymentMessageSummaryDto> page = service.searchByCursor(null, null, null, 2);

        assertThat(page.content()).hasSize(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void cursorPageShouldRejectUnreadableCursor() {
        assertThatThrownBy(() -> service.searchByCursor(null, null, "not-a-cursor", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Curseur");
    }

    private static PaymentMessageSummary summary(Long id, String messageId) {
        OffsetDateTime receivedAt = OffsetDateTime.now().minusMinutes(id);
        return new PaymentMessageSummary(id, messageId, "R" + id, "PAYMENT_REQUEST",
                PaymentMessageStatus.RECEIVED, 120, 0, null, receivedAt, receivedAt);
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
        verify(events, never()).publishEvent(any(DeadLetterRequestedEvent.class));
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
        // La publication est déléguée à un événement joué après commit, jamais pendant.
        verify(events).publishEvent(deadLetterCaptor.capture());
        assertThat(deadLetterCaptor.getValue().id()).isEqualTo(1L);
        verify(deadLetterPublisher, never()).publish(any());
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
    void retryFailedBatchShouldReplayOnlyOneBoundedBatch() {
        PaymentMessage m1 = PaymentMessage.builder().id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED).retryCount(0).build();
        PaymentMessage m2 = PaymentMessage.builder().id(2L).messageId("m2").reference("R2")
                .status(PaymentMessageStatus.FAILED).retryCount(MAX_RETRIES).build();
        when(repository.findAllByStatus(eq(PaymentMessageStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m1, m2)));

        int affected = service.retryFailedBatch(500);

        assertThat(affected).isEqualTo(2);
        assertThat(m1.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(m2.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        verify(events).publishEvent(deadLetterCaptor.capture());
        assertThat(deadLetterCaptor.getValue().id()).isEqualTo(2L);
        verify(repository).saveAll(List.of(m1, m2));
    }

    @Test
    void retryFailedBatchShouldRequestOnlyTheConfiguredBatchSize() {
        when(repository.findAllByStatus(eq(PaymentMessageStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        service.retryFailedBatch(500);

        verify(repository).findAllByStatus(eq(PaymentMessageStatus.FAILED), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    void purgeShouldDeleteOnlyTheReturnedBatch() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);
        when(repository.findPurgeableIds(eq(PaymentMessageStatus.PROCESSED), eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L));

        int purged = service.purgeProcessedBefore(cutoff, 500);

        assertThat(purged).isEqualTo(3);
        verify(repository).deleteAllByIdInBatch(List.of(1L, 2L, 3L));
    }

    @Test
    void purgeShouldNotIssueDeleteWhenNothingIsEligible() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);
        when(repository.findPurgeableIds(eq(PaymentMessageStatus.PROCESSED), eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.purgeProcessedBefore(cutoff, 500)).isZero();
        verify(repository, never()).deleteAllByIdInBatch(any());
    }

    @Test
    void republishPendingShouldConfirmOnlySuccessfulPublications() {
        PaymentMessage published = PaymentMessage.builder().id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.DEAD_LETTER).retryCount(MAX_RETRIES + 1).build();
        PaymentMessage stillFailing = PaymentMessage.builder().id(2L).messageId("m2").reference("R2")
                .status(PaymentMessageStatus.DEAD_LETTER).retryCount(MAX_RETRIES + 1).build();
        when(repository.findByStatusAndDlqPublishedAtIsNull(eq(PaymentMessageStatus.DEAD_LETTER), any(Pageable.class)))
                .thenReturn(List.of(published, stillFailing));
        when(deadLetterPublisher.publish(published)).thenReturn(true);
        when(deadLetterPublisher.publish(stillFailing)).thenReturn(false);

        int republished = service.republishPendingDeadLetters(100);

        assertThat(republished).isEqualTo(1);
        assertThat(published.getDlqPublishedAt()).isNotNull();
        // Non confirmé : la ligne reste éligible à la prochaine reprise.
        assertThat(stillFailing.getDlqPublishedAt()).isNull();
        verify(repository).saveAll(List.of(published));
    }

    @Test
    void updateStatusShouldNotPublishBeforeCommit() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED).retryCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PaymentMessage.class))).thenAnswer(i -> i.getArgument(0));

        service.updateStatus(1L, PaymentMessageStatus.DEAD_LETTER, null);

        // Rien n'est envoyé au broker tant que la transaction n'est pas committée :
        // c'est DeadLetterDispatcher qui publiera et posera dlqPublishedAt.
        verify(deadLetterPublisher, never()).publish(any());
        assertThat(entity.getDlqPublishedAt()).isNull();
    }

    @Test
    void updateStatusShouldRequestPublicationOnceWhenEnteringDeadLetter() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED).retryCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PaymentMessage.class))).thenAnswer(i -> i.getArgument(0));

        PaymentMessageDto dto = service.updateStatus(1L, PaymentMessageStatus.DEAD_LETTER, null);

        assertThat(dto.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        verify(events).publishEvent(any(DeadLetterRequestedEvent.class));

        // Déjà en DEAD_LETTER : le second appel ne redemande pas de publication.
        service.updateStatus(1L, PaymentMessageStatus.DEAD_LETTER, null);

        verify(events).publishEvent(any(DeadLetterRequestedEvent.class));
    }

    @Test
    void updateStatusShouldRejectTransitionForbiddenByStateMachine() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.RECEIVED).retryCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        // Aucune tentative n'a eu lieu : un abandon direct n'a pas de sens.
        assertThatThrownBy(() -> service.updateStatus(1L, PaymentMessageStatus.DEAD_LETTER, null))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(repository, never()).save(any());
        verify(events, never()).publishEvent(any(DeadLetterRequestedEvent.class));
    }

    @Test
    void updateStatusShouldRejectAnyTransitionOutOfTerminalStatus() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.PROCESSED).retryCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(1L, PaymentMessageStatus.RECEIVED, null))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(repository, never()).save(any());
    }

    /** Rejouer la même commande ne doit pas produire d'erreur, ni consommer de version. */
    @Test
    void updateStatusShouldBeNeutralWhenStatusIsUnchanged() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.PROCESSED).retryCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        PaymentMessageDto dto = service.updateStatus(1L, PaymentMessageStatus.PROCESSED, null);

        assertThat(dto.getStatus()).isEqualTo(PaymentMessageStatus.PROCESSED);
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatusShouldKeepReasonAsErrorMessageOnFailureStatus() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.RECEIVED).retryCount(0).build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PaymentMessage.class))).thenAnswer(i -> i.getArgument(0));

        service.updateStatus(1L, PaymentMessageStatus.FAILED, "IBAN créditeur invalide");

        assertThat(entity.getErrorMessage()).isEqualTo("IBAN créditeur invalide");
    }

    /** Un retour à un statut sain ne doit pas laisser un diagnostic obsolète attaché. */
    @Test
    void updateStatusShouldClearErrorMessageWhenLeavingFailure() {
        PaymentMessage entity = PaymentMessage.builder()
                .id(1L).messageId("m1").reference("R1")
                .status(PaymentMessageStatus.FAILED).retryCount(1)
                .errorMessage("Validation en échec").build();
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(PaymentMessage.class))).thenAnswer(i -> i.getArgument(0));

        service.updateStatus(1L, PaymentMessageStatus.PROCESSED, "Résolu manuellement");

        assertThat(entity.getErrorMessage()).isNull();
    }
}
