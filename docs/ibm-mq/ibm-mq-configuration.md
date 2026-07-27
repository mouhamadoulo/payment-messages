# Configuration IBM MQ

## 1. Queue Manager

| Propriété | Où elle est définie |
|---|---|
| Nom du Queue Manager | `MQ_QMGR` — `QM1` dans la pile de développement |
| Canal | `MQ_CHANNEL` |
| Connexion | `MQ_CONN_NAME` — `localhost(1414)` hors conteneur, `ibm-mq(1414)` dans Compose |
| Utilisateur / mot de passe | `MQ_USER` / `MQ_PASSWORD` |
| Ports | `1414` (JMS applicatif), `9443` (console d'administration HTTPS) |

Les valeurs concrètes de développement ne sont pas recopiées ici : elles vivent dans
`backend/src/main/resources/application-dev.example.yaml` (exécution hors conteneur) et dans le
bloc `environment:` du service `backend` de `docker-compose.yaml` (pile complète). La liste des
variables et leurs valeurs par défaut sont dans le [README](../../README.md), section
« Configuration ».

---

## 2. Files

Les trois files sont créées à la **création du queue manager** par
`infra/mq/payment-queues.mqsc`, monté dans le conteneur MQ (cf. `docker-compose.yaml`). Une
modification du script ne s'applique donc qu'après recréation du conteneur, ou en rejouant le
script à la main.

| File | Type | Rôle | Attributs notables |
|---|---|---|---|
| `PAYMENT.REQUEST.QUEUE` | QLOCAL | File d'entrée, la **seule** consommée par l'application (`ibm.mq.queue`) | `DEFPSIST(YES)`, `BOTHRESH(5)`, `BOQNAME('PAYMENT.BACKOUT.QUEUE')` |
| `PAYMENT.DLQ.QUEUE` | QLOCAL | Dead Letter Queue **applicative** (`ibm.mq.dlq-queue`) : payload republié quand un message dépasse `max-retries` rejeux | `DEFPSIST(YES)` |
| `PAYMENT.BACKOUT.QUEUE` | QLOCAL | File de backout du **gestionnaire de files** : messages empoisonnés écartés après `BOTHRESH` redélivrances | `DEFPSIST(YES)` |

> Les deux mécanismes d'écartement ne se confondent pas. `PAYMENT.BACKOUT.QUEUE` est géré par
> le queue manager et borne les redélivrances d'une **erreur transitoire** ; `PAYMENT.DLQ.QUEUE`
> est géré par l'application et reçoit les messages abandonnés après épuisement des **rejeux
> métier** (`/retry`), avec passage en `DEAD_LETTER` en base.

Le script accorde en outre les droits d'accès sur `PAYMENT.**` à l'utilisateur applicatif `app`
du conteneur de développement.

---

## 3. Configuration dans l'application

### 3.1 Variables d'environnement

```yaml
ibm:
  mq:
    queue-manager: ${MQ_QMGR}
    channel: ${MQ_CHANNEL}
    conn-name: ${MQ_CONN_NAME}
    user: ${MQ_USER}
    password: ${MQ_PASSWORD}
    receive-timeout: 5000
    queue: ${MQ_QUEUE}
    dlq-queue: ${MQ_DLQ_QUEUE}
    max-retries: ${MQ_MAX_RETRIES}
    dlq-recovery:
      enabled: ${MQ_DLQ_RECOVERY_ENABLED:true}
      interval: ${MQ_DLQ_RECOVERY_INTERVAL:60000}
      batch-size: ${MQ_DLQ_RECOVERY_BATCH_SIZE:100}
```

Aucune valeur n'est écrite en dur : `application.yaml` n'est qu'un jeu de substitutions.

`dlq-queue` est la Dead Letter Queue applicative : quand un message dépasse `max-retries` rejeux,
`mq/DeadLetterPublisher` y republie le payload brut (avec les propriétés JMS `originalMessageId`,
`reference`, `retryCount`, `errorMessage`) et le statut passe à `DEAD_LETTER`.
La publication n'est confirmée en base (`dlqPublishedAt`) que si le broker l'accepte ; sinon
`mq/DeadLetterRecoveryJob` (`dlq-recovery.*`) republie périodiquement les lignes non confirmées,
ce qui évite qu'un message soit marqué abandonné en base sans exister côté MQ.

`PAYMENT.REQUEST.QUEUE` déclare `BOTHRESH(5)` et `BOQNAME('PAYMENT.BACKOUT.QUEUE')` : au-delà de
5 redélivrances, le queue manager écarte le message vers la file de backout au lieu de le laisser
boucler indéfiniment sur les consommateurs (cf. §2).

### 3.2 Valeurs de développement

Le gabarit `backend/src/main/resources/application-dev.example.yaml` porte un bloc `ibm.mq`
complet, à recopier tel quel :

```bash
cp backend/src/main/resources/application-dev.example.yaml \
   backend/src/main/resources/application-dev.yaml
```

`dlq-queue` et `max-retries` n'ont **pas** de valeur par défaut dans `application.yaml` : sans
elles, le contexte ne démarre pas. Les clés `dlq-recovery.*` sont facultatives (valeurs par
défaut ci-dessus).

> Le fichier `application-dev.yaml` est git-ignoré : il échappe donc aux relectures et conserve
> silencieusement d'anciennes valeurs. Après un changement de clé MQ, le mettre à jour en même
> temps que `application.yaml` et le gabarit — en particulier `ibm.mq.queue`, qui doit désigner
> une file réellement créée par `infra/mq/payment-queues.mqsc`.

---

## 4. Consumer JMS

### 4.1 Configuration

```yaml
spring:
  jms:
    listener:
      session:
        transacted: true          # explicite : rollback = redélivrance
      min-concurrency: ${MQ_MIN_CONCURRENCY:5}
      max-concurrency: ${MQ_MAX_CONCURRENCY:10}
      receive-timeout: 5s
```

> `spring.jms.listener.acknowledge-mode` (utilisée jusqu'ici) n'est plus liée en
> Spring Boot 4 : elle était ignorée silencieusement. Le mode réel est la session
> transactée, désormais déclarée explicitement.

### 4.2 Listener

```java
@Component
public class PaymentMessageListener {

    @JmsListener(destination = "${ibm.mq.queue}")
    public void receive(String payload) {
        // 1. Désérialisation JSON → PaymentMessageEvent
        //    → illisible : persistance en FAILED avec le payload brut, puis acquittement
        // 2. Validation (Jakarta Validation)
        //    → en échec : persistance en FAILED avec le motif, puis acquittement
        // 3. service.saveMessage(event, rawPayload) — idempotent sur messageId
        //    → erreur transitoire : exception relancée, rollback, redélivrance
    }
}
```

Points clés :

- **Concurrence** : pilotée par `spring.jms.listener.min/max-concurrency` (5 à 10 par défaut)
- **Acquittement** : session transactée — un rollback provoque une redélivrance
- **Erreurs définitives** (JSON illisible, validation) : persistées en `FAILED` avec le
  payload brut et rejouables depuis l'API — aucun message n'est perdu
- **Erreurs transitoires** (base indisponible) : redélivrance, bornée par `BOTHRESH`/`BOQNAME`
- **Désérialisation** : `JsonMapper` (Jackson 3) auto-configuré par Spring Boot, avec
  `FAIL_ON_UNKNOWN_PROPERTIES` désactivé (tolérance aux champs ajoutés en amont)
- **Validation** : Jakarta Bean Validation (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`).
  `payment` porte `@Valid` : sans cette cascade, les contraintes du bloc imbriqué ne seraient
  pas évaluées et un `payment: {}` ou un montant négatif entrerait en base. `messageId`,
  `messageType` et `reference` sont bornés à 255 caractères — la longueur des colonnes : une
  valeur plus longue passerait la validation puis casserait à l'`INSERT`, et cette violation
  d'intégrité, indiscernable d'une panne, ferait boucler la redélivrance
- **Métriques** : `payment.mq.messages.rejected`, `payment.mq.messages.duplicates`,
  `payment.mq.listener.rollbacks`, `payment.dlq.publish.failures`

### 4.3 Format attendu du message MQ

```json
{
  "messageId": "MQ-20250115-001",
  "messageType": "PAYMENT_REQUEST",
  "reference": "PAY-2025-001",
  "payment": {
    "transactionId": "TXN-001",
    "amount": 1500.00,
    "currency": "EUR",
    "executionDate": "2025-01-15"
  },
  "debtor": {
    "accountNumber": "FR7630001007941234567890185",
    "name": "Client A",
    "bankCode": "BDFEFRPP"
  },
  "creditor": {
    "accountNumber": "FR7630001007941234567890186",
    "name": "Client B",
    "bankCode": "BNPAFRPP"
  },
  "status": "RECEIVED",
  "createdAt": "2025-01-15T10:00:00"
}
```

Contrat (`dto/mq/PaymentMessageEvent`) :

| Champ | Obligatoire | Contrainte |
|---|:---:|---|
| `messageId` | ✔ | non blanc, ≤ 255 caractères, **clé d'idempotence** |
| `messageType` | ✔ | non blanc, ≤ 255 caractères |
| `reference` | ✔ | non blanc, ≤ 255 caractères |
| `payment` | ✔ | objet, validé **en profondeur** (`@Valid`) |
| `payment.transactionId` | ✔ | non blanc |
| `payment.amount` | ✔ | strictement positif |
| `payment.currency` | ✔ | non blanc |
| `payment.executionDate` | ✔ | `LocalDate` — `AAAA-MM-JJ`, sans heure |
| `status` | ✔ | valeur de `PaymentMessageStatus` ; reprise telle quelle comme statut initial |
| `debtor` / `creditor` | ✘ | aucune contrainte |
| `createdAt` | ✘ | `LocalDateTime` — **sans décalage horaire**, contrairement aux horodatages de l'API |

Les bornes à 255 caractères sont exactement la longueur des colonnes correspondantes : elles
transforment un dépassement en rejet définitif au lieu d'un échec d'`INSERT` pris pour une
panne. Jeux de données couvrant chaque cas : [`docs/jdd/`](../jdd/README.md).

---

## 5. Sécurité

- **Identifiants externalisés** : aucun n'est écrit dans le code ni dans `application.yaml`,
  qui ne contient que des substitutions de variables d'environnement.
- **Développement local** : recopier `application-dev.example.yaml` en `application-dev.yaml`
  (git-ignoré) plutôt que de modifier les fichiers versionnés.
- **Portée** : les valeurs livrées sont celles de l'image de développement IBM et de la pile
  Compose locale. Elles n'ont pas vocation à sortir de ce cadre.
- **Authentification applicative hors périmètre** : l'API et l'actuator répondent sans
  identification, ce qui suppose un déploiement sur réseau de confiance. Le contrôle d'accès MQ
  reste, lui, celui du gestionnaire de files (droits `PAYMENT.**` accordés à `app`).

---

## 6. Docker

Le service `ibm-mq` de `docker-compose.yaml` démarre le gestionnaire de files et monte le script
MQSC. Points à connaître :

| Élément | Valeur | Remarque |
|---|---|---|
| Image | `icr.io/ibm-messaging/mq:latest` | **non figée** — le client Java, lui, est épinglé par `mq-jms-spring-boot-starter` 4.1.0 (`com.ibm.mq.jakarta.client` 10.0.0.0). Épingler l'image sur un environnement partagé |
| `MQ_QMGR_NAME` | `QM1` | doit correspondre à `MQ_QMGR` côté backend |
| `MQ_APP_PASSWORD` / `MQ_ADMIN_PASSWORD` | cf. `docker-compose.yaml` | mots de passe de développement de l'image IBM |
| Port `1414` | connexion applicative JMS | consommé par le backend (`MQ_CONN_NAME`) |
| Port `9443` | console d'administration HTTPS | `https://localhost:9443`, certificat auto-signé |
| Volume MQSC | `./infra/mq/payment-queues.mqsc` | joué **à la création** du gestionnaire seulement (cf. §2) |
| `healthcheck` | `chkmqhealthy` | `start_period: 60s` — le démarrage du gestionnaire est lent, et le backend attend cette sonde |

---

## 7. Voir aussi

- Chemins de rejet et redélivrance : [`docs/architecture/flux.md`](../architecture/flux.md#3-ingestion-mq) §3
- Listener et métriques : [`docs/architecture/architecture-backend.md`](../architecture/architecture-backend.md) §5.4
- Jeux de données de la file : [`docs/jdd/`](../jdd/README.md)
- Variables d'environnement : [README](../../README.md), section « Configuration »
