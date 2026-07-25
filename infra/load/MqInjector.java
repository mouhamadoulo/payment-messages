import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory;
import com.ibm.msg.client.jakarta.jms.JmsFactoryFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Injecteur de messages de paiement sur la file d'entrée IBM MQ.
 *
 * <p>Répond à la question que l'API ne pose pas : combien de messages par seconde la chaîne
 * MQ -> listener -> PostgreSQL absorbe-t-elle avant que la file ne s'allonge ? Le débit
 * affiché ici est celui de la <b>production</b> ; le débit de consommation se lit sur
 * {@code payment.mq.messages.received} et la profondeur de file sur le gestionnaire
 * ({@code DISPLAY QLOCAL(PAYMENT.REQUEST.QUEUE) CURDEPTH}).
 *
 * <p>Programme à fichier unique, lancé directement par le JDK 21 — pas de module à
 * construire, seul le client MQ est nécessaire sur le classpath :
 *
 * <pre>
 * cd backend
 * ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "$(cat target/cp.txt)" ../infra/load/MqInjector.java --count 10000 --threads 4
 * </pre>
 *
 * <p>Options (toutes facultatives) : {@code --host --port --qmgr --channel --user
 * --password --queue --count --threads --rate --persistent}.
 */
public class MqInjector {

    public static void main(String[] args) throws Exception {

        Options options = Options.parse(args);
        System.out.printf("Injection de %d messages sur %s@%s(%d) via %d thread(s)%n",
                options.count, options.queue, options.host, options.port, options.threads);

        JmsConnectionFactory factory = connectionFactory(options);

        AtomicLong sent = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        CountDownLatch done = new CountDownLatch(options.threads);
        Instant startedAt = Instant.now();

        for (int t = 0; t < options.threads; t++) {
            int share = options.count / options.threads + (t < options.count % options.threads ? 1 : 0);
            Thread.ofPlatform().name("injector-" + t).start(() -> {
                try {
                    produce(factory, options, share, sent, failed);
                } catch (Exception e) {
                    System.err.println("Producteur interrompu : " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        done.await();

        Duration elapsed = Duration.between(startedAt, Instant.now());
        double seconds = Math.max(elapsed.toMillis(), 1) / 1000.0;
        System.out.printf("%d messages envoyés, %d en échec, en %.1f s -> %.0f msg/s%n",
                sent.get(), failed.get(), seconds, sent.get() / seconds);
    }

    private static void produce(JmsConnectionFactory factory, Options options, int count,
                                AtomicLong sent, AtomicLong failed) throws Exception {

        // Une connexion et une session par thread : partagées, elles sérialiseraient les
        // envois et mesureraient le verrou du client plutôt que le broker.
        try (Connection connection = factory.createConnection(options.user, options.password);
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            Queue queue = session.createQueue("queue:///" + options.queue);
            MessageProducer producer = session.createProducer(queue);
            producer.setDeliveryMode(options.persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

            connection.start();

            // Espacement calculé une fois : --rate 0 signifie « aussi vite que possible ».
            long nanosBetweenMessages = options.rate <= 0
                    ? 0
                    : 1_000_000_000L / Math.max(1, options.rate / options.threads);
            long nextSendAt = System.nanoTime();

            for (int i = 0; i < count; i++) {
                if (nanosBetweenMessages > 0) {
                    nextSendAt += nanosBetweenMessages;
                    long wait = nextSendAt - System.nanoTime();
                    if (wait > 0) {
                        Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                    }
                }

                try {
                    producer.send(session.createTextMessage(payload()));
                    sent.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            }
        }
    }

    private static JmsConnectionFactory connectionFactory(Options options) throws Exception {

        JmsFactoryFactory ff = JmsFactoryFactory.getInstance(WMQConstants.JAKARTA_WMQ_PROVIDER);
        JmsConnectionFactory factory = ff.createConnectionFactory();

        factory.setStringProperty(WMQConstants.WMQ_HOST_NAME, options.host);
        factory.setIntProperty(WMQConstants.WMQ_PORT, options.port);
        factory.setStringProperty(WMQConstants.WMQ_CHANNEL, options.channel);
        factory.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
        factory.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, options.qmgr);
        factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);

        return factory;
    }

    /** Message valide au regard de la validation du listener : sinon on mesurerait le rejet. */
    private static String payload() {

        String id = UUID.randomUUID().toString();

        return """
                {
                  "messageId": "%s",
                  "messageType": "pacs.008",
                  "reference": "REF-%s",
                  "status": "RECEIVED",
                  "payment": {
                    "transactionId": "TX-%s",
                    "amount": 1250.75,
                    "currency": "EUR",
                    "executionDate": "%s"
                  },
                  "debtor": { "name": "ACME SA", "iban": "FR7630006000011234567890189", "bic": "AGRIFRPP" },
                  "creditor": { "name": "Globex", "iban": "DE89370400440532013000", "bic": "COBADEFF" }
                }
                """.formatted(id, id.substring(0, 8), id.substring(0, 8), LocalDate.now());
    }

    private record Options(String host, int port, String qmgr, String channel, String user,
                           String password, String queue, int count, int threads, int rate,
                           boolean persistent) {

        static Options parse(String[] args) {

            String host = "localhost";
            int port = 1414;
            String qmgr = "QM1";
            String channel = "DEV.APP.SVRCONN";
            String user = "app";
            String password = "passw0rd";
            String queue = "PAYMENT.REQUEST.QUEUE";
            int count = 1000;
            int threads = 1;
            int rate = 0;
            boolean persistent = true;

            for (int i = 0; i < args.length; i++) {
                String value = i + 1 < args.length ? args[i + 1] : null;
                switch (args[i]) {
                    case "--host" -> host = require(args[i], value);
                    case "--port" -> port = Integer.parseInt(require(args[i], value));
                    case "--qmgr" -> qmgr = require(args[i], value);
                    case "--channel" -> channel = require(args[i], value);
                    case "--user" -> user = require(args[i], value);
                    case "--password" -> password = require(args[i], value);
                    case "--queue" -> queue = require(args[i], value);
                    case "--count" -> count = Integer.parseInt(require(args[i], value));
                    case "--threads" -> threads = Integer.parseInt(require(args[i], value));
                    case "--rate" -> rate = Integer.parseInt(require(args[i], value));
                    case "--persistent" -> persistent = Boolean.parseBoolean(require(args[i], value));
                    default -> {
                        continue;
                    }
                }
                i++;
            }

            return new Options(host, port, qmgr, channel, user, password, queue,
                    count, Math.max(1, threads), rate, persistent);
        }

        private static String require(String option, String value) {
            if (value == null) {
                throw new IllegalArgumentException("Valeur manquante pour " + option);
            }
            return value;
        }
    }
}
