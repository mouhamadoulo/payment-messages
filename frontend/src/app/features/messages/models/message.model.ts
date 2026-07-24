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
  payload: string;
  retryCount: number;
  errorMessage: string | null;
  receivedAt: string;
  updatedAt: string;
}

export interface MessageFilters {
  status?: PaymentMessageStatus;
  receivedAfter?: string;
}
