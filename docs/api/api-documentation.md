# API REST — Documentation

Base URL : `http://localhost:8080`

Swagger UI : `http://localhost:8080/swagger-ui.html`

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

Retourne le nombre de messages pour chaque statut.

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

**Erreur** `404 Not Found`

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Message introuvable avec l'id : 99",
  "timestamp": "2025-01-15T10:30:00.000+00:00"
}
```

---

### DELETE /api/v1/messages/{id}

Supprime un message par son ID.

**Paramètres**

| Nom | Type | Requis |
|---|---|---|
| `id` | `Long` | Oui (path) |

**Réponse** `204 No Content`

**Erreur** `404 Not Found`

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

**Paramètres**

| Nom | Type | Requis |
|---|---|---|
| `id` | `Long` | Oui (path) |
| `status` (body) | `PaymentMessageStatus` | Oui |

**Requête**

```json
"PROCESSED"
```

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
| `400 Bad Request` | Statut invalide |
| `404 Not Found` | Message inexistant |
| `409 Conflict` | Verrou optimiste perdu : le message a été modifié par une autre opération, le recharger avant de rejouer l'action |

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

## Schéma des réponses d'erreur

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Message introuvable avec l'id : 99",
  "timestamp": "2025-01-15T10:30:00.000+00:00"
}
```
