import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { retry, throwError, timeout, timer } from 'rxjs';

/**
 * Délai maximal d'une requête. Aligné sur `app.request-timeout` côté serveur (15 s) : au-delà,
 * le serveur a déjà abandonné, attendre plus longtemps ne peut plus rien produire d'utile.
 */
export const REQUEST_TIMEOUT_MS = 15_000;

/** Nombre de tentatives supplémentaires après l'échec initial. */
export const RETRY_ATTEMPTS = 2;

/** Base de la temporisation exponentielle : 300 ms, puis 600 ms. */
const RETRY_BASE_DELAY_MS = 300;

/**
 * Résilience réseau, appliquée en bout de chaîne (donc sur la requête définitive, jeton
 * compris).
 *
 * - **Délai maximal** sur toutes les méthodes : sans lui, une requête peut rester en vol
 *   indéfiniment et laisser la vue sur son squelette de chargement.
 * - **Rejeu avec temporisation exponentielle** limité aux méthodes idempotentes : rejouer un
 *   `POST /retry` ou un `DELETE` doublerait l'effet métier. Un `GET` peut être rejoué sans
 *   conséquence.
 *
 * Seules les pannes réellement transitoires sont rejouées : coupure réseau (`status 0`) et
 * erreurs serveur (`5xx`). Un `4xx` est une erreur de la requête elle-même — la rejouer
 * donnerait la même réponse. Un dépassement du délai n'est pas rejoué non plus : trois
 * tentatives à 15 s enchaîneraient 45 s d'attente pour l'utilisateur.
 *
 * Le rejeu ne concerne que les échecs : une requête annulée (`switchMap` du service, ou
 * changement de paramètres d'une ressource) est un désabonnement, pas une erreur.
 */
export const resilienceInterceptor: HttpInterceptorFn = (req, next) => {
  const bounded = next(req).pipe(timeout(REQUEST_TIMEOUT_MS));
  const idempotent = req.method === 'GET' || req.method === 'HEAD';

  return idempotent
    ? bounded.pipe(retry({ count: RETRY_ATTEMPTS, delay: backoff }))
    : bounded;
};

/** `attempt` vaut 1 à la première reprise : 300 ms, puis 600 ms. */
function backoff(error: unknown, attempt: number) {
  return isTransient(error)
    ? timer(RETRY_BASE_DELAY_MS * 2 ** (attempt - 1))
    : throwError(() => error);
}

function isTransient(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}
