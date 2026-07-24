# API REST — Documentation

Base URL : `http://localhost:8080`

Swagger UI : `http://localhost:8080/swagger-ui.html`

---

## Messages

**Base path :** `/api/v1/messages`

---

### GET /api/v1/messages

Liste paginée des messages avec filtres optionnels.

**Paramètres**

| Nom | Type | Requis | Description |
|---|---|---|---|
| `status` | `PaymentMessageStatus` | Non | Filtre par statut |
| `receivedAfter` | `LocalDateTime` (ISO) | Non | Filtre par date de réception |
| `page` | `int` | Non (défaut: 0) | Numéro de page |
| `size` | `int` | Non (défaut: 20) | Taille de page |

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
      "payload": "{\"messageId\":\"...\"}",
      "retryCount": 0,
      "errorMessage": null,
      "receivedAt": "2025-01-15T10:00:00",
      "updatedAt": "2025-01-15T10:00:05"
    }
  ],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 1,
  "totalPages": 1
}
```

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
  "retryCount": 0,
  "errorMessage": null,
  "receivedAt": "2025-01-15T10:00:00",
  "updatedAt": "2025-01-15T10:00:05"
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

**Réponse** `200 OK`

```json
{
  "affected": 3,
  "status": "RECEIVED"
}
```

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
  "updatedAt": "2025-01-15T10:00:05"
}
```

**Erreurs**

| Code | Cas |
|---|---|
| `400 Bad Request` | Statut invalide |
| `404 Not Found` | Message inexistant |

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
| `payload` | `String` | Message JSON brut |
| `retryCount` | `Integer` | Nombre de tentatives |
| `errorMessage` | `String` | Message d'erreur |
| `receivedAt` | `LocalDateTime` | Date de réception MQ |
| `updatedAt` | `LocalDateTime` | Date de mise à jour |

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
