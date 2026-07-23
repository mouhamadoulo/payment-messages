# Flux de données

## 1. Vue d'ensemble

```mermaid
flowchart LR
    BO[Applications<br/>Back Office] -->|Message JSON| MQ[IBM MQ<br/>Queue Manager]

    MQ -->|Consommation JMS| LISTENER[PaymentMessageListener]

    LISTENER -->|Validation & Mapping| SERVICE[PaymentMessageService]

    SERVICE -->|Persistance| DB[(PostgreSQL)]

    USER[Utilisateur] -->|Consultation| UI[Angular Frontend]
    UI -->|API REST| API[PaymentMessageController]
    API -->|Requêtes| SERVICE
    SERVICE -->|Réponses| API
    API -->|JSON| UI
```

---

## 2. Cycle de vie d'un message

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: Message reçu de MQ
    RECEIVED --> VALIDATING: Validation démarrée
    VALIDATING --> PROCESSING: Validation réussie
    VALIDATING --> REJECTED: Structure invalide
    PROCESSING --> PROCESSED: Traitement réussi
    PROCESSING --> FAILED: Erreur technique
    PROCESSING --> RETRY_PENDING: Erreur temporaire
    FAILED --> RETRY_PENDING: Nouvelle tentative
    RETRY_PENDING --> PROCESSING: Reprise traitement
    FAILED --> DEAD_LETTER: Abandon (DLQ)
    RETRY_PENDING --> DEAD_LETTER: Échecs répétés
    PROCESSED --> [*]
    REJECTED --> [*]
    DEAD_LETTER --> [*]
```

### États

| Statut | Description |
|---|---|
| `RECEIVED` | Message reçu de la file MQ et persisté |
| `VALIDATING` | Validation de la structure en cours |
| `PROCESSING` | Traitement métier en cours |
| `PROCESSED` | Traitement terminé avec succès |
| `FAILED` | Erreur technique ou métier |
| `RETRY_PENDING` | En attente d'une nouvelle tentative |
| `REJECTED` | Message définitivement invalide |
| `DEAD_LETTER` | Message envoyé en Dead Letter Queue |

---

## 3. Flux détaillé : consommation MQ

```mermaid
sequenceDiagram
    participant BO as Back Office
    participant MQ as IBM MQ
    participant Listener as PaymentMessageListener
    participant Service as PaymentMessageService
    participant DB as PostgreSQL

    BO->>MQ: PUT message JSON
    MQ->>Listener: JMS Message (String payload)
    Listener->>Listener: Désérialisation JSON → PaymentMessageEvent
    Listener->>Listener: Validation (Jakarta Validation)

    alt Validation OK
        Listener->>Service: saveMessage(event, rawPayload)
        Service->>Service: Mapper.toEntity(event, rawPayload)
        Service->>DB: INSERT INTO payment_messages
        DB-->>Service: PaymentMessage (avec id)
        Service-->>Listener: PaymentMessage
        Listener->>Listener: Log INFO "Message reçu et persisté"
    else Validation échouée
        Listener->>Listener: Log ERROR "Échec validation"
    else Exception technique
        Listener->>Listener: Log ERROR "Exception"
    end
```

---

## 4. Flux détaillé : API REST

```mermaid
sequenceDiagram
    participant Client as Client HTTP
    participant Controller as PaymentMessageController
    participant Service as PaymentMessageService
    participant DB as PostgreSQL

    Client->>Controller: GET /api/v1/messages?status=FAILED&page=0&size=20
    Controller->>Service: search(status=FAILED, receivedAfter=null, pageable)
    Service->>DB: findByStatus(FAILED, PageRequest(0,20))
    DB-->>Service: Page<PaymentMessage>
    Service->>Service: toDto() sur chaque entité
    Service-->>Controller: Page<PaymentMessageDto>
    Controller-->>Client: JSON paginé

    Client->>Controller: GET /api/v1/messages/stats
    Controller->>Service: getStats()
    Service->>DB: countByStatus() (JPQL GROUP BY)
    DB-->>Service: List<Object[status, count]>
    Service->>Service: Complète avec tous les statuts (0 si absent)
    Service-->>Controller: Map<PaymentMessageStatus, Long>
    Controller-->>Client: {"RECEIVED": 15, "PROCESSED": 42, ...}

    Client->>Controller: POST /api/v1/messages/batch/retry-failed
    Controller->>Service: batchRetryFailed()
    Service->>DB: findByStatusIn([FAILED, RETRY_PENDING])
    Service->>Service: Pour chaque → setStatus(RETRY_PENDING), retryCount=0
    Service->>DB: saveAll(updated)
    Service-->>Controller: int (nombre affecté)
    Controller-->>Client: {"affected": 3, "status": "SUCCESS"}
```

---

## 5. Flux Docker Compose

```mermaid
flowchart LR
    COMPOSE[docker compose up -d] --> POSTGRES[postgres:18<br/>:5432]
    COMPOSE --> PGADMIN[dpage/pgadmin4<br/>:5050]
    COMPOSE --> IBM_MQ[icr.io/ibm-messaging/mq<br/>:1414 / :9443]

    BACKEND[Spring Boot<br/>:8080] -->|JDBC| POSTGRES
    BACKEND -->|JMS| IBM_MQ

    FRONTEND[Angular<br/>:4200] -->|HTTP| BACKEND
```

### Services

| Service | Image | Ports | Dépend |
|---|---|---|---|
| PostgreSQL | postgres:18 | 5432 | - |
| pgAdmin | dpage/pgadmin4 | 5050 | postgres |
| IBM MQ | icr.io/ibm-messaging/mq | 1414, 9443 | - |
| Backend | build local | 8080 | postgres, ibm-mq |
| Frontend | build local | 4200 | backend |
