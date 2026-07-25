# Architecture Frontend

## 1. Présentation

Le frontend est une application **Angular 22** en **standalone components** (sans NgModule),
routes entièrement paresseuses (`loadComponent`), état en **signaux**, mode *zoneless*.

---

## 2. Stack technique

| Technologie | Version | Rôle |
|---|---|---|
| Angular | 22 | Framework |
| TypeScript | 6 | Langage |
| RxJS | 7.8 | Programmation réactive |
| Angular Material + CDK | 22 | Dialogues, snackbar, sélecteurs |
| Vitest | 4 | Tests unitaires |
| Prettier | 3.8 | Formateur de code |
| SCSS | - | Préprocesseur CSS |

---

## 3. Structure

```
frontend/
├── angular.json                   # Budgets de build, outputHashing, inlineCritical: false
├── proxy.conf.json                # Relais /api → localhost:8080 pour `ng serve`
├── Dockerfile                     # node:22-alpine → nginx:1.27-alpine (cf. §9)
├── nginx.conf                     # Service statique, cache, relais /api → backend:8080
├── security-headers.conf          # CSP et en-têtes, inclus dans chaque location
└── src/
    ├── main.ts                    # Bootstrap Angular
    ├── styles/_tokens.scss        # Design tokens (couleurs, espacements, polices)
    └── app/
        ├── app.ts                 # Racine : rend le gabarit applicatif
        ├── app.config.ts          # Providers (router, HttpClient + intercepteurs, Material)
        ├── app.routes.ts          # Routes paresseuses (loadComponent)
        ├── core/                  # Singletons applicatifs
        │   ├── config/api.config.ts             # Chemins d'API
        │   ├── interceptors/api.interceptor.ts  # Préfixe /api/v1
        │   ├── interceptors/resilience.interceptor.ts # Délai maximal + rejeu des GET
        │   └── services/                        # NotificationService, ThemeService
        ├── features/
        │   ├── messages/                        # Domaine : pages, composants, service, modèles
        │   └── simulation/                      # Dépôt de messages de test sur IBM MQ
        ├── layout/                              # header / sidebar / main-layout
        └── shared/                              # status-badge, ui/icon, ui/kpi-card,
                                                 # ui/auto-animate.directive, config/status.config,
                                                 # pipes/date-format, util/payload.util
```

---

## 4. Accès à l'API

**L'authentification et les autorisations sont hors périmètre du sujet** : l'application ne
gère ni session, ni jeton, ni rôle. Aucun écran de connexion, aucun garde de route, aucun
en-tête `Authorization` — le gabarit applicatif est rendu directement au démarrage.

Deux intercepteurs seulement restent sur le chemin des requêtes :

```mermaid
flowchart LR
    REQ[Requête HTTP] --> API_I[apiInterceptor]
    API_I -->|préfixe /api/v1| RES_I[resilienceInterceptor]
    RES_I -->|timeout 15s + rejeu des GET| API[(API)]
```

- **`apiInterceptor`** préfixe toute URL commençant par `/api` avec `API_CONFIG.baseUrl`.
- **`resilienceInterceptor`** ferme la marche : délai maximal de 15 s et deux rejeux à
  repli exponentiel, **sur `GET`/`HEAD` uniquement** — rejouer un `POST /retry` ou un
  `DELETE` doublerait l'effet métier.

Les requêtes partent donc en `/api/v1/…`, sur l'origine du front : **jamais** vers un hôte
codé en dur. Le relais vers le backend est assuré par un proxy, différent selon le mode
d'exécution — `proxy.conf.json` pour `ng serve` (vers `http://localhost:8080`), le bloc
`location /api/` de `nginx.conf` pour l'image de production (vers `http://backend:8080`).
Cette même origine est aussi la raison pour laquelle le CORS ne joue en pratique que pour les
appels directs au port de l'API.

---

## 5. Endpoints consommés

| Méthode | Path | Usage |
|---|---|---|
| `GET` | `/api/v1/messages` | Liste paginée avec filtres (statut, date, type, recherche) |
| `GET` | `/api/v1/messages/stats` | Compteurs par statut sous les filtres actifs |
| `GET` | `/api/v1/messages/stats/dashboard` | Agrégats du dashboard (volume horaire, types, tentatives, alertes) |
| `GET` | `/api/v1/messages/types` | Types présents en base (sélecteur de la barre de filtres) |
| `GET` | `/api/v1/messages/{id}` | Détail d'un message (payload inclus) |
| `GET` | `/api/v1/config` | Configuration MQ non sensible |
| `GET` | `/api/v1/simulation/config` | File visée et bornes de la simulation d'envoi |
| `GET` | `/api/v1/simulation/sends/{taskId}` | Avancement d'un envoi de test |
| `DELETE` | `/api/v1/messages/{id}` | Suppression |
| `POST` | `/api/v1/messages/batch/retry-failed` | Rejeu massif, suivi par `taskId` |
| `POST` | `/api/v1/messages/{id}/retry` | Rejeu individuel |
| `POST` | `/api/v1/simulation/sends` | Dépôt de messages de test, suivi par `taskId` |
| `PUT` | `/api/v1/messages/{id}/status` | Changement de statut, corps `{ status, reason }` |

Le sélecteur de statut ne propose que les transitions autorisées (`STATUS_TRANSITIONS` dans
`shared/config/status.config.ts`, recopie de la machine à états du serveur) et recueille un
motif facultatif. Le serveur reste l'autorité : une transition interdite répond `422`, dont le
`detail` est affiché tel quel.

---

## 6. Données et réactivité

- **Aucun filtre client.** Statut, date, type et recherche texte partent au serveur, et les
  compteurs des pastilles sont calculés sous les mêmes critères : une pastille annonce ce que
  donnerait un clic dessus. Filtrer la page affichée pendant que les compteurs portaient sur
  toute la table affichait « FAILED 1 240 » puis trois lignes.
- **Anti-rebond de 250 ms** sur la recherche : une requête par saisie, pas une par frappe.
- **Le dashboard ne télécharge plus d'échantillon.** Deux appels (`/stats` et
  `/stats/dashboard`) remplacent le rapatriement de 200 messages complets dont le client
  recomptait tout : les chiffres portent sur la table entière au lieu d'un échantillon non
  représentatif.
- **Requêtes concurrentes annulées.** Chaque flux (liste, compteurs, agrégats) passe par un
  `Subject` consommé en `switchMap` dans `MessageService` : la requête en vol est annulée dès
  que la suivante part, donc c'est la dernière page *demandée* qui s'affiche, et non la
  dernière *arrivée*.
- **Rafraîchissement périodique** toutes les 30 s (`AUTO_REFRESH_MS`), suspendu quand l'onglet
  est masqué (`visibilityState`) et rattrapé au retour. La pastille du bandeau le pilote et
  indique quand l'écran est volontairement figé ; seules les vues déjà chargées sont rejouées.
  Un rafraîchissement de fond ne repasse pas la vue en squelette.
- **Tableau ou cartes, jamais les deux.** Le rendu est conditionné par un signal de point de
  rupture (`BreakpointObserver`, `max-width: 700px`) au lieu d'être masqué en CSS, et la
  taille formatée de chaque ligne est mémorisée dans un modèle de ligne.
- **Lectures unitaires en `httpResource`.** Le message affiché (`/messages/{id}`), la liste des
  types et la configuration MQ sont décrits en fonction d'un signal : Angular émet la requête,
  annule la précédente et expose `isLoading()` / `error()`. Une description `undefined` laisse
  la ressource au repos — c'est ce qui remplace les gardes « déjà chargé ». Les `subscribe`
  restants sont des **commandes** (`POST`, `PUT`, `DELETE`) et le suivi du rejeu massif.
- **Détection de changement `OnPush`** sur tous les composants : en mode *zoneless*, un cycle
  parcourt sinon les vues non `OnPush` alors qu'un seul signal a changé.

---

## 7. Résilience et chargement

- **`resilienceInterceptor`** (en bout de chaîne, donc sur la requête définitive) : délai
  maximal de 15 s sur toutes les méthodes, et rejeu avec temporisation exponentielle
  (300 ms puis 600 ms, deux reprises) **limité aux méthodes idempotentes** — rejouer un
  `POST /retry` doublerait l'effet métier. Seuls un `status 0` (coupure réseau) et les `5xx`
  sont rejoués : un `4xx` renverrait la même réponse, et un dépassement de délai rejoué trois
  fois enchaînerait 45 s d'attente.
- **`withFetch()`** : l'API `fetch` remplace `XMLHttpRequest`, ce qui annule réellement la
  requête (et non le seul abonnement) quand un `switchMap` ou une ressource abandonne.
- **Pas de moteur d'animations.** Toutes les animations sont en CSS et Angular Material 22
  n'importe plus `@angular/animations` : `provideAnimations()` et la dépendance sont retirés
  plutôt que chargés en différé (`provideAnimationsAsync()` est déprécié depuis la 20.2).
- **`@defer`** sur les blocs sous la ligne de flottaison du dashboard (`on viewport`, avec un
  substitut qui réserve la hauteur) et sur le tiroir de détail (`when`), sorti du lot de la
  page de liste avec la directive d'animation de liste qu'il embarque.
- **Budgets de build** (`angular.json`) : `initial` et `allScript`, ce dernier couvrant la
  somme des lots différés — sans lui, une régression de poids passait inaperçue dès qu'elle
  tombait dans un *chunk* paresseux.

---

## 8. Simulation d'envoi

Route `/simulation`, lot paresseux dédié. L'écran dépose des messages de test sur une file
IBM MQ, dans le rôle que tiennent les applications de back-office du flux réel.

**Rien n'est court-circuité.** Le payload part tel quel sur la file et repasse par le
consommateur applicatif : même désérialisation, même validation, mêmes rejets. C'est ce qui
rend l'écran utile — un message y devient une ligne `RECEIVED` (ou `FAILED` si le payload est
invalide) exactement comme un message venu d'un vrai producteur.

- **Le serveur cadence, le client interroge.** `POST /simulation/sends` répond `202` avec un
  `taskId` ; l'avancement est relu toutes les 500 ms jusqu'à l'état terminal. L'IHM ne compte
  rien elle-même : deux onglets ouverts voient le même envoi, et un rechargement de page n'en
  invente pas un second.
- **La file de destination est affichée, jamais choisie.** L'API ne prend pas de destination en
  paramètre : les messages partent sur la file configurée, que `GET /simulation/config` expose
  pour affichage. Le champ correspondant est un encart en lecture seule, pas un `<select>` —
  proposer un choix inexistant serait mentir sur ce que fait le bouton. Le nom affiché vient de
  `SimulationService.queue()`, qui replie sur la configuration MQ (`GET /config`, déjà chargée
  par le bandeau latéral) : les deux exposent la même propriété serveur (`ibm.mq.queue`), et le
  repli garde le champ lisible tant que la configuration de simulation n'a pas répondu.
- **Les bornes viennent du serveur** (même endpoint) : plafond du nombre de messages et de la
  cadence. Le formulaire s'y ajuste, mais c'est le serveur qui refuse — une borne recopiée en
  dur aurait dérivé au premier changement de configuration.
- **Les compteurs disent « publiés », pas « traités ».** Ils mesurent l'acceptation par le
  broker ; le sort applicatif se lit dans l'onglet Messages, et l'écran renvoie vers lui plutôt
  que d'afficher un succès qu'il ne peut pas constater.
- **Modèles de payload** (`features/simulation/config/templates.ts`) écrits sur le contrat de
  la file d'entrée (`PaymentMessageEvent`), **pas** sur celui de l'API REST : `executionDate`
  y est une `LocalDate` et `createdAt` une `LocalDateTime` **sans décalage horaire**, à
  l'inverse des horodatages de l'API. Deux modèles sont invalides à dessein et couvrent les
  deux chemins de rejet définitif — JSON illisible et validation en échec.
- **`uniqueIds` est cochée par défaut** : l'ingestion est idempotente sur `messageId`, sans
  réécriture un envoi en masse ne produirait qu'une seule ligne. La case reste décochable,
  c'est justement de quoi tester la déduplication.
- **L'historique est celui de l'onglet**, pas un journal : le serveur ne retient que les
  derniers envois, le temps d'en suivre l'avancement. L'en-tête de la carte le dit.
- **Écran coupable par configuration.** Quand `app.simulation.enabled` est `false`, l'API
  répond `503` et l'écran affiche un bandeau — il ne prétend pas fonctionner à vide.

---

## 9. Livraison et conteneurisation

`frontend/Dockerfile` : image multi-étapes `node:22-alpine` → `nginx:1.27-alpine`. Aucun
runtime Node en production — nginx sert des fichiers statiques et relaie `/api/`.

### 9.1 Étape de build

- **`npm ci` strict**, sans `--legacy-peer-deps` : le drapeau ne masquait qu'un
  `@angular/animations` resté en arrière, dépendance depuis retirée du projet. Le conserver
  ferait diverger l'image de ce que valide la CI.
- **Copie ciblée** (`angular.json`, `tsconfig*`, `public/`, `src/`) plutôt que `COPY . .` :
  seules les entrées du build invalident le cache. Modifier ce document, `proxy.conf.json` ou
  `nginx.conf` ne relance plus `npm run build`.
- **Précompression au build** : les `.js`/`.css`/`.html`/`.svg`/`.json` produits sont gzippés
  au **niveau 9** et déposés à côté de l'original. Servis ensuite par `gzip_static`, ils
  coûtent zéro CPU par requête, à un taux de compression inatteignable à la volée.

### 9.2 Service nginx (`nginx.conf`)

| Bloc | Politique de cache | Pourquoi |
|---|---|---|
| `location = /index.html` | `no-cache` | Seul fichier non haché : un exemplaire périmé pointerait vers des bundles supprimés au déploiement suivant, donc une page blanche jusqu'au vidage du cache navigateur |
| Bundles hachés (`-<hash8>.js|css`) | `max-age=31536000, immutable` | `outputHashing: all` — le nom change avec le contenu, l'immutabilité est acquise |
| Assets de `public/` (icônes, polices…) | `max-age=86400` | Nom stable : `immutable` y figerait une version pour un an |
| `location /api/` | — | Relais vers `http://backend:8080`, via le résolveur DNS interne de Docker (`127.0.0.11`) pour que nginx démarre même si le backend n'est pas encore prêt |
| `location /` | — | Repli SPA `try_files $uri $uri/ /index.html` |

Le repli SPA provoque une **redirection interne** qui repasse par `location = /index.html` :
le `no-cache` couvre donc aussi les routes Angular profondes.

### 9.3 En-têtes de sécurité (`security-headers.conf`)

Le fichier est `include` dans **chaque** `location`, sans exception. `add_header` n'est pas
cumulatif en nginx : un bloc qui pose son propre `Cache-Control` écrase l'héritage, et la
politique de sécurité disparaîtrait précisément sur les réponses qui portent le code.

La CSP tient un `script-src 'self'` strict — possible uniquement parce que
`optimization.styles.inlineCritical` est **désactivé** dans `angular.json` : cette optimisation
émet un `<link … onload="…">`, gestionnaire en ligne qui imposerait `'unsafe-inline'`. En
regard, `style-src` conserve `'unsafe-inline'` (Angular injecte les styles de composant en
`<style>` à l'exécution) et `font-src` conserve `fonts.gstatic.com` (les règles `@font-face`
sont intégrées au build, les `.woff2` restent téléchargés).

`server_tokens off` retire la version de nginx des en-têtes et des pages d'erreur.

### 9.4 Sonde de disponibilité

L'image déclare un `HEALTHCHECK` (`wget --spider http://127.0.0.1/`) : sans lui,
`docker compose up --wait` — le test de fumée de la CI — n'attendrait pas l'état *healthy* du
conteneur. `wget` vient de busybox, déjà présent dans l'image alpine.
