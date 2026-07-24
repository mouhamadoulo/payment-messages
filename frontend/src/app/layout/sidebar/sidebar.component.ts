import { Component, output, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MessageService } from '../../features/messages/services/message.service';
import { STATUS_ORDER, statusMeta } from '../../shared/config/status.config';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <div class="brand"><span class="dot"></span><span class="name">Payment Messages</span></div>

    <nav class="nav">
      <a class="item" routerLink="/dashboard" routerLinkActive="active" (click)="navigate.emit()">Dashboard</a>
      <a class="item" routerLink="/messages" routerLinkActive="active"
         [routerLinkActiveOptions]="{ exact: true }" (click)="navigate.emit()">Messages</a>

      <button class="group-toggle" (click)="open.set(!open())">
        <span>Statuts</span><span class="caret" [class.up]="open()"></span>
      </button>
      @if (open()) {
        @for (s of statuses; track s.status) {
          <a class="item sub" routerLink="/messages" [queryParams]="{ status: s.status }" (click)="navigate.emit()">
            <span class="swatch" [style.background]="s.color"></span>
            {{ s.label }}
            <span class="count">{{ count(s.status) }}</span>
          </a>
        }
      }
    </nav>

    <div class="info"><div class="info-title">Payment Messages</div><div class="info-sub">v1.0.0</div></div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; padding: var(--space-4); }
    .brand { display: flex; align-items: center; gap: var(--space-3); font-weight: 700; font-size: 1.05rem;
             padding: var(--space-2) var(--space-2) var(--space-5); }
    .brand .dot { width: 20px; height: 20px; border-radius: 6px; background: var(--primary); flex: none; }
    .nav { display: flex; flex-direction: column; gap: 2px; }
    .item { display: flex; align-items: center; gap: var(--space-3); padding: 10px var(--space-3);
            border-radius: var(--radius-pill); color: var(--muted); text-decoration: none;
            font-weight: 500; font-size: .9rem; }
    .item:hover { background: var(--bg); }
    .item.active { background: var(--primary-soft); color: var(--primary); }
    .item.sub { font-size: .82rem; padding-left: var(--space-4); }
    .swatch { width: 8px; height: 8px; border-radius: 3px; flex: none; }
    .count { margin-left: auto; font-size: .72rem; color: var(--muted); }
    .group-toggle { display: flex; align-items: center; justify-content: space-between; width: 100%;
            background: none; border: none; cursor: pointer; color: var(--muted); font-weight: 600;
            font-size: .72rem; text-transform: uppercase; letter-spacing: .04em;
            padding: var(--space-4) var(--space-3) var(--space-2); }
    .caret { width: 0; height: 0; border-left: 4px solid transparent; border-right: 4px solid transparent;
             border-top: 5px solid var(--muted); transition: transform .2s ease; }
    .caret.up { transform: rotate(180deg); }
    .info { margin-top: auto; background: var(--bg); border-radius: var(--radius-card);
            padding: var(--space-3) var(--space-4); }
    .info-title { font-weight: 600; font-size: .82rem; }
    .info-sub { color: var(--muted); font-size: .72rem; }
  `]
})
export class SidebarComponent {
  readonly navigate = output<void>();
  private readonly svc = inject(MessageService);
  protected readonly open = signal(true);
  protected readonly statuses = STATUS_ORDER.map((status) => ({ status, ...statusMeta(status) }));
  protected count(status: string): number {
    return (this.svc.stats() as Record<string, number>)?.[status] ?? 0;
  }
}
