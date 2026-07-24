/**
 * Le backend ne stocke que le payload MQ brut (`PaymentMessageEvent` sérialisé).
 * Ces helpers en extraient les informations métier affichées par l'UI, sans
 * jamais échouer sur un payload absent ou mal formé.
 */
export interface ParsedPayload {
  /** payload ré-indenté, ou le brut si le JSON est invalide */
  pretty: string;
  valid: boolean;
  transactionId?: string;
  amount?: number;
  currency?: string;
  executionDate?: string;
  debtor?: string;
  creditor?: string;
}

export function parsePayload(raw: string | null | undefined): ParsedPayload {
  if (!raw) return { pretty: '', valid: false };
  try {
    const o = JSON.parse(raw);
    return {
      pretty: JSON.stringify(o, null, 2),
      valid: true,
      transactionId: o?.payment?.transactionId,
      amount: typeof o?.payment?.amount === 'number' ? o.payment.amount : Number(o?.payment?.amount) || undefined,
      currency: o?.payment?.currency,
      executionDate: o?.payment?.executionDate,
      debtor: o?.debtor?.name,
      creditor: o?.creditor?.name,
    };
  } catch {
    return { pretty: raw, valid: false };
  }
}

export function payloadSize(raw: string | null | undefined): number {
  if (!raw) return 0;
  return new TextEncoder().encode(raw).length;
}

export function formatBytes(bytes: number): string {
  if (!bytes) return '—';
  return bytes > 1024 ? `${(bytes / 1024).toFixed(1)} Ko` : `${bytes} o`;
}

export function formatAmount(amount?: number, currency?: string): string {
  if (amount == null || Number.isNaN(amount)) return '—';
  try {
    return amount.toLocaleString('fr-FR', { style: 'currency', currency: currency || 'EUR' });
  } catch {
    return `${amount} ${currency ?? ''}`.trim();
  }
}

export function relativeTime(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return '—';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '—';
  const s = Math.max(0, Math.round((now - t) / 1000));
  if (s < 60) return "à l'instant";
  const m = Math.round(s / 60);
  if (m < 60) return `il y a ${m} min`;
  const h = Math.round(m / 60);
  if (h < 24) return `il y a ${h} h`;
  return `il y a ${Math.round(h / 24)} j`;
}
