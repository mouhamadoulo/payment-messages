package com.bank.paymentmessages;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@OpenAPIDefinition(
        info = @Info(
                title = "Payment Messages API",
                version = "1.0",
                description = "API REST de gestion des messages IBM MQ",
                contact = @Contact(
                        name = "Mouhamadou LO",
                        email = "mouhamadoulo39@gmail.com"
                )
        )
)
@SpringBootApplication
@EnableScheduling
public class PaymentMessagesApplication {

	public static void main(String[] args) {

		SpringApplication.run(PaymentMessagesApplication.class, args);
	}

}
