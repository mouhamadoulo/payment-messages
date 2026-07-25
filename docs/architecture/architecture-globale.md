# Architecture Overview

## 1. Introduction

L'application **Payment Messages** est une application web permettant de récupérer, stocker et consulter des messages de paiement transitant via IBM MQ.

Elle intervient dans la chaîne de traitement des paiements entre les applications Back Office et les systèmes de routage bancaire.

Les objectifs principaux sont :

- récupérer les messages depuis IBM MQ ;
- assurer la persistance des messages ;
- permettre leur consultation via une interface web ;
- garantir performance, résilience et traçabilité.

---

## 2. Architecture globale

```mermaid
flowchart LR
    BO[Applications Back Office]
    MQ[(IBM MQ Queue Manager)]
    API[Spring Boot Backend]
    DB[(PostgreSQL)]
    FRONT[Angular Frontend]

    BO -->|Dépose messages JSON| MQ
    MQ -->|Consommation JMS 5-10 threads| API
    API -->|Persistance| DB
    FRONT -->|API REST /api/v1/messages| API
    API -->|JSON| FRONT
```

### Flux principal

1. Les applications **Back Office** déposent des messages JSON dans une file **IBM MQ**
2. Le **Spring Boot Backend** consomme ces messages via un listener JMS (5-10 threads concurrents)
3. Chaque message est désérialisé, validé, puis persisté dans **PostgreSQL**
4. Les messages sont exposés via une **API REST** paginée
5. Le **Frontend Angular** consomme l'API pour afficher et gérer les messages

---

## 3. Cycle de vie des messages

```mermaid
stateDiagram-v2
    direction LR
    [*] --> RECEIVED: Message reçu de MQ
    RECEIVED --> PROCESSED: Succès
    RECEIVED --> FAILED: Erreur
    FAILED --> RECEIVED: Nouvelle tentative (/retry)
    FAILED --> PROCESSED: Résolution manuelle
    FAILED --> DEAD_LETTER: Abandon après max-retries
```

Ces transitions sont les **seules** acceptées : `PUT /{id}/status` confronte toute demande à
la machine à états (`PaymentMessageStatus`) et refuse le reste en `422`. `PROCESSED` et
`DEAD_LETTER` sont terminaux.

Le cycle de vie complet (4 statuts) et les transitions détaillées sont documentés dans [flux.md](./flux.md).

---

## 4. API REST

L'API est **fermée** : hormis l'authentification, tout appel exige un jeton
(`Authorization: Bearer <token>`) obtenu sur `POST /api/v1/auth/login`.

| Méthode | Path | Action | Rôle |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Émission d'un jeton JWT | public |
| `GET` | `/api/v1/messages` | Liste paginée avec filtres (statut, date, type, recherche) | authentifié |
| `GET` | `/api/v1/messages/cursor` | Pagination par curseur (keyset) | authentifié |
| `GET` | `/api/v1/messages/stats` | Compteurs par statut sous les filtres actifs | authentifié |
| `GET` | `/api/v1/messages/stats/dashboard` | Agrégats du tableau de bord, calculés en SQL | authentifié |
| `GET` | `/api/v1/messages/types` | Types de messages présents en base | authentifié |
| `GET` | `/api/v1/messages/{id}` | Détail d'un message | authentifié |
| `DELETE` | `/api/v1/messages/{id}` | Suppression | `ADMIN` |
| `POST` | `/api/v1/messages/batch/retry-failed` | Relance des messages en échec | `ADMIN` |
| `POST` | `/api/v1/messages/{id}/retry` | Relance individuelle | authentifié |
| `PUT` | `/api/v1/messages/{id}/status` | Mise à jour du statut | `ADMIN` |

Swagger UI : `http://localhost:8080/swagger-ui.html`

Documentation complète : [docs/api/api-documentation.md](../api/api-documentation.md)

---

## 5. Stack technique

### Backend

| Technologie | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring Data JPA | - |
| Spring JMS | - |
| IBM MQ Client | 9.4.2.0 |
| PostgreSQL | 18 |
| H2 (tests) | - |
| Lombok | - |
| Jackson | - |
| SpringDoc OpenAPI | 2.8.9 |
| Spring Boot Actuator | - |
| Spring Security (JWT HMAC) | - |
| Micrometer + Prometheus | - |

### Frontend

| Technologie | Version |
|---|---|
| Angular | 22 |
| TypeScript | 6 |
| RxJS | 7.8 |
| Vitest | 4 |

### Infrastructure

| Technologie | Version |
|---|---|
| Docker | - |
| Docker Compose | - |

---

## 6. Déploiement

```mermaid
flowchart LR
    COMPOSE[Docker Compose] --> PG[PostgreSQL:5432]
    COMPOSE --> MQ[IBM MQ:1414]
    COMPOSE --> PGADMIN[pgAdmin:5050]
    BACKEND[Backend:8080] --> PG
    BACKEND --> MQ
```

Documentation détaillée :

- [Architecture backend](./architecture-backend.md)
- [Architecture frontend](./architecture-frontend.md)
- [Flux de données](./flux.md)
- [Modèle de données](../database/database-model.md)
- [Configuration IBM MQ](../ibm-mq/ibm-mq-configuration.md)
- [API REST](../api/api-documentation.md)
