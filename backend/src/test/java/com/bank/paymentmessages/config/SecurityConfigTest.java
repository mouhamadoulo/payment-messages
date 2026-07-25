package com.bank.paymentmessages.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie la chaîne complète : un jeton réellement émis par l'application authentifie les
 * appels suivants, et ses rôles sont bien reconstitués côté serveur de ressources.
 * <p>
 * C'est le seul test qui couvre l'accord entre l'encodeur et le décodeur HMAC ; un test de
 * couche web avec un utilisateur simulé ne le ferait pas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void issuedTokenShouldGrantAccessToProtectedEndpoints() throws Exception {
        String token = login("operator", "operator");

        mockMvc.perform(get("/api/v1/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void issuedTokenShouldCarryRolesUsedForAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/messages/stats")
                        .header("Authorization", "Bearer " + login("operator", "operator")))
                .andExpect(status().isOk());

        // Le rôle USER ne suffit pas pour une suppression.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/messages/{id}", 1L)
                        .header("Authorization", "Bearer " + login("operator", "operator")))
                .andExpect(status().isForbidden());

        // Le compte ADMIN passe la règle d'autorisation (404 : le message n'existe pas).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/messages/{id}", 1L)
                        .header("Authorization", "Bearer " + login("admin", "admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void forgedTokenShouldBeRefused() throws Exception {
        // Signature produite avec un autre secret : le décodeur doit la rejeter.
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl19.J-o0FAKE";

        mockMvc.perform(get("/api/v1/messages").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbeShouldStayReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = jsonMapper.readTree(body).get("token").asString();
        assertThat(token).isNotBlank();
        return token;
    }
}
