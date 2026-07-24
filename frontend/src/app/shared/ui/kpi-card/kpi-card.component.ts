import { Component, input, signal, effect } from '@angular/core';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  template: `
    <div class="card">
      <div class="label">{{ label() }}</div>
      <div class="row">
        <span class="value">{{ display() || value() }}</span>
        @if (unit()) { <span class="unit">{{ unit() }}</span> }
      </div>
      <div class="note" [style.color]="noteColor()">
        {{ note() }} <span class="hint">{{ hint() }}</span>
      </div>
    </div>
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-card);
            padding: 12px 13px; display: flex; flex-direction: column; gap: 5px; height: 100%; min-width: 0; }
    .label { font-size: .64rem; color: var(--muted-2); font-weight: 600; letter-spacing: .03em;
             text-transform: uppercase; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .row { display: flex; align-items: baseline; gap: 4px; }
    .value { font-size: 1.32rem; font-weight: 600; font-family: var(--font-mono); color: var(--text);
             letter-spacing: -.02em; }
    .unit { font-size: .72rem; color: var(--muted-2); }
    .note { font-size: .7rem; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .hint { color: var(--faint); font-weight: 400; }
  `]
})
export class KpiCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly unit = input<string>('');
  readonly note = input<string>('');
  readonly hint = input<string>('');
  readonly noteColor = input<string>('var(--primary)');

  /** valeur affichée : compteur animé 0 → cible, sinon la chaîne brute */
  protected readonly display = signal('');
  private prev = 0;

  constructor() {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    effect((onCleanup) => {
      const raw = this.value();
      const parsed = this.parse(raw);
      if (!parsed || reduced) { this.display.set(raw); this.prev = parsed?.target ?? 0; return; }

      const from = this.prev;
      const { target, decimals } = parsed;
      const dur = 650;
      let raf = 0;
      let t0 = 0;
      const step = (t: number) => {
        if (!t0) t0 = t;
        const p = Math.min(1, (t - t0) / dur);
        const eased = 1 - Math.pow(1 - p, 3);
        this.display.set(this.fmt(from + (target - from) * eased, decimals));
        if (p < 1) { raf = requestAnimationFrame(step); }
        else { this.display.set(raw); this.prev = target; }
      };
      raf = requestAnimationFrame(step);
      onCleanup(() => cancelAnimationFrame(raf));
    });
  }

  private parse(raw: string): { target: number; decimals: number } | null {
    if (!raw) return null;
    const cleaned = raw.replace(/\s/g, '').replace(',', '.');
    if (!/^-?\d*\.?\d+$/.test(cleaned)) return null;
    const target = parseFloat(cleaned);
    if (Number.isNaN(target)) return null;
    const decimals = raw.includes(',') ? (raw.split(',')[1]?.length ?? 0) : 0;
    return { target, decimals };
  }

  private fmt(v: number, decimals: number): string {
    return v.toLocaleString('fr-FR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
  }
}
