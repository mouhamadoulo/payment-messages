import { AfterViewInit, Component, OnInit, inject, signal, computed, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MessageService, DEFAULT_SORT } from '../../services/message.service';
import { MessageFilterComponent } from '../../components/message-filter/message-filter.component';
import { MessageTableComponent, TablePageEvent } from '../../components/message-table/message-table.component';
import { MessageDrawerComponent } from '../../components/message-drawer/message-drawer.component';
import { MessageFilters, PaymentMessage, PaymentMessageStatus } from '../../models/message.model';
import { payloadSize } from '../../../../shared/util/payload.util';

@Component({
  selector: 'app-message-list',
  standalone: true,
  imports: [MessageFilterComponent, MessageTableComponent, MessageDrawerComponent],
  template: `
    <div class="page">
      <app-message-filter
        [counts]="counts()" [total]="svc.total()" [types]="types()"
        (filterChange)="onFilter($event)"
        (typeChange)="typeFilter.set($event)"
        (searchChange)="svc.searchTerm.set($event)"
        (exportCsv)="exportCsv()" />

      @if (svc.loading()) {
        <div class="state">Chargement…</div>
      } @else if (!rows().length) {
        <div class="state">Aucun message ne correspond aux filtres</div>
      } @else {
        <app-message-table
          [messages]="rows()" [page]="svc.currentPage()" [sort]="sort()"
          [selectedId]="svc.currentMessage()?.id ?? null"
          (sortChange)="onSort($event)"
          (pageChange)="onPageChange($event)"
          (select)="openDrawer($event)"
          (open)="goToDetail($event)" />
      }

      <app-message-drawer
        [message]="svc.currentMessage()"
        (close)="svc.clearCurrent()"
        (retry)="onRetry()"
        (changeStatus)="onChangeStatus()"
        (delete)="onDelete()" />
    </div>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 16px; }
    @media (prefers-reduced-motion: no-preference) { .page { animation: mq-up .3s ease; } }
    .state { text-align: center; color: var(--muted-2); font-size: .85rem;
             background: var(--surface); border: 1px solid var(--border);
             border-radius: var(--radius-card); padding: var(--space-6); }
  `]
})
export class MessageListPage implements OnInit, AfterViewInit {
  protected readonly svc = inject(MessageService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly filterCmp = viewChild(MessageFilterComponent);

  protected readonly sort = signal(DEFAULT_SORT);
  protected readonly typeFilter = signal('');
  private filters: MessageFilters = {};
  private pageIndex = 0;
  private pageSize = 20;

  protected readonly counts = computed(() => (this.svc.stats() as Record<string, number>) ?? {});

  protected readonly types = computed(() =>
    [...new Set(this.svc.messages().map((m) => m.messageType).filter(Boolean))].sort());

  /** page serveur, puis filtres client (recherche plein texte + type) */
  protected readonly rows = computed(() => {
    const type = this.typeFilter();
    const list = this.svc.filteredMessages();
    return type ? list.filter((m) => m.messageType === type) : list;
  });

  ngOnInit() {
    this.svc.clearCurrent();
    this.route.queryParamMap.subscribe((q) => {
      const status = q.get('status') as PaymentMessageStatus | null;
      this.filters = status ? { status } : {};
      this.pageIndex = 0;
      this.filterCmp()?.setStatus(status ?? undefined);
      this.load();
    });
    this.svc.loadStats();
  }

  /** la première émission des query params précède l'initialisation de la vue */
  ngAfterViewInit() {
    this.filterCmp()?.setStatus(this.filters.status);
  }

  private load() {
    this.svc.loadMessages(this.filters, this.pageIndex, this.pageSize, this.sort());
  }

  protected onFilter(filters: MessageFilters) {
    this.filters = filters;
    this.pageIndex = 0;
    this.load();
  }

  protected onPageChange(e: TablePageEvent) {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  protected onSort(key: string) {
    const [currentKey, currentDir] = this.sort().split(',');
    const dir = currentKey === key && currentDir === 'desc' ? 'asc' : 'desc';
    this.sort.set(`${key},${dir}`);
    this.pageIndex = 0;
    this.load();
  }

  protected openDrawer(m: PaymentMessage) { this.svc.loadMessage(m.id); }
  protected goToDetail(m: PaymentMessage) { this.router.navigate(['/messages', m.id]); }

  protected onRetry() {
    const msg = this.svc.currentMessage();
    if (msg) this.svc.retry(msg.id);
  }

  protected async onChangeStatus() {
    const msg = this.svc.currentMessage();
    if (!msg) return;
    const { SelectStatusDialog } = await import('../message-detail/select-status.dialog');
    this.dialog.open(SelectStatusDialog).afterClosed()
      .subscribe((status: PaymentMessageStatus) => {
        if (status) this.svc.updateStatus(msg.id, status);
      });
  }

  protected async onDelete() {
    const msg = this.svc.currentMessage();
    if (!msg) return;
    const { ConfirmDeleteDialog } = await import('../message-detail/confirm-delete.dialog');
    this.dialog.open(ConfirmDeleteDialog).afterClosed()
      .subscribe((confirmed: boolean) => {
        if (confirmed) this.svc.deleteMessage(msg.id, false);
      });
  }

  protected exportCsv() {
    const rows = this.rows();
    if (!rows.length) return;
    const header = ['id', 'reference', 'messageId', 'messageType', 'status', 'retryCount',
                    'payloadBytes', 'receivedAt', 'updatedAt', 'errorMessage'];
    const escape = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const csv = [
      header.join(';'),
      ...rows.map((m) => [
        m.id, m.reference, m.messageId, m.messageType, m.status, m.retryCount,
        payloadSize(m.payload), m.receivedAt, m.updatedAt, m.errorMessage,
      ].map(escape).join(';')),
    ].join('\r\n');

    const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `messages-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
