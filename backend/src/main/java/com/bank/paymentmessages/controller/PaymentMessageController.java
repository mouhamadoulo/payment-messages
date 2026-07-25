package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.service.BatchRetryService;
import com.bank.paymentmessages.service.BatchRetryTask;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;



@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Gestion des messages MQ")
public class PaymentMessageController {

    private final PaymentMessageService service;
    private final BatchRetryService batchRetryService;

    public PaymentMessageController(PaymentMessageService service, BatchRetryService batchRetryService){
        this.service = service;
        this.batchRetryService = batchRetryService;
    }

    @GetMapping
    @Operation(
            summary = "Pagine et filtre la liste des messages",
            description = "Retourne une page de messages de paiement, sans leur payload : seule sa taille "
                    + "(payloadSize) est renvoyée, le payload complet est servi par GET /{id}. "
                    + "Filtres optionnels : status, receivedAfter"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de messages récupérée avec succès")
    })
    public Page<PaymentMessageSummaryDto> findAll(
            @RequestParam(required = false) PaymentMessageStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedAfter,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (status != null || receivedAfter != null) {
            return service.search(status, receivedAfter, pageable);
        }
        return service.findAll(pageable);
    }

    @GetMapping("/cursor")
    @Operation(
            summary = "Pagine la liste par curseur (keyset)",
            description = "Navigation séquentielle sans COUNT(*) ni OFFSET : coût constant quelle que soit "
                    + "la profondeur. Le tri est figé sur receivedAt DESC, id DESC. Repasser le nextCursor "
                    + "de la réponse précédente pour obtenir la page suivante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de messages récupérée avec succès"),
            @ApiResponse(responseCode = "400", description = "Curseur illisible")
    })
    public CursorPageDto<PaymentMessageSummaryDto> findByCursor(
            @RequestParam(required = false) PaymentMessageStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedAfter,
            @Parameter(description = "Curseur rendu par l'appel précédent") @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return service.searchByCursor(status, receivedAfter, cursor, size);
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
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Relance tous les messages FAILED",
            description = "Traitement de fond par lots bornés : la réponse est immédiate (202) et rend un "
                    + "taskId à suivre via GET /batch/retry-failed/{taskId}. Chaque message voit son "
                    + "retryCount incrémenté et repasse en RECEIVED ; au-delà du seuil ibm.mq.max-retries, "
                    + "il part en DEAD_LETTER et son payload est republié sur la Dead Letter Queue. "
                    + "Un seul rejeu massif peut être en cours à la fois.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Rejeu massif accepté")
    })
    public BatchRetryTask batchRetryFailed() {
        return batchRetryService.start();
    }

    @GetMapping("/batch/retry-failed/{taskId}")
    @Operation(summary = "Suit l'avancement d'un rejeu massif")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "État de la tâche"),
            @ApiResponse(responseCode = "404", description = "Tâche inconnue")
    })
    public BatchRetryTask batchRetryStatus(
            @Parameter(description = "Identifiant rendu au démarrage du rejeu") @PathVariable String taskId) {
        return batchRetryService.find(taskId)
                .orElseThrow(() -> PaymentMessageNotFoundException.forBatchRetryTask(taskId));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Rejoue un message en échec",
            description = "Incrémente retryCount, efface l'erreur et repasse le statut en RECEIVED. "
                    + "Au-delà du seuil ibm.mq.max-retries, le message part en DEAD_LETTER. "
                    + "Seuls les messages FAILED sont rejouables.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message rejoué"),
            @ApiResponse(responseCode = "400", description = "Message non rejouable (statut différent de FAILED)"),
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
