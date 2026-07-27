package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                repository.findNextPage(null, null, null, null, null, null, PageRequest.ofSize(2));
        PaymentMessageSummary last = firstPage.get(1);
        List<PaymentMessageSummary> secondPage =
                repository.findNextPage(null, null, null, null, last.receivedAt(), last.id(), PageRequest.ofSize(2));

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
                repository.findNextPage(null, null, null, null, null, null, PageRequest.ofSize(1)).get(0);
        List<PaymentMessageSummary> secondPage = repository.findNextPage(
                null, null, null, null, firstPage.receivedAt(), firstPage.id(), PageRequest.ofSize(10));

        assertThat(firstPage.id()).isEqualTo(second.getId());
        assertThat(secondPage).extracting(PaymentMessageSummary::id).containsExactly(first.getId());
    }

    @Test
    void cursorPaginationShouldApplyStatusFilter() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(message("s1", "RS1", PaymentMessageStatus.FAILED, now, 10));
        repository.save(message("s2", "RS2", PaymentMessageStatus.RECEIVED, now.minusMinutes(1), 10));

        List<PaymentMessageSummary> page = repository.findNextPage(
                PaymentMessageStatus.FAILED, null, null, null, null, null, PageRequest.ofSize(10));

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

    // ------------------------------------------- F2 / F3 : filtres appliqués côté serveur

    @Test
    void searchWithoutCriteriaShouldReturnEveryRowSortedByThePageable() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(message("f1", "RF1", PaymentMessageStatus.RECEIVED, now.minusMinutes(2), 10));
        repository.save(message("f2", "RF2", PaymentMessageStatus.RECEIVED, now, 10));

        Page<PaymentMessageSummary> page = repository.search(null, null, null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "receivedAt")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(PaymentMessageSummary::messageId).containsExactly("f2", "f1");
    }

    @Test
    void searchShouldCombineStatusTypeAndTextCriteria() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(typedMessage("f3", "REF-ALPHA", "pacs.008", PaymentMessageStatus.FAILED, now));
        repository.save(typedMessage("f4", "REF-BETA", "pacs.008", PaymentMessageStatus.FAILED, now));
        repository.save(typedMessage("f5", "REF-ALPHA-2", "pacs.002", PaymentMessageStatus.FAILED, now));
        repository.save(typedMessage("f6", "REF-ALPHA-3", "pacs.008", PaymentMessageStatus.RECEIVED, now));

        Page<PaymentMessageSummary> page = repository.search(
                PaymentMessageStatus.FAILED, null, "pacs.008", "%alpha%", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PaymentMessageSummary::reference).containsExactly("REF-ALPHA");
    }

    @Test
    void searchTextShouldAlsoMatchMessageId() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(typedMessage("uuid-9f3", "REF-1", "pacs.008", PaymentMessageStatus.RECEIVED, now));
        repository.save(typedMessage("uuid-000", "REF-2", "pacs.008", PaymentMessageStatus.RECEIVED, now));

        Page<PaymentMessageSummary> page =
                repository.search(null, null, null, "%9f3%", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PaymentMessageSummary::messageId).containsExactly("uuid-9f3");
    }

    @Test
    void statusCountsShouldHonourTheOtherCriteria() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(typedMessage("c1", "REF-ALPHA", "pacs.008", PaymentMessageStatus.FAILED, now));
        repository.save(typedMessage("c2", "REF-ALPHA", "pacs.008", PaymentMessageStatus.RECEIVED, now));
        repository.save(typedMessage("c3", "REF-BETA", "pacs.008", PaymentMessageStatus.FAILED, now));

        // Le statut est volontairement absent du prédicat : chaque pastille annonce ce que
        // donnerait un clic dessus, sous les filtres déjà actifs.
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : repository.countByStatusFiltered(null, "pacs.008", "%alpha%")) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        assertThat(counts).containsEntry("FAILED", 1L).containsEntry("RECEIVED", 1L);
    }

    @Test
    void distinctMessageTypesShouldIgnoreDuplicatesAndNulls() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(typedMessage("t1", "R1", "pacs.008", PaymentMessageStatus.RECEIVED, now));
        repository.save(typedMessage("t2", "R2", "pacs.008", PaymentMessageStatus.RECEIVED, now));
        repository.save(typedMessage("t3", "R3", "pacs.002", PaymentMessageStatus.RECEIVED, now));
        repository.save(typedMessage("t4", "R4", null, PaymentMessageStatus.RECEIVED, now));

        assertThat(repository.findDistinctMessageTypes()).containsExactly("pacs.002", "pacs.008");
    }

    // ------------------------------------------------ F1 : agrégats calculés en SQL

    @Test
    void hourlyCountsShouldBucketByHourOfReception() {
        OffsetDateTime base = OffsetDateTime.now().truncatedTo(ChronoUnit.HOURS);
        repository.save(message("h1", "RH1", PaymentMessageStatus.RECEIVED, base.minusHours(2), 10));
        repository.save(message("h2", "RH2", PaymentMessageStatus.RECEIVED, base.minusHours(2), 10));
        repository.save(message("h3", "RH3", PaymentMessageStatus.RECEIVED, base.minusHours(1), 10));
        // Hors fenêtre : ne doit pas être compté.
        repository.save(message("h4", "RH4", PaymentMessageStatus.RECEIVED, base.minusHours(30), 10));

        Map<Integer, Long> perHour = new HashMap<>();
        for (Object[] row : repository.countByHourSince(base.minusHours(24))) {
            perHour.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        assertThat(perHour).containsEntry(base.minusHours(2).getHour(), 2L)
                .containsEntry(base.minusHours(1).getHour(), 1L);
        assertThat(perHour.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(3L);
    }

    @Test
    void typeAndRetryAggregatesShouldCoverTheWholeTable() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(retried("a1", "RA1", "pacs.008", 0, now));
        repository.save(retried("a2", "RA2", "pacs.008", 2, now));
        repository.save(retried("a3", "RA3", "pacs.002", 7, now));

        Map<String, Long> types = new HashMap<>();
        for (Object[] row : repository.countByMessageType()) {
            types.put((String) row[0], ((Number) row[1]).longValue());
        }
        Map<Integer, Long> retries = new HashMap<>();
        for (Object[] row : repository.countByRetryCount()) {
            retries.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        assertThat(types).containsEntry("pacs.008", 2L).containsEntry("pacs.002", 1L);
        assertThat(retries).containsEntry(0, 1L).containsEntry(2, 1L).containsEntry(7, 1L);
    }

    @Test
    void recentFailuresShouldReturnTheLatestFailedAndDeadLetterRows() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.save(message("r1", "RR1", PaymentMessageStatus.FAILED, now.minusMinutes(1), 10));
        repository.save(message("r2", "RR2", PaymentMessageStatus.DEAD_LETTER, now, 10));
        repository.save(message("r3", "RR3", PaymentMessageStatus.PROCESSED, now.plusMinutes(1), 10));

        List<PaymentMessageSummary> recent = repository.findRecentByStatusIn(
                List.of(PaymentMessageStatus.FAILED, PaymentMessageStatus.DEAD_LETTER),
                PageRequest.ofSize(5));

        assertThat(recent).extracting(PaymentMessageSummary::messageId).containsExactly("r2", "r1");
    }

    private static PaymentMessage typedMessage(String messageId, String reference, String messageType,
                                               PaymentMessageStatus status, OffsetDateTime receivedAt) {
        PaymentMessage message = message(messageId, reference, status, receivedAt, 10);
        message.setMessageType(messageType);
        return message;
    }

    private static PaymentMessage retried(String messageId, String reference, String messageType,
                                          int retryCount, OffsetDateTime receivedAt) {
        PaymentMessage message = typedMessage(messageId, reference, messageType,
                PaymentMessageStatus.FAILED, receivedAt);
        message.setRetryCount(retryCount);
        return message;
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
