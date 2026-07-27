export enum PaymentMessageStatus {
  RECEIVED = 'RECEIVED',
  PROCESSED = 'PROCESSED',
  FAILED = 'FAILED',
  DEAD_LETTER = 'DEAD_LETTER'
}

export interface PaymentMessage {
  id: number;
  messageId: string;
  reference: string;
  messageType: string;
  status: PaymentMessageStatus;
  /** absent des réponses de liste : seul `GET /messages/{id}` renvoie le payload complet */
  payload?: string | null;
  /** taille du payload en octets, calculée côté serveur à l'ingestion */
  payloadSize?: number | null;
  retryCount: number;
  errorMessage: string | null;
  receivedAt: string;
  updatedAt: string;
}

/** Suivi d'un rejeu massif : l'API répond 202 puis l'état est interrogé jusqu'à la fin. */
export interface BatchRetryTask {
  taskId: string;
  state: 'RUNNING' | 'COMPLETED' | 'FAILED';
  processed: number;
  truncated: boolean;
  startedAt: string;
  finishedAt: string | null;
  error: string | null;
}

/**
 * Critères de liste. Les quatre sont envoyés au serveur : filtrer le texte et le type sur
 * la page affichée contredisait les compteurs globaux et rendait toute recherche aveugle
 * au-delà de la page courante.
 */
export interface MessageFilters {
  status?: PaymentMessageStatus;
  receivedAfter?: string;
  /** type exact */
  type?: string;
  /** fragment recherché dans la référence, le messageId ou le type */
  q?: string;
}

/** Tranche horaire du volume de réception, bornes calculées par le serveur. */
export interface HourlyBucket {
  bucketStart: string;
  hour: number;
  count: number;
}

export interface TypeCount {
  messageType: string;
  count: number;
}

export interface RetryBuckets {
  none: number;
  one: number;
  two: number;
  threeOrMore: number;
}

/**
 * Agrégats du tableau de bord (`GET /messages/stats/dashboard`). Ils remplacent
 * l'échantillon de 200 messages complets que le client téléchargeait pour recompter
 * lui-même : quelques centaines d'octets, et des chiffres exacts sur toute la table.
 */
export interface DashboardStats {
  windowFrom: string;
  windowTo: string;
  windowTotal: number;
  lastReceivedAt: string | null;
  hourly: HourlyBucket[];
  types: TypeCount[];
  retries: RetryBuckets;
  recentFailures: PaymentMessage[];
}

/** Configuration MQ non sensible exposée par le backend (GET /config). */
export interface MqConfig {
  queue: string;
  dlqQueue: string;
  queueManager: string;
  channel: string;
}
