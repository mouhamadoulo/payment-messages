import { Component, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PaymentMessageStatus, MessageFilters } from '../../models/message.model';
import { STATUS_ORDER, statusMeta } from '../../../../shared/config/status.config';

@Component({
  selector: 'app-message-filter',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="filter">
      <div class="chips">
        <button class="chip" [class.active]="!selected()"
                [style.--c]="'#6B7280'" (click)="pick(undefined)">Tous</button>
        @for (s of statuses; track s.status) {
          <button class="chip" [class.active]="selected() === s.status"
                  [style.--c]="s.color" (click)="pick(s.status)">{{ s.label }}</button>
        }
      </div>
      <div class="right">
        <label class="date">
          <span>Reçu après</span>
          <input type="date" [ngModel]="date()" (ngModelChange)="setDate($event)" />
        </label>
        @if (selected() || date()) {
          <button class="reset" (click)="reset()">✕ Effacer</button>
        }
      </div>
    </div>
  `,
  styles: [`
    .filter { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4);
              flex-wrap: wrap; margin-bottom: var(--space-4); }
    .chips { display: flex; gap: var(--space-2); flex-wrap: wrap; }
    .chip { border: 1px solid var(--border); background: var(--surface); color: var(--muted);
            border-radius: var(--radius-pill); padding: 6px var(--space-3); font-size: .8rem;
            font-weight: 600; cursor: pointer; font-family: inherit; }
    .chip:hover { background: var(--bg); }
    .chip.active { background: color-mix(in srgb, var(--c) 14%, transparent);
                   color: var(--c); border-color: color-mix(in srgb, var(--c) 40%, transparent); }
    .right { display: flex; align-items: center; gap: var(--space-3); }
    .date { display: flex; align-items: center; gap: var(--space-2); font-size: .8rem; color: var(--muted); }
    .date input { border: 1px solid var(--border); border-radius: 10px; padding: 7px var(--space-3);
                  font-family: inherit; font-size: .82rem; color: var(--text); background: var(--surface); }
    .reset { border: none; background: none; color: var(--danger); font-weight: 600; font-size: .8rem;
             cursor: pointer; font-family: inherit; }
  `]
})
export class MessageFilterComponent {
  readonly filterChange = output<MessageFilters>();
  protected readonly statuses = STATUS_ORDER.map((status) => ({ status, ...statusMeta(status) }));
  protected readonly selected = signal<PaymentMessageStatus | undefined>(undefined);
  protected readonly date = signal<string>('');

  protected pick(status: PaymentMessageStatus | undefined) {
    this.selected.set(status);
    this.emit();
  }
  protected setDate(value: string) {
    this.date.set(value ?? '');
    this.emit();
  }
  protected reset() {
    this.selected.set(undefined);
    this.date.set('');
    this.emit();
  }
  private emit() {
    const filters: MessageFilters = {};
    if (this.selected()) filters.status = this.selected();
    if (this.date()) filters.receivedAfter = new Date(this.date()).toISOString();
    this.filterChange.emit(filters);
  }
}
