# Jeux de données — file d'entrée

33 payloads prêts à déposer sur `ibm.mq.queue`, couvrant les chemins d'ingestion du
consommateur : cas nominaux, les deux familles de rejet définitif, déduplication, statuts à
l'arrivée, limites du contrat et volumétrie.

Le contrat est celui de la **file** (`backend/.../dto/mq/PaymentMessageEvent.java`), pas celui
de l'API REST. Deux pièges de sérialisation, chacun couvert par un jeu du dossier `02` :

| Champ | Type Java | Format attendu |
|---|---|---|
| `payment.executionDate` | `LocalDate` | `2026-07-27` |
| `createdAt` | `LocalDateTime` | `2026-07-26T09:15:30` — **sans** décalage horaire |

Tous les horodatages de l'API REST, eux, sont des `OffsetDateTime` et en portent un.

## Champs obligatoires

`messageId`, `messageType`, `reference` (`@NotBlank`), `payment` et `status` (`@NotNull`).
`debtor`, `creditor` et `createdAt` sont facultatifs.

**Le bloc `payment` est validé en profondeur** : le champ porte `@NotNull` **et** `@Valid`,
donc les contraintes déclarées dans `Payment` sont évaluées — `transactionId` (`@NotBlank`),
`amount` (`@NotNull`, `@Positive`), `currency` (`@NotBlank`), `executionDate` (`@NotNull`).
Un `payment: {}` ou un montant négatif est **rejeté**, avec le chemin fautif dans
l'`errorMessage` (`payment.amount : amount doit être strictement positif`).

`messageId`, `messageType` et `reference` sont bornés à **255 caractères** (`@Size`) : c'est
la longueur des colonnes correspondantes. Au-delà, rejet définitif à la validation.

## Organisation

### `01-nominal/` — ce qui doit passer

| Fichier | Cas |
|---|---|
| `01-virement-sepa.json` | Virement complet, cas de référence |
| `02-virement-swift-mt103.json` | Devise étrangère, montant élevé |
| `03-prelevement-sepa.json` | Petit montant de flux de masse |
| `04-confirmation-paiement.json` | Même `reference` que le virement 01 — deux messages, une opération |
| `05-notification-statut.json` | Ni débiteur ni créancier |
| `06-champs-obligatoires-seuls.json` | Le strict minimum du contrat |

→ une ligne `RECEIVED` par message.

### `02-rejet-deserialisation/` — illisible, rejeté à la lecture

| Fichier | Cas |
|---|---|
| `01-json-tronque.json` | Objet coupé en plein milieu |
| `02-guillemets-manquants.json` | Clés et valeurs nues |
| `03-statut-inconnu.json` | `"status": "EN_COURS"` — hors énumération |
| `04-montant-non-numerique.json` | `"15 750,00 €"` dans une chaîne |
| `05-date-execution-jj-mm-aaaa.json` | `27/07/2026` au lieu de `2026-07-27` |
| `06-createdat-avec-decalage-horaire.json` | `LocalDateTime` avec `+02:00` |
| `07-tableau-au-lieu-d-objet.json` | Un lot envoyé comme un seul message |

→ ligne `FAILED`, `errorMessage` commençant par « Payload JSON illisible », `messageId` de
repli `UNKNOWN-<uuid>` — le payload brut est conservé, la ligne reste rejouable. Le message
est **acquitté** : pas de redélivrance, un rejeu produirait la même erreur.

> Ces fichiers sont du JSON invalide **à dessein** : un éditeur les soulignera en rouge,
> c'est normal.

### `03-rejet-validation/` — lisible mais incomplet

Un fichier par champ obligatoire manquant, vide ou blanc, plus `07-tous-obligatoires-absents.json`
qui les cumule.

→ ligne `FAILED`, `errorMessage` commençant par « Validation en échec : » et énumérant les
champs fautifs. `messageId` et `reference` sont conservés quand ils sont exploitables, sinon
remplacés par `UNKNOWN`.

### `04-idempotence/` — déduplication sur `messageId`

`01-original.json` puis `02-doublon-meme-message-id.json` : même `messageId`, tout le reste
diffère (référence, montant, date).

→ le second **ne crée rien** et **ne met pas à jour** la ligne existante. Le compteur
`payment.mq.messages.duplicates` s'incrémente ; `payment.mq.messages.received` non.

> À envoyer avec `uniqueIds = false`, sinon le serveur réécrit `messageId` sur chaque copie
> et le doublon n'en est plus un. C'est le défaut du script `envoyer.ps1`.

### `05-statut-a-l-arrivee/` — le payload pilote le statut initial

`PaymentMessageMapper.toEntity` reprend le `status` du message reçu ; `RECEIVED` n'est qu'un
repli, jamais atteint par la file puisque le champ y est obligatoire.

| Fichier | Effet |
|---|---|
| `01-arrive-en-processed.json` | Naît `PROCESSED`, donc terminal, sans être passé par `RECEIVED` |
| `02-arrive-en-failed.json` | Naît `FAILED`, immédiatement rejouable par `POST /{id}/retry` — le chemin le plus court pour peupler le rejeu puis la bascule DLQ |
| `03-arrive-en-dead-letter.json` | Naît `DEAD_LETTER` avec `dlqPublishedAt` à `NULL` |

> ⚠️ Le troisième déclenche `DeadLetterRecoveryJob` : la tâche voit une divergence base /
> broker et **republie réellement le payload sur la DLQ applicative** à la passe suivante
> (60 s par défaut, `MQ_DLQ_RECOVERY_INTERVAL`).

### `06-limites-et-pieges/` — les bords du contrat

| Fichier | Ce qu'il démontre | Sort |
|---|---|---|
| `01-payment-vide-rejete.json` | `payment: {}` — la cascade `@Valid` descend dans le bloc | `FAILED` |
| `02-montant-negatif-rejete.json` | Montant négatif, arrêté par `@Positive` | `FAILED` |
| `03-champs-inconnus-toleres.json` | Champs hors contrat ignorés (`FAIL_ON_UNKNOWN_PROPERTIES` désactivé) : un émetteur amont qui enrichit son message ne casse pas l'ingestion | `RECEIVED` |
| `04-unicode-et-emoji.json` | Accents, idéogrammes, emoji — `payloadSize` compte des **octets UTF-8**, pas des caractères | `RECEIVED` |
| `05-montant-haute-precision.json` | 14 chiffres et 5 décimales, lus sans perte par `BigDecimal` | `RECEIVED` |
| `06-message-id-trop-long-rejete.json` | `messageId` de 300 caractères, arrêté par `@Size(max = 255)` | `FAILED` |

Les trois rejets énumèrent le chemin fautif dans l'`errorMessage`, `payment.` compris —
`payment.amount : amount doit être strictement positif`.

> **Ces trois cas passaient avant correction.** `01` et `02` entraient en base parce que
> `payment` portait `@NotNull` sans `@Valid` : Bean Validation ne descend pas dans un objet
> imbriqué sans cascade explicite. `06` était pire — un **message empoisonné** : la validation
> passait faute de borne de taille, puis l'`INSERT` cassait sur `VARCHAR(255)`. Cette violation
> d'intégrité n'étant pas un doublon, elle était relancée, donc traitée comme une erreur
> **transitoire** : rollback de session et redélivrance en boucle jusqu'à `BOTHRESH`.
>
> Deux verrous ferment ce chemin : les bornes `@Size` au contrat, qui en font un rejet
> définitif, et la troncature à 255 des identifiants dans `PaymentMessageMapper.toFailedEntity`
> — sans elle, l'écriture du rejet casserait à son tour sur la même colonne. Cette troncature
> garde le préfixe et n'ajoute pas de suffixe aléatoire : une redélivrance retombe sur le même
> `messageId`, donc l'idempotence la reconnaît au lieu d'insérer une ligne par tentative.
>
> Le garde-fou côté gestionnaire de files reste en place et reste utile pour tout ce que le
> contrat ne prévoit pas : `BOTHRESH(5)` + `BOQNAME` sur `PAYMENT.REQUEST.QUEUE` écartent un
> message qui boucle vers `PAYMENT.BACKOUT.QUEUE`. Le vérifier sur une file montée à la main :
> ```
> DISPLAY QLOCAL('<votre file>') BOTHRESH BOQNAME
> ```
> Plus aucun jeu ne porte le marqueur `POISON` ; le filtre `-InclurePoison` d'`envoyer.ps1`
> subsiste pour un jeu futur.

### `07-volumetrie/`

- `gabarit.json` — un message à envoyer avec `count` élevé et `uniqueIds = true` : c'est le
  serveur qui réécrit `messageId` à chaque copie.
- `lot-50-messages.ndjson` — 50 messages, **un par ligne** (NDJSON) : cinq types, trois
  devises, montants échelonnés et statuts répartis (34 `RECEIVED`, 8 `PROCESSED`, 8 `FAILED`),
  de quoi remplir les répartitions du dashboard et donner de la matière au rejeu massif. Le
  fichier entier n'est pas un payload valide, seule une ligne l'est.

## Envoyer

### Script fourni (Windows / PowerShell)

```powershell
cd docs\jdd
.\envoyer.ps1 .\01-nominal\01-virement-sepa.json      # un fichier
.\envoyer.ps1 .\01-nominal                            # un dossier, récursif
.\envoyer.ps1 .\07-volumetrie\lot-50-messages.ndjson  # une ligne = un message
.\envoyer.ps1 .\07-volumetrie\gabarit.json -Count 500 -RatePerSecond 100 -UniqueIds
```

`uniqueIds` est **faux** par défaut, contrairement à l'IHM : les jeux portent des `messageId`
choisis. Le script encode le corps en UTF-8 (PowerShell 5.1 utilise ISO-8859-1 sinon, ce qui
mutilerait le jeu `04-unicode-et-emoji.json`) et attend la fin de chaque envoi — le serveur
n'accepte qu'un envoi en vol à la fois.

### À la main

```bash
curl -X POST http://localhost:8080/api/v1/simulation/sends \
     -H 'Content-Type: application/json' \
     -d "$(jq -Rs '{payload: ., count: 1, uniqueIds: false}' 01-nominal/01-virement-sepa.json)"
```

### Autres chemins

- **Postman** — collection `docs/postman/`, dossier « 03 · Simulation » : coller le contenu
  d'un fichier dans le champ `payload` et passer `uniqueIds` à `false`.
- **IHM** — onglet *Simulation d'envoi*, décocher « identifiants uniques ».
- **Directement sur la file** — `infra/load/MqInjector.java`, ou `amqsput` dans le conteneur MQ,
  sans passer par l'API.

## Observer le résultat

L'API de simulation n'écrit **rien** en base : elle publie, le consommateur décide. Le sort de
chaque message se lit ailleurs.

```bash
curl -s 'http://localhost:8080/api/v1/messages?q=JDD&size=50'      # les lignes créées
curl -s 'http://localhost:8080/api/v1/messages/stats'              # compteurs par statut
curl -s http://localhost:8080/actuator/metrics/payment.mq.messages.received
curl -s http://localhost:8080/actuator/metrics/payment.mq.messages.rejected
curl -s http://localhost:8080/actuator/metrics/payment.mq.messages.duplicates
```

Tous les `messageId` des jeux commencent par `JDD-`, donc `?q=JDD` les isole du reste de la
base — **sauf les rejets du dossier `02`**, dont le `messageId` est remplacé par
`UNKNOWN-<uuid>` puisqu'il n'a jamais pu être lu. Les retrouver par
`?status=FAILED` ou par leur `errorMessage` sur l'écran de détail. Celui de
`06-…-trop-long-rejete.json` est bien présent, mais **tronqué à 255 caractères** : c'est le
préfixe qui est conservé, la recherche le trouve.

Le jeu porte 75 `messageId` distincts ; un seul est partagé par deux fichiers, la paire de
`04-idempotence/` — par construction.

Les jeux sont **statiques** : renvoyer deux fois le même fichier est un doublon, pas deux
lignes. C'est voulu — pour de la volumétrie, passer par `07-volumetrie/`.

## `manifeste.json`

L'attendu de chaque fichier, sous forme exploitable : étape qui échoue (désérialisation ou
validation), champs en violation, statut attendu en base.

Ce manifeste n'est pas déclaratif : les 33 jeux ont été rejoués contre le `JsonMapper`
(Jackson 3, `FAIL_ON_UNKNOWN_PROPERTIES` désactivé comme dans `config/JacksonConfig`) et le
`Validator` (Hibernate Validator 9.1) chargés avec les DTO compilés du projet — rejeu refait
après l'ajout de la cascade `@Valid` et des bornes `@Size`. Les 33 se comportent comme
annoncé, les 50 lignes du lot de volumétrie comprises. Refaire la vérification après un
changement de contrat vaut mieux que de faire confiance à ce tableau.

## Voir aussi

- Contrat d'entrée : `backend/src/main/java/com/bank/paymentmessages/dto/mq/PaymentMessageEvent.java`
- Chemins de rejet : [`docs/architecture/flux.md`](../architecture/flux.md) §3
- Cycle de vie des statuts : [`docs/architecture/flux.md`](../architecture/flux.md#2-cycle-de-vie-dun-message) §2
- Collection Postman : [`docs/postman/`](../postman/README.md)
