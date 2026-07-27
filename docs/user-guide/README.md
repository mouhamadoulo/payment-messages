# Guide utilisateur — Payment Messages

Application de supervision des messages de paiement consommés depuis IBM MQ et persistés en
base. Ce guide décrit les parcours fonctionnels de l'interface Angular, écran par écran.

Toutes les captures ont été prises sur une instance réelle (backend + PostgreSQL + IBM MQ),
en français, thème clair, viewport 1440 px sauf mention contraire.

## Parcours

| # | Parcours | Route | Fichier |
|---|----------|-------|---------|
| 1 | Superviser le flux (KPI, graphiques, alertes) | `/dashboard` | [01-tableau-de-bord.md](01-tableau-de-bord.md) |
| 2 | Consulter la liste des messages | `/messages` | [02-consultation-messages.md](02-consultation-messages.md) |
| 3 | Rechercher et filtrer | `/messages` | [03-recherche-et-filtres.md](03-recherche-et-filtres.md) |
| 4 | Consulter le détail d'un message | `/messages` · `/messages/:id` | [04-detail-message.md](04-detail-message.md) |
| 5 | Agir sur un message (rejeu, statut, suppression) | `/messages/:id` | [05-actions-sur-un-message.md](05-actions-sur-un-message.md) |
| 6 | Simuler un envoi de messages | `/simulation` | [06-simulation-envoi.md](06-simulation-envoi.md) |
| 7 | Préférences d'affichage et rafraîchissement | toutes | [07-preferences-interface.md](07-preferences-interface.md) |

## Ce qu'il faut savoir avant de commencer

- **Aucune authentification.** L'application n'a ni écran de connexion, ni rôle, ni jeton :
  c'est un choix de périmètre du projet. Tous les écrans sont accessibles directement.
- **L'application ne crée pas les messages.** Ils arrivent par la file IBM MQ, sont consommés
  par le backend et persistés. L'interface les consulte et pilote leur cycle de vie.
- **L'écran de simulation ne triche pas** : il publie sur la même file d'entrée, les messages
  reviennent par le consommateur avec la même validation. Voir
  [06-simulation-envoi.md](06-simulation-envoi.md).
- **Quatre statuts** : `RECEIVED` (initial) → `PROCESSED` ou `FAILED` ; un `FAILED` peut être
  rejoué (`RECEIVED`) ou basculé en `DEAD_LETTER`. `PROCESSED` et `DEAD_LETTER` sont terminaux.

## Navigation générale

Le bandeau latéral gauche donne accès aux trois écrans (Tableau de bord, Messages, Simulation
d'envoi) et rappelle en pied de page l'état du flux MQ : nombre de messages en base, date du
dernier reçu, nom de la file consommée.
