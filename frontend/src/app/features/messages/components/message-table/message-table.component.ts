import { Component, input, output, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PaymentMessage } from '../../models/message.model';
import { Page } from '../../models/page.model';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { formatBytes, payloadSize } from '../../../../shared/util/payload.util';

export interface TablePageEvent { pageIndex: number; pageSize: number; }

interface Column { key: string; label: string; align: 'left' | 'right'; sortable: boolean; }

const COLUMNS: Column[] = [
  { key: 'reference',   label: 'Référence',  align: 'left',  sortable: true },
  { key: 'messageId',   label: 'Message ID', align: 'left',  sortable: true },
  { key: 'messageType', label: 'Type',       align: 'left',  sortable: true },
  { key: 'status',      label: 'Statut',     align: 'left',  sortable: true },
  { key: 'retryCount',  label: 'Tentatives', align: 'right', sortable: true },
  { key: 'size',        label: 'Taille',     align: 'right', sortable: false },
  { key: 'receivedAt',  label: 'Reçu le',    align: 'left',  sortable: true },
];

@Component({
  selector: 'app-message-table',
  standalone: true,
  imports: [DatePipe, FormsModule, StatusBadgeComponent, IconComponent],
  template: `
    <div class="card">
      <div class="scroll">
        <table>
          <thead>
            <tr>
              @for (col of columns; track col.key) {
                <th [class.right]="col.align === 'right'" [class.sortable]="col.sortable"
                    [class.active]="sortKey() === col.key"
                    (click)="col.sortable && sortChange.emit(col.key)">
                  {{ col.label }}
                  @if (sortKey() === col.key) { <span class="arrow">{{ sortDir() === 'asc' ? '▲' : '▼' }}</span> }
                </th>
              }
              <th class="right"></th>
            </tr>
          </thead>
          <tbody>
            @for (m of messages(); track m.id) {
              <tr [class.selected]="m.id === selectedId()" (click)="select.emit(m)">
                <td class="mono link-like">{{ m.reference }}</td>
                <td class="mono muted ellipsis">{{ m.messageId }}</td>
                <td class="mono">{{ m.messageType }}</td>
                <td><app-status-badge [status]="m.status" /></td>
                <td class="mono right" [class.warn]="m.retryCount > 0">{{ m.retryCount }}</td>
                <td class="mono right muted">{{ size(m) }}</td>
                <td>
                  <span class="mono">{{ m.receivedAt | date:'HH:mm:ss' }}</span>
                  <span class="faint">{{ m.receivedAt | date:'dd/MM' }}</span>
                </td>
                <td class="right">
                  <a class="open" [href]="'/messages/' + m.id" (click)="$event.preventDefault(); open.emit(m)">Détail</a>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="footer">
        <span class="range">{{ rangeLabel() }}</span>
        <div class="pager">
          <label class="size">
            Lignes
            <select [ngModel]="pageSize()" (ngModelChange)="changeSize($event)">
              @for (s of sizes; track s) { <option [value]="s">{{ s }}</option> }
            </select>
          </label>
          <button class="nav" [disabled]="isFirst()" (click)="go(-1)" aria-label="Page précédente">
            <app-icon name="chevron-left" [size]="14" />
          </button>
          <span class="page mono">{{ pageLabel() }}</span>
          <button class="nav" [disabled]="isLast()" (click)="go(1)" aria-label="Page suivante">
            <app-icon name="chevron-right" [size]="14" />
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); overflow: hidden; }
    .scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; }

    th { text-align: left; padding: 13px 16px; font-size: .655rem; font-weight: 700; letter-spacing: .05em;
         text-transform: uppercase; color: var(--muted-2); border-bottom: 1px solid var(--border);
         white-space: nowrap; background: var(--surface-head); user-select: none; }
    th.sortable { cursor: pointer; }
    th.sortable:hover { color: var(--text-2); }
    th.active { color: var(--text); }
    th.right, td.right { text-align: right; }
    .arrow { color: var(--primary); }

    td { padding: 12px 16px; font-size: .8rem; border-bottom: 1px solid #F0F3F6;
         color: var(--text-2); white-space: nowrap; }
    tbody tr { cursor: pointer; }
    tbody tr:hover { background: #F5F8FC; }
    tbody tr.selected { background: var(--primary-soft); }
    .mono { font-family: var(--font-mono); }
    .muted { color: var(--muted); }
    .faint { color: var(--faint); font-size: .72rem; margin-left: 6px; }
    .warn { color: var(--warning); font-weight: 600; }
    .link-like { color: var(--primary); font-weight: 500; }
    .ellipsis { max-width: 240px; overflow: hidden; text-overflow: ellipsis; }
    .open { font-size: .75rem; font-weight: 600; }

    .footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3);
              padding: 13px 18px; border-top: 1px solid var(--border-soft); background: var(--surface-head);
              flex-wrap: wrap; }
    .range { font-size: .78rem; color: var(--muted-2); }
    .pager { display: flex; align-items: center; gap: 10px; }
    .size { font-size: .75rem; color: var(--muted-2); display: flex; align-items: center; gap: 6px; }
    .size select { border: 1px solid #D3DAE2; border-radius: 8px; padding: 4px 6px;
                   font-size: .75rem; font-family: var(--font-mono); background: var(--surface); }
    .nav { width: 32px; height: 32px; border-radius: 8px; border: 1px solid #D3DAE2; background: var(--surface);
           color: var(--muted); cursor: pointer; display: grid; place-items: center; }
    .nav:hover:not(:disabled) { background: var(--bg); color: var(--primary); }
    .nav:disabled { opacity: .4; cursor: not-allowed; }
    .page { font-size: .78rem; color: var(--text-2); min-width: 56px; text-align: center; }
  `]
})
export class MessageTableComponent {
  readonly messages = input.required<PaymentMessage[]>();
  readonly page = input<Page<PaymentMessage> | null>(null);
  /** tri courant, format Spring `propriété,direction` */
  readonly sort = input<string>('receivedAt,desc');
  readonly selectedId = input<number | null>(null);

  readonly sortChange = output<string>();
  readonly pageChange = output<TablePageEvent>();
  readonly select = output<PaymentMessage>();
  readonly open = output<PaymentMessage>();

  protected readonly columns = COLUMNS;
  protected readonly sizes = [10, 20, 50, 100];

  protected readonly sortKey = computed(() => this.sort().split(',')[0]);
  protected readonly sortDir = computed(() => this.sort().split(',')[1] ?? 'desc');
  protected readonly pageSize = computed(() => this.page()?.size ?? 20);
  protected readonly isFirst = computed(() => this.page()?.first ?? true);
  protected readonly isLast = computed(() => this.page()?.last ?? true);

  protected readonly pageLabel = computed(() => {
    const p = this.page();
    return p ? `${p.number + 1} / ${Math.max(1, p.totalPages)}` : '1 / 1';
  });

  protected readonly rangeLabel = computed(() => {
    const p = this.page();
    if (!p || !p.totalElements) return 'Aucun message';
    const start = p.number * p.size + 1;
    const end = Math.min(start + p.size - 1, p.totalElements);
    return `Affichage ${start}–${end} sur ${p.totalElements} messages`;
  });

  protected size(m: PaymentMessage) { return formatBytes(payloadSize(m.payload)); }

  protected go(delta: number) {
    const p = this.page();
    const index = (p?.number ?? 0) + delta;
    if (index < 0) return;
    this.pageChange.emit({ pageIndex: index, pageSize: this.pageSize() });
  }

  protected changeSize(size: string | number) {
    this.pageChange.emit({ pageIndex: 0, pageSize: Number(size) });
  }
}
