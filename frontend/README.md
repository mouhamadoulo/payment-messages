# Frontend — Payment Messages

Application **Angular 22** standalone (sans NgModule), routes entièrement paresseuses, état en
signaux, mode *zoneless*. Elle consulte et pilote les messages de paiement exposés par l'API
Spring Boot du dépôt.

Documentation détaillée : [`docs/architecture/architecture-frontend.md`](../docs/architecture/architecture-frontend.md).

## Prérequis

- **Node.js 22** (la CI utilise cette version)
- Un backend joignable sur `http://localhost:8080` pour le mode développement
  (`docker compose up -d postgres ibm-mq` puis `./mvnw spring-boot:run` depuis `backend/`)

## Serveur de développement

```bash
npm install
ng serve
```

Application disponible sur `http://localhost:4200`, rechargée à chaque modification.

Les services appellent des chemins relatifs (`/api/v1/…`) : le relais vers le backend est
assuré par `proxy.conf.json`, déclaré dans `angular.json`. Un appel d'API qui répond `404`
vient presque toujours de là — vérifier que le backend écoute bien sur `:8080`.

## Tests

```bash
npm run test                 # Vitest en mode observation
npm run test -- --no-watch   # exécution unique (mode CI)
```

`--no-watch` est indispensable en intégration continue : sans lui le builder
`@angular/build:unit-test` reste en observation et le job n'aboutit jamais.

## Build de production

```bash
npm run build                # sortie dans dist/frontend/browser
```

Les budgets `initial` et `allScript` d'`angular.json` font échouer le build sur une régression
de poids — `allScript` couvre la somme des lots différés, qu'un budget `initial` seul laisserait
passer.

## Image de production

```bash
docker build -t payment-messages-frontend .
```

Image multi-étapes `node:22-alpine` → `nginx:1.27-alpine` : nginx sert les fichiers statiques
(assets précompressés au niveau 9, servis par `gzip_static`) et relaie `/api/` vers le service
`backend`. Les en-têtes de sécurité vivent dans `security-headers.conf` et sont `include` dans
**chaque** bloc `location` — `add_header` n'est pas cumulatif en nginx.

Le plus simple reste `docker compose up -d` à la racine du dépôt : le service `frontend` y est
construit et câblé au backend.

## Structure

```
src/app/
├── core/       # Singletons : intercepteurs, NotificationService, ThemeService, api.config
├── features/   # messages/ (domaine) et simulation/ (dépôt de messages de test sur MQ)
├── layout/     # header / sidebar / main-layout
└── shared/     # status-badge, ui/, pipes/, config/, util/
```

## Conventions

- **`ChangeDetectionStrategy.OnPush` sur tous les composants** : en mode *zoneless*, un cycle
  parcourt sinon les vues non `OnPush` alors qu'un seul signal a changé.
- **L'état lu par un gabarit vit dans un signal.** Un champ mutable simple lié par `ngModel` ne
  rafraîchit pas de façon fiable.
- **Aucun filtre client** : statut, date, type et recherche partent au serveur, et les compteurs
  des pastilles sont calculés sous les mêmes critères.
- `shared/config/status.config.ts` recopie la machine à états du serveur
  (`PaymentMessageStatus`) — **garder les deux synchronisées**.
