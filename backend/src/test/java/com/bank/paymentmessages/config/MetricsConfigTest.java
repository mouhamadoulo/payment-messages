package com.bank.paymentmessages.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @AutoConfigureMetrics} est indispensable : Spring Boot désactive l'export de
 * métriques dans les tests, seul un {@code SimpleMeterRegistry} est câblé par défaut et
 * l'endpoint Prometheus n'existe alors pas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
class MetricsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusEndpointShouldExposeBusinessMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("payment_messages_pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("payment_messages_failed")));
    }

    /** Les métriques renseignent sur le trafic et l'état interne : elles restent fermées. */
    @Test
    void prometheusEndpointShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void environmentEndpointShouldNotBeExposedAtAll() throws Exception {
        // `env` révélait toute la configuration résolue : il est retiré de l'exposition.
        mockMvc.perform(get("/actuator/env").with(jwt()))
                .andExpect(status().isNotFound());
    }
}
