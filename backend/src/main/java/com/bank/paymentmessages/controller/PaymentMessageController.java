package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;



@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Gestion des messages MQ")
public class PaymentMessageController {

    private final PaymentMessageService service;

    public PaymentMessageController(PaymentMessageService service){
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Pagine et filtre la liste des messages",
            description = "Retourne une page de messages de paiement. Filtres optionnels : status, receivedAfter"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de messages récupérée avec succès")
    })
    public Page<PaymentMessageDto> findAll(
            @RequestParam(required = false) PaymentMessageStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime receivedAfter,
            @PageableDefault(size = 20) Pageable pageable) {
        if (status != null || receivedAfter != null) {
            return service.search(status, receivedAfter, pageable);
        }
        return service.findAll(pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Stats des messages par statut")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistiques calculées")
    })
    public Map<PaymentMessageStatus, Long> getStats() {
        return service.getStats();
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime un message")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Message supprimé"),
            @ApiResponse(responseCode = "404", description = "Message inexistant")
    })
    public void deleteById(@Parameter(description = "Identifiant du message") @PathVariable Long id) {
        service.deleteById(id);
    }

    @PostMapping("/batch/retry-failed")
    @Operation(summary = "Relance tous les messages FAILED et RETRY_PENDING",
            description = "Réinitialise retryCount, efface les erreurs et repasse en RETRY_PENDING")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Messages relancés")
    })
    public Map<String, Object> batchRetryFailed() {
        int count = service.batchRetryFailed();
        return Map.of("affected", count, "status", "RETRY_PENDING");
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Réinitialise un message pour nouvel essai",
            description = "Remet retryCount à 0, efface l'erreur et passe le statut en RETRY_PENDING")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message réinitialisé pour retry"),
            @ApiResponse(responseCode = "404", description = "Message inexistant")
    })
    public PaymentMessageDto retry(@Parameter(description = "Identifiant du message") @PathVariable Long id) {
        return service.retry(id);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Change le statut d'un message")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut mis à jour"),
            @ApiResponse(responseCode = "404", description = "Message inexistant")
    })
    public PaymentMessageDto updateStatus(
            @Parameter(description = "Identifiant du message") @PathVariable Long id,
            @RequestBody PaymentMessageStatus status) {
        return service.updateStatus(id, status);
    }

}
