import {
  AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal, computed, viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MessageService, DEFAULT_SORT } from '../../services/message.service';
import { MessageFilterComponent } from '../../components/message-filter/message-filter.component';
import { MessageTableComponent, TablePageEvent } from '../../components/message-table/message-table.component';
import { MessageDrawerComponent } from '../../components/message-drawer/message-drawer.component';
import { MessageFilters, PaymentMessage, PaymentMessageStatus } from '../../models/message.model';
import { messagePayloadSize } from '../../../../shared/util/payload.util';

@Component({
  selector: 'app-message-list',
  standalone: true,
  imports: [MessageFilterComponent, MessageTableComponent, MessageDrawerComponent],
  template: `
    <div class="page">
      <app-message-filter
        [counts]="counts()" [total]="svc.total()" [types]="svc.messageTypes()"
        (filterChange)="onFilter($event)"
        (exportCsv)="exportCsv()" />

      @if (svc.loading()) {
        <div class="card sk-table" aria-label="Chargement des messages" role="status">
          <div class="sk-row sk-head">
            @for (c of skCols; track $index) { <span class="skeleton"></span> }
          </div>
          @for (r of skRows; track $index) {
            <div class="sk-row">
              @for (c of skCols; track $index) { <span class="skeleton"></span> }
            </div>
          }
        </div>
      } @else if (!svc.messages().length) {
        <div class="state">Aucun message ne correspond aux filtres</div>
      } @else {
        <app-message-table
          [messages]="svc.messages()" [page]="svc.currentPage()" [sort]="sort()"
          [selectedId]="svc.currentMessage()?.id ?? null"
          [flashId]="svc.changedId()"
          (sortChange)="onSort($event)"
          (pageChange)="onPageChange($event)"
          (select)="openDrawer($event)"
          (open)="goToDetail($event)" />
      }

      <!--
        Le tiroir n'est monté qu'à la première ouverture : son gabarit, ses styles et l'analyse
        du payload ne sont plus dans le lot de la page de liste alors que la plupart des visites
        n'ouvrent aucun message.
      -->
      @defer (when svc.currentMessage() !== null) {
        <app-message-drawer
          [message]="svc.currentMessage()"
          (close)="svc.clearCurrent()"
          (retry)="onRetry()"
          (changeStatus)="onChangeStatus()"
          (delete)="onDelete()" />
      }
    </div>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 16px; }
    @media (prefers-reduced-motion: no-preference) { .page { animation: mq-up .3s ease; } }
    .state { text-align: center; color: var(--muted-2); font-size: .85rem;
             background: var(--surface); border: 1px solid var(--border);
             border-radius: var(--radius-card); padding: var(--space-6); }

    .sk-table { background: var(--surface); border: 1px solid var(--border);
                border-radius: var(--radius-card); padding: 0; overflow: hidden; }
    .sk-row { display: grid; grid-template-columns: 1.4fr 1.6fr 1fr .9fr .7fr .7fr 1fr .7fr;
              gap: 16px; align-items: center; padding: 14px 16px;
              border-bottom: 1px solid var(--border-soft); }
    .sk-row:last-child { border-bottom: 0; }
    .sk-head { background: var(--surface-head); }
    .sk-row .skeleton { height: 12px; }
    .sk-head .skeleton { height: 9px; opacity: .7; }
    @media (max-width: 700px) {
      .sk-row { grid-template-columns: 1.4fr 1fr .7fr; }
      .sk-row .skeleton:nth-child(n+4) { display: none; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MessageListPage implements OnInit, AfterViewInit {
  protected readonly svc = inject(MessageService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly filterCmp = viewChild(MessageFilterComponent);

  protected readonly sort = signal(DEFAULT_SORT);
  protected readonly skRows = Array(8);
  protected readonly skCols = Array(8);
  private filters: MessageFilters = {};
  private pageIndex = 0;
  private pageSize = 20;

  /** compteurs par statut, calculés par le serveur sous les autres filtres actifs */
  protected readonly counts = computed(() => (this.svc.stats() as Record<string, number>) ?? {});

  ngOnInit() {
    this.svc.clearCurrent();
    // `takeUntilDestroyed` explicite : la route complète bien son flux, mais l'abonnement
    // s'aligne sur celui du bandeau plutôt que de dépendre de ce détail. Hors contexte
    // d'injection (ngOnInit), le `DestroyRef` doit être fourni.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((q) => {
      const status = q.get('status') as PaymentMessageStatus | null;
      this.filters = status ? { status } : {};
      this.pageIndex = 0;
      this.filterCmp()?.setStatus(status ?? undefined);
      this.load();
    });
    this.svc.loadMessageTypes();
  }

  /** la première émission des query params précède l'initialisation de la vue */
  ngAfterViewInit() {
    this.filterCmp()?.setStatus(this.filters.status);
  }

  /**
   * Liste et compteurs partent du même prédicat : les pastilles annoncent ce que donnerait
   * un clic dessus, au lieu de compter toute la table pendant que le tableau n'affiche
   * qu'un sous-ensemble filtré.
   */
  private load() {
    this.svc.loadMessages(this.filters, this.pageIndex, this.pageSize, this.sort());
    this.svc.loadStats(this.filters);
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
    this.dialog.open(SelectStatusDialog, { data: { current: msg.status } }).afterClosed()
      .subscribe((result?: { status: PaymentMessageStatus; reason?: string }) => {
        if (result) this.svc.updateStatus(msg.id, result.status, result.reason);
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
    const rows = this.svc.messages();
    if (!rows.length) return;
    const header = ['id', 'reference', 'messageId', 'messageType', 'status', 'retryCount',
                    'payloadBytes', 'receivedAt', 'updatedAt', 'errorMessage'];
    const escape = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const csv = [
      header.join(';'),
      ...rows.map((m) => [
        m.id, m.reference, m.messageId, m.messageType, m.status, m.retryCount,
        messagePayloadSize(m), m.receivedAt, m.updatedAt, m.errorMessage,
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
