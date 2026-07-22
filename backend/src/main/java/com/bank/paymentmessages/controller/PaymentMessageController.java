package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.service.PaymentMessageService;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@RestController
@RequestMapping("/api/v1/messages")
public class PaymentMessageController {

    private final PaymentMessageService service;

    public PaymentMessageController(PaymentMessageService service){
        this.service = service;
    }

    @GetMapping
    public List<PaymentMessage> findAll(){
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PaymentMessage findById(@PathVariable Long id){
        return service.findById(id);
    }

}
