import { describe, it, expect } from 'vitest';
import { formatAmount, formatBytes, parsePayload, payloadSize, relativeTime } from './payload.util';

const RAW = JSON.stringify({
  messageId: 'abc',
  payment: { transactionId: 'TX-1', amount: 1500.5, currency: 'EUR', executionDate: '2026-07-24' },
  debtor: { name: 'ACME Corp' },
  creditor: { name: 'Vinci' },
});

describe('parsePayload', () => {
  it('extracts the business fields of a PaymentMessageEvent', () => {
    const p = parsePayload(RAW);
    expect(p.valid).toBe(true);
    expect(p.transactionId).toBe('TX-1');
    expect(p.amount).toBe(1500.5);
    expect(p.currency).toBe('EUR');
    expect(p.executionDate).toBe('2026-07-24');
    expect(p.debtor).toBe('ACME Corp');
    expect(p.creditor).toBe('Vinci');
    expect(p.pretty).toContain('\n');
  });

  it('falls back to the raw text on invalid JSON', () => {
    const p = parsePayload('{ not json');
    expect(p.valid).toBe(false);
    expect(p.pretty).toBe('{ not json');
  });

  it('handles null payloads', () => {
    expect(parsePayload(null)).toEqual({ pretty: '', valid: false });
  });
});

describe('formatting helpers', () => {
  it('sizes payloads in bytes then kilobytes', () => {
    expect(payloadSize('abc')).toBe(3);
    expect(formatBytes(0)).toBe('—');
    expect(formatBytes(512)).toBe('512 o');
    expect(formatBytes(2048)).toBe('2.0 Ko');
  });

  it('formats amounts and tolerates unknown currencies', () => {
    expect(formatAmount(undefined)).toBe('—');
    expect(formatAmount(10, 'NOT_A_CURRENCY')).toBe('10 NOT_A_CURRENCY');
  });

  it('renders relative times', () => {
    const now = Date.parse('2026-07-24T12:00:00Z');
    expect(relativeTime('2026-07-24T11:59:30Z', now)).toBe("à l'instant");
    expect(relativeTime('2026-07-24T11:30:00Z', now)).toBe('il y a 30 min');
    expect(relativeTime('2026-07-24T09:00:00Z', now)).toBe('il y a 3 h');
    expect(relativeTime('2026-07-22T12:00:00Z', now)).toBe('il y a 2 j');
    expect(relativeTime(null, now)).toBe('—');
  });
});
