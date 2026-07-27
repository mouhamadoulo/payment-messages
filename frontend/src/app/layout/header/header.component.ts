import { ChangeDetectionStrategy, Component, output, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter, startWith } from 'rxjs';
import { AUTO_REFRESH_MS, MessageService } from '../../features/messages/services/message.service';
import { ThemeService } from '../../core/services/theme.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [IconComponent],
  template: `
    <header class="bar">
      <button class="icon-btn hamburger" (click)="toggleMenu.emit()" aria-label="Menu">
        <app-icon name="menu" />
      </button>

      <div class="titles">
        <h1>{{ title() }}</h1>
        <div class="sub">{{ subtitle() }}</div>
      </div>

      <div class="right">
        <button class="live" [class.paused]="!svc.autoRefresh()" (click)="toggleAutoRefresh()"
                [title]="svc.autoRefresh()
                  ? 'Rafraîchissement automatique toutes les ' + refreshSeconds() + ' s — cliquer pour suspendre'
                  : 'Rafraîchissement automatique suspendu — cliquer pour reprendre'"
                [attr.aria-pressed]="svc.autoRefresh()">
          <span class="dot"></span>
          <span class="live-label">{{ svc.autoRefresh() ? 'Auto' : 'Figé' }}</span>
          <span class="live-time">{{ updatedAt() }}</span>
        </button>
        <button class="icon-btn" (click)="theme.toggle()"
                [title]="isDark() ? 'Passer en mode clair' : 'Passer en mode sombre'"
                [attr.aria-label]="isDark() ? 'Passer en mode clair' : 'Passer en mode sombre'">
          <app-icon [name]="isDark() ? 'sun' : 'moon'" [size]="17" />
        </button>
        <button class="icon-btn" (click)="refresh()" title="Actualiser" aria-label="Actualiser">
          <app-icon name="refresh" [size]="17" />
        </button>
      </div>
    </header>
  `,
  styles: [`
    .bar { flex: none; height: 66px; background: var(--surface); border-bottom: 1px solid var(--border);
           display: flex; align-items: center; gap: var(--space-4); padding: 0 28px; }
    .titles { min-width: 0; }
    .titles h1 { margin: 0; font-size: 1.19rem; font-weight: 600; color: var(--text); letter-spacing: -.01em;
                 white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .sub { font-size: .78rem; color: var(--muted-2); margin-top: 1px;
           white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .right { margin-left: auto; display: flex; align-items: center; gap: 14px; }

    .live { display: flex; align-items: center; gap: 7px; padding: 6px 12px; border-radius: var(--radius-pill);
            background: var(--live-bg); border: 1px solid var(--live-border); cursor: pointer; font: inherit; }
    .live .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); }
    .live-label { font-size: .75rem; font-weight: 600; color: var(--success); }
    .live-time { font-size: .75rem; color: var(--live-text); font-family: var(--font-mono); }
    /* Suspendu : la pastille cesse de battre, elle ne peut plus promettre de fraîcheur. */
    .live.paused { background: var(--surface); border-color: var(--ctl-border); }
    .live.paused .dot { background: var(--muted-2); }
    .live.paused .live-label { color: var(--muted-2); }

    .icon-btn { width: 38px; height: 38px; flex: none; border-radius: var(--radius-ctl);
                border: 1px solid var(--ctl-border); background: var(--surface); color: var(--muted);
                cursor: pointer; display: grid; place-items: center; }
    .icon-btn:hover { background: var(--bg); color: var(--primary); }
    .hamburger { display: none; }

    @media (prefers-reduced-motion: no-preference) {
      .live:not(.paused) .dot { animation: mq-pulse 1.8s infinite; }
    }
    @media (max-width: 900px) {
      .bar { padding: 0 var(--space-4); gap: var(--space-3); }
      .hamburger { display: grid; }
      .live { display: none; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HeaderComponent {
  readonly toggleMenu = output<void>();
  protected readonly svc = inject(MessageService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly isDark = computed(() => this.theme.theme() === 'dark');

  protected readonly title = signal('Payment Messages');
  protected readonly subtitle = signal('');
  protected readonly updatedAt = computed(() => {
    const d = this.svc.lastUpdated();
    return d ? d.toLocaleTimeString('fr-FR') : '—';
  });

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), startWith(null), takeUntilDestroyed())
      .subscribe(() => {
        let r = this.route;
        while (r.firstChild) r = r.firstChild;
        const data = r.snapshot.data as { title?: string; subtitle?: string };
        this.title.set(data.title ?? 'Payment Messages');
        this.subtitle.set(data.subtitle ?? '');
      });
  }

  protected readonly refreshSeconds = computed(() => AUTO_REFRESH_MS / 1000);

  protected refresh() {
    this.svc.refreshAll();
  }

  /**
   * La pastille n'annonce plus un temps réel inexistant : elle pilote le rafraîchissement
   * périodique et indique quand l'écran est volontairement figé.
   */
  protected toggleAutoRefresh() {
    const enabled = !this.svc.autoRefresh();
    this.svc.autoRefresh.set(enabled);
    if (enabled) this.svc.refreshAll(true);
  }
}
