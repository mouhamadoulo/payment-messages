import { PaymentMessage } from '../../../features/messages/models/message.model';

export function hourHistogram(messages: PaymentMessage[]): number[] {
  const buckets = new Array(24).fill(0);
  for (const m of messages) {
    if (!m?.receivedAt) continue;
    const d = new Date(m.receivedAt);
    if (Number.isNaN(d.getTime())) continue;
    const h = d.getHours();
    buckets[h]++;
  }
  return buckets;
}
