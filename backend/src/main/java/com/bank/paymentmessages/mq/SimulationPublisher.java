package com.bank.paymentmessages.mq;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Producteur des messages de test : dépose un payload brut sur une file, en tenant le rôle
 * que jouent les applications de back-office dans le flux réel.
 * <p>
 * La file vient de {@code SimulationService}, qui la lit dans la configuration
 * ({@code ibm.mq.queue}) et ne l'expose pas comme paramètre d'API. Publier sur un nom de file
 * venu du client ouvrirait l'écriture sur n'importe quelle destination du gestionnaire.
 */
@Component
public class SimulationPublisher {

    private static final Logger log = LoggerFactory.getLogger(SimulationPublisher.class);

    /** Marque le message comme injecté par l'IHM : visible dans les outils MQ, pas en base. */
    private static final String ORIGIN_PROPERTY = "paymentOrigin";
    private static final String ORIGIN_SIMULATION = "SIMULATION";

    private final JmsTemplate jmsTemplate;
    private final Counter published;
    private final Counter failures;

    public SimulationPublisher(JmsTemplate jmsTemplate, MeterRegistry meterRegistry) {
        this.jmsTemplate = jmsTemplate;
        this.published = Counter.builder("payment.simulation.messages.published")
                .description("Messages de test acceptés par le broker")
                .register(meterRegistry);
        this.failures = Counter.builder("payment.simulation.publish.failures")
                .description("Echecs de publication d'un message de test")
                .register(meterRegistry);
    }

    /**
     * Publie un message de test.
     *
     * @return {@code true} si le broker a accepté le message. Un échec ne compromet pas
     *         l'envoi en cours : il est compté et le suivant est tenté.
     */
    public boolean publish(String queue, String payload) {

        try {
            jmsTemplate.send(queue, session -> {
                jakarta.jms.TextMessage message = session.createTextMessage(payload);
                message.setStringProperty(ORIGIN_PROPERTY, ORIGIN_SIMULATION);
                return message;
            });

            published.increment();
            return true;

        } catch (Exception e) {
            failures.increment();
            log.error("Echec de publication d'un message de test sur {}", queue, e);
            return false;
        }
    }
}
