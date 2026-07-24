import { Component, computed, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PaymentMessage, PaymentMessageStatus } from '../../models/message.model';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { formatAmount, formatBytes, parsePayload, payloadSize } from '../../../../shared/util/payload.util';

@Component({
  selector: 'app-message-card',
  standalone: true,
  imports: [DatePipe, StatusBadgeComponent, IconComponent],
  template: `
    @if (message(); as msg) {
      <div class="card">
        <div class="head">
          <div class="title">
            <div>
              <div class="eyebrow">Message de paiement</div>
              <h1 class="mono">{{ msg.reference }}</h1>
            </div>
            <app-status-badge [status]="msg.status" />
          </div>
          <div class="actions">
            <button class="btn primary" [disabled]="!canRetry(msg)"
                    title="Seuls les messages FAILED sont rejouables" (click)="retry.emit()">
              <app-icon name="replay" [size]="15" /> Rejouer
            </button>
            <button class="btn" (click)="changeStatus.emit()">Changer statut</button>
            <button class="btn danger" (click)="delete.emit()">
              <app-icon name="trash" [size]="15" /> Supprimer
            </button>
          </div>
        </div>

        <div class="amount-row">
          <span class="amount mono">{{ amount() }}</span>
          @if (parsed().transactionId) { <span class="tx mono">{{ parsed().transactionId }}</span> }
        </div>

        <dl class="grid">
          <div><dt>Message ID</dt><dd class="mono">{{ msg.messageId }}</dd></div>
          <div><dt>Type</dt><dd class="mono">{{ msg.messageType }}</dd></div>
          <div><dt>Tentatives</dt><dd class="mono">{{ msg.retryCount }}</dd></div>
          <div><dt>Débiteur</dt><dd>{{ parsed().debtor ?? '—' }}</dd></div>
          <div><dt>Créditeur</dt><dd>{{ parsed().creditor ?? '—' }}</dd></div>
          <div><dt>Date d'exécution</dt><dd class="mono">{{ parsed().executionDate ?? '—' }}</dd></div>
          <div><dt>Reçu le</dt><dd class="mono">{{ msg.receivedAt | date:'dd/MM/yyyy HH:mm:ss' }}</dd></div>
          <div><dt>Mis à jour</dt><dd class="mono">{{ msg.updatedAt | date:'dd/MM/yyyy HH:mm:ss' }}</dd></div>
          <div><dt>Taille payload</dt><dd class="mono">{{ size(msg) }}</dd></div>
        </dl>

        @if (msg.errorMessage) { <div class="error">{{ msg.errorMessage }}</div> }

        <button class="payload-toggle" (click)="showPayload.set(!showPayload())">
          <span class="caret" [class.up]="showPayload()"></span> Payload MQ brut
        </button>
        @if (showPayload()) { <pre class="payload">{{ parsed().pretty }}</pre> }
      </div>
    }
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); padding: 20px 22px; }
    .head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4);
            flex-wrap: wrap; margin-bottom: var(--space-4); }
    .title { display: flex; align-items: center; gap: var(--space-3); }
    .eyebrow { font-size: .69rem; color: var(--muted-2); font-weight: 600; text-transform: uppercase;
               letter-spacing: .04em; }
    .title h1 { font-size: 1.19rem; font-weight: 600; margin: 4px 0 0; word-break: break-all; }
    .actions { display: flex; gap: var(--space-2); flex-wrap: wrap; }

    .btn { display: inline-flex; align-items: center; gap: 7px; border-radius: var(--radius-ctl);
           padding: 9px 14px; font-weight: 600; font-size: .81rem; cursor: pointer;
           border: 1px solid #D3DAE2; background: var(--surface); color: var(--text-2); }
    .btn:hover:not(:disabled) { background: var(--bg); }
    .btn.primary { background: var(--primary); color: #fff; border-color: var(--primary); }
    .btn.primary:hover:not(:disabled) { filter: brightness(1.07); background: var(--primary); }
    .btn.danger { color: var(--danger); }
    .btn.danger:hover { background: var(--danger-soft); }
    .btn:disabled { opacity: .45; cursor: not-allowed; }

    .amount-row { display: flex; align-items: baseline; gap: 12px; margin-bottom: var(--space-5); }
    .amount { font-size: 1.5rem; font-weight: 600; color: var(--text); }
    .tx { font-size: .78rem; color: var(--muted-2); }

    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1px; margin: 0 0 var(--space-4);
            background: var(--border-soft); border: 1px solid var(--border-soft);
            border-radius: 10px; overflow: hidden; }
    .grid > div { background: var(--surface); padding: 10px 13px; }
    .grid dt { color: var(--faint); font-size: .655rem; text-transform: uppercase; letter-spacing: .04em;
               font-weight: 600; }
    .grid dd { margin: 3px 0 0; font-size: .8rem; color: var(--text); word-break: break-all; }
    .mono { font-family: var(--font-mono); }

    .error { background: var(--danger-soft); color: var(--danger); border-radius: 10px;
             padding: 11px 14px; font-size: .82rem; margin-bottom: var(--space-4); }

    .payload-toggle { display: inline-flex; align-items: center; gap: var(--space-2); background: none;
             border: none; color: var(--muted); font-weight: 600; font-size: .78rem; cursor: pointer;
             padding: var(--space-2) 0; }
    .caret { width: 0; height: 0; border-left: 4px solid transparent; border-right: 4px solid transparent;
             border-top: 5px solid var(--muted); transition: transform .2s ease; }
    .caret.up { transform: rotate(180deg); }
    .payload { background: #1C2836; color: #C9D6E3; padding: 16px; border-radius: 10px;
             font-family: var(--font-mono); white-space: pre; overflow-x: auto;
             font-size: .75rem; line-height: 1.6; margin: var(--space-2) 0 0; }

    @media (max-width: 900px) { .grid { grid-template-columns: 1fr 1fr; } }
    @media (max-width: 560px) { .grid { grid-template-columns: 1fr; } }
  `]
})
export class MessageCardComponent {
  readonly message = input.required<PaymentMessage | null>();
  readonly retry = output<void>();
  readonly changeStatus = output<void>();
  readonly delete = output<void>();

  protected readonly showPayload = signal(false);
  protected readonly parsed = computed(() => parsePayload(this.message()?.payload));
  protected readonly amount = computed(() => formatAmount(this.parsed().amount, this.parsed().currency));

  protected canRetry(msg: PaymentMessage) { return msg.status === PaymentMessageStatus.FAILED; }
  protected size(msg: PaymentMessage) { return formatBytes(payloadSize(msg.payload)); }
}
