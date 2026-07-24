import { describe, it, expect } from 'vitest';
import { PaymentMessageStatus } from '../../features/messages/models/message.model';
import { STATUS_META, statusMeta, STATUS_ORDER } from './status.config';

describe('status.config', () => {
  it('covers all 8 statuses', () => {
    expect(Object.keys(STATUS_META)).toHaveLength(8);
    for (const s of Object.values(PaymentMessageStatus)) {
      expect(STATUS_META[s]).toBeDefined();
      expect(STATUS_META[s].color).toMatch(/^#/);
      expect(STATUS_META[s].label.length).toBeGreaterThan(0);
    }
  });
  it('STATUS_ORDER lists every status once', () => {
    expect([...STATUS_ORDER].sort()).toEqual(Object.values(PaymentMessageStatus).sort());
  });
  it('statusMeta falls back for unknown input', () => {
    expect(statusMeta('NOPE' as PaymentMessageStatus).label).toBe('NOPE');
  });
});
