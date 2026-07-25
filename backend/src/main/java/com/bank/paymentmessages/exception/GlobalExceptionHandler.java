package com.bank.paymentmessages.exception;

import com.bank.paymentmessages.web.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Réponses d'erreur au format {@code application/problem+json} (RFC 9457).
 * <p>
 * Deux principes :
 * <ul>
 *   <li>la classe étend {@link ResponseEntityExceptionHandler}, si bien que les exceptions
 *       déjà qualifiées par Spring (corps illisible, paramètre manquant, méthode non
 *       supportée, validation) conservent leur statut d'origine — un fourre-tout sur
 *       {@code Exception} les dégraderait toutes en 500 ;</li>
 *   <li>aucun message d'exception n'est renvoyé pour une erreur non maîtrisée : le détail
 *       (message SQL, chemin interne, classe) part dans les logs, le client ne reçoit
 *       qu'un {@code correlationId} qui permet de retrouver la trace correspondante.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TYPE_PREFIX = "urn:payment-messages:";

    @ExceptionHandler(PaymentMessageNotFoundException.class)
    public ProblemDetail handleNotFound(PaymentMessageNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Ressource inexistante", ex.getMessage());
    }

    /**
     * Statut demandé incompatible avec l'état courant : la requête est bien formée, son
     * contenu est incohérent — 422 et non 400.
     */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidStatusTransitionException ex) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-status-transition",
                "Transition de statut interdite", ex.getMessage());
        problem.setProperty("from", ex.getFrom());
        problem.setProperty("to", ex.getTo());
        problem.setProperty("allowedTransitions", ex.getFrom().allowedTransitions());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Requête invalide", ex.getMessage());
    }

    /**
     * Verrou optimiste perdu : un autre appel a modifié la même ligne entre-temps.
     * Le client doit recharger le message et rejouer son action plutôt que d'écraser
     * silencieusement la modification concurrente.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Modification concurrente",
                "Le message a été modifié par une autre opération, rechargez-le");
    }

    /** Identifiants refusés sur {@code /auth/login}. Le motif exact n'est jamais détaillé. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationFailure(AuthenticationException ex) {
        log.warn("Authentification refusée : {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentification refusée",
                "Identifiants invalides");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Accès refusé",
                "Cette opération requiert un rôle supplémentaire");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {

        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Erreur interne",
                "Le traitement a échoué. Communiquez le correlationId au support.");

        log.error("Erreur non maîtrisée (correlationId={})", problem.getProperties().get("correlationId"), ex);
        return problem;
    }

    /** Détaille les champs en échec, sinon un 400 de validation n'est pas exploitable côté client. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "Requête invalide", "Un ou plusieurs champs sont invalides");
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Complète les réponses produites par la classe mère (400, 405, 415…) avec les mêmes
     * métadonnées que les nôtres, pour que le format reste homogène sur toute l'API.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            enrich(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        enrich(problem);
        return problem;
    }

    private static void enrich(ProblemDetail problem) {
        problem.setProperty("timestamp", OffsetDateTime.now());
        problem.setProperty("correlationId", CorrelationIdFilter.currentOrNew());
    }
}
