package com.bank.paymentmessages.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Jeton d'accès à présenter dans l'en-tête Authorization")
public record LoginResponse(

        @Schema(description = "Jeton JWT signé", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,

        @Schema(description = "Schéma d'authentification à utiliser", example = "Bearer")
        String tokenType,

        @Schema(description = "Durée de validité du jeton, en secondes", example = "3600")
        long expiresIn,

        String username,

        @Schema(description = "Rôles applicatifs du compte", example = "[\"ADMIN\"]")
        List<String> roles) {
}
