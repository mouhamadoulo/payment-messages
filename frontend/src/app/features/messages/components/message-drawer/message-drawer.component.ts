import { Component, computed, inject, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PaymentMessage, PaymentMessageStatus } from '../../models/message.model';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { formatAmount, formatBytes, parsePayload, payloadSize } from '../../../../shared/util/payload.util';

@Component({
  selector: 'app-message-drawer',
  standalone: true,
  imports: [RouterLink, StatusBadgeComponent, IconComponent],
  template: `
    @if (message(); as msg) {
      <div class="scrim" (click)="close.emit()"></div>
      <aside class="drawer">
        <div class="head">
          <div>
            <div class="eyebrow">Détail du message</div>
            <div class="ref">{{ msg.reference }}</div>
          </div>
          <button class="icon-btn" (click)="close.emit()" aria-label="Fermer">
            <app-icon name="close" [size]="15" />
          </button>
        </div>

        <div class="body">
          <div class="summary">
            <app-status-badge [status]="msg.status" />
            <span class="amount mono">{{ amount() }}</span>
          </div>

          @if (msg.errorMessage) {
            <div class="error">{{ msg.errorMessage }}</div>
          }

          <div class="eyebrow section">Métadonnées</div>
          <div class="meta">
            @for (m of meta(); track m.k) {
              <div class="cell">
                <div class="k">{{ m.k }}</div>
                <div class="v mono">{{ m.v }}</div>
              </div>
            }
          </div>

          <div class="eyebrow section">
            Payload MQ brut
            @if (!parsed().valid) { <span class="invalid">· JSON illisible, affiché tel quel</span> }
          </div>
          <pre class="payload">{{ parsed().pretty }}</pre>
        </div>

        <div class="foot">
          <button class="btn primary" [disabled]="!canRetry()" (click)="retry.emit()"
                  title="Seuls les messages FAILED sont rejouables">
            <app-icon name="replay" [size]="15" /> Rejouer
          </button>
          <button class="btn" (click)="changeStatus.emit()">Changer statut</button>
          <a class="btn" [routerLink]="['/messages', msg.id]">Page complète</a>
          <button class="btn icon" (click)="copyPayload(msg)" title="Copier le payload">
            <app-icon name="copy" [size]="16" />
          </button>
          <button class="btn icon danger" (click)="delete.emit()" title="Supprimer">
            <app-icon name="trash" [size]="16" />
          </button>
        </div>
      </aside>
    }
  `,
  styles: [`
    .scrim { position: fixed; inset: 0; background: rgba(20, 32, 45, .32); z-index: 40; }
    .drawer { position: fixed; top: 0; right: 0; bottom: 0; width: min(472px, 100vw); background: var(--surface);
              border-left: 1px solid var(--border); box-shadow: -8px 0 32px rgba(20, 32, 45, .14);
              z-index: 41; display: flex; flex-direction: column; }
    @media (prefers-reduced-motion: no-preference) { .drawer { animation: mq-slide .22s ease; } }

    .head { flex: none; padding: 20px 24px; border-bottom: 1px solid var(--border-soft);
            display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-3); }
    .eyebrow { font-size: .69rem; color: var(--muted-2); font-weight: 600; text-transform: uppercase;
               letter-spacing: .04em; }
    .ref { font-size: 1.06rem; font-weight: 600; font-family: var(--font-mono); color: var(--text);
           margin-top: 5px; word-break: break-all; }

    .body { flex: 1; overflow-y: auto; padding: 22px 24px; }
    .summary { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
    .amount { font-size: 1.19rem; font-weight: 600; color: var(--text); }
    .error { background: var(--danger-soft); color: var(--danger); border-radius: 10px;
             padding: 11px 14px; font-size: .82rem; margin-bottom: 20px; }
    .section { margin-bottom: 11px; }

    .meta { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; background: var(--border-soft);
            border: 1px solid var(--border-soft); border-radius: 10px; overflow: hidden; margin-bottom: 22px; }
    .cell { background: var(--surface); padding: 10px 13px; }
    .k { font-size: .655rem; color: var(--faint); font-weight: 600; text-transform: uppercase; margin-bottom: 3px; }
    .v { font-size: .78rem; color: var(--text); word-break: break-all; }

    .payload { margin: 0; padding: 16px; background: #1C2836; border-radius: 10px;
               font-family: var(--font-mono); font-size: .75rem; line-height: 1.6; color: #C9D6E3;
               overflow-x: auto; white-space: pre; }
    .invalid { color: var(--warning); font-weight: 500; text-transform: none; letter-spacing: 0; }

    .foot { flex: none; padding: 16px 24px; border-top: 1px solid var(--border-soft);
            display: flex; gap: 11px; flex-wrap: wrap; }
    .btn { display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 11px 14px;
           border: 1px solid #D3DAE2; border-radius: var(--radius-ctl); background: var(--surface);
           color: var(--text-2); font-size: .81rem; font-weight: 600; cursor: pointer; }
    .btn:hover:not(:disabled) { background: var(--bg); }
    .btn.primary { flex: 1; border: 0; background: var(--primary); color: #fff; }
    .btn.primary:hover:not(:disabled) { filter: brightness(1.07); background: var(--primary); }
    .btn:disabled { opacity: .45; cursor: not-allowed; }
    .btn.icon { padding: 11px; }
    .btn.icon.danger { color: var(--danger); }
    .btn.icon.danger:hover { background: var(--danger-soft); }
  `]
})
export class MessageDrawerComponent {
  readonly message = input<PaymentMessage | null>(null);
  readonly close = output<void>();
  readonly retry = output<void>();
  readonly changeStatus = output<void>();
  readonly delete = output<void>();

  private readonly notification = inject(NotificationService);
  // motifs purement numériques : la locale par défaut suffit (aucun registerLocaleData requis)
  private readonly datePipe = new DatePipe('en-US');

  protected readonly parsed = computed(() => parsePayload(this.message()?.payload));
  protected readonly amount = computed(() => formatAmount(this.parsed().amount, this.parsed().currency));
  protected readonly canRetry = computed(() => this.message()?.status === PaymentMessageStatus.FAILED);

  protected readonly meta = computed(() => {
    const m = this.message();
    if (!m) return [];
    const p = this.parsed();
    return [
      { k: 'Id', v: String(m.id) },
      { k: 'Message Id', v: m.messageId },
      { k: 'Référence', v: m.reference },
      { k: 'Type', v: m.messageType ?? '—' },
      { k: 'Transaction Id', v: p.transactionId ?? '—' },
      { k: "Date d'exécution", v: p.executionDate ?? '—' },
      { k: 'Débiteur', v: p.debtor ?? '—' },
      { k: 'Créditeur', v: p.creditor ?? '—' },
      { k: 'Reçu le', v: this.datePipe.transform(m.receivedAt, 'dd/MM/yyyy HH:mm:ss') ?? '—' },
      { k: 'Mis à jour', v: this.datePipe.transform(m.updatedAt, 'dd/MM/yyyy HH:mm:ss') ?? '—' },
      { k: 'Tentatives', v: String(m.retryCount ?? 0) },
      { k: 'Taille payload', v: formatBytes(payloadSize(m.payload)) },
    ];
  });

  protected async copyPayload(msg: PaymentMessage) {
    try {
      await navigator.clipboard.writeText(msg.payload ?? '');
      this.notification.success('Payload copié');
    } catch {
      this.notification.error('Copie impossible dans ce navigateur');
    }
  }
}
