import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MessageFilterComponent } from './message-filter.component';
import { MessageFilters, PaymentMessageStatus } from '../../models/message.model';

describe('MessageFilterComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<MessageFilterComponent>>;
  let emitted: MessageFilters[];

  beforeEach(() => {
    vi.useFakeTimers();
    fixture = TestBed.createComponent(MessageFilterComponent);
    emitted = [];
    fixture.componentInstance.filterChange.subscribe((f) => emitted.push(f));
  });

  afterEach(() => vi.useRealTimers());

  /** Le champ de recherche déclenche une requête serveur : une par saisie, pas une par frappe. */
  it('debounces the search before emitting', () => {
    const cmp = fixture.componentInstance as unknown as { onSearch(v: string): void };
    cmp.onSearch('R');
    cmp.onSearch('RE');
    cmp.onSearch('REF');

    expect(emitted).toHaveLength(0);

    vi.advanceTimersByTime(250);

    expect(emitted).toEqual([{ q: 'REF' }]);
  });

  it('emits status, type and search together as one server-side query', () => {
    const cmp = fixture.componentInstance as unknown as {
      onSearch(v: string): void; onType(v: string): void; pick(s?: PaymentMessageStatus): void;
    };
    cmp.onSearch('ref-1');
    vi.advanceTimersByTime(250);
    cmp.onType('pacs.008');
    cmp.pick(PaymentMessageStatus.FAILED);

    expect(emitted.at(-1)).toEqual({
      status: PaymentMessageStatus.FAILED, type: 'pacs.008', q: 'ref-1',
    });
  });

  it('emits an empty query on reset', () => {
    const cmp = fixture.componentInstance as unknown as {
      onType(v: string): void; reset(): void;
    };
    cmp.onType('pacs.008');
    cmp.reset();

    expect(emitted.at(-1)).toEqual({});
  });
});
