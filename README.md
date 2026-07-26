# Payment Messages

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)
![Angular](https://img.shields.io/badge/Angular-22-red?logo=angular)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue?logo=postgresql)
![IBM MQ](https://img.shields.io/badge/IBM%20MQ-9.4.2-blue?logo=ibm)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven)

Collecte, stockage et consultation des messages de paiement transitant par **IBM MQ** : un listener
JMS persiste en **PostgreSQL**, une **API REST** paginée expose, une **IHM Angular** consulte et pilote.

<p align="center">
  <img src="docs/images/flux-architecture.svg" alt="Les applications Back Office déposent un message JSON sur PAYMENT.REQUEST.QUEUE ; le listener Spring Boot le consomme et le persiste en PostgreSQL ; l'IHM Angular le consulte via l'API REST" width="100%">
</p>

---

## Démarrage

```bash
cp backend/src/main/resources/application-dev.example.yaml \
   backend/src/main/resources/application-dev.yaml     # requis : sans ce fichier, pas de contexte
docker compose up -d                                   # pile complète, images applicatives buildées
```

| Accès | URL |
|---|---|
| IHM | `http://localhost:4200` |
| API | `http://localhost:8080/api/v1/messages` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| pgAdmin · console MQ | `http://localhost:5050` · `https://localhost:9443` |

<details>
<summary><b>Développer en local</b> (backend et frontend hors conteneur)</summary>

```bash
docker compose up -d postgres ibm-mq   # infrastructure seule

cd backend  && ./mvnw spring-boot:run  # profil dev par défaut → :8080
cd frontend && npm install && ng serve  # relais /api vers :8080 → :4200
```

Prérequis : **Java 21**, **Node.js 22**, **Docker Compose**. Sur Windows, `mvnw.cmd`.
</details>

---

## Ce que fait l'application

| Brique | Comportement |
|---|---|
| **Ingestion** | `@JmsListener`, 5-10 consommateurs, session transactée, idempotence sur `messageId` |
| **Erreurs** | définitives → ligne `FAILED` + payload brut ; transitoires → rollback et redélivrance bornée |
| **Persistance** | PostgreSQL, schéma piloté par Flyway (`ddl-auto: validate`) |
| **API** | pagination page / curseur, 4 filtres serveur, agrégats SQL cachés, `ETag`, RFC 9457 |
| **Reprise** | rejeu unitaire ou par lots bornés (202 + `taskId`), DLQ applicative publiée après commit |
| **IHM** | tableau de bord, liste filtrable et triable, détail, changement de statut, simulation d'envoi |
| **Exploitation** | Actuator + Prometheus, `X-Request-Id` corrélé aux logs, rétention planifiée (option) |

<p align="center">
  <img src="docs/images/cycle-de-vie-message.svg" alt="RECEIVED est l'état initial posé par le listener ; PUT /status mène à PROCESSED ou FAILED ; POST /retry rejoue un FAILED tant que retryCount reste sous max-retries, au-delà le message part en DEAD_LETTER" width="100%">
</p>

> **Hors périmètre : authentification et autorisations.** Aucun compte, aucun jeton, aucun rôle —
> tous les endpoints répondent en clair. Le déploiement doit rester sur un réseau de confiance.

---

## Documentation

| Sujet | Fichier |
|---|---|
| Architecture globale, backend, frontend, flux | [docs/architecture/](docs/architecture/architecture-globale.md) |
| API REST (contrat complet, exemples, erreurs) | [docs/api/](docs/api/api-documentation.md) |
| Modèle de données et requêtes notables | [docs/database/](docs/database/database-model.md) |
| Configuration IBM MQ (files, canal, MQSC) | [docs/ibm-mq/](docs/ibm-mq/ibm-mq-configuration.md) |
| Guide utilisateur, écran par écran | [docs/user-guide/](docs/user-guide/README.md) |
| Collection Postman prête à importer | [docs/postman/](docs/postman/README.md) |
| Jeux de données de la file d'entrée | [docs/jdd/](docs/jdd/README.md) |

Les diagrammes animés sont dans [docs/images/](docs/images/) (SVG autonomes, sans script) ; les
séquences restantes sont en Mermaid, avec leur rendu PNG à côté pour les visualiseurs qui ne
l'exécutent pas.

---

<details>
<summary><b>Configuration</b> — variables requises et options</summary>

`application.yaml` ne contient que des placeholders : tout vient de l'environnement, ou de
`application-dev.yaml` (git-ignoré) en développement.

| Requise | Exemple |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/payment_messages` |
| `DB_USER` / `DB_PASSWORD` | `payment` / `payment` |
| `MQ_QMGR` / `MQ_CHANNEL` | `QM1` / `DEV.APP.SVRCONN` |
| `MQ_CONN_NAME` | `localhost(1414)` |
| `MQ_USER` / `MQ_PASSWORD` | `app` / `passw0rd` |
| `MQ_QUEUE` / `MQ_DLQ_QUEUE` | `PAYMENT.REQUEST.QUEUE` / `PAYMENT.DLQ.QUEUE` |
| `MQ_MAX_RETRIES` | `3` |

| Option (défaut) | Effet |
|---|---|
| `MQ_MIN_CONCURRENCY` / `MQ_MAX_CONCURRENCY` (`5` / `10`) | nombre de consommateurs JMS |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` (`20` / `5`) | HikariCP, à tenir ≥ consommateurs + threads HTTP |
| `MQ_DLQ_RECOVERY_ENABLED` / `_INTERVAL` / `_BATCH_SIZE` (`true` / `60000` / `100`) | reprise des `DEAD_LETTER` non republiés |
| `STATS_CACHE_TTL` (`15s`) | TTL des caches de stats **et** du `Cache-Control` associé |
| `BATCH_RETRY_SIZE` / `BATCH_RETRY_MAX` (`500` / `100000`) | taille et plafond du rejeu massif |
| `RETENTION_ENABLED` (`false`) + `RETENTION_*` | purge planifiée des messages traités |
| `SIMULATION_ENABLED` (`true`) / `SIMULATION_MAX_COUNT` / `SIMULATION_MAX_RATE` (`1000` / `200`) | simulation d'envoi et ses bornes |
| `CORS_ALLOWED_ORIGINS` (`http://localhost:4200`) | seule politique navigateur restante |
| `MAX_PAGE_SIZE` (`200`) · `REQUEST_TIMEOUT` (`15s`) · `CONNECTION_TIMEOUT` (`5s`) | garde-fous HTTP |
| `JPA_DDL_AUTO` (`validate`) · `FLYWAY_ENABLED` (`true`) | gestion du schéma |
| `MANAGEMENT_PORT` (vide) | isole l'actuator sur un port dédié |

Le schéma appartient à **Flyway** (`backend/src/main/resources/db/migration`) : toute évolution
d'entité sans migration correspondante fait échouer le démarrage.
</details>

<details>
<summary><b>API REST</b> — 15 endpoints, tous ouverts</summary>

```bash
curl -s http://localhost:8080/api/v1/messages
```

| Méthode | Path | Description |
|---|---|---|
| `GET` | `/api/v1/config` | configuration MQ non sensible (files, gestionnaire, canal) |
| `GET` | `/api/v1/messages` | liste paginée sans payload — filtres `status`, `receivedAfter`, `type`, `q` |
| `GET` | `/api/v1/messages/cursor` | pagination par curseur (keyset), mêmes filtres |
| `GET` | `/api/v1/messages/stats` | compteurs par statut sous les filtres actifs (`ETag`) |
| `GET` | `/api/v1/messages/stats/dashboard` | agrégats SQL : 24 tranches horaires, types, tentatives, alertes |
| `GET` | `/api/v1/messages/types` | types présents en base |
| `GET` | `/api/v1/messages/{id}` | détail, payload inclus |
| `DELETE` | `/api/v1/messages/{id}` | suppression (204) |
| `POST` | `/api/v1/messages/{id}/retry` | rejeu unitaire |
| `POST` | `/api/v1/messages/batch/retry-failed` | rejeu massif (202 + `taskId`) |
| `GET` | `/api/v1/messages/batch/retry-failed/{taskId}` | suivi du rejeu massif |
| `PUT` | `/api/v1/messages/{id}/status` | corps `{ "status": "…", "reason": "…" }`, 422 si interdit |
| `GET` | `/api/v1/simulation/config` | file visée et plafonds |
| `POST` | `/api/v1/simulation/sends` | dépôt de messages de test (202 + `taskId`) |
| `GET` | `/api/v1/simulation/sends/{taskId}` | suivi de l'envoi |

La simulation **publie sur la file d'entrée et n'écrit rien en base** : les messages reviennent par
le consommateur applicatif, avec les mêmes rejets. La destination n'est pas un paramètre — c'est
toujours `ibm.mq.queue`.

Contrat complet : [docs/api/api-documentation.md](docs/api/api-documentation.md).
</details>

<details>
<summary><b>Tests et CI</b></summary>

```bash
cd backend
./mvnw test                    # unitaires seuls, sur H2, sans Docker
./mvnw verify                  # + tests d'intégration (Testcontainers)
./mvnw verify -Dmq.it=true     # + le tir de bout en bout sur un vrai IBM MQ

cd frontend
npm run test -- --no-watch     # Vitest, exécution unique
```

**Surefire** exécute les `*Test` sur H2, **Failsafe** les `*IT` sur des services réels démarrés par
Testcontainers. Sans démon Docker, les `*IT` sont *skipped* et le build reste vert — la CI est donc
le seul endroit où les migrations Flyway rencontrent un vrai PostgreSQL.

| Classe | Type | Scope |
|---|---|---|
| `PaymentMessagesApplicationTests` | intégration | chargement du contexte |
| `PaymentMessageRepositoryTest` | tranche JPA | projection de liste, keyset, purge |
| `PaymentMessageServiceTest` | unitaire | logique métier, idempotence, curseur, lots bornés |
| `PaymentMessageListenerTest` | unitaire | erreurs définitives / transitoires, chronomètre, MDC |
| `BatchRetryServiceTest` · `SimulationServiceTest` | unitaire | enchaînement des lots, cadence, bornes, single-flight |
| `DeadLetterDispatcherTest` · `JmsConfigTest` | unitaire | publication après commit, session transactée |
| `PaymentMessageControllerTest` | tranche web | endpoints, contrat de statut, garde-fous |
| `HealthProbesTest` · `MetricsConfigTest` · `HttpCacheAndCorrelationTest` | intégration | sondes, `/prometheus` (sans `env`), `ETag` / `X-Request-Id` |
| `PaymentMessageStatusTest` · `PaymentMessageMapperTest` | unitaire | machine à états, mapping et `payloadSize` |
| `SchemaMigrationIT` · `PaymentMessagePersistenceIT` | Testcontainers PostgreSQL | migrations, `timestamptz`, index, concurrence, DLQ, rétention |
| `PaymentMessageMqIT` | Testcontainers IBM MQ | redélivrance, idempotence, bascule DLQ (`-Dmq.it=true`) |

CI GitHub Actions à chaque push et PR : backend `mvnw verify` · frontend `npm ci` + tests + build ·
`docker compose config` + `up -d --wait`. Tirs de charge dans `infra/load/` (injecteur JMS, scénario k6).
</details>

<details>
<summary><b>Observabilité</b></summary>

| Endpoint | Contenu |
|---|---|
| `/actuator/health` | santé, sondes `liveness` / `readiness` |
| `/actuator/info` · `/actuator/metrics` | informations, métriques JVM et applicatives |
| `/actuator/prometheus` | exposition Prometheus, métriques métier comprises |

`/actuator/env` a été **retiré** : il exposait toute la configuration résolue, identifiants MQ compris.

Métriques métier : `payment.mq.messages.received` / `.rejected` / `.duplicates`,
`payment.mq.listener.rollbacks`, `payment.dlq.publish.failures`, le chronomètre
`payment.mq.processing` (étiqueté par issue) et les jauges `payment.messages.pending` / `.failed` /
`.dead.letter`. Chaque réponse porte un `X-Request-Id`, repris dans les logs (`requestId`) aux côtés
du `messageId` pour l'ingestion.
</details>

<details>
<summary><b>Structure du projet</b></summary>

```
payment-messages
├── backend/                  # Spring Boot 4.1, Java 21
│   └── src/main/java/com/bank/paymentmessages/
│       ├── config/           # JMS, cache, CORS, ETag, métriques, OpenAPI, exécuteurs
│       ├── controller/       # REST : messages, configuration MQ, simulation
│       ├── dto/              # api/ (contrat REST) et mq/ (contrat de la file)
│       ├── entity/           # entité JPA + machine à états des statuts
│       ├── exception/        # ProblemDetail (RFC 9457) et exceptions métier
│       ├── mapper/ repository/ service/   # mapping, accès données, logique métier
│       ├── mq/               # listener, publication DLQ, reprise, publication de test
│       └── web/              # CorrelationIdFilter (X-Request-Id + MDC)
├── frontend/                 # Angular 22 standalone, zoneless (core, features, layout, shared)
├── infra/                    # mq/ (MQSC), load/ (injecteur JMS, k6)
├── docs/                     # architecture, api, database, ibm-mq, user-guide, postman, jdd, images
├── docker-compose.yaml · .github/workflows/ci.yml
```

Le schéma vit dans `backend/src/main/resources/db/migration` ; `application-dev.yaml` est git-ignoré
et créé depuis `application-dev.example.yaml`.
</details>

<details>
<summary><b>Stack</b></summary>

| Backend | Frontend | Infra |
|---|---|---|
| Java 21 · Spring Boot 4.1.0 | Angular 22 (standalone, zoneless) | Docker · Docker Compose |
| Spring Data JPA · Spring JMS | Angular Material + CDK 22 | PostgreSQL 18 |
| IBM MQ Client 9.4.2.0 | TypeScript 6 · RxJS 7.8 | IBM MQ 9.4.2 |
| Flyway · Actuator · Micrometer | Vitest 4 | nginx 1.27-alpine |
| SpringDoc OpenAPI 2.8.9 · Lombok | | H2 (tests) |
</details>

---

**Mouhamadou LO** — [mouhamadoulo39@gmail.com](mailto:mouhamadoulo39@gmail.com)
