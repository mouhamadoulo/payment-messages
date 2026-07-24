package com.bank.paymentmessages.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.jms.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.util.ErrorHandler;

/**
 * Personnalise la factory de conteneurs de listeners pour tracer les rollbacks.
 * <p>
 * Le {@link DefaultJmsListenerContainerFactoryConfigurer} applique d'abord toute la
 * configuration {@code spring.jms.listener.*} (session transactée, concurrence,
 * receive-timeout) : seul l'{@link ErrorHandler} est ajouté par-dessus.
 */
@Configuration
public class JmsConfig {

    private static final Logger log = LoggerFactory.getLogger(JmsConfig.class);

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            ErrorHandler jmsListenerErrorHandler) {

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setErrorHandler(jmsListenerErrorHandler);
        return factory;
    }

    /**
     * Toute exception remontée par le listener provoque un rollback de la session
     * transactée, donc une redélivrance. On la compte et on la trace : sans ce
     * compteur, une redélivrance en boucle reste invisible jusqu'à saturation.
     */
    @Bean
    public ErrorHandler jmsListenerErrorHandler(MeterRegistry meterRegistry) {

        Counter rollbacks = Counter.builder("payment.mq.listener.rollbacks")
                .description("Traitements en échec ayant provoqué un rollback de session JMS")
                .register(meterRegistry);

        return throwable -> {
            rollbacks.increment();
            log.error("Traitement du message en échec, rollback de la session JMS "
                    + "(le message sera redélivré jusqu'au seuil BOTHRESH)", throwable);
        };
    }
}
