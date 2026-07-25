package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
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

    @Test
    void listProjectionShouldExposeSizeInsteadOfPayload() {
        repository.save(message("u4", "R4", PaymentMessageStatus.RECEIVED, OffsetDateTime.now(), 512));

        List<PaymentMessageSummary> summaries =
                repository.findAllProjectedBy(PageRequest.of(0, 10)).getContent();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).payloadSize()).isEqualTo(512);
        assertThat(summaries.get(0).messageId()).isEqualTo("u4");
    }

    @Test
    void cursorPaginationShouldWalkTheWholeListWithoutOverlap() {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 5; i++) {
            repository.save(message("c" + i, "RC" + i, PaymentMessageStatus.RECEIVED, now.minusMinutes(i), 10));
        }

        List<PaymentMessageSummary> firstPage =
                repository.findNextPage(null, null, null, null, PageRequest.ofSize(2));
        PaymentMessageSummary last = firstPage.get(1);
        List<PaymentMessageSummary> secondPage =
                repository.findNextPage(null, null, last.receivedAt(), last.id(), PageRequest.ofSize(2));

        assertThat(firstPage).extracting(PaymentMessageSummary::messageId).containsExactly("c0", "c1");
        assertThat(secondPage).extracting(PaymentMessageSummary::messageId).containsExactly("c2", "c3");
    }

    @Test
    void cursorPaginationShouldDepartTiesById() {
        // Deux messages au même instant : l'id départage, sinon une ligne serait sautée ou rendue deux fois.
        OffsetDateTime sameInstant = OffsetDateTime.now();
        PaymentMessage first = repository.save(message("t1", "RT1", PaymentMessageStatus.RECEIVED, sameInstant, 10));
        PaymentMessage second = repository.save(message("t2", "RT2", PaymentMessageStatus.RECEIVED, sameInstant, 10));

        // Le curseur reprend l'horodatage tel que relu en base : la précision stockée
        // (microsecondes) est inférieure à celle d'un OffsetDateTime Java.
        PaymentMessageSummary firstPage =
                repository.findNextPage(null, null, null, null, PageRequest.ofSize(1)).get(0);
        List<PaymentMessageSummary> secondPage = repository.findNextPage(
                null, null, firstPage.receivedAt(), firstPage.id(), PageRequest.ofSize(10));

        assertThat(firstPage.id()).isEqualTo(second.getId());
        assertThat(secondPage).extracting(PaymentMessageSummary::id).containsExactly(first.getId());
    }

    @Test
    void cursorPaginationShouldApplyStatusFilter() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(message("s1", "RS1", PaymentMessageStatus.FAILED, now, 10));
        repository.save(message("s2", "RS2", PaymentMessageStatus.RECEIVED, now.minusMinutes(1), 10));

        List<PaymentMessageSummary> page = repository.findNextPage(
                PaymentMessageStatus.FAILED, null, null, null, PageRequest.ofSize(10));

        assertThat(page).extracting(PaymentMessageSummary::messageId).containsExactly("s1");
    }

    @Test
    void purgeableIdsShouldOnlyMatchProcessedMessagesBeforeCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentMessage old = repository.save(message("p1", "RP1", PaymentMessageStatus.PROCESSED, now.minusDays(120), 10));
        repository.save(message("p2", "RP2", PaymentMessageStatus.PROCESSED, now, 10));
        repository.save(message("p3", "RP3", PaymentMessageStatus.FAILED, now.minusDays(120), 10));

        List<Long> purgeable = repository.findPurgeableIds(
                PaymentMessageStatus.PROCESSED, now.minusDays(90), PageRequest.ofSize(100));

        assertThat(purgeable).containsExactly(old.getId());
    }

    private static PaymentMessage message(String messageId, String reference, PaymentMessageStatus status,
                                          OffsetDateTime receivedAt, int payloadSize) {
        return PaymentMessage.builder()
                .messageId(messageId)
                .reference(reference)
                .messageType("PAYMENT_REQUEST")
                .status(status)
                .payload("{\"amount\":500}")
                .payloadSize(payloadSize)
                .retryCount(0)
                .receivedAt(receivedAt)
                .updatedAt(receivedAt)
                .build();
    }
}
