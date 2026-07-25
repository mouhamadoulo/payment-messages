package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.CursorPageDto;
import com.bank.paymentmessages.dto.api.DashboardStatsDto;
import com.bank.paymentmessages.dto.api.PaymentMessageDto;
import com.bank.paymentmessages.dto.api.PaymentMessageSummaryDto;
import com.bank.paymentmessages.dto.api.UpdateStatusRequest;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.service.BatchRetryService;
import com.bank.paymentmessages.service.BatchRetryTask;
import com.bank.paymentmessages.service.MessageQuery;
import com.bank.paymentmessages.service.PaymentMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messages", description = "Gestion des messages MQ")
public class PaymentMessageController {

    /**
     * Borne haute de la pagination par curseur, alignée sur
     * {@code spring.data.web.pageable.max-page-size} : sans elle, {@code ?size=100000}
     * était accepté tel quel.
     */
    private static final long MAX_PAGE_SIZE = 200;

    private final PaymentMessageService service;
    private final BatchRetryService batchRetryService;
    private final Duration statsCacheTtl;

    public PaymentMessageController(PaymentMessageService service,
                                    BatchRetryService batchRetryService,
                                    @Value("${app.http-cache.stats-ttl}") Duration statsCacheTtl){
        this.service = service;
        this.batchRetryService = batchRetryService;
        this.statsCacheTtl = statsCacheTtl;
    }

    @GetMapping
    @Operation(
            summary = "Pagine et filtre la liste des messages",
            description = "Retourne une page de messages de paiement, sans leur payload : seule sa taille "
                    + "(payloadSize) est renvoyée, le payload complet est servi par GET /{id}. "
                    + "Filtres optionnels : status, receivedAfter, type, q (fragment recherché dans la "
                    + "référence, le messageId ou le type). Tous sont appliqués en base : une recherche "
                    + "porte sur toute la table, pas sur la page affichée."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de messages récupérée avec succès")
    })
    public Page<PaymentMessageSummaryDto> findAll(
            @RequestParam(required = false) PaymentMessageStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedAfter,
            @Parameter(description = "Type de message exact") @RequestParam(required = false) String type,
            @Parameter(description = "Fragment recherché (référence, messageId, type)") @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(MessageQuery.of(status, receivedAfter, type, q), pageable);
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
            @Parameter(description = "Type de message exact") @RequestParam(required = false) String type,
            @Parameter(description = "Fragment recherché (référence, messageId, type)") @RequestParam(required = false) String q,
            @Parameter(description = "Curseur rendu par l'appel précédent") @RequestParam(required = false) String cursor,
            @Parameter(description = "Taille de page, 200 au maximum")
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return service.searchByCursor(MessageQuery.of(status, receivedAfter, type, q), cursor, size);
    }

    /**
     * L'agrégat est déjà mis en cache côté service ; un {@code Cache-Control} de même durée
     * évite en plus l'aller-retour réseau, le front interrogeant {@code /stats} après chaque
     * action unitaire. L'{@code ETag} posé par le filtre applicatif transforme un rappel
     * inchangé en 304 sans corps.
     */
    @GetMapping("/stats")
    @Operation(summary = "Stats des messages par statut",
            description = "Compteurs par statut sous les filtres actifs (receivedAfter, type, q) : les "
                    + "pastilles de la liste annoncent ainsi ce que donnerait un clic dessus. Sans filtre, "
                    + "le résultat est mis en cache quelques secondes (cf. STATS_CACHE_TTL) et servi avec "
                    + "un Cache-Control de même durée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistiques calculées"),
            @ApiResponse(responseCode = "304", description = "Statistiques inchangées depuis l'ETag fourni")
    })
    public ResponseEntity<Map<PaymentMessageStatus, Long>> getStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime receivedAfter,
            @Parameter(description = "Type de message exact") @RequestParam(required = false) String type,
            @Parameter(description = "Fragment recherché (référence, messageId, type)") @RequestParam(required = false) String q) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(statsCacheTtl))
                .body(service.getStats(MessageQuery.of(null, receivedAfter, type, q)));
    }

    /**
     * Agrégats du dashboard. Ils remplacent l'échantillon de 200 messages complets que le
     * client téléchargeait pour recompter lui-même : quelques centaines d'octets, et des
     * comptages portant sur toute la table plutôt que sur l'échantillon.
     */
    @GetMapping("/stats/dashboard")
    @Operation(summary = "Agrégats du tableau de bord",
            description = "Volume par tranche horaire sur 24 h glissantes, répartition par type, "
                    + "répartition par nombre de tentatives et derniers messages en échec. Calculé en SQL, "
                    + "mis en cache quelques secondes (cf. STATS_CACHE_TTL).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agrégats calculés"),
            @ApiResponse(responseCode = "304", description = "Agrégats inchangés depuis l'ETag fourni")
    })
    public ResponseEntity<DashboardStatsDto> getDashboardStats() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(statsCacheTtl))
                .body(service.getDashboardStats());
    }

    @GetMapping("/types")
    @Operation(summary = "Types de messages présents en base",
            description = "Alimente le sélecteur de type de la barre de filtres. La liste ne peut pas être "
                    + "déduite de la page affichée, le filtre s'appliquant désormais à toute la table.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Types distincts, triés")
    })
    public ResponseEntity<List<String>> getMessageTypes() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(statsCacheTtl))
                .body(service.getMessageTypes());
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
    @Operation(summary = "Change le statut d'un message",
            description = "La transition doit être autorisée par la machine à états : "
                    + "RECEIVED → PROCESSED | FAILED, FAILED → RECEIVED | PROCESSED | DEAD_LETTER. "
                    + "PROCESSED et DEAD_LETTER sont terminaux. Un statut identique à l'actuel est "
                    + "accepté sans effet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut mis à jour"),
            @ApiResponse(responseCode = "400", description = "Corps de requête invalide"),
            @ApiResponse(responseCode = "404", description = "Message inexistant"),
            @ApiResponse(responseCode = "409", description = "Message modifié entre-temps"),
            @ApiResponse(responseCode = "422", description = "Transition de statut interdite")
    })
    public PaymentMessageDto updateStatus(
            @Parameter(description = "Identifiant du message") @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return service.updateStatus(id, request.status(), request.reason());
    }

}
