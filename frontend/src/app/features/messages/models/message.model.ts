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

export interface MessageFilters {
  status?: PaymentMessageStatus;
  receivedAfter?: string;
}

/** Configuration MQ non sensible exposée par le backend (GET /config). */
export interface MqConfig {
  queue: string;
  dlqQueue: string;
  queueManager: string;
  channel: string;
}
