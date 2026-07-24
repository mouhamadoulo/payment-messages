import { Component, output, inject, computed, isDevMode } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { MessageService } from '../../features/messages/services/message.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { relativeTime } from '../../shared/util/payload.util';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, DecimalPipe, IconComponent],
  template: `
    <div class="brand">
      <div class="logo">MQ</div>
      <div class="brand-text">
        <div class="brand-name">Payment Messages</div>
        <div class="brand-sub">Supervision paiements</div>
      </div>
    </div>

    <div class="section">Navigation</div>
    <nav class="nav">
      <a class="item" routerLink="/dashboard" routerLinkActive="active" (click)="navigate.emit()">
        <app-icon name="dashboard" /> Tableau de bord
      </a>
      <a class="item" routerLink="/messages" routerLinkActive="active" (click)="navigate.emit()">
        <app-icon name="messages" /> Messages
      </a>
      <span class="item disabled" title="Disponible dans une prochaine version">
        <app-icon name="play" /> Simulation d'envoi
        <span class="soon">bientôt</span>
      </span>
    </nav>

    <div class="foot">
      <div class="flux">
        <div class="flux-head">
          <span class="dot"></span>
          <span>Flux MQ consommé</span>
        </div>
        <div class="flux-line">{{ svc.total() | number:'1.0-0' }} messages en base</div>
        <div class="flux-line">dernier · {{ lastReceived() }}</div>
      </div>
      <div class="env">
        <span class="env-label">Environnement</span>
        <span class="env-badge" [class.prod]="!isDev">{{ isDev ? 'DEV' : 'PROD' }}</span>
      </div>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; padding: 18px 14px; }

    .brand { display: flex; align-items: center; gap: 11px; padding: 6px 8px 20px; }
    .logo { width: 38px; height: 38px; flex: none; border-radius: 10px; background: var(--primary);
            display: grid; place-items: center; color: #fff; font-family: var(--font-mono);
            font-weight: 600; font-size: .94rem; letter-spacing: -.02em; }
    .brand-text { line-height: 1.15; min-width: 0; }
    .brand-name { font-size: .94rem; font-weight: 600; color: var(--text); }
    .brand-sub { font-size: .69rem; color: var(--muted-2); font-weight: 500; }

    .section { font-size: .655rem; font-weight: 600; letter-spacing: .08em; color: var(--faint);
               text-transform: uppercase; padding: 4px 10px 8px; }

    .nav { display: flex; flex-direction: column; gap: 3px; }
    .item { display: flex; align-items: center; gap: 11px; padding: 10px 12px; border-radius: var(--radius-ctl);
            font-size: .875rem; font-weight: 500; cursor: pointer; border-left: 3px solid transparent;
            color: var(--muted); text-decoration: none; }
    .item:hover { background: #F0F4F9; color: var(--text-2); }
    .item.active { background: var(--primary-soft); color: var(--primary-dark); border-left-color: var(--primary); }
    .item.disabled { color: var(--faint); cursor: not-allowed; }
    .item.disabled:hover { background: transparent; color: var(--faint); }
    .soon { margin-left: auto; font-size: .62rem; font-weight: 600; text-transform: uppercase;
            letter-spacing: .04em; color: var(--muted-2); background: var(--border-soft);
            padding: 2px 7px; border-radius: 6px; }

    .foot { margin-top: auto; display: flex; flex-direction: column; gap: 10px;
            padding-top: 16px; border-top: 1px solid var(--border-soft); }
    .flux { background: var(--surface-alt); border: 1px solid var(--border-soft);
            border-radius: 10px; padding: 11px 12px; }
    .flux-head { display: flex; align-items: center; gap: 7px; margin-bottom: 6px;
                 font-size: .75rem; font-weight: 600; color: var(--text-2); }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--success); flex: none; }
    .flux-line { font-size: .69rem; color: var(--muted-2); font-family: var(--font-mono); }

    .env { display: flex; align-items: center; gap: 8px; padding: 2px 4px; }
    .env-label { font-size: .69rem; color: var(--muted-2); }
    .env-badge { font-size: .69rem; font-weight: 600; font-family: var(--font-mono); padding: 2px 8px;
                 border-radius: 5px; background: var(--success-soft); color: var(--success); }
    .env-badge.prod { background: var(--danger-soft); color: var(--danger); }

    @media (prefers-reduced-motion: no-preference) {
      .dot { animation: mq-pulse 1.8s infinite; }
    }
  `]
})
export class SidebarComponent {
  readonly navigate = output<void>();
  protected readonly svc = inject(MessageService);
  protected readonly isDev = isDevMode();
  protected readonly lastReceived = computed(() => {
    const latest = this.svc.activitySample()[0] ?? this.svc.messages()[0];
    return latest ? relativeTime(latest.receivedAt) : '—';
  });
}
