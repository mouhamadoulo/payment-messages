# Payment Messages

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)
![Angular](https://img.shields.io/badge/Angular-22-red?logo=angular)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-blue?logo=postgresql)
![IBM MQ](https://img.shields.io/badge/IBM%20MQ-9.4.2-blue?logo=ibm)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven)

---

## Description

**Payment Messages** est une application web permettant de collecter, stocker et consulter des messages de paiement transitant via une infrastructure **IBM MQ Series**.

L'application simule un contexte bancaire où plusieurs applications Back Office déposent des messages financiers dans une file MQ. Ces messages sont ensuite :

- consommés automatiquement depuis IBM MQ ;
- persistés dans une base relationnelle PostgreSQL ;
- exposés via des API REST ;
- consultables depuis une interface Angular.

L'objectif est de proposer une solution robuste répondant aux contraintes d'un environnement bancaire :

- forte volumétrie ;
- performance ;
- résilience ;
- traçabilité ;
- supervision des traitements.

---

# Architecture

```mermaid
flowchart LR

A[Applications Back Office]
    --> B[IBM MQ Queue]

B --> C[Spring Boot JMS Consumer]

C --> D[Message Processing Service]

D --> E[(PostgreSQL)]

E --> F[REST API]

F --> G[Angular Web Application]
```

---

# Stack technique

## Backend

| Technologie | Version |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Spring Data JPA | - |
| Spring JMS | - |
| IBM MQ Client | 9.4.2.0 |
| PostgreSQL | 18 |
| Maven | - |


## Frontend

| Technologie | Version |
|---|---|
| Angular | 22 |
| TypeScript | - |


## Infrastructure

| Technologie |
|-|
| Docker |
| Docker Compose |

---

# Structure du projet

```text
payment-messages
│
├── backend
│   ├── src
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend
│   ├── src
│   └── Dockerfile
│
├── docs
│   
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

✅ Consommation des messages IBM MQ    
✅ API REST de consultation  


## Frontend

✅ Dashboard des messages  
✅ Recherche et filtrage  
✅ Consultation du détail d'un message  
 


---

# Prérequis

Avant de démarrer :



---

# Configuration



---

# Lancement avec Docker Compose

Depuis la racine du projet :

```bash
docker compose up -d
```

Services démarrés :

| Service | Port |
|-|-|
| Backend Spring Boot | 8080 |
| PostgreSQL | 5432 |
| Angular | 4200 |
| IBM MQ | 1414 |

---

# Lancement Backend

```bash
cd backend

./mvnw spring-boot:run
```

ou :

```bash
mvn spring-boot:run
```

---

# Lancement Frontend

```bash
cd frontend

npm install

ng serve
```

Application disponible :

```
http://localhost:4200
```

---

# API REST

Documentation OpenAPI disponible :

```
http://localhost:8080/swagger-ui.html
```



---

# Tests

Backend :

```bash
cd backend

mvn test
```

Tests couverts :




---

# Observabilité



---

# Documentation

Documentation technique disponible dans :

```
/docs
```

Contenu :

- Architecture globale
- Architecture backend
- Flux de données
- Choix techniques


---