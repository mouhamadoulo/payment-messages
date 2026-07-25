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
frontend/src/
├── main.ts                        # Bootstrap Angular
├── styles/_tokens.scss            # Design tokens (couleurs, espacements, polices)
└── app/
    ├── app.ts                     # Racine : gabarit applicatif si session, sinon router-outlet
    ├── app.config.ts              # Providers (router, HttpClient + intercepteurs, Material)
    ├── app.routes.ts              # Routes paresseuses, protégées par authGuard
    ├── core/                      # Singletons applicatifs
    │   ├── auth/auth.service.ts             # Session : jeton, identité, rôles (signaux)
    │   ├── config/api.config.ts             # Chemins d'API
    │   ├── guards/auth.guard.ts             # Redirection vers /login sans session
    │   ├── interceptors/api.interceptor.ts  # Préfixe /api/v1
    │   ├── interceptors/auth.interceptor.ts # Jeton Bearer + traitement centralisé des 401
    │   ├── interceptors/resilience.interceptor.ts # Délai maximal + rejeu des GET
    │   └── services/                        # NotificationService, ThemeService
    ├── features/
    │   ├── auth/login.page.ts               # Écran de connexion
    │   └── messages/                        # Domaine : pages, composants, service, modèles
    ├── layout/                              # header / sidebar / main-layout
    └── shared/                              # status-badge, ui/icon, kpi-card, config, pipes
```

---

## 4. Session et sécurité

L'API est fermée : sans jeton, toutes les requêtes répondent `401`. La chaîne côté client :

```mermaid
flowchart LR
    GUARD[authGuard] -->|pas de session| LOGIN[/login/]
    LOGIN -->|POST /auth/login| API[(API)]
    API -->|token + rôles| AUTH[AuthService]
    AUTH -->|sessionStorage| STORE[(sessionStorage)]
    AUTH --> INTERCEPT[authInterceptor]
    INTERCEPT -->|Authorization: Bearer| API
    API -->|401| INTERCEPT
    INTERCEPT -->|logout| LOGIN
```

- **`AuthService`** conserve jeton, identité et rôles en signaux, persistés dans
  `sessionStorage` — et non `localStorage` : la session disparaît à la fermeture de l'onglet,
  ce qui réduit la fenêtre d'exploitation en cas de XSS.
- **`authInterceptor`** pose l'en-tête `Authorization` sur les appels API (sauf
  l'authentification) et traite le `401` en un seul endroit : session fermée, redirection vers
  `/login`. Un `403` ne ferme pas la session, c'est un défaut de rôle.
- **`authGuard`** évite d'ouvrir une vue qui ne pourrait rien charger, et mémorise l'URL
  demandée (`returnUrl`).
- Hors session, `app.ts` rend la page de connexion **seule** : le gabarit (barre latérale,
  bandeau, bouton d'actualisation) n'aurait rien à afficher.
- Le bandeau affiche le compte connecté, ses rôles en infobulle, et un bouton de déconnexion.
- Les opérations réservées à `ADMIN` (suppression, changement de statut, rejeu massif)
  restent visibles : un `403` est signalé par un message explicite plutôt que masqué.

---

## 5. Endpoints consommés

| Méthode | Path | Usage |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Obtention du jeton |
| `GET` | `/api/v1/messages` | Liste paginée avec filtres (statut, date, type, recherche) |
| `GET` | `/api/v1/messages/stats` | Compteurs par statut sous les filtres actifs |
| `GET` | `/api/v1/messages/stats/dashboard` | Agrégats du dashboard (volume horaire, types, tentatives, alertes) |
| `GET` | `/api/v1/messages/types` | Types présents en base (sélecteur de la barre de filtres) |
| `GET` | `/api/v1/messages/{id}` | Détail d'un message (payload inclus) |
| `GET` | `/api/v1/config` | Configuration MQ non sensible |
| `DELETE` | `/api/v1/messages/{id}` | Suppression (`ADMIN`) |
| `POST` | `/api/v1/messages/batch/retry-failed` | Rejeu massif (`ADMIN`), suivi par `taskId` |
| `POST` | `/api/v1/messages/{id}/retry` | Rejeu individuel |
| `PUT` | `/api/v1/messages/{id}/status` | Changement de statut (`ADMIN`), corps `{ status, reason }` |

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
