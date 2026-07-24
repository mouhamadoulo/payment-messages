import { describe, it, expect } from 'vitest';
import { hourHistogram } from './hour-histogram';

const at = (h: number) => ({ receivedAt: new Date(2026, 0, 1, h, 0, 0).toISOString() } as any);

describe('hourHistogram', () => {
  it('returns 24 zero buckets for empty input', () => {
    const r = hourHistogram([]);
    expect(r).toHaveLength(24);
    expect(r.every((x) => x === 0)).toBe(true);
  });
  it('counts messages into their local hour bucket', () => {
    const r = hourHistogram([at(13), at(13), at(9)]);
    expect(r[13]).toBe(2);
    expect(r[9]).toBe(1);
  });
  it('ignores invalid or missing dates', () => {
    const r = hourHistogram([{ receivedAt: 'not-a-date' } as any, { receivedAt: null } as any]);
    expect(r.every((x) => x === 0)).toBe(true);
  });
});
