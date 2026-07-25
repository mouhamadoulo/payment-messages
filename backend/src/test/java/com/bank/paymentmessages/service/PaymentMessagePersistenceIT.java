package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.dto.mq.Payment;
import com.bank.paymentmessages.dto.mq.PaymentMessageEvent;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.mq.DeadLetterPublisher;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import com.bank.paymentmessages.support.AbstractPostgresIT;
import com.bank.paymentmessages.support.RequiresDocker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Comportements de persistance qui ne se vérifient que sur PostgreSQL.
 * <p>
 * Les tests unitaires travaillent sur des dépôts simulés ou sur H2 : ils décrivent
 * l'intention du code, pas la réaction du moteur. Ce qui est couvert ici en dépend
 * directement — arbitrage d'une insertion concurrente par la contrainte d'unicité,
 * conservation du fuseau des horodatages, ordre du curseur de pagination, publication DLQ
 * déclenchée <b>après</b> un vrai commit.
 * <p>
 * Le publieur DLQ est simulé : il n'y a pas de broker ici, et la question posée est
 * « la publication est-elle demandée au bon moment ? », pas « le broker accepte-t-il ? »
 * (couvert par {@code PaymentMessageMqIT}).
 */
@RequiresDocker
class PaymentMessagePersistenceIT extends AbstractPostgresIT {

    @Autowired
    private PaymentMessageService service;

    @Autowired
    private PaymentMessageRepository repository;

    @MockitoBean
    private DeadLetterPublisher deadLetterPublisher;

    @BeforeEach
    void resetDatabase() {
        repository.deleteAll();
    }

    /** Redélivrance JMS d'un message déjà consommé : deuxième insertion refusée sans erreur. */
    @Test
    void redeliveredMessageShouldNotBeInsertedTwice() {

        PaymentMessageEvent event = event("msg-idempotent");

        assertThat(service.saveMessage(event, payload("msg-idempotent"))).isTrue();
        assertThat(service.saveMessage(event, payload("msg-idempotent"))).isFalse();

        assertThat(repository.count()).isEqualTo(1);
    }

    /**
     * Deux consommateurs traitant la même redélivrance au même instant : le pré-contrôle
     * {@code existsByMessageId} passe des deux côtés, seule la contrainte d'unicité
     * arbitre. C'est précisément ce que des dépôts simulés ne peuvent pas reproduire.
     */
    @Test
    void concurrentInsertsOfTheSameMessageShouldLeaveASingleRow() throws Exception {

        PaymentMessageEvent event = event("msg-race");
        CyclicBarrier startTogether = new CyclicBarrier(2);

        Callable<Boolean> insert = () -> {
            startTogether.await();
            return service.saveMessage(event, payload("msg-race"));
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(insert);
            Future<Boolean> second = pool.submit(insert);

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }

        assertThat(repository.count()).isEqualTo(1);
    }

    /**
     * Une colonne {@code timestamptz} restitue l'instant, pas l'heure murale : la valeur
     * relue doit désigner le même point du temps quel que soit le fuseau d'écriture (D8).
     */
    @Test
    void timestampsShouldSurviveARoundTripAcrossTimeZones() {

        OffsetDateTime writtenAt = OffsetDateTime.now(ZoneOffset.ofHours(-5)).truncatedTo(ChronoUnit.MILLIS);

        PaymentMessage stored = repository.save(PaymentMessage.builder()
                .messageId("msg-tz")
                .reference("REF-TZ")
                .messageType("pacs.008")
                .status(PaymentMessageStatus.RECEIVED)
                .payload("{}")
                .payloadSize(2)
                .retryCount(0)
                .receivedAt(writtenAt)
                .updatedAt(writtenAt)
                .build());


        OffsetDateTime reread = repository.findById(stored.getId()).orElseThrow().getReceivedAt();

        assertThat(reread.toInstant()).isEqualTo(writtenAt.toInstant());
    }

    /**
     * Le curseur repose sur l'ordre total {@code (received_at DESC, id DESC)} : deux
     * messages reçus au même instant doivent malgré tout se suivre sans doublon ni trou.
     */
    @Test
    void cursorPaginationShouldWalkEveryRowExactlyOnce() {

        OffsetDateTime sameInstant = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 0; i < 7; i++) {
            repository.save(PaymentMessage.builder()
                    .messageId("msg-cursor-" + i)
                    .reference("REF-" + i)
                    .messageType("pacs.008")
                    .status(PaymentMessageStatus.RECEIVED)
                    .payload("{}")
                    .payloadSize(2)
                    .retryCount(0)
                    .receivedAt(sameInstant)
                    .updatedAt(sameInstant)
                    .build());
        }

        List<String> visited = new ArrayList<>();
        String cursor = null;
        do {
            CursorPageDto<PaymentMessageSummaryDto> page = service.searchByCursor(null, null, cursor, 3);
            page.content().forEach(row -> visited.add(row.getMessageId()));
            cursor = page.nextCursor();
        } while (cursor != null);

        assertThat(visited).hasSize(7).doesNotHaveDuplicates();
    }

    /**
     * Dépassement du seuil de tentatives : la ligne passe en {@code DEAD_LETTER} et la
     * publication n'a lieu qu'<b>après</b> le commit, {@code dlqPublishedAt} n'étant posé
     * que sur accusé du broker (B7).
     */
    @Test
    void exhaustingRetriesShouldPublishToTheDeadLetterQueueAfterCommit() {

        when(deadLetterPublisher.publish(any())).thenReturn(true);

        PaymentMessage failed = repository.save(PaymentMessage.builder()
                .messageId("msg-dlq")
                .reference("REF-DLQ")
                .messageType("pacs.008")
                .status(PaymentMessageStatus.FAILED)
                .payload("{}")
                .payloadSize(2)
                // Une tentative de plus atteint `ibm.mq.max-retries` (3) + 1.
                .retryCount(3)
                .receivedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        PaymentMessageDto retried = service.retry(failed.getId());

        assertThat(retried.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        assertThat(repository.findById(failed.getId()).orElseThrow().getDlqPublishedAt()).isNotNull();
    }

    /** Publication refusée par le broker : la ligne reste reprenable par le job de reprise. */
    @Test
    void refusedPublicationShouldLeaveTheRowRecoverable() {

        when(deadLetterPublisher.publish(any())).thenReturn(false);

        PaymentMessage failed = repository.save(PaymentMessage.builder()
                .messageId("msg-dlq-ko")
                .reference("REF-DLQ-KO")
                .messageType("pacs.008")
                .status(PaymentMessageStatus.FAILED)
                .payload("{}")
                .payloadSize(2)
                .retryCount(3)
                .receivedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        service.retry(failed.getId());

        PaymentMessage reread = repository.findById(failed.getId()).orElseThrow();
        assertThat(reread.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        assertThat(reread.getDlqPublishedAt()).isNull();

        when(deadLetterPublisher.publish(any())).thenReturn(true);
        assertThat(service.republishPendingDeadLetters(10)).isEqualTo(1);
        assertThat(repository.findById(failed.getId()).orElseThrow().getDlqPublishedAt()).isNotNull();
    }

    /** Rétention : suppression par lots bornés, sans toucher aux autres statuts (D6). */
    @Test
    void retentionShouldOnlyPurgeProcessedRowsOlderThanTheCutoff() {

        OffsetDateTime old = OffsetDateTime.now().minusDays(120);
        OffsetDateTime recent = OffsetDateTime.now().minusDays(1);

        repository.save(row("msg-old-processed", PaymentMessageStatus.PROCESSED, old));
        repository.save(row("msg-recent-processed", PaymentMessageStatus.PROCESSED, recent));
        repository.save(row("msg-old-failed", PaymentMessageStatus.FAILED, old));

        int purged = service.purgeProcessedBefore(OffsetDateTime.now().minusDays(90), 500);

        assertThat(purged).isEqualTo(1);
        assertThat(repository.findByMessageId("msg-old-processed")).isEmpty();
        assertThat(repository.findByMessageId("msg-recent-processed")).isPresent();
        assertThat(repository.findByMessageId("msg-old-failed")).isPresent();
    }

    private static PaymentMessage row(String messageId, PaymentMessageStatus status, OffsetDateTime receivedAt) {
        return PaymentMessage.builder()
                .messageId(messageId)
                .reference("REF-" + messageId)
                .messageType("pacs.008")
                .status(status)
                .payload("{}")
                .payloadSize(2)
                .retryCount(0)
                .receivedAt(receivedAt)
                .updatedAt(receivedAt)
                .build();
    }

    private static PaymentMessageEvent event(String messageId) {
        return PaymentMessageEvent.builder()
                .messageId(messageId)
                .messageType("pacs.008")
                .reference("REF-" + messageId)
                .status(PaymentMessageStatus.RECEIVED)
                .payment(Payment.builder()
                        .transactionId("TX-" + messageId)
                        .amount(new BigDecimal("250.00"))
                        .currency("EUR")
                        .executionDate(LocalDate.now())
                        .build())
                .build();
    }

    private static String payload(String messageId) {
        return "{\"messageId\":\"" + messageId + "\"}";
    }
}
