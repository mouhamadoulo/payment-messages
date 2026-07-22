# Payment Messages

Application web de gestion et de consultation des messages de paiement transitant via IBM MQ.

## Contexte

Le département de paiement d'une banque reçoit des messages provenant de différentes applications Back Office via une file IBM MQ Series.

Ces messages doivent être :

- consommés depuis IBM MQ ;
- stockés dans une base de données relationnelle ;
- exposés via des API REST ;
- consultables depuis une interface web Angular.

L'application doit répondre à des contraintes fortes :

- volumétrie importante de messages ;
- performance ;
- résilience ;
- traçabilité des traitements.

---

# Stack technique

## Backend

- Java 21
- Spring Boot 4.1.0
- Spring Data JPA
- Spring JMS
- IBM MQ Client 9.4.2.0
- PostgreSQL
- Maven


## Frontend

- Angular 22
- TypeScript


## Infrastructure

- Docker
- Docker Compose

---

# Architecture globale

```text

```

---

# Structure du projet

```text
payment-messages
│
├── backend
│
├── frontend
│
├── docs
│
├── docker
│
├── docker-compose.yml
│
└── README.md
```

---

# Fonctionnalités

## Backend

- Consommation IBM MQ
- Stockage des messages
- Recherche des messages
- API REST
- Pagination
- Monitoring


## Frontend

- Dashboard messages
- Recherche
- Consultation détail