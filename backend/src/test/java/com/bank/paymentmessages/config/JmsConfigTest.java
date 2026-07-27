package com.bank.paymentmessages.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrouille la configuration d'acquittement : la clé historique
 * {@code spring.jms.listener.acknowledge-mode} n'est plus liée en Spring Boot 4 et
 * était donc ignorée silencieusement. Le mode réellement appliqué doit être la
 * session transactée, seul mode qui permet à un rollback de provoquer une redélivrance.
 */
@SpringBootTest
@ActiveProfiles("test")
class JmsConfigTest {

    @Autowired
    private DefaultJmsListenerContainerFactory factory;

    @Test
    void listenerContainerShouldBeTransactedAndConfiguredFromProperties() {

        SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setDestination("TEST.QUEUE");
        endpoint.setMessageListener(message -> { });

        DefaultMessageListenerContainer container = factory.createListenerContainer(endpoint);

        assertThat(container.isSessionTransacted()).isTrue();
        assertThat(container.getConcurrentConsumers()).isEqualTo(2);
        assertThat(container.getMaxConcurrentConsumers()).isEqualTo(4);
        assertThat(container.getErrorHandler()).isNotNull();
    }
}
