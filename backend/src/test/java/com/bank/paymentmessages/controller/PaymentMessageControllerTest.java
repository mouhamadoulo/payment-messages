package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.DashboardStatsDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.InvalidStatusTransitionException;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.service.BatchRetryService;
import com.bank.paymentmessages.service.BatchRetryTask;
import com.bank.paymentmessages.service.MessageQuery;
import com.bank.paymentmessages.service.PaymentMessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Contrat HTTP du contrôleur. Le profil {@code test} fournit les propriétés que la
 * configuration attend ({@code app.http-cache.stats-ttl} notamment).
 */
@WebMvcTest(PaymentMessageController.class)
@ActiveProfiles("test")
class PaymentMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentMessageService service;

    @MockitoBean
    private BatchRetryService batchRetryService;

    @Test
    void findAllShouldReturnListWithoutPayload() throws Exception {
        Page<PaymentMessageSummaryDto> page = new PageImpl<>(List.of(
                PaymentMessageSummaryDto.builder().id(1L).messageId("m1").payloadSize(120)
                        .status(PaymentMessageStatus.RECEIVED).build(),
                PaymentMessageSummaryDto.builder().id(2L).messageId("m2").payloadSize(240)
                        .status(PaymentMessageStatus.PROCESSED).build()
        ));

        when(service.search(any(MessageQuery.class), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].messageId").value("m1"))
                .andExpect(jsonPath("$.content[1].messageId").value("m2"))
                .andExpect(jsonPath("$.content[0].payloadSize").value(120))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist());
    }

    @Test
    void cursorEndpointShouldReturnNextCursor() throws Exception {
        when(service.searchByCursor(any(MessageQuery.class), isNull(), anyInt())).thenReturn(
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
    void batchRetryShouldAcceptAndReturnTaskId() throws Exception {
        when(batchRetryService.start()).thenReturn(BatchRetryTask.running("task-1"));

        mockMvc.perform(post("/api/v1/messages/batch/retry-failed"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    void batchRetryStatusShouldReturn404WhenTaskUnknown() throws Exception {
        when(batchRetryService.find("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/messages/batch/retry-failed/{taskId}", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
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
    void findByIdShouldReturn404WhenNotFound() throws Exception {
        when(service.findById(99L)).thenThrow(new PaymentMessageNotFoundException(99L));

        mockMvc.perform(get("/api/v1/messages/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Ressource inexistante"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void deleteShouldRemoveTheMessage() throws Exception {
        mockMvc.perform(delete("/api/v1/messages/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(service).deleteById(1L);
    }

    // ------------------------------------------------- A3 / A4 : contrat de statut

    @Test
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
    void statusUpdateShouldRejectMissingStatus() throws Exception {
        mockMvc.perform(put("/api/v1/messages/{id}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"sans statut\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
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
    void cursorSizeAboveLimitShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/messages/cursor").param("size", "100000"))
                .andExpect(status().isBadRequest());

        verify(service, never()).searchByCursor(any(), any(), anyInt());
    }

    // ------------------------------------- F1 / F2 / F3 : filtres et agrégats côté serveur

    @Test
    void listShouldForwardEveryFilterToTheService() throws Exception {
        when(service.search(any(MessageQuery.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<MessageQuery> query = ArgumentCaptor.forClass(MessageQuery.class);

        mockMvc.perform(get("/api/v1/messages")
                        .param("status", "FAILED")
                        .param("type", "pacs.008")
                        .param("q", "  REF-42 ")
                        .param("receivedAfter", "2026-07-01T00:00:00+02:00"))
                .andExpect(status().isOk());

        verify(service).search(query.capture(), any(Pageable.class));
        assertThat(query.getValue().status()).isEqualTo(PaymentMessageStatus.FAILED);
        assertThat(query.getValue().type()).isEqualTo("pacs.008");
        // Normalisé une fois pour toutes : la requête compare lower(colonne) au motif.
        assertThat(query.getValue().text()).isEqualTo("ref-42");
        assertThat(query.getValue().receivedAfter()).isNotNull();
    }

    @Test
    void statsShouldForwardTheNonStatusFilters() throws Exception {
        when(service.getStats(any(MessageQuery.class))).thenReturn(Map.of(PaymentMessageStatus.FAILED, 2L));
        ArgumentCaptor<MessageQuery> query = ArgumentCaptor.forClass(MessageQuery.class);

        mockMvc.perform(get("/api/v1/messages/stats").param("type", "pacs.008").param("q", "ref"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FAILED").value(2));

        verify(service).getStats(query.capture());
        assertThat(query.getValue().type()).isEqualTo("pacs.008");
        assertThat(query.getValue().status()).isNull();
    }

    @Test
    void dashboardShouldExposeAggregatesAndBeCacheable() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2026-07-24T15:00:00+02:00");
        when(service.getDashboardStats()).thenReturn(new DashboardStatsDto(
                from, from.plusHours(24), 12L, from.plusHours(23),
                List.of(new DashboardStatsDto.HourlyBucket(from, 15, 12L)),
                List.of(new DashboardStatsDto.TypeCount("pacs.008", 12L)),
                new DashboardStatsDto.RetryBuckets(10L, 1L, 0L, 1L),
                List.of(PaymentMessageSummaryDto.builder().id(9L).messageId("m9")
                        .status(PaymentMessageStatus.FAILED).build())));

        mockMvc.perform(get("/api/v1/messages/stats/dashboard"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=15"))
                .andExpect(jsonPath("$.windowTotal").value(12))
                .andExpect(jsonPath("$.hourly[0].count").value(12))
                .andExpect(jsonPath("$.types[0].messageType").value("pacs.008"))
                .andExpect(jsonPath("$.retries.threeOrMore").value(1))
                .andExpect(jsonPath("$.recentFailures[0].messageId").value("m9"))
                .andExpect(jsonPath("$.recentFailures[0].payload").doesNotExist());
    }

    @Test
    void typesEndpointShouldNotBeShadowedByTheIdRoute() throws Exception {
        when(service.getMessageTypes()).thenReturn(List.of("pacs.002", "pacs.008"));

        mockMvc.perform(get("/api/v1/messages/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("pacs.002"))
                .andExpect(jsonPath("$[1]").value("pacs.008"));

        verify(service, never()).findById(any());
    }

    /** L'ETag, posé par un filtre enregistré via FilterRegistrationBean, est couvert par
     *  {@code HttpCacheAndCorrelationTest} : {@code @WebMvcTest} n'enregistre pas ces filtres. */
    @Test
    void statsShouldBeCacheable() throws Exception {
        when(service.getStats(any(MessageQuery.class))).thenReturn(Map.of(PaymentMessageStatus.RECEIVED, 3L));

        mockMvc.perform(get("/api/v1/messages/stats"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=15"));
    }
}
