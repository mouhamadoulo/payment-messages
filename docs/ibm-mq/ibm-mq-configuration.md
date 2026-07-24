# Configuration IBM MQ

## 1. Queue Manager

| Propriété | Valeur (dev) |
|---|---|
| Nom du Queue Manager | `QM1` |
| Channel | `REDACTED` |
| Connexion | `REDACTED(1414)` |
| Utilisateur | `app` |
| Mot de passe | `REDACTED` |

## 2. Queue

| Propriété | Valeur (dev) |
|---|---|
| Queue | `DEV.QUEUE.1` |
| Type | File locale |

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

Toutes les valeurs sont externalisées via variables d'environnement pour la sécurité.

`dlq-queue` est la Dead Letter Queue applicative : quand un message dépasse `max-retries` rejeux,
`mq/DeadLetterPublisher` y republie le payload brut (avec les propriétés JMS `originalMessageId`,
`reference`, `retryCount`, `errorMessage`) et le statut passe à `DEAD_LETTER`.
La publication n'est confirmée en base (`dlqPublishedAt`) que si le broker l'accepte ; sinon
`mq/DeadLetterRecoveryJob` (`dlq-recovery.*`) republie périodiquement les lignes non confirmées,
ce qui évite qu'un message soit marqué abandonné en base sans exister côté MQ.

`PAYMENT.REQUEST.QUEUE` déclare `BOTHRESH(5)` et `BOQNAME('PAYMENT.BACKOUT.QUEUE')` : au-delà de
5 redélivrances, le queue manager écarte le message vers la file de backout au lieu de le laisser
boucler indéfiniment sur les consommateurs.
Les trois files sont créées au démarrage du conteneur MQ par `infra/mq/payment-queues.mqsc`.

### 3.2 Configuration exemple (application-dev.example.yaml)

```yaml
ibm:
  mq:
    queue-manager: QM1
    channel: REDACTED
    conn-name: localhost(1414)
    user: app
    password: REDACTED
    receive-timeout: 5000
    queue: PAYMENT.REQUEST.QUEUE
```

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
- **Validation** : Jakarta Bean Validation (`@NotBlank`, `@NotNull`, `@Positive`)
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

---

## 5. Sécurité

Les informations d'identification (utilisateur, mot de passe) sont externalisées dans des variables d'environnement. 

Pour le développement local, utiliser `application-dev.example.yaml` comme référence et créer votre propre `application-dev.yaml`.

---

## 6. Docker

```yaml
ibm-mq:
  image: icr.io/ibm-messaging/mq:latest
  container_name: payment-mq
  environment:
    LICENSE: accept
    MQ_QMGR_NAME: QM1
    MQ_APP_PASSWORD: REDACTED
    MQ_ADMIN_PASSWORD: admin
  ports:
    - "1414:1414"   # Port JMS/AMQP
    - "9443:9443"   # Console de gestion HTTPS
```

- **Port 9443** : console d'administration IBM MQ (https://localhost:9443)
- **Port 1414** : connexion applicative JMS
