package com.bank.paymentmessages.controller;

import com.bank.paymentmessages.dto.api.SimulationConfigDto;
import com.bank.paymentmessages.dto.api.SimulationSendRequest;
import com.bank.paymentmessages.exception.PaymentMessageNotFoundException;
import com.bank.paymentmessages.service.SimulationService;
import com.bank.paymentmessages.service.SimulationTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulation d'envoi : dépôt de messages de test sur une file IBM MQ. L'écran correspondant
 * remplace les scripts d'injection manuels, et rien n'y court-circuite le flux — les messages
 * repassent par le consommateur applicatif comme ceux d'un vrai back-office.
 */
@RestController
@RequestMapping("/api/v1/simulation")
@Tag(name = "Simulation", description = "Dépôt de messages de test sur IBM MQ")
public class SimulationController {

    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    @GetMapping("/config")
    @Operation(summary = "File visée et bornes de la simulation",
            description = "File d'entrée sur laquelle les messages de test sont déposés — elle n'est "
                    + "pas choisie par l'appelant —, plafond du nombre de messages et de la cadence, "
                    + "et drapeau d'activation. L'IHM s'en sert pour afficher la destination et cadrer "
                    + "sa saisie ; le serveur refuse de toute façon une demande hors bornes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuration de la simulation")
    })
    public SimulationConfigDto config() {
        return service.config();
    }

    @PostMapping("/sends")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Dépose des messages de test sur la file d'entrée",
            description = "La destination est la file configurée (ibm.mq.queue), pas un paramètre. "
                    + "La publication est cadencée en tâche de fond : la réponse est immédiate (202) "
                    + "et rend un taskId à suivre via GET /sends/{taskId}. Le payload part tel quel "
                    + "sur la file — un payload illisible est un cas de test valide, il sera rejeté "
                    + "en FAILED par le consommateur. Sauf uniqueIds=false, le champ messageId de "
                    + "chaque copie est réécrit, faute de quoi l'ingestion, idempotente sur ce champ, "
                    + "n'en garderait qu'une. Un seul envoi peut être en cours à la fois.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Envoi accepté"),
            @ApiResponse(responseCode = "400", description = "Payload vide ou bornes dépassées"),
            @ApiResponse(responseCode = "503", description = "Simulation désactivée sur cet environnement")
    })
    public SimulationTask send(@Valid @RequestBody SimulationSendRequest request) {
        return service.start(request);
    }

    @GetMapping("/sends/{taskId}")
    @Operation(summary = "Suit l'avancement d'un envoi de test",
            description = "Les compteurs portent sur la publication : `published` signifie que le "
                    + "broker a accepté le message, pas qu'il a été traité. Son sort applicatif "
                    + "s'observe dans la liste des messages.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "État de l'envoi"),
            @ApiResponse(responseCode = "404", description = "Envoi inconnu")
    })
    public SimulationTask status(
            @Parameter(description = "Identifiant rendu au démarrage de l'envoi") @PathVariable String taskId) {
        return service.find(taskId)
                .orElseThrow(() -> PaymentMessageNotFoundException.forSimulationTask(taskId));
    }
}
