import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MessageService } from '../../services/message.service';
import { MessageFilterComponent } from '../../components/message-filter/message-filter.component';
import { MessageTableComponent } from '../../components/message-table/message-table.component';
import { MessageFilters, PaymentMessageStatus } from '../../models/message.model';

@Component({
  selector: 'app-message-list',
  standalone: true,
  imports: [MessageFilterComponent, MessageTableComponent],
  template: `
    <div class="head">
      <h1>Messages</h1>
      @if (showRetry()) {
        <button class="btn" (click)="batchRetry()">Rejouer les échecs</button>
      }
    </div>

    <app-message-filter (filterChange)="onFilter($event)" />

    @if (svc.loading()) {
      <div class="state">Chargement…</div>
    } @else if (!svc.filteredMessages().length) {
      <div class="state"><p>Aucun message trouvé</p></div>
    } @else {
      <app-message-table [messages]="svc.filteredMessages()" [page]="svc.currentPage()"
                         (pageChange)="onPageChange($event)" />
    }
  `,
  styles: [`
    .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-4); }
    h1 { font-size: 1.6rem; font-weight: 700; margin: 0; }
    .btn { background: var(--primary); color: #fff; border: none; border-radius: 10px;
           padding: 10px var(--space-4); font-weight: 600; cursor: pointer; font-family: inherit; }
    .state { text-align: center; color: var(--muted); padding: var(--space-6); }
  `]
})
export class MessageListPage implements OnInit {
  protected readonly svc = inject(MessageService);
  private readonly route = inject(ActivatedRoute);
  protected currentFilters: MessageFilters = {};

  ngOnInit() {
    this.route.queryParamMap.subscribe((q) => {
      const status = q.get('status') as PaymentMessageStatus | null;
      this.currentFilters = status ? { status } : {};
      this.svc.loadMessages(this.currentFilters);
    });
  }
  protected onFilter(filters: MessageFilters) { this.currentFilters = filters; this.svc.loadMessages(filters); }
  protected onPageChange(e: PageEvent) { this.svc.loadMessages(this.currentFilters, e.pageIndex, e.pageSize); }
  protected showRetry() {
    return this.svc.filteredMessages().some(
      (m) => m.status === PaymentMessageStatus.FAILED || m.status === PaymentMessageStatus.RETRY_PENDING);
  }
  protected batchRetry() { this.svc.batchRetryFailed(); }
}
