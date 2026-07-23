package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.PaymentMessageDto;
import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Gestion des messages MQ")
public class PaymentMessageController {

    private final PaymentMessageService service;

    public PaymentMessageController(PaymentMessageService service){
        this.service = service;
    }

    @GetMapping
    public List<PaymentMessageDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Retourne un message",
            description = "Recherche un message par son identifiant"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message trouvé"),
            @ApiResponse(responseCode = "404", description = "Message inexistant")
    })
    public PaymentMessageDto findById(@Parameter(description = "Identifiant du message") @PathVariable Long id) {
        return service.findById(id);
    }

}
