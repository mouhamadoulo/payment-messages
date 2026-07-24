import { Component, OnInit, inject, computed } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MessageService } from '../../services/message.service';
import { STATUS_ORDER, statusMeta } from '../../../../shared/config/status.config';
import { KpiCardComponent } from '../../../../shared/ui/kpi-card/kpi-card.component';
import { ApexDonutComponent } from '../../../../shared/ui/apex/apex-donut.component';
import { ApexGaugeComponent } from '../../../../shared/ui/apex/apex-gauge.component';
import { ApexBarComponent } from '../../../../shared/ui/apex/apex-bar.component';
import { hourHistogram } from '../../../../shared/ui/histogram/hour-histogram';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, KpiCardComponent, StatusBadgeComponent,
    ApexDonutComponent, ApexGaugeComponent, ApexBarComponent,
  ],
  template: `
    <h1 class="title">Dashboard</h1>

    <section class="kpis fade">
      <app-kpi-card label="Total messages" [value]="total()" accent="var(--primary)" />
      <app-kpi-card label="Traités" [value]="count('PROCESSED')" accent="var(--success)" [share]="share('PROCESSED')" />
      <app-kpi-card label="Échoués" [value]="count('FAILED')" accent="var(--danger)" [share]="share('FAILED')" />
      <app-kpi-card label="En attente" [value]="pending()" accent="var(--warning)" [share]="pendingShare()" />
    </section>

    <section class="grid fade">
      <div class="card">
        <div class="card-head"><h2>Distribution par statut</h2></div>
        <app-apex-donut [series]="donut().series" [labels]="donut().labels" [colors]="donut().colors" />
      </div>
      <div class="rail">
        <div class="card">
          <div class="card-head"><h2>Taux de succès</h2></div>
          <app-apex-gauge [value]="successRate()" label="messages traités" color="#22C55E" />
        </div>
        <div class="card actions">
          <div class="card-head"><h2>À traiter</h2></div>
          <div class="big-num">{{ actionable() | number }}</div>
          <p class="muted">messages en échec, rejouables</p>
          <button class="btn" (click)="retryFailed()">Rejouer les échecs</button>
        </div>
      </div>
    </section>

    <section class="card fade">
      <div class="card-head"><h2>Activité par heure</h2></div>
      <app-apex-bar [data]="hourData()" [categories]="hourCategories" name="Messages" color="#2F6BFF" />
    </section>

    <section class="card fade">
      <div class="card-head">
        <h2>Messages récents</h2>
        <a class="link" (click)="go('/messages')">Voir tous</a>
      </div>
      @if (!recent().length) { <p class="muted pad">Aucun message</p> }
      <table class="table">
        <thead><tr><th>Référence</th><th>Message ID</th><th>Statut</th><th>Reçu le</th></tr></thead>
        <tbody>
          @for (m of recent(); track m.id) {
            <tr (click)="go('/messages/' + m.id)">
              <td>{{ m.reference }}</td>
              <td class="muted">{{ m.messageId }}</td>
              <td><app-status-badge [status]="m.status" /></td>
              <td class="muted">{{ m.receivedAt | date:'dd/MM HH:mm' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
  styles: [`
    .title { font-size: 1.6rem; font-weight: 700; margin: 0 0 var(--space-5); }
    .kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-4); margin-bottom: var(--space-5); }
    .grid { display: grid; grid-template-columns: 2fr 1fr; gap: var(--space-4); margin-bottom: var(--space-5); align-items: start; }
    .rail { display: flex; flex-direction: column; gap: var(--space-4); }
    .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-card);
            box-shadow: var(--shadow-card); padding: var(--space-5); margin-bottom: var(--space-5); }
    .grid .card, .rail .card, .kpis { margin-bottom: 0; }
    .card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-4); }
    .card-head h2 { font-size: 1rem; font-weight: 600; margin: 0; }
    .big-num { font-size: 2.4rem; font-weight: 700; }
    .muted { color: var(--muted); font-size: .82rem; }
    .pad { padding: var(--space-4) 0; }
    .btn { margin-top: var(--space-4); display: inline-flex; align-items: center; background: var(--primary);
           color: #fff; border: none; border-radius: 10px; padding: 10px var(--space-4); font-weight: 600;
           cursor: pointer; font-family: inherit; }
    .link { color: var(--primary); font-size: .82rem; cursor: pointer; }
    .table { width: 100%; border-collapse: collapse; }
    .table th { text-align: left; font-size: .72rem; text-transform: uppercase; letter-spacing: .04em;
                color: var(--muted); padding: var(--space-2) var(--space-3); }
    .table td { padding: var(--space-3); border-top: 1px solid var(--border); font-size: .88rem; }
    .table tbody tr { cursor: pointer; }
    .table tbody tr:hover { background: var(--bg); }
    @media (prefers-reduced-motion: no-preference) {
      .fade { animation: fade-up .4s ease both; }
      .grid.fade { animation-delay: .05s; }
      section.card.fade { animation-delay: .1s; }
    }
    @keyframes fade-up { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }
    @media (max-width: 1100px) { .kpis { grid-template-columns: repeat(2, 1fr); } .grid { grid-template-columns: 1fr; } }
  `]
})
export class DashboardPage implements OnInit {
  protected readonly svc = inject(MessageService);
  private readonly router = inject(Router);
  protected readonly hourCategories = Array.from({ length: 24 }, (_, i) => `${i}h`);

  ngOnInit() {
    this.svc.loadStats();
    this.svc.loadMessages(undefined, 0, 6);
    this.svc.loadActivitySample(200);
  }

  private stats = () => (this.svc.stats() as Record<string, number>) ?? {};
  protected count(s: string) { return this.stats()[s] ?? 0; }
  protected total = computed(() => Object.values(this.stats()).reduce((a, b) => a + b, 0));
  protected share(s: string) { const t = this.total(); return t ? Math.round((this.count(s) / t) * 100) : 0; }
  protected pending = computed(() => this.count('RECEIVED'));
  protected pendingShare = computed(() => { const t = this.total(); return t ? Math.round((this.pending() / t) * 100) : 0; });
  protected actionable = computed(() => this.count('FAILED'));
  protected successRate = computed(() => { const t = this.total(); return t ? Math.round((this.count('PROCESSED') / t) * 100) : 0; });
  protected recent = computed(() => this.svc.messages());
  protected hourData = computed(() => hourHistogram(this.svc.activitySample()));
  protected donut = computed(() => {
    const items = STATUS_ORDER
      .map((s) => ({ label: statusMeta(s).label, color: statusMeta(s).color, value: this.count(s) }))
      .filter((i) => i.value > 0);
    return { series: items.map((i) => i.value), labels: items.map((i) => i.label), colors: items.map((i) => i.color) };
  });

  protected go(path: string) { this.router.navigateByUrl(path); }
  protected retryFailed() { this.svc.batchRetryFailed(); }
}
