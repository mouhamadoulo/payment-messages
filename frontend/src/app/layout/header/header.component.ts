import { Component, output, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter, startWith } from 'rxjs';
import { MessageService } from '../../features/messages/services/message.service';
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
        <div class="live" [title]="'Dernier chargement de données'">
          <span class="dot"></span>
          <span class="live-label">Données</span>
          <span class="live-time">{{ updatedAt() }}</span>
        </div>
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
            background: #F0F7F2; border: 1px solid #D9ECDF; }
    .live .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--success); }
    .live-label { font-size: .75rem; font-weight: 600; color: var(--success); }
    .live-time { font-size: .75rem; color: #5F8A70; font-family: var(--font-mono); }

    .icon-btn { width: 38px; height: 38px; flex: none; border-radius: var(--radius-ctl);
                border: 1px solid #D3DAE2; background: var(--surface); color: var(--muted);
                cursor: pointer; display: grid; place-items: center; }
    .icon-btn:hover { background: var(--bg); color: var(--primary); }
    .hamburger { display: none; }

    @media (prefers-reduced-motion: no-preference) { .live .dot { animation: mq-pulse 1.8s infinite; } }
    @media (max-width: 900px) {
      .bar { padding: 0 var(--space-4); gap: var(--space-3); }
      .hamburger { display: grid; }
      .live { display: none; }
    }
  `]
})
export class HeaderComponent {
  readonly toggleMenu = output<void>();
  protected readonly svc = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

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

  protected refresh() {
    this.svc.refreshAll();
  }
}
