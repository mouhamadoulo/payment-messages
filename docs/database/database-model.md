# Database Model


## Table payment_messages


| Colonne | Type | Description |
|-|-|-|
| id | bigint | Identifiant technique |
| message_id | varchar | Identifiant MQ |
| reference | varchar | Référence paiement |
| status | varchar | Etat traitement |
| payload | text | Message original |
| received_at | timestamp | Date réception |
| processed_at | timestamp | Date traitement |


## Index

- idx_message_reference
