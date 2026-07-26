# Collection Postman

Collection de test de l'API `payment-messages` : **38 requêtes** couvrant les trois groupes de
routes (`/config`, `/messages`, `/simulation`), l'actuator et les cas d'erreur du contrat.

| Fichier | Rôle |
|---|---|
| `payment-messages.postman_collection.json` | La collection (format v2.1) |
| `payment-messages.local.postman_environment.json` | `baseUrl = http://localhost:8080` — backend lancé par `./mvnw spring-boot:run` ou conteneur `backend` |
| `payment-messages.docker.postman_environment.json` | `baseUrl = http://localhost:4200` — pile `docker compose`, à travers le proxy nginx |

## Import

Postman → **Import** → déposer les trois fichiers → sélectionner l'environnement en haut à
droite.

L'environnement ne porte que `baseUrl` : tout le reste est en variables **de collection**,
alimentées par les tests au fil des appels. Rien à saisir à la main.

> L'environnement *docker* relaie `/api` mais **pas** `/actuator` : nginx ne proxifie que
> `/api/`. Pour le dossier « 04 · Actuator », repasser sur l'environnement *local*.

## Ordre de lancement

1. **01 · Messages · lecture → GET Liste paginée** — enregistre `messageId`, utilisé par la
   moitié de la collection. Sans elle, les requêtes sur `/{{messageId}}` partent avec une
   variable vide.
2. Base vide ? **03 · Simulation → POST Envoi unitaire** d'abord : l'API n'a aucun endpoint de
   création, les messages n'entrent que par la file MQ.

Le reste s'enchaîne dans l'ordre d'affichage. Les variables `cursor`, `statsEtag`,
`batchTaskId` et `simTaskId` sont posées par les tests des requêtes qui les produisent.

## Requêtes qui modifient l'état

| Requête | Effet |
|---|---|
| `03 · Simulation → POST …` | **Publie réellement sur la file IBM MQ.** Les messages reviennent par le consommateur et créent des lignes en base — c'est le but, mais ce n'est pas anodin sur un environnement partagé |
| `02 · … → POST Rejeu unitaire` / `POST Rejeu massif` | Incrémente `retryCount`, repasse en `RECEIVED`, bascule en `DEAD_LETTER` au-delà de `ibm.mq.max-retries` (avec republication DLQ) |
| `02 · … → PUT Changer le statut` | Écrit le statut et consomme une version (`@Version`) |
| `02 · … → DELETE Supprimer un message` | **Irréversible.** Placée en dernier du dossier, elle détruit `{{messageId}}` |

Le reste de la collection est en lecture seule.

## Ce que les tests vérifient

Au-delà du code HTTP, chaque requête assied une propriété du contrat :

- **assertions communes** (script de collection) — `X-Request-Id` renvoyé sur toute réponse
  `/api/**`, et format `ProblemDetail` sur toute réponse ≥ 400 ;
- les listes ne portent **pas** le payload, seulement `payloadSize` ;
- `GET /stats` pose un `ETag` + `Cache-Control`, et la revalidation `If-None-Match` répond
  **304 sans corps** ;
- le dashboard rend **24** tranches horaires, toujours présentes même à zéro ;
- `GET /config` n'expose ni utilisateur ni mot de passe ;
- `/actuator/health` ne détaille pas ses composants (`show-details: never`) ;
- le `correlationId` d'une erreur est bien le `X-Request-Id` envoyé ;
- le curseur **refuse** `size > 200` (400) là où `GET /messages` rabote silencieusement à la
  borne — deux comportements différents, tous deux volontaires.

Certaines assertions acceptent plusieurs codes, quand le résultat dépend de l'état de la base :
un rejeu répond `200` sur un `FAILED` et `400` sur tout autre statut, un envoi de simulation
répond `503` si `app.simulation.enabled` est `false`. Le test le dit alors dans la console
Postman plutôt que de se déclarer en échec.

## Payloads de simulation

Les corps du dossier « 03 · Simulation » suivent le contrat de la **file**
(`dto/mq/PaymentMessageEvent`), pas celui de l'API REST — deux pièges de sérialisation :

- `payment.executionDate` est une `LocalDate` (`AAAA-MM-JJ`) ;
- `createdAt` est une `LocalDateTime`, **sans décalage horaire**, à l'inverse de tous les
  horodatages de l'API qui sont des `OffsetDateTime`.

Un script de dossier régénère `simMessageId`, `simReference`, `simTransactionId`, `simDate` et
`simDateTime` avant **chaque** envoi : deux exécutions de suite ne produisent pas de doublon.

Deux requêtes envoient un payload invalide **à dessein** — champs manquants (rejet à la
validation) et JSON illisible (rejet à la lecture). Les deux répondent `202` : l'API ne juge
pas le payload, elle le publie. Le rejet a lieu côté consommateur, qui persiste une ligne
`FAILED` porteuse du payload brut. C'est le comportement à observer, pas un bug.

## Exécution en ligne de commande

```bash
npm install -g newman
newman run docs/postman/payment-messages.postman_collection.json \
       -e docs/postman/payment-messages.local.postman_environment.json
```

⚠️ Un `newman run` exécute **tout**, y compris les envois MQ et le `DELETE` final. Pour s'en
tenir aux lectures :

```bash
newman run docs/postman/payment-messages.postman_collection.json \
       -e docs/postman/payment-messages.local.postman_environment.json \
       --folder "01 · Messages · lecture"
```

## Voir aussi

- Contrat détaillé : [`docs/api/api-documentation.md`](../api/api-documentation.md)
- Swagger UI : `http://localhost:8080/swagger-ui.html`
