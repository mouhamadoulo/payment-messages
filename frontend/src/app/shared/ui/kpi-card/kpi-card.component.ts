import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="card" [style.--accent]="accent()">
      <span class="bar"></span>
      <div class="body">
        <span class="label">{{ label() }}</span>
        <div class="value">{{ value() | number }}</div>
        @if (share() !== undefined) { <div class="share">{{ share() }} % du total</div> }
      </div>
    </div>
  `,
  styles: [`
    .card { position: relative; display: flex; gap: var(--space-4); background: var(--surface);
            border: 1px solid var(--border); border-radius: var(--radius-card);
            box-shadow: var(--shadow-card); padding: var(--space-4); overflow: hidden; }
    .bar { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: var(--accent); }
    .body { padding-left: var(--space-2); }
    .label { color: var(--muted); font-size: .82rem; font-weight: 500; }
    .value { font-size: 2rem; font-weight: 700; margin-top: var(--space-2); }
    .share { color: var(--muted); font-size: .78rem; margin-top: 2px; }
  `]
})
export class KpiCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<number>();
  readonly accent = input<string>('var(--primary)');
  readonly share = input<number | undefined>(undefined);
}
