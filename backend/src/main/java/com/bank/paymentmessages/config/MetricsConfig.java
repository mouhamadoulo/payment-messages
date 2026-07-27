package com.bank.paymentmessages.config;

import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Métriques métier exposées via {@code /actuator/prometheus}.
 * <p>
 * Les compteurs et le chronomètre d'ingestion sont déclarés au plus près du flux
 * ({@code PaymentMessageListener}, {@code DeadLetterPublisher}, {@code JmsConfig}) ;
 * seules les jauges, qui interrogent l'état courant, sont câblées ici.
 * <p>
 * Une jauge est lue à chaque collecte : elle s'appuie donc sur l'agrégat déjà mis en
 * cache par le service, sans quoi chaque scrutation Prometheus déclencherait un
 * {@code GROUP BY} sur toute la table.
 */
@Configuration
public class MetricsConfig {

    /**
     * Nombre de messages en attente de traitement. C'est l'indicateur de saturation : il
     * croît dès que l'ingestion dépasse la capacité de traitement en aval.
     */
    @Bean
    public Gauge pendingMessagesGauge(MeterRegistry registry, PaymentMessageService service) {
        return Gauge.builder("payment.messages.pending",
                        () -> service.getStats().getOrDefault(PaymentMessageStatus.RECEIVED, 0L))
                .description("Messages en statut RECEIVED, en attente de traitement")
                .strongReference(true)
                .register(registry);
    }

    /** Messages abandonnés, dont la republication DLQ a abouti ou non. */
    @Bean
    public Gauge deadLetterMessagesGauge(MeterRegistry registry, PaymentMessageService service) {
        return Gauge.builder("payment.messages.dead.letter",
                        () -> service.getStats().getOrDefault(PaymentMessageStatus.DEAD_LETTER, 0L))
                .description("Messages en statut DEAD_LETTER")
                .strongReference(true)
                .register(registry);
    }

    @Bean
    public Gauge failedMessagesGauge(MeterRegistry registry, PaymentMessageService service) {
        return Gauge.builder("payment.messages.failed",
                        () -> service.getStats().getOrDefault(PaymentMessageStatus.FAILED, 0L))
                .description("Messages en statut FAILED, rejouables")
                .strongReference(true)
                .register(registry);
    }
}
