export const API_CONFIG = {
  baseUrl: '/api/v1',
  messages: '/messages',
  stats: '/messages/stats',
  /** agrégats du dashboard calculés en SQL (volume horaire, types, tentatives, alertes) */
  dashboardStats: '/messages/stats/dashboard',
  /** types présents en base : le filtre par type s'applique à toute la table */
  messageTypes: '/messages/types',
  batchRetry: '/messages/batch/retry-failed',
  config: '/config',
  /** bornes et files admises par la simulation d'envoi */
  simulationConfig: '/simulation/config',
  /** dépôt de messages de test : 202 + taskId, suivi sur `/{taskId}` */
  simulationSends: '/simulation/sends'
};
