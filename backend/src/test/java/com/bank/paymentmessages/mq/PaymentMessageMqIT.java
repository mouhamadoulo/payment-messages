package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import com.bank.paymentmessages.service.PaymentMessageService;
import com.bank.paymentmessages.support.AbstractPostgresIT;
import com.bank.paymentmessages.support.RequiresDocker;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Ingestion de bout en bout sur un vrai IBM MQ.
 * <p>
 * Trois comportements ne sont observables qu'ici, parce qu'ils appartiennent au broker et
 * non au code : la <b>redélivrance</b> après rollback d'une session transactée,
 * l'<b>idempotence</b> face à cette redélivrance, et la <b>bascule DLQ</b> réellement
 * acceptée par le gestionnaire de files. Un test avec un {@code JmsTemplate} simulé ne
 * prouve rien de tout cela : il vérifie seulement que le code appelle ce qu'on lui a dit
 * d'appeler.
 * <p>
 * <b>Désactivé par défaut</b> : l'image IBM MQ pèse près d'un gigaoctet et son gestionnaire
 * de files met une minute à démarrer, ce qui n'a pas sa place dans la boucle de build
 * courante. Activation explicite :
 * <pre>./mvnw verify -Dmq.it=true</pre>
 */
@ActiveProfiles("mq-it")
@RequiresDocker
@EnabledIfSystemProperty(named = "mq.it", matches = "true",
        disabledReason = "Test d'intégration IBM MQ : activer avec -Dmq.it=true")
class PaymentMessageMqIT extends AbstractPostgresIT {

    private static final String REQUEST_QUEUE = "PAYMENT.REQUEST.QUEUE";
    private static final String DLQ_QUEUE = "PAYMENT.DLQ.QUEUE";

    /** Laisse le temps au broker de redélivrer : la latence n'est pas maîtrisée par le test. */
    private static final Duration SETTLE = Duration.ofSeconds(30);

    static final GenericContainer<?> MQ = new GenericContainer<>("icr.io/ibm-messaging/mq:latest")
            .withEnv("LICENSE", "accept")
            .withEnv("MQ_QMGR_NAME", "QM1")
            .withEnv("MQ_APP_PASSWORD", "passw0rd")
            .withEnv("MQ_ADMIN_PASSWORD", "admin")
            .withExposedPorts(1414)
            // Même script qu'en développement : les files de test portent donc les mêmes
            // BOTHRESH / BOQNAME que la production locale, sinon le garde-fou testé ici
            // serait celui du test et non celui du projet.
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "infra", "mq", "payment-queues.mqsc").toAbsolutePath()),
                    "/etc/mqm/payment-queues.mqsc")
            .waitingFor(Wait.forLogMessage(".*Started web server.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(5)));

    static {
        MQ.start();
    }

    @DynamicPropertySource
    static void mqConnection(DynamicPropertyRegistry registry) {
        registry.add("ibm.mq.conn-name", () -> MQ.getHost() + "(" + MQ.getMappedPort(1414) + ")");
    }

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private PaymentMessageRepository repository;

    @MockitoSpyBean
    private PaymentMessageService service;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void resetState() {
        repository.deleteAll();
        drain(REQUEST_QUEUE);
        drain(DLQ_QUEUE);
    }

    @Test
    void validMessageShouldBePersisted() {

        jmsTemplate.convertAndSend(REQUEST_QUEUE, payload("mq-nominal", "REF-1"));

        await().atMost(SETTLE).until(() -> repository.findByMessageId("mq-nominal").isPresent());

        PaymentMessage stored = repository.findByMessageId("mq-nominal").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentMessageStatus.RECEIVED);
        assertThat(stored.getPayloadSize()).isPositive();
    }

    /**
     * Erreur définitive : le rejeu produirait la même erreur. Le message est persisté en
     * {@code FAILED} avec son payload brut puis acquitté — il ne doit pas revenir en boucle
     * ni finir sur la file de backout (B1).
     */
    @Test
    void unreadablePayloadShouldBePersistedAsFailedAndAcknowledged() {

        jmsTemplate.convertAndSend(REQUEST_QUEUE, "{ceci n'est pas du JSON");

        await().atMost(SETTLE).until(() -> repository.count() == 1);

        PaymentMessage stored = repository.findAll().getFirst();
        assertThat(stored.getStatus()).isEqualTo(PaymentMessageStatus.FAILED);
        assertThat(stored.getErrorMessage()).contains("Payload JSON illisible");
        assertThat(stored.getPayload()).isEqualTo("{ceci n'est pas du JSON");

        // Acquitté : la file d'entrée est vide et le broker n'a rien à redélivrer.
        assertThat(receive(REQUEST_QUEUE)).isNull();
    }

    /**
     * Redélivrance d'un message déjà consommé (rollback d'un lot, reprise du broker) :
     * la contrainte d'unicité sur {@code messageId} en fait un acquittement sans écriture
     * plutôt qu'un doublon (B4).
     */
    @Test
    void redeliveredMessageShouldNotCreateADuplicate() {

        String payload = payload("mq-duplicate", "REF-DUP");
        double duplicatesBefore = duplicateCount();

        jmsTemplate.convertAndSend(REQUEST_QUEUE, payload);
        await().atMost(SETTLE).until(() -> repository.findByMessageId("mq-duplicate").isPresent());

        jmsTemplate.convertAndSend(REQUEST_QUEUE, payload);
        // Le compteur, et non la file : consommer soi-même le second message le volerait
        // au listener, qui est justement ce que l'on observe.
        await().atMost(SETTLE).until(() -> duplicateCount() > duplicatesBefore);

        assertThat(repository.count()).isEqualTo(1);
    }

    private double duplicateCount() {
        return meterRegistry.counter("payment.mq.messages.duplicates").count();
    }

    /**
     * Erreur transitoire (base indisponible) : l'exception remonte, la session transactée
     * effectue un rollback et le broker redélivre. Rien n'est perdu, contrairement à un
     * acquittement automatique qui aurait détruit le message (B1/B2).
     */
    @Test
    void transientFailureShouldBeRedeliveredUntilItSucceeds() {

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new DataAccessResourceFailureException("base indisponible (simulée)");
            }
            return invocation.callRealMethod();
        }).when(service).saveMessage(any(), any());

        jmsTemplate.convertAndSend(REQUEST_QUEUE, payload("mq-transient", "REF-TRANSIENT"));

        await().atMost(SETTLE).until(() -> repository.findByMessageId("mq-transient").isPresent());

        assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
    }

    /**
     * Bascule DLQ : le message est réellement accepté par le gestionnaire de files, avec
     * ses propriétés de corrélation, et {@code dlqPublishedAt} n'est posé qu'ensuite (B7).
     */
    @Test
    void deadLetteredMessageShouldReachTheDeadLetterQueue() throws Exception {

        PaymentMessage failed = repository.save(PaymentMessage.builder()
                .messageId("mq-dlq")
                .reference("REF-DLQ")
                .messageType("pacs.008")
                .status(PaymentMessageStatus.FAILED)
                .payload(payload("mq-dlq", "REF-DLQ"))
                .payloadSize(payload("mq-dlq", "REF-DLQ").length())
                // Une tentative de plus dépasse ibm.mq.max-retries (3).
                .retryCount(3)
                .receivedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        service.retry(failed.getId());

        Message published = receive(DLQ_QUEUE);

        assertThat(published).isInstanceOf(TextMessage.class);
        assertThat(published.getStringProperty("originalMessageId")).isEqualTo("mq-dlq");
        assertThat(published.getStringProperty("reference")).isEqualTo("REF-DLQ");
        assertThat(((TextMessage) published).getText()).contains("mq-dlq");

        PaymentMessage reread = repository.findById(failed.getId()).orElseThrow();
        assertThat(reread.getStatus()).isEqualTo(PaymentMessageStatus.DEAD_LETTER);
        assertThat(reread.getDlqPublishedAt()).isNotNull();
    }

    private Message receive(String queue) {
        return jmsTemplate.receive(queue);
    }

    private void drain(String queue) {
        while (receive(queue) != null) {
            // Consomme les reliquats du test précédent.
        }
    }

    private static String payload(String messageId, String reference) {
        return """
                {
                  "messageId": "%s",
                  "messageType": "pacs.008",
                  "reference": "%s",
                  "status": "RECEIVED",
                  "payment": {
                    "transactionId": "TX-%s",
                    "amount": 250.00,
                    "currency": "EUR",
                    "executionDate": "2026-07-25"
                  }
                }
                """.formatted(messageId, reference, messageId);
    }
}
