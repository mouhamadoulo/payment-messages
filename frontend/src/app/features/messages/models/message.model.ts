export enum PaymentMessageStatus {
  RECEIVED = 'RECEIVED',
  VALIDATING = 'VALIDATING',
  PROCESSING = 'PROCESSING',
  PROCESSED = 'PROCESSED',
  FAILED = 'FAILED',
  RETRY_PENDING = 'RETRY_PENDING',
  REJECTED = 'REJECTED',
  DEAD_LETTER = 'DEAD_LETTER'
}

export interface PaymentMessage {
  id: number;
  messageId: string;
  reference: string;
  messageType: string;
  status: PaymentMessageStatus;
  payload: string;
  retryCount: number;
  errorMessage: string | null;
  receivedAt: string;
  processedAt: string | null;
  updatedAt: string;
}

export interface MessageFilters {
  status?: PaymentMessageStatus;
  receivedAfter?: string;
}
