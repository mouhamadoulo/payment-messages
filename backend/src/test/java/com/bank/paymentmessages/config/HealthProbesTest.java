package com.bank.paymentmessages.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat des sondes consommées par l'orchestrateur.
 * <p>
 * Le healthcheck de {@code docker-compose.yaml} et, plus tard, la {@code readinessProbe}
 * Kubernetes appellent ces routes <b>sans jeton</b> : une régression sur leur exposition ou
 * sur leur ouverture rendrait le conteneur définitivement « unhealthy » sans qu'aucun test
 * fonctionnel ne bronche.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthProbesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readinessProbeShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Le détail reste réservé aux appelants authentifiés : il énumère les composants et
     * leur état, donc la topologie interne.
     */
    @Test
    void probeDetailsShouldStayHiddenFromAnonymousCallers() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
