/**
 * File visée et bornes, fixées par le serveur (cf. `GET /simulation/config`).
 *
 * `queue` est la file d'entrée configurée : elle est **affichée**, jamais choisie — l'API ne
 * prend pas de destination en paramètre. L'IHM lit les bornes pour cadrer sa saisie ; c'est
 * le serveur qui refuse une demande hors bornes, la borne côté client n'est qu'un confort.
 */
export interface SimulationConfig {
  enabled: boolean;
  queue: string;
  maxCount: number;
  maxRate: number;
}

/**
 * Suivi d'un envoi. Les compteurs portent sur la **publication** : `published` signifie que
 * le broker a accepté le message, pas qu'il a été traité — son sort applicatif s'observe
 * ensuite dans la liste des messages.
 */
export interface SimulationTask {
  taskId: string;
  state: 'RUNNING' | 'COMPLETED' | 'FAILED';
  destination: string;
  total: number;
  sent: number;
  published: number;
  failed: number;
  startedAt: string;
  finishedAt: string | null;
  error: string | null;
}

/** La destination n'y figure pas : le serveur publie toujours sur la file configurée. */
export interface SimulationSendRequest {
  payload: string;
  count: number;
  ratePerSecond: number;
  uniqueIds: boolean;
}

export type SendMode = 'single' | 'bulk';

/**
 * Ligne d'historique. Elle vit dans la mémoire de l'onglet : le serveur ne conserve que les
 * quelques derniers envois et rien n'est persisté — l'annoncer autrement serait mentir.
 */
export interface SendHistoryEntry {
  taskId: string;
  title: string;
  destination: string;
  time: string;
  published: number;
  total: number;
  outcome: 'ok' | 'partial' | 'ko';
}
