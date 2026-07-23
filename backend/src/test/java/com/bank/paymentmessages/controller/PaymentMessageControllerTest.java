package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.PaymentMessageDto;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.service.PaymentMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentMessageController.class)
class PaymentMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentMessageService service;

    @Test
    void findAllShouldReturnList() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                PaymentMessageDto.builder().id(1L).messageId("m1").status(PaymentMessageStatus.RECEIVED).build(),
                PaymentMessageDto.builder().id(2L).messageId("m2").status(PaymentMessageStatus.PROCESSED).build()
        ));

        mockMvc.perform(get("/api/v1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value("m1"))
                .andExpect(jsonPath("$[1].messageId").value("m2"));
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
        when(service.findById(99L)).thenThrow(new RuntimeException("Message introuvable"));

        mockMvc.perform(get("/api/v1/messages/{id}", 99L))
                .andExpect(status().isNotFound());
    }
}
