import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PaymentMessageStatus, MessageFilters } from '../../models/message.model';
import { STATUS_ORDER, statusMeta } from '../../../../shared/config/status.config';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';

@Component({
  selector: 'app-message-filter',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="toolbar">
      <div class="row">
        <div class="search">
          <app-icon name="search" [size]="16" />
          <input type="search" [ngModel]="search()" (ngModelChange)="onSearch($event)"
                 placeholder="Filtrer la page courante (référence, message ID, type, payload…)" />
        </div>

        <select class="select" [ngModel]="type()" (ngModelChange)="onType($event)"
                title="Type de message (page courante)">
          <option value="">Tous les types</option>
          @for (t of types(); track t) { <option [value]="t">{{ t }}</option> }
        </select>

        <label class="date">
          <span>Reçu après</span>
          <input type="date" [ngModel]="date()" (ngModelChange)="onDate($event)" />
        </label>

        <button class="btn" (click)="exportCsv.emit()" title="Exporter la page courante">
          <app-icon name="download" [size]="15" /> Export CSV
        </button>

        @if (status() || date() || type() || search()) {
          <button class="icon-btn" (click)="reset()" title="Réinitialiser les filtres">
            <app-icon name="close" [size]="15" />
          </button>
        }
      </div>

      <div class="chips">
        <button class="chip" [class.active]="!status()"
                [style.--c]="'var(--primary)'" [style.--cbg]="'var(--primary-soft)'"
                (click)="pick(undefined)">ALL <span>{{ total() }}</span></button>
        @for (s of statuses; track s.status) {
          <button class="chip" [class.active]="status() === s.status"
                  [style.--c]="s.color" [style.--cbg]="s.bg" (click)="pick(s.status)">
            {{ s.label }} <span>{{ counts()[s.status] ?? 0 }}</span>
          </button>
        }
      </div>
    </div>
  `,
  styles: [`
    .toolbar { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-card);
               padding: 16px 18px; display: flex; flex-direction: column; gap: 14px; }
    .row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

    .search { position: relative; flex: 1; min-width: 240px; display: flex; align-items: center; }
    .search app-icon { position: absolute; left: 12px; color: var(--faint); pointer-events: none; }
    .search input { width: 100%; padding: 10px 12px 10px 36px; border: 1px solid #D3DAE2;
                    border-radius: var(--radius-ctl); font-size: .84rem; color: var(--text);
                    background: var(--surface-head); }

    .select { padding: 10px 12px; border: 1px solid #D3DAE2; border-radius: var(--radius-ctl);
              font-size: .81rem; color: var(--text-2); background: var(--surface); cursor: pointer;
              font-family: var(--font-mono); }
    .date { display: flex; align-items: center; gap: var(--space-2); font-size: .78rem; color: var(--muted-2); }
    .date input { border: 1px solid #D3DAE2; border-radius: var(--radius-ctl); padding: 9px 12px;
                  font-size: .81rem; color: var(--text); background: var(--surface); }

    .btn { display: flex; align-items: center; gap: 7px; padding: 9px 14px; border: 1px solid #D3DAE2;
           border-radius: var(--radius-ctl); background: var(--surface); color: var(--text-2);
           font-size: .81rem; font-weight: 500; cursor: pointer; }
    .btn:hover { background: var(--bg); color: var(--primary); }
    .icon-btn { width: 38px; height: 38px; flex: none; border: 1px solid #D3DAE2;
                border-radius: var(--radius-ctl); background: var(--surface); color: var(--muted);
                cursor: pointer; display: grid; place-items: center; }
    .icon-btn:hover { background: var(--bg); color: var(--danger); }

    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .chip { display: flex; align-items: center; gap: 7px; padding: 6px 12px; border-radius: var(--radius-pill);
            font-size: .72rem; font-weight: 600; cursor: pointer; border: 1px solid var(--border);
            background: var(--surface); color: var(--muted-2); font-family: var(--font-mono); }
    .chip:hover { background: var(--bg); }
    .chip span { font-weight: 500; opacity: .7; }
    .chip.active { border-color: var(--c); background: var(--cbg); color: var(--c); }
  `]
})
export class MessageFilterComponent {
  /** compteurs globaux issus de /messages/stats */
  readonly counts = input<Record<string, number>>({});
  readonly total = input<number>(0);
  /** types présents dans la page courante */
  readonly types = input<string[]>([]);

  readonly filterChange = output<MessageFilters>();
  readonly typeChange = output<string>();
  readonly searchChange = output<string>();
  readonly exportCsv = output<void>();

  protected readonly statuses = STATUS_ORDER.map((status) => ({ status, ...statusMeta(status) }));
  protected readonly status = signal<PaymentMessageStatus | undefined>(undefined);
  protected readonly date = signal('');
  protected readonly type = signal('');
  protected readonly search = signal('');

  /** synchronise les chips avec le filtre reçu en query param, sans ré-émettre */
  setStatus(status: PaymentMessageStatus | undefined) {
    this.status.set(status);
  }

  protected pick(status: PaymentMessageStatus | undefined) {
    this.status.set(status);
    this.emit();
  }
  protected onDate(value: string) {
    this.date.set(value ?? '');
    this.emit();
  }
  protected onType(value: string) {
    this.type.set(value ?? '');
    this.typeChange.emit(this.type());
  }
  protected onSearch(value: string) {
    this.search.set(value ?? '');
    this.searchChange.emit(this.search());
  }
  protected reset() {
    this.status.set(undefined);
    this.date.set('');
    this.type.set('');
    this.search.set('');
    this.typeChange.emit('');
    this.searchChange.emit('');
    this.emit();
  }

  private emit() {
    const filters: MessageFilters = {};
    const status = this.status();
    if (status) filters.status = status;
    if (this.date()) filters.receivedAfter = new Date(this.date()).toISOString();
    this.filterChange.emit(filters);
  }
}
