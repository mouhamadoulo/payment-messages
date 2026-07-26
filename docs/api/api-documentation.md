# API REST — Documentation

Base URL : `http://localhost:8080`

Swagger UI : `http://localhost:8080/swagger-ui.html`

---

## Authentification

**Hors périmètre du sujet.** L'API n'exige aucun jeton et ne connaît ni compte ni rôle :
tous les endpoints, y compris `/actuator/**` et Swagger UI, sont accessibles sans
identification. Le service doit donc rester déployé sur un réseau de confiance.

La seule politique navigateur restante est le CORS (`app.cors.allowed-origins`), qui borne
les origines admises sur `/api/**`.

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

## Configuration

### GET /api/v1/config

Configuration IBM MQ **non sensible**, pour affichage dans l'IHM (bandeau latéral, écran de
simulation). Ni utilisateur ni mot de passe n'est retourné : le DTO ne porte pas ces champs,
ce n'est pas un filtrage à l'exécution.

**Réponse** `200 OK`

```json
{
  "queue": "PAYMENT.REQUEST.QUEUE",
  "dlqQueue": "PAYMENT.DLQ.QUEUE",
  "queueManager": "QM1",
  "channel": "DEV.APP.SVRCONN"
}
```

Les valeurs sont figées à la construction du contrôleur depuis `ibm.mq.*` : l'endpoint ne relit
pas la configuration à chaque appel.

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

Supprime un message par son ID.

**Paramètres**

| Nom | Type | Requis |
|---|---|---|
| `id` | `Long` | Oui (path) |

**Réponse** `204 No Content`

**Erreurs** : `404 Not Found`

---

### POST /api/v1/messages/batch/retry-failed

Rejoue tous les messages en statut `FAILED` : `retryCount` est incrémenté et chaque message repasse en
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

Met à jour le statut d'un message.

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
| `404 Not Found` | Message inexistant |
| `409 Conflict` | Verrou optimiste perdu : le message a été modifié par une autre opération, le recharger avant de rejouer l'action |
| `422 Unprocessable Entity` | Transition interdite par la machine à états. La réponse porte `from`, `to` et `allowedTransitions` |

---

## Simulation d'envoi

**Base path :** `/api/v1/simulation`

Dépôt de messages de test sur la file d'entrée IBM MQ, dans le rôle que tiennent les
applications de back-office du flux réel. **Rien n'est écrit en base par ces endpoints** : le
payload est publié tel quel sur la file et repasse par le consommateur applicatif, avec la même
désérialisation, la même validation et les mêmes rejets. C'est ce qui rend l'écran utile — et
pourquoi il se coupe par configuration (`app.simulation.enabled`) là où la file porte un vrai
flux.

**La destination n'est pas un paramètre.** Les messages partent toujours sur `ibm.mq.queue`, la
seule file que l'application consomme : elle est *exposée* par `GET /config` pour affichage, pas
*choisie* par l'appelant. Laisser le client la fournir aurait ouvert l'écriture sur les autres
destinations du gestionnaire de files, pour un besoin qui n'existe pas — un dépôt ailleurs ne
produirait rien d'observable.

Restent deux garde-fous, côté serveur :

- le nombre de messages et la cadence sont bornés (`app.simulation.max-count` / `max-rate`) ;
- un seul envoi peut être en vol : deux envois concurrents ne tiendraient plus aucune des deux
  cadences demandées. Un second appel rend l'envoi déjà en cours.

---

### GET /api/v1/simulation/config

File visée et bornes. L'IHM s'en sert pour afficher la destination et cadrer sa saisie ; le
serveur refuse de toute façon ce qui dépasse les bornes.

**Réponse** `200 OK`

```json
{
  "enabled": true,
  "queue": "PAYMENT.REQUEST.QUEUE",
  "maxCount": 1000,
  "maxRate": 200
}
```

---

### POST /api/v1/simulation/sends

Dépose `count` copies du payload sur la file d'entrée, à la cadence demandée. La publication
s'exécute en tâche de fond : la réponse est immédiate et rend un `taskId` à suivre.

**Corps** — aucun champ de destination : elle vient de la configuration.

| Champ | Type | Requis | Défaut | Description |
|---|---|---|---|---|
| `payload` | `String` (≤ 64 Ko) | Oui | — | Publié **tel quel**. Un payload illisible est un cas de test valide : il sera rejeté en `FAILED` par le consommateur |
| `count` | `int ≥ 1` | Non | `1` | Plafonné par `app.simulation.max-count` |
| `ratePerSecond` | `int ≥ 1` | Non | `20` | Plafonné par `app.simulation.max-rate` |
| `uniqueIds` | `boolean` | Non | `true` | Réécrit `messageId` sur chaque copie |

> `uniqueIds` n'est pas un confort : l'ingestion est **idempotente sur `messageId`**. Sans
> réécriture, les copies d'un envoi en masse sont traitées comme des redélivrances du même
> message et une seule ligne est persistée. La réécriture ne s'applique qu'aux payloads qui
> sont des objets JSON — un payload illisible part inchangé, c'est précisément ce qu'on veut
> faire consommer.

**Requête**

```json
{
  "payload": "{\"messageId\":\"MSG-1\",\"messageType\":\"SEPA_CREDIT_TRANSFER\",\"reference\":\"REF-1\",\"payment\":{\"transactionId\":\"TX-1\",\"amount\":150.00,\"currency\":\"EUR\",\"executionDate\":\"2026-07-25\"},\"status\":\"RECEIVED\"}",
  "count": 200,
  "ratePerSecond": 20,
  "uniqueIds": true
}
```

**Réponse** `202 Accepted`

```json
{
  "taskId": "8f2c1b74-3a51-4d0e-9c11-7b0a5e2d4c93",
  "state": "RUNNING",
  "destination": "PAYMENT.REQUEST.QUEUE",
  "total": 200,
  "sent": 0,
  "published": 0,
  "failed": 0,
  "startedAt": "2026-07-25T10:00:00+02:00",
  "finishedAt": null,
  "error": null
}
```

**Erreurs**

| Code | Cas |
|---|---|
| `400 Bad Request` | `payload` vide ou trop long, `count`/`ratePerSecond` < 1 ou au-delà du plafond |
| `503 Service Unavailable` | Simulation désactivée (`app.simulation.enabled: false`) |

---

### GET /api/v1/simulation/sends/{taskId}

Suit l'avancement d'un envoi.

**Réponse** `200 OK`

```json
{
  "taskId": "8f2c1b74-3a51-4d0e-9c11-7b0a5e2d4c93",
  "state": "COMPLETED",
  "destination": "PAYMENT.REQUEST.QUEUE",
  "total": 200,
  "sent": 200,
  "published": 200,
  "failed": 0,
  "startedAt": "2026-07-25T10:00:00+02:00",
  "finishedAt": "2026-07-25T10:00:10+02:00",
  "error": null
}
```

`state` : `RUNNING` · `COMPLETED` · `FAILED`.

> Les compteurs portent sur la **publication**, pas sur le traitement : `published` signifie
> que le broker a accepté le message. Son sort applicatif — persisté en `RECEIVED` ou rejeté
> en `FAILED` — se lit dans `GET /api/v1/messages`.

**Erreur** `404 Not Found` — envoi inconnu ou sorti de l'historique (20 derniers).

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
| `urn:payment-messages:not-found` | 404 | — |
| `urn:payment-messages:concurrent-update` | 409 | — |
| `urn:payment-messages:invalid-status-transition` | 422 | `from`, `to`, `allowedTransitions` |
| `urn:payment-messages:internal-error` | 500 | `correlationId` (le message d'exception n'est **jamais** exposé) |

Les erreurs qualifiées nativement par Spring (corps illisible, paramètre manquant, méthode
non supportée, type de média) conservent leur statut d'origine et le même format.

---

## Voir aussi

| Sujet | Fichier |
|---|---|
| Contrat de la **file d'entrée** (distinct de celui-ci) | [`ibm-mq-configuration.md`](../ibm-mq/ibm-mq-configuration.md) §4.3 |
| Jeux de données prêts à déposer sur la file | [`docs/jdd/`](../jdd/README.md) |
| Collection Postman (38 requêtes, assertions de contrat) | [`docs/postman/`](../postman/README.md) |
| Couches, transactions et caches | [`architecture-backend.md`](../architecture/architecture-backend.md) |
| Modèle de données et requêtes | [`database-model.md`](../database/database-model.md) |

> Les deux contrats ne se confondent pas : les horodatages de **cette** API sont des
> `OffsetDateTime` (décalage horaire obligatoire), alors que la file attend une `LocalDate`
> pour `payment.executionDate` et une `LocalDateTime` **sans** décalage pour `createdAt`.
