import { Component, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { PaymentMessage } from '../../models/message.model';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-message-card',
  standalone: true,
  imports: [DatePipe, StatusBadgeComponent],
  template: `
    @if (message(); as msg) {
      <div class="card">
        <div class="head">
          <div class="title"><h1>{{ msg.reference }}</h1><app-status-badge [status]="msg.status" /></div>
          <div class="actions">
            <button class="btn primary" (click)="retry.emit()">Réessayer</button>
            <button class="btn ghost" (click)="changeStatus.emit()">Changer statut</button>
            <button class="btn danger" (click)="delete.emit()">Supprimer</button>
          </div>
        </div>

        <dl class="grid">
          <div><dt>Message ID</dt><dd>{{ msg.messageId }}</dd></div>
          <div><dt>Type</dt><dd>{{ msg.messageType }}</dd></div>
          <div><dt>Tentatives</dt><dd>{{ msg.retryCount }}</dd></div>
          <div><dt>Reçu le</dt><dd>{{ msg.receivedAt | date:'dd/MM/yyyy HH:mm' }}</dd></div>
          <div><dt>Traité le</dt><dd>{{ msg.processedAt ? (msg.processedAt | date:'dd/MM/yyyy HH:mm') : '—' }}</dd></div>
          <div><dt>Mis à jour</dt><dd>{{ msg.updatedAt | date:'dd/MM/yyyy HH:mm' }}</dd></div>
        </dl>

        @if (msg.errorMessage) { <div class="error">{{ msg.errorMessage }}</div> }

        <button class="payload-toggle" (click)="showPayload.set(!showPayload())">
          <span class="caret" [class.up]="showPayload()"></span> Payload brut
        </button>
        @if (showPayload()) { <pre class="payload">{{ msg.payload }}</pre> }
      </div>
    }
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-card);
            box-shadow: var(--shadow-card); padding: var(--space-5); }
    .head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4);
            flex-wrap: wrap; margin-bottom: var(--space-5); }
    .title { display: flex; align-items: center; gap: var(--space-3); }
    .title h1 { font-size: 1.4rem; font-weight: 700; margin: 0; }
    .actions { display: flex; gap: var(--space-2); flex-wrap: wrap; }
    .btn { border-radius: 10px; padding: 8px var(--space-3); font-weight: 600; font-size: .82rem;
           cursor: pointer; border: 1px solid var(--border); background: var(--surface); color: var(--text);
           font-family: inherit; }
    .btn.primary { background: var(--primary); color: #fff; border-color: var(--primary); }
    .btn.danger { color: var(--danger); border-color: var(--danger); }
    .btn.ghost:hover { background: var(--bg); }
    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-4); margin: 0 0 var(--space-4); }
    .grid dt { color: var(--muted); font-size: .72rem; text-transform: uppercase; letter-spacing: .04em; }
    .grid dd { margin: 2px 0 0; font-size: .9rem; font-weight: 500; word-break: break-all; }
    .error { background: #FEF2F2; color: var(--danger); border-radius: 10px;
             padding: var(--space-3) var(--space-4); font-size: .88rem; margin-bottom: var(--space-4); }
    .payload-toggle { display: inline-flex; align-items: center; gap: var(--space-2); background: none;
             border: none; color: var(--muted); font-weight: 600; font-size: .82rem; cursor: pointer;
             padding: var(--space-2) 0; font-family: inherit; }
    .caret { width: 0; height: 0; border-left: 4px solid transparent; border-right: 4px solid transparent;
             border-top: 5px solid var(--muted); transition: transform .2s ease; }
    .caret.up { transform: rotate(180deg); }
    .payload { background: var(--bg); padding: var(--space-4); border-radius: 10px; font-family: monospace;
             white-space: pre-wrap; word-break: break-word; font-size: .82rem; margin: var(--space-2) 0 0; }
    @media (max-width: 700px) { .grid { grid-template-columns: 1fr 1fr; } }
    @media (max-width: 480px) { .grid { grid-template-columns: 1fr; } }
  `]
})
export class MessageCardComponent {
  readonly message = input.required<PaymentMessage | null>();
  readonly retry = output<void>();
  readonly changeStatus = output<void>();
  readonly delete = output<void>();
  protected readonly showPayload = signal(false);
}
