# API REST — Documentation

Base URL : `http://localhost:8080`

Swagger UI : `http://localhost:8080/swagger-ui.html` (bouton *Authorize* pour coller le jeton)

---

## Authentification

Toute l'API est fermée : `/api/v1/**` exige un jeton, à la seule exception de
`POST /api/v1/auth/login`. Le jeton est un JWT signé en HMAC-SHA256 par l'application
elle-même (secret `app.security.jwt.secret`, 32 octets minimum) — il n'y a pas de serveur
d'autorisation externe.

Les comptes sont déclarés en configuration (`app.security.users[*]`), avec un mot de passe
préfixé par son algorithme (`{bcrypt}…`, `{noop}…` en développement).

### POST /api/v1/auth/login

**Requête**

```json
{ "username": "admin", "password": "admin" }
```

**Réponse** `200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "admin",
  "roles": ["ADMIN", "USER"]
}
```

**Erreurs** : `400` requête incomplète · `401` identifiants invalides (le motif exact n'est
jamais détaillé).

Tous les autres appels portent ensuite :

```
Authorization: Bearer <token>
```

### Droits

| Rôle | Autorisé |
|---|---|
| `USER` | lecture (`GET`), rejeu unitaire (`POST /{id}/retry`) |
| `ADMIN` | tout, dont `DELETE /{id}`, `PUT /{id}/status`, `POST /batch/retry-failed` |

Un appel sans jeton répond `401`, un rôle insuffisant `403`.

### Endpoints publics

`POST /api/v1/auth/login`, `/actuator/health`, `/actuator/info`, et — tant que
`app.security.public-docs` vaut `true` — Swagger UI et `/v3/api-docs`. Le reste, y compris
`/actuator/metrics` et `/actuator/prometheus`, exige un jeton.

---

## Conventions transverses

- **En-tête de corrélation** : chaque réponse porte un `X-Request-Id`, repris de la requête
  s'il est fourni. Le même identifiant apparaît dans les logs du serveur et dans le champ
  `correlationId` des erreurs.
- **Erreurs** : format `application/problem+json` (RFC 9457), cf. dernière section.
- **Compression** : `gzip` actif au-delà de 1 Ko.
- **Taille de page bornée à 200** (`spring.data.web.pageable.max-page-size`) : au-delà, la
  valeur est ramenée à la borne pour `GET /messages`, et refusée en `400` pour
  `GET /messages/cursor`.
- **`ETag`** sur les lectures de `/api/v1/messages*` : renvoyer l'empreinte dans
  `If-None-Match` produit un `304 Not Modified` sans corps.

---

## Messages

**Base path :** `/api/v1/messages`

---

### GET /api/v1/messages

Liste paginée des messages avec filtres optionnels.

**Le payload n'est pas renvoyé** : la liste porte une projection (`PaymentMessageSummaryDto`)
où seule la taille du payload (`payloadSize`, en octets) est exposée. Le contenu complet est
servi par `GET /api/v1/messages/{id}`.

**Paramètres**

| Nom | Type | Requis | Description |
|---|---|---|---|
| `status` | `PaymentMessageStatus` | Non | Filtre par statut |
| `receivedAfter` | `OffsetDateTime` (ISO, fuseau inclus) | Non | Filtre par date de réception |
| `type` | `String` | Non | Filtre par type de message (égalité stricte) |
| `q` | `String` | Non | Fragment recherché, insensible à la casse, dans `reference`, `messageId` ou `messageType` |
| `page` | `int` | Non (défaut: 0) | Numéro de page |
| `size` | `int` | Non (défaut: 20) | Taille de page |
| `sort` | `String` | Non (défaut: `receivedAt,desc`) | Tri serveur |

**Réponse** `200 OK`

```json
{
  "content": [
    {
      "id": 1,
      "messageId": "MQ-MSG-20250115-001",
      "reference": "PAY-2025-001",
      "messageType": "PAYMENT_REQUEST",
      "status": "PROCESSED",
      "payloadSize": 412,
      "retryCount": 0,
      "errorMessage": null,
      "receivedAt": "2025-01-15T10:00:00+01:00",
      "updatedAt": "2025-01-15T10:00:05+01:00"
    }
  ],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 1,
  "totalPages": 1
}
```

---

### GET /api/v1/messages/cursor

Pagination par curseur (*keyset*) : ni `COUNT(*)` ni `OFFSET`, donc un coût constant quelle
que soit la profondeur de navigation. Le tri est figé sur `receivedAt DESC, id DESC`.

**Paramètres**

| Nom | Type | Requis | Description |
|---|---|---|---|
| `status` | `PaymentMessageStatus` | Non | Filtre par statut |
| `receivedAfter` | `OffsetDateTime` (ISO) | Non | Filtre par date de réception |
| `type` | `String` | Non | Filtre par type de message |
| `q` | `String` | Non | Fragment recherché (`reference`, `messageId`, `messageType`) |
| `cursor` | `String` | Non | Curseur opaque rendu par l'appel précédent ; absent = première page |
| `size` | `int` | Non (défaut: 20) | Nombre de messages à rendre |

**Réponse** `200 OK`

```json
{
  "content": [ { "id": 42, "messageId": "MQ-MSG-20250115-042", "payloadSize": 412 } ],
  "nextCursor": "MjAyNS0wMS0xNVQxMDowMDowMCswMTowMHw0Mg",
  "hasNext": true
}
```

**Erreur** `400 Bad Request` — curseur illisible.

---

### GET /api/v1/messages/stats

Retourne le nombre de messages pour chaque statut, **sous les filtres actifs** : les pastilles
de la liste annoncent ainsi ce que donnerait un clic dessus, au lieu d'un total global sans
rapport avec les lignes affichées. Le statut n'est volontairement pas un paramètre — c'est la
dimension de regroupement.

Sans filtre, l'agrégat est mis en cache côté serveur quelques secondes (`STATS_CACHE_TTL`) et
servi avec un `Cache-Control: max-age` de même durée plus un `ETag` : un rappel inchangé
répond `304` sans corps. Les variantes filtrées ne sont pas mises en cache (la clé
contiendrait un texte libre, donc un nombre non borné d'entrées).

**Paramètres**

| Nom | Type | Requis | Description |
|---|---|---|---|
| `receivedAfter` | `OffsetDateTime` (ISO) | Non | Filtre par date de réception |
| `type` | `String` | Non | Filtre par type de message |
| `q` | `String` | Non | Fragment recherché (`reference`, `messageId`, `messageType`) |

**Réponse** `200 OK`

```json
{
  "RECEIVED": 15,
  "PROCESSED": 42,
  "FAILED": 3,
  "DEAD_LETTER": 1
}
```

---

### GET /api/v1/messages/stats/dashboard

Agrégats du tableau de bord, **calculés en SQL**. Le dashboard dérivait auparavant toutes ses
visualisations d'un échantillon de 200 messages complets : plusieurs mégaoctets transférés
pour afficher une vingtaine de nombres, et des chiffres faux dès que la table dépassait 200
lignes. Cette réponse pèse quelques centaines d'octets et porte des comptages exacts.

- `hourly` : 24 tranches horaires consécutives, alignées sur l'heure pleine, **toujours
  présentes même à zéro**. Chaque tranche porte son instant de début (`bucketStart`, fuseau
  inclus) et l'heure correspondante côté serveur (`hour`) — l'heure est extraite dans le
  fuseau de la session base de données.
- `types` et `retries` : calculés sur **toute la table**, donc cohérents avec l'agrégat par
  statut de `/stats`.
- `recentFailures` : les cinq derniers messages `FAILED` / `DEAD_LETTER`, sous forme de
  `PaymentMessageSummaryDto` (sans payload).

Mis en cache et servi avec `Cache-Control` + `ETag` comme `/stats`. Le cache n'est **pas**
invalidé par l'ingestion (sous un flux soutenu, il serait vidé à chaque message et ne
servirait plus à rien) mais l'est par toute action unitaire ou massive de l'API.

**Réponse** `200 OK`

```json
{
  "windowFrom": "2025-01-14T11:00:00+01:00",
  "windowTo": "2025-01-15T11:00:00+01:00",
  "windowTotal": 128,
  "lastReceivedAt": "2025-01-15T10:58:12+01:00",
  "hourly": [ { "bucketStart": "2025-01-14T11:00:00+01:00", "hour": 11, "count": 4 } ],
  "types": [ { "messageType": "PAYMENT_REQUEST", "count": 120 } ],
  "retries": { "none": 118, "one": 6, "two": 3, "threeOrMore": 1 },
  "recentFailures": [ { "id": 42, "messageId": "MQ-MSG-20250115-042", "status": "FAILED" } ]
}
```

---

### GET /api/v1/messages/types

Types de messages présents en base, triés. Alimente le sélecteur de la barre de filtres : la
liste ne peut plus être déduite de la page affichée, le filtre par type s'appliquant
désormais à toute la table. Mis en cache et servi avec `Cache-Control` comme `/stats`.

**Réponse** `200 OK`

```json
["PAYMENT_REQUEST", "PAYMENT_STATUS"]
```

---

### GET /api/v1/messages/{id}

Détail d'un message par son ID technique.

**Paramètres**

| Nom | Type | Requis |
|---|---|---|
| `id` | `Long` | Oui (path) |

**Réponse** `200 OK`

```json
{
  "id": 1,
  "messageId": "MQ-MSG-20250115-001",
  "reference": "PAY-2025-001",
  "messageType": "PAYMENT_REQUEST",
  "status": "PROCESSED",
  "payload": "{...}",
  "payloadSize": 412,
  "retryCount": 0,
  "errorMessage": null,
  "receivedAt": "2025-01-15T10:00:00+01:00",
  "updatedAt": "2025-01-15T10:00:05+01:00"
}
```

**Erreur** `404 Not Found` — cf. « Schéma des réponses d'erreur ».

---

### DELETE /api/v1/messages/{id}

Supprime un message par son ID. **Réservé au rôle `ADMIN`.**

**Paramètres**

| Nom | Type | Requis |
|---|---|---|
| `id` | `Long` | Oui (path) |

**Réponse** `204 No Content`

**Erreurs** : `403 Forbidden` (rôle `ADMIN` requis) · `404 Not Found`

---

### POST /api/v1/messages/batch/retry-failed

**Réservé au rôle `ADMIN`.** Rejoue tous les messages en statut `FAILED` : `retryCount` est incrémenté et chaque message repasse en
`RECEIVED`. Au-delà de `ibm.mq.max-retries` tentatives, le message part en `DEAD_LETTER` et son payload
est republié sur la Dead Letter Queue.

Le traitement s'exécute **en tâche de fond, par lots bornés** (`app.batch-retry.batch-size`) :
la réponse est immédiate et rend un `taskId` à suivre. Un seul rejeu massif peut être en vol
à la fois ; un second appel rend la tâche déjà en cours. Le plafond
`app.batch-retry.max-messages` interrompt un rejeu trop volumineux (`truncated: true`) :
relancer l'appel pour poursuivre.

**Réponse** `202 Accepted`

```json
{
  "taskId": "4f1d0f8e-1c1a-4a0a-9f2b-0c7d2b0a5c31",
  "state": "RUNNING",
  "processed": 0,
  "truncated": false,
  "startedAt": "2025-01-15T10:00:00+01:00",
  "finishedAt": null,
  "error": null
}
```

---

### GET /api/v1/messages/batch/retry-failed/{taskId}

Suit l'avancement d'un rejeu massif.

**Réponse** `200 OK`

```json
{
  "taskId": "4f1d0f8e-1c1a-4a0a-9f2b-0c7d2b0a5c31",
  "state": "COMPLETED",
  "processed": 1200,
  "truncated": false,
  "startedAt": "2025-01-15T10:00:00+01:00",
  "finishedAt": "2025-01-15T10:00:12+01:00",
  "error": null
}
```

`state` : `RUNNING` · `COMPLETED` · `FAILED`.

**Erreur** `404 Not Found` — tâche inconnue ou sortie de l'historique (50 dernières).

---

### POST /api/v1/messages/{id}/retry

Rejoue un message en échec : `retryCount` est incrémenté et le message repasse en `RECEIVED`.
Au-delà de `ibm.mq.max-retries` tentatives, il passe en `DEAD_LETTER` et son payload est republié
sur la Dead Letter Queue. Seuls les messages `FAILED` sont rejouables.

**Paramètres**

| Nom | Type | Requis |
|---|---|---|
| `id` | `Long` | Oui (path) |

**Réponse** `200 OK`

```json
{
  "id": 1,
  "status": "RECEIVED",
  "retryCount": 2,
  "errorMessage": null
}
```

**Erreurs**

| Code | Cas |
|---|---|
| `400 Bad Request` | Statut différent de `FAILED` |
| `404 Not Found` | Message inexistant |

---

### PUT /api/v1/messages/{id}/status

Met à jour le statut d'un message. **Réservé au rôle `ADMIN`.**

La transition doit être autorisée par la machine à états (cf. `PaymentMessageStatus`) :

```
RECEIVED ──▶ PROCESSED (terminal)
   └──▶ FAILED ──▶ RECEIVED | PROCESSED | DEAD_LETTER (terminal)
```

Un statut identique au statut courant est accepté sans effet (commande idempotente, aucune
version consommée).

**Paramètres**

| Nom | Type | Requis |
|---|---|---|
| `id` | `Long` | Oui (path) |
| `status` (body) | `PaymentMessageStatus` | Oui |
| `reason` (body) | `String` (≤ 500) | Non |

**Requête**

```json
{ "status": "PROCESSED", "reason": "Vérifié manuellement après correction du bénéficiaire" }
```

> Le corps était auparavant une chaîne JSON brute (`"PROCESSED"`). Il est désormais un objet
> validé ; le `reason` est tracé dans les logs, et conservé comme `errorMessage` quand le
> statut cible est `FAILED` ou `DEAD_LETTER` (effacé sinon).

**Réponse** `200 OK`

```json
{
  "id": 1,
  "status": "PROCESSED",
  "updatedAt": "2025-01-15T10:00:05+01:00"
}
```

**Erreurs**

| Code | Cas |
|---|---|
| `400 Bad Request` | Corps invalide (statut absent, valeur d'enum inconnue, motif trop long) |
| `403 Forbidden` | Rôle `ADMIN` requis |
| `404 Not Found` | Message inexistant |
| `409 Conflict` | Verrou optimiste perdu : le message a été modifié par une autre opération, le recharger avant de rejouer l'action |
| `422 Unprocessable Entity` | Transition interdite par la machine à états. La réponse porte `from`, `to` et `allowedTransitions` |

---

## Modèles

### PaymentMessageStatus

| Valeur | Description |
|---|---|
| `RECEIVED` | Message reçu de la file MQ, en attente de traitement |
| `PROCESSED` | Traité avec succès (terminal) |
| `FAILED` | Erreur technique/métier, rejouable via `/retry` |
| `DEAD_LETTER` | Abandonné après `max-retries` tentatives, republié sur la DLQ (terminal) |

### PaymentMessageDto

| Champ | Type | Description |
|---|---|---|
| `id` | `Long` | Identifiant technique |
| `messageId` | `String` | Identifiant unique MQ |
| `reference` | `String` | Référence métier |
| `messageType` | `String` | Type de message |
| `status` | `PaymentMessageStatus` | Statut courant |
| `payload` | `String` | Message JSON brut (détail uniquement) |
| `payloadSize` | `Integer` | Taille du payload en octets |
| `retryCount` | `Integer` | Nombre de tentatives |
| `errorMessage` | `String` | Message d'erreur |
| `receivedAt` | `OffsetDateTime` | Date de réception MQ (fuseau inclus) |
| `updatedAt` | `OffsetDateTime` | Date de mise à jour |

### PaymentMessageSummaryDto (listes)

Mêmes champs que `PaymentMessageDto`, **sans `payload`**. C'est le type rendu par
`GET /api/v1/messages` et `GET /api/v1/messages/cursor`.

---

### PaymentMessageStatus — transitions

| Depuis | Vers |
|---|---|
| `RECEIVED` | `PROCESSED`, `FAILED` |
| `FAILED` | `RECEIVED`, `PROCESSED`, `DEAD_LETTER` |
| `PROCESSED` | — (terminal) |
| `DEAD_LETTER` | — (terminal) |

Une transition hors de ce tableau est refusée en `422`. La reprise d'un `DEAD_LETTER` passe
par la Dead Letter Queue, pas par un retour en base.

---

## Schéma des réponses d'erreur

Format `application/problem+json` (RFC 9457, `ProblemDetail`) :

```json
{
  "type": "urn:payment-messages:not-found",
  "title": "Ressource inexistante",
  "status": 404,
  "detail": "Message introuvable avec l'id : 99",
  "instance": "/api/v1/messages/99",
  "timestamp": "2026-07-25T10:30:00+02:00",
  "correlationId": "8f2c1e2a-6b41-4a0e-9a55-1d0e6b3c7a12"
}
```

`correlationId` reprend le `X-Request-Id` de la requête : c'est la clé pour retrouver la
trace serveur d'un incident.

**Variantes utiles**

| `type` | Statut | Champs supplémentaires |
|---|---|---|
| `urn:payment-messages:validation-failed` | 400 | `errors` : `{ champ: message }` |
| `urn:payment-messages:unauthorized` | 401 | — |
| `urn:payment-messages:forbidden` | 403 | — |
| `urn:payment-messages:not-found` | 404 | — |
| `urn:payment-messages:concurrent-update` | 409 | — |
| `urn:payment-messages:invalid-status-transition` | 422 | `from`, `to`, `allowedTransitions` |
| `urn:payment-messages:internal-error` | 500 | `correlationId` (le message d'exception n'est **jamais** exposé) |

Les erreurs qualifiées nativement par Spring (corps illisible, paramètre manquant, méthode
non supportée, type de média) conservent leur statut d'origine et le même format.
