import { PaymentMessageStatus } from '../../features/messages/models/message.model';

export interface StatusMeta { icon: string; color: string; label: string; }

export const STATUS_META: Record<PaymentMessageStatus, StatusMeta> = {
  [PaymentMessageStatus.RECEIVED]:      { icon: 'inbox',        color: '#2F6BFF', label: 'Reçus' },
  [PaymentMessageStatus.VALIDATING]:    { icon: 'verified',     color: '#F59E0B', label: 'En validation' },
  [PaymentMessageStatus.PROCESSING]:    { icon: 'sync',         color: '#6366F1', label: 'En traitement' },
  [PaymentMessageStatus.PROCESSED]:     { icon: 'check_circle', color: '#22C55E', label: 'Traités' },
  [PaymentMessageStatus.FAILED]:        { icon: 'error',        color: '#EF4444', label: 'Échoués' },
  [PaymentMessageStatus.RETRY_PENDING]: { icon: 'replay',       color: '#F97316', label: 'À réessayer' },
  [PaymentMessageStatus.REJECTED]:      { icon: 'block',        color: '#6B7280', label: 'Rejetés' },
  [PaymentMessageStatus.DEAD_LETTER]:   { icon: 'report',       color: '#9333EA', label: 'Dead letter' },
};

export const STATUS_ORDER: PaymentMessageStatus[] = [
  PaymentMessageStatus.RECEIVED, PaymentMessageStatus.VALIDATING, PaymentMessageStatus.PROCESSING,
  PaymentMessageStatus.PROCESSED, PaymentMessageStatus.FAILED, PaymentMessageStatus.RETRY_PENDING,
  PaymentMessageStatus.REJECTED, PaymentMessageStatus.DEAD_LETTER,
];

export function statusMeta(status: PaymentMessageStatus): StatusMeta {
  return STATUS_META[status] ?? { icon: 'circle', color: '#6B7280', label: String(status) };
}
