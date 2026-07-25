package com.bank.paymentmessages.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Identifiants de connexion")
public record LoginRequest(

        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        @Schema(example = "admin")
        String username,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String password) {
}
