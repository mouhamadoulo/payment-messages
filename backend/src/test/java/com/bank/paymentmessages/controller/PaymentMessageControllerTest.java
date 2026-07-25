package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.InvalidStatusTransitionException;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.service.BatchRetryService;
import com.bank.paymentmessages.service.BatchRetryTask;
import com.bank.paymentmessages.config.SecurityConfig;
import com.bank.paymentmessages.service.PaymentMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} n'embarque pas les {@code @Configuration} applicatives : sans import
 * explicite, c'est la sécurité par défaut de Boot qui s'appliquerait (CSRF actif, aucune
 * règle de rôle) et les tests d'autorisation ne vaudraient rien. Le profil {@code test}
 * fournit les propriétés {@code app.security.*} que cette configuration exige.
 */
@WebMvcTest(PaymentMessageController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class PaymentMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentMessageService service;

    @MockitoBean
    private BatchRetryService batchRetryService;

    @Test
    @WithMockUser
    void findAllShouldReturnListWithoutPayload() throws Exception {
        Page<PaymentMessageSummaryDto> page = new PageImpl<>(List.of(
                PaymentMessageSummaryDto.builder().id(1L).messageId("m1").payloadSize(120)
                        .status(PaymentMessageStatus.RECEIVED).build(),
                PaymentMessageSummaryDto.builder().id(2L).messageId("m2").payloadSize(240)
                        .status(PaymentMessageStatus.PROCESSED).build()
        ));

        when(service.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].messageId").value("m1"))
                .andExpect(jsonPath("$.content[1].messageId").value("m2"))
                .andExpect(jsonPath("$.content[0].payloadSize").value(120))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist());
    }

    @Test
    @WithMockUser
    void cursorEndpointShouldReturnNextCursor() throws Exception {
        when(service.searchByCursor(isNull(), isNull(), isNull(), anyInt())).thenReturn(
                new CursorPageDto<>(
                        List.of(PaymentMessageSummaryDto.builder().id(1L).messageId("m1").build()),
                        "Y3Vyc29y", true));

        mockMvc.perform(get("/api/v1/messages/cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].messageId").value("m1"))
                .andExpect(jsonPath("$.nextCursor").value("Y3Vyc29y"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void batchRetryShouldAcceptAndReturnTaskId() throws Exception {
        when(batchRetryService.start()).thenReturn(BatchRetryTask.running("task-1"));

        mockMvc.perform(post("/api/v1/messages/batch/retry-failed"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    @WithMockUser
    void batchRetryStatusShouldReturn404WhenTaskUnknown() throws Exception {
        when(batchRetryService.find("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/messages/batch/retry-failed/{taskId}", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void findByIdShouldReturnMessage() throws Exception {
        when(service.findById(1L)).thenReturn(
                PaymentMessageDto.builder().id(1L).messageId("m1").reference("REF-001").status(PaymentMessageStatus.RECEIVED).build()
        );

        mockMvc.perform(get("/api/v1/messages/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value("m1"))
                .andExpect(jsonPath("$.reference").value("REF-001"));
    }

    @Test
    @WithMockUser
    void findByIdShouldReturn404WhenNotFound() throws Exception {
        when(service.findById(99L)).thenThrow(new PaymentMessageNotFoundException(99L));

        mockMvc.perform(get("/api/v1/messages/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Ressource inexistante"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    // ---------------------------------------------------------------- A1 : accès

    @Test
    void listShouldBeRefusedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verify(service, never()).findAll(any(Pageable.class));
    }

    @Test
    @WithMockUser
    void deleteShouldBeRefusedWithoutAdminRole() throws Exception {
        mockMvc.perform(delete("/api/v1/messages/{id}", 1L))
                .andExpect(status().isForbidden());

        verify(service, never()).deleteById(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteShouldSucceedForAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/messages/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(service).deleteById(1L);
    }

    @Test
    @WithMockUser
    void statusUpdateShouldBeRefusedWithoutAdminRole() throws Exception {
        mockMvc.perform(put("/api/v1/messages/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSED\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------- A3 / A4 : contrat de statut

    @Test
    @WithMockUser(roles = "ADMIN")
    void statusUpdateShouldAcceptStructuredBody() throws Exception {
        when(service.updateStatus(eq(1L), eq(PaymentMessageStatus.PROCESSED), eq("Vérifié manuellement")))
                .thenReturn(PaymentMessageDto.builder().id(1L).messageId("m1")
                        .status(PaymentMessageStatus.PROCESSED).build());

        mockMvc.perform(put("/api/v1/messages/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSED\",\"reason\":\"Vérifié manuellement\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statusUpdateShouldRejectMissingStatus() throws Exception {
        mockMvc.perform(put("/api/v1/messages/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"sans statut\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void statusUpdateShouldReturn422OnForbiddenTransition() throws Exception {
        when(service.updateStatus(eq(1L), eq(PaymentMessageStatus.DEAD_LETTER), isNull()))
                .thenThrow(new InvalidStatusTransitionException(
                        PaymentMessageStatus.RECEIVED, PaymentMessageStatus.DEAD_LETTER));

        mockMvc.perform(put("/api/v1/messages/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DEAD_LETTER\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.from").value("RECEIVED"))
                .andExpect(jsonPath("$.to").value("DEAD_LETTER"));
    }

    // ------------------------------------------------ A7 / A8 : garde-fous et cache

    @Test
    @WithMockUser
    void cursorSizeAboveLimitShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/messages/cursor").param("size", "100000"))
                .andExpect(status().isBadRequest());

        verify(service, never()).searchByCursor(any(), any(), any(), anyInt());
    }

    /** L'ETag, posé par un filtre enregistré via FilterRegistrationBean, est couvert par
     *  {@code HttpCacheAndCorrelationTest} : {@code @WebMvcTest} n'enregistre pas ces filtres. */
    @Test
    @WithMockUser
    void statsShouldBeCacheable() throws Exception {
        when(service.getStats()).thenReturn(Map.of(PaymentMessageStatus.RECEIVED, 3L));

        mockMvc.perform(get("/api/v1/messages/stats"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=15"));
    }
}
