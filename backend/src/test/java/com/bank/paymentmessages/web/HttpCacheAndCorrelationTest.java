package com.bank.paymentmessages.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtres transverses enregistrés par {@code FilterRegistrationBean} : ils ne sont actifs
 * qu'en contexte applicatif complet, pas dans un test de couche web.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HttpCacheAndCorrelationTest {

    @Autowired
    private MockMvc mockMvc;

    /** Deuxième appel identique : le corps n'est pas retransmis (A8). */
    @Test
    void unchangedStatsShouldAnswer304OnSecondCall() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/messages/stats"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn();

        String etag = first.getResponse().getHeader("ETag");

        MvcResult second = mockMvc.perform(get("/api/v1/messages/stats")
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andReturn();

        assertThat(second.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void responseShouldCarryTheProvidedCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/messages/stats")
                        .header("X-Request-Id", "trace-42"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-42"));
    }

    /** Sans en-tête fourni, un identifiant est généré : toute requête reste corrélable. */
    @Test
    void responseShouldCarryAGeneratedCorrelationIdOtherwise() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/messages/stats"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
    }
}
