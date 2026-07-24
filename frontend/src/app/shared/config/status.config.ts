import { PaymentMessageStatus } from '../../features/messages/models/message.model';

export interface StatusMeta {
  /** libellé technique affiché dans les badges et les chips (mono) */
  label: string;
  /** libellé métier affiché dans les cartes du tableau de bord */
  title: string;
  color: string;
  bg: string;
}

export const STATUS_META: Record<PaymentMessageStatus, StatusMeta> = {
  [PaymentMessageStatus.RECEIVED]:    { label: 'RECEIVED',    title: 'Reçus',       color: '#2F5FA8', bg: '#E9F0FA' },
  [PaymentMessageStatus.PROCESSED]:   { label: 'PROCESSED',   title: 'Traités',     color: '#27794C', bg: '#E2F2E8' },
  [PaymentMessageStatus.FAILED]:      { label: 'FAILED',      title: 'En échec',    color: '#B53535', bg: '#F8E6E6' },
  [PaymentMessageStatus.DEAD_LETTER]: { label: 'DEAD_LETTER', title: 'Dead letter', color: '#586170', bg: '#ECEFF2' },
};

export const STATUS_ORDER: PaymentMessageStatus[] = [
  PaymentMessageStatus.RECEIVED, PaymentMessageStatus.PROCESSED,
  PaymentMessageStatus.FAILED, PaymentMessageStatus.DEAD_LETTER,
];

export function statusMeta(status: PaymentMessageStatus): StatusMeta {
  return STATUS_META[status]
    ?? { label: String(status), title: String(status), color: '#586170', bg: '#ECEFF2' };
}
