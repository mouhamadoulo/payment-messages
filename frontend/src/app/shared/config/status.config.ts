import { PaymentMessageStatus } from '../../features/messages/models/message.model';

export interface StatusMeta { icon: string; color: string; label: string; }

export const STATUS_META: Record<PaymentMessageStatus, StatusMeta> = {
  [PaymentMessageStatus.RECEIVED]:    { icon: 'inbox',        color: '#2F6BFF', label: 'Reçus' },
  [PaymentMessageStatus.PROCESSED]:   { icon: 'check_circle', color: '#22C55E', label: 'Traités' },
  [PaymentMessageStatus.FAILED]:      { icon: 'error',        color: '#EF4444', label: 'Échoués' },
  [PaymentMessageStatus.DEAD_LETTER]: { icon: 'report',       color: '#9333EA', label: 'Dead letter' },
};

export const STATUS_ORDER: PaymentMessageStatus[] = [
  PaymentMessageStatus.RECEIVED, PaymentMessageStatus.PROCESSED,
  PaymentMessageStatus.FAILED, PaymentMessageStatus.DEAD_LETTER,
];

export function statusMeta(status: PaymentMessageStatus): StatusMeta {
  return STATUS_META[status] ?? { icon: 'circle', color: '#6B7280', label: String(status) };
}
