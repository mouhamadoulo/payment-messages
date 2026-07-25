package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.LoginRequest;
import com.bank.paymentmessages.dto.api.LoginResponse;
import com.bank.paymentmessages.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Émission des jetons d'accès. Seul point d'entrée non authentifié de l'API.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification", description = "Obtention d'un jeton d'accès")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authentifie un compte et retourne un jeton",
            description = "Le jeton obtenu doit être présenté sur tous les autres appels dans "
                    + "l'en-tête `Authorization: Bearer <token>`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jeton émis"),
            @ApiResponse(responseCode = "400", description = "Requête incomplète"),
            @ApiResponse(responseCode = "401", description = "Identifiants invalides")
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        return tokenService.issue(authentication);
    }
}
