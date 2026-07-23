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
```

Toutes les valeurs sont externalisées via variables d'environnement pour la sécurité.

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
      acknowledge-mode: auto
```

### 4.2 Listener

```java
@Component
public class PaymentMessageListener {

    @JmsListener(destination = "${ibm.mq.queue}", concurrency = "5-10")
    public void receive(String payload) {
        // 1. Log de réception
        // 2. Désérialisation JSON → PaymentMessageEvent
        // 3. Validation (Jakarta Validation)
        // 4. Si valide → service.saveMessage(event, rawPayload)
        // 5. Si invalide → log erreur (message acquitté)
    }
}
```

Points clés :

- **Concurrence** : 5 à 10 threads en parallèle
- **Acquittement** : auto (message acquitté dès la réception)
- **Désérialisation** : Jackson ObjectMapper avec JavaTimeModule
- **Validation** : Jakarta Bean Validation (`@NotBlank`, `@NotNull`, `@Positive`)

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
