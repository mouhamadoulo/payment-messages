package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.service.BatchRetryService;
import com.bank.paymentmessages.service.BatchRetryTask;
import com.bank.paymentmessages.service.PaymentMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentMessageController.class)
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

        when(service.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].messageId").value("m1"))
                .andExpect(jsonPath("$.content[1].messageId").value("m2"))
                .andExpect(jsonPath("$.content[0].payloadSize").value(120))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist());
    }

    @Test
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
                .andExpect(status().isNotFound());
    }
}
