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
 * Kubernetes appellent ces routes : une régression sur leur exposition rendrait le
 * conteneur définitivement « unhealthy » sans qu'aucun test fonctionnel ne bronche.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthProbesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readinessProbeShouldAnswerUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeShouldAnswerUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * L'API n'ayant plus de chaîne d'authentification, le détail des sondes reste fermé
     * ({@code show-details: never}) : il énumère les composants et leur état, donc la
     * topologie interne. Seul le profil {@code dev} le rouvre.
     */
    @Test
    void probeDetailsShouldStayHidden() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
