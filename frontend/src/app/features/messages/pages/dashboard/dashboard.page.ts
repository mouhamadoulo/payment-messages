import { Component, OnInit, inject, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from '../../services/message.service';
import { PaymentMessage, PaymentMessageStatus } from '../../models/message.model';
import { STATUS_ORDER, statusMeta } from '../../../../shared/config/status.config';
import { KpiCardComponent } from '../../../../shared/ui/kpi-card/kpi-card.component';
import { hourHistogram } from '../../../../shared/ui/histogram/hour-histogram';
import { relativeTime } from '../../../../shared/util/payload.util';

const SAMPLE_SIZE = 200;

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, KpiCardComponent],
  template: `
    <div class="page">
      <section class="kpis">
        <app-kpi-card label="Volume 24 h" [value]="volume24h()" unit="msgs"
                      note="consommés" hint="depuis IBM MQ" noteColor="var(--primary)" />
        <app-kpi-card label="Total en base" [value]="fmt(total())" unit="msgs"
                      note="tous statuts" hint="" noteColor="var(--muted-2)" />
        <app-kpi-card label="Taux d'erreur" [value]="errorRate()" unit="%"
                      [note]="errorCount() + ' en erreur'" hint="FAILED + DEAD_LETTER"
                      [noteColor]="errorCount() ? 'var(--danger)' : 'var(--success)'" />
        <app-kpi-card label="En attente" [value]="fmt(count('RECEIVED'))" unit="RECEIVED"
                      note="à traiter" hint="" noteColor="var(--primary)" />
        <app-kpi-card label="Rejouables" [value]="fmt(count('FAILED'))" unit="msgs"
                      note="via /retry" hint="" noteColor="var(--warning)" />
      </section>

      <section class="grid main">
        <div class="card">
          <div class="card-head">
            <h3>Volume par heure de réception</h3>
            <span class="meta">{{ sampleSize() }} derniers messages</span>
          </div>
          @if (sampleSize()) {
            <div class="bars">
              @for (bar of volumeBars(); track bar.hour) {
                <div class="bar" [style.height.%]="bar.pct"
                     [title]="bar.hour + 'h · ' + bar.count + ' message(s)'"></div>
              }
            </div>
            <div class="axis">
              <span>00h</span><span>06h</span><span>12h</span><span>18h</span><span>23h</span>
            </div>
          } @else {
            <p class="empty">Aucun message à représenter</p>
          }
        </div>

        <div class="card">
          <div class="card-head"><h3>Répartition par statut</h3></div>
          <div class="stack">
            @for (s of statusDist(); track s.label) {
              <div>
                <div class="line">
                  <span class="mono" [style.color]="s.color">{{ s.label }}</span>
                  <span class="mono muted">{{ s.count }}</span>
                </div>
                <div class="track"><div class="fill" [style.width.%]="s.pct" [style.background]="s.color"></div></div>
              </div>
            }
          </div>
        </div>
      </section>

      <section class="grid split">
        <div class="card">
          <div class="card-head">
            <h3>Répartition par type de message</h3>
            <span class="meta">{{ sampleSize() }} derniers messages</span>
          </div>
          @if (typeDist().length) {
            <div class="rows">
              @for (t of typeDist(); track t.label) {
                <div class="row">
                  <span class="row-label mono">{{ t.label }}</span>
                  <div class="track"><div class="fill grad" [style.width.%]="t.pct"></div></div>
                  <span class="row-count mono">{{ t.count }}</span>
                </div>
              }
            </div>
          } @else {
            <p class="empty">Aucun message</p>
          }
        </div>

        <div class="card">
          <div class="card-head">
            <h3>Tentatives de rejeu</h3>
            <span class="meta">retryCount</span>
          </div>
          <div class="tiles">
            @for (b of retryBuckets(); track b.label) {
              <div class="tile">
                <div class="tile-value mono">{{ b.count }}</div>
                <div class="tile-label">{{ b.label }}</div>
              </div>
            }
          </div>
          <button class="btn" [disabled]="!count('FAILED')" (click)="retryFailed()">
            Rejouer les {{ count('FAILED') }} message(s) en échec
          </button>
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <h3>Alertes récentes</h3>
          <a routerLink="/messages" class="link">Voir tous les messages →</a>
        </div>
        @if (alerts().length) {
          <div class="alerts">
            @for (a of alerts(); track a.id) {
              <a class="alert" [routerLink]="['/messages', a.id]"
                 [style.background]="a.bg" [style.border-color]="a.border">
                <span class="level" [style.background]="a.color">{{ a.level }}</span>
                <span class="alert-msg">{{ a.message }}</span>
                <span class="alert-time mono">{{ a.time }}</span>
              </a>
            }
          </div>
        } @else {
          <p class="empty">Aucun message en échec sur les {{ sampleSize() }} derniers reçus</p>
        }
      </section>
    </div>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 18px; }
    @media (prefers-reduced-motion: no-preference) { .page { animation: mq-up .3s ease; } }

    .kpis { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; }
    .grid { display: grid; gap: 18px; }
    .grid.main { grid-template-columns: 1.85fr 1fr; }
    .grid.split { grid-template-columns: 1.15fr 1fr; }

    .card { background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); padding: 18px 20px; }
    .card-head { display: flex; align-items: center; justify-content: space-between;
                 gap: var(--space-3); margin-bottom: 16px; }
    .card-head h3 { margin: 0; font-size: .875rem; font-weight: 600; color: var(--text); }
    .meta { font-size: .75rem; color: var(--muted-2); }
    .link { font-size: .78rem; font-weight: 500; }
    .empty { color: var(--muted-2); font-size: .82rem; margin: 0; padding: var(--space-4) 0; }
    .mono { font-family: var(--font-mono); }
    .muted { color: var(--muted-2); }

    .bars { display: flex; align-items: flex-end; gap: 4px; height: 150px; }
    .bar { flex: 1; min-height: 4px; border-radius: 3px 3px 0 0;
           background: linear-gradient(180deg, #6FA0E0, #2B5FB0); }
    .axis { display: flex; justify-content: space-between; margin-top: 8px;
            font-size: .69rem; color: var(--faint); font-family: var(--font-mono); }

    .stack { display: flex; flex-direction: column; gap: 11px; }
    .line { display: flex; justify-content: space-between; margin-bottom: 4px; font-size: .72rem; font-weight: 600; }
    .track { height: 6px; background: var(--border-soft); border-radius: 4px; overflow: hidden; }
    .fill { height: 100%; border-radius: 4px; }
    .fill.grad { background: linear-gradient(90deg, #4A86D8, #2B5FB0); }

    .rows { display: flex; flex-direction: column; gap: 13px; }
    .row { display: flex; align-items: center; gap: 12px; }
    .row .track { flex: 1; height: 8px; border-radius: 5px; }
    .row-label { width: 150px; flex: none; font-size: .78rem; color: var(--text-2);
                 overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .row-count { width: 42px; text-align: right; font-size: .78rem; font-weight: 600; color: var(--text); }

    .tiles { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 16px; }
    .tile { text-align: center; padding: 14px 6px; background: var(--surface-alt);
            border: 1px solid var(--border-soft); border-radius: 10px; }
    .tile-value { font-size: 1.35rem; font-weight: 600; color: var(--text); }
    .tile-label { font-size: .69rem; color: var(--muted-2); font-weight: 600;
                  text-transform: uppercase; margin-top: 2px; }

    .btn { width: 100%; padding: 11px; border: 0; border-radius: 10px; background: var(--primary);
           color: #fff; font-size: .85rem; font-weight: 600; cursor: pointer; }
    .btn:hover:not(:disabled) { filter: brightness(1.07); }
    .btn:disabled { opacity: .45; cursor: not-allowed; }

    .alerts { display: flex; flex-direction: column; gap: 9px; }
    .alert { display: flex; align-items: center; gap: 13px; padding: 11px 14px;
             border-radius: var(--radius-ctl); border: 1px solid transparent; color: inherit; }
    .alert:hover { filter: brightness(.98); }
    .level { font-size: .655rem; font-weight: 700; letter-spacing: .04em; padding: 3px 9px;
             border-radius: 6px; color: #fff; flex: none; }
    .alert-msg { flex: 1; font-size: .8rem; color: var(--text-2); overflow: hidden;
                 text-overflow: ellipsis; white-space: nowrap; }
    .alert-time { font-size: .72rem; color: var(--muted-2); flex: none; }

    @media (max-width: 1400px) { .kpis { grid-template-columns: repeat(3, 1fr); } }
    @media (max-width: 1100px) {
      .grid.main, .grid.split { grid-template-columns: 1fr; }
    }
    @media (max-width: 700px) {
      .kpis { grid-template-columns: repeat(2, 1fr); }
      .tiles { grid-template-columns: repeat(2, 1fr); }
      .row-label { width: 100px; }
    }
  `]
})
export class DashboardPage implements OnInit {
  protected readonly svc = inject(MessageService);

  ngOnInit() {
    this.svc.loadStats();
    this.svc.loadActivitySample(SAMPLE_SIZE);
    this.svc.loadVolume24h();
  }

  private readonly statsMap = computed(() => (this.svc.stats() as Record<string, number>) ?? {});
  protected readonly total = computed(() => this.svc.total());
  protected count(s: string) { return this.statsMap()[s] ?? 0; }
  protected fmt(n: number) { return n.toLocaleString('fr-FR'); }

  protected readonly sampleSize = computed(() => this.svc.activitySample().length);

  protected readonly volume24h = computed(() => {
    const v = this.svc.volume24h();
    return v == null ? '—' : this.fmt(v);
  });

  protected readonly errorCount = computed(() => this.count('FAILED') + this.count('DEAD_LETTER'));
  protected readonly errorRate = computed(() => {
    const t = this.total();
    if (!t) return '0,0';
    return ((this.errorCount() / t) * 100).toFixed(1).replace('.', ',');
  });

  protected readonly volumeBars = computed(() => {
    const buckets = hourHistogram(this.svc.activitySample());
    const max = Math.max(1, ...buckets);
    return buckets.map((count, hour) => ({ hour, count, pct: Math.max(3, (count / max) * 100) }));
  });

  protected readonly statusDist = computed(() => {
    const max = Math.max(1, ...STATUS_ORDER.map((s) => this.count(s)));
    return STATUS_ORDER.map((s) => {
      const meta = statusMeta(s);
      const count = this.count(s);
      return { label: meta.label, color: meta.color, count, pct: (count / max) * 100 };
    });
  });

  protected readonly typeDist = computed(() => {
    const counts = new Map<string, number>();
    for (const m of this.svc.activitySample()) {
      const key = m.messageType || 'INCONNU';
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    const max = Math.max(1, ...counts.values());
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1])
      .map(([label, count]) => ({ label, count, pct: (count / max) * 100 }));
  });

  protected readonly retryBuckets = computed(() => {
    const sample = this.svc.activitySample();
    const at = (n: number) => sample.filter((m) => (m.retryCount ?? 0) === n).length;
    return [
      { label: 'aucune', count: at(0) },
      { label: '1 essai', count: at(1) },
      { label: '2 essais', count: at(2) },
      { label: '3+', count: sample.filter((m) => (m.retryCount ?? 0) >= 3).length },
    ];
  });

  protected readonly alerts = computed(() => {
    const now = Date.now();
    return this.svc.activitySample()
      .filter((m) => m.status === PaymentMessageStatus.FAILED || m.status === PaymentMessageStatus.DEAD_LETTER)
      .slice(0, 5)
      .map((m) => this.toAlert(m, now));
  });

  private toAlert(m: PaymentMessage, now: number) {
    const dead = m.status === PaymentMessageStatus.DEAD_LETTER;
    return {
      id: m.id,
      level: dead ? 'CRITIQUE' : 'ERREUR',
      color: dead ? 'var(--critical)' : 'var(--danger)',
      bg: dead ? 'var(--critical-soft)' : '#FCF1F1',
      border: dead ? '#F2DDE4' : '#F3DEDE',
      message: m.errorMessage
        ?? `${m.reference} — ${dead ? 'routé vers la Dead Letter Queue' : 'en échec de traitement'}`,
      time: relativeTime(m.updatedAt ?? m.receivedAt, now),
    };
  }

  protected retryFailed() { this.svc.batchRetryFailed(); }
}
