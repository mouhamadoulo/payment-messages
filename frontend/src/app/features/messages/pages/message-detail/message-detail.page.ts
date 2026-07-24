import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MessageService } from '../../services/message.service';
import { MessageCardComponent } from '../../components/message-card/message-card.component';
import { PaymentMessageStatus } from '../../models/message.model';

@Component({
  selector: 'app-message-detail',
  standalone: true,
  imports: [RouterLink, MessageCardComponent],
  template: `
    <div class="detail">
      <a routerLink="/messages" class="back-link">← Retour</a>

      @if (messageService.detailLoading()) {
        <div class="state">Chargement…</div>
      } @else if (messageService.error(); as err) {
        <div class="state error">{{ err }}</div>
      } @else {
        <app-message-card
          [message]="messageService.currentMessage()"
          (retry)="onRetry()"
          (changeStatus)="onChangeStatus()"
          (delete)="onDelete()" />
      }
    </div>
  `,
  styles: [`
    .detail { max-width: 960px; margin: 0 auto; }
    .back-link { display: inline-block; margin-bottom: var(--space-4); color: var(--muted);
                 text-decoration: none; font-weight: 600; font-size: .82rem; }
    .back-link:hover { color: var(--text); }
    .state { text-align: center; color: var(--muted); padding: var(--space-6); }
    .state.error { color: var(--danger); }
  `]
})
export class MessageDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  protected readonly messageService = inject(MessageService);

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) this.messageService.loadMessage(id);
  }

  protected onRetry() {
    const msg = this.messageService.currentMessage();
    if (msg) this.messageService.retry(msg.id);
  }

  protected async onChangeStatus() {
    const { SelectStatusDialog } = await import('./select-status.dialog');
    const ref = this.dialog.open(SelectStatusDialog);
    ref.afterClosed().subscribe((status: PaymentMessageStatus) => {
      if (status) {
        const msg = this.messageService.currentMessage();
        if (msg) this.messageService.updateStatus(msg.id, status);
      }
    });
  }

  protected async onDelete() {
    const { ConfirmDeleteDialog } = await import('./confirm-delete.dialog');
    const ref = this.dialog.open(ConfirmDeleteDialog);
    ref.afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed) {
        const msg = this.messageService.currentMessage();
        if (msg) this.messageService.deleteMessage(msg.id);
      }
    });
  }
}
