import { Component, input } from '@angular/core';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  template: `
    <div class="card">
      <div class="label">{{ label() }}</div>
      <div class="row">
        <span class="value">{{ value() }}</span>
        @if (unit()) { <span class="unit">{{ unit() }}</span> }
      </div>
      <div class="note" [style.color]="noteColor()">
        {{ note() }} <span class="hint">{{ hint() }}</span>
      </div>
    </div>
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-card);
            padding: 15px 17px; display: flex; flex-direction: column; gap: 7px; height: 100%; }
    .label { font-size: .69rem; color: var(--muted-2); font-weight: 600; letter-spacing: .03em;
             text-transform: uppercase; }
    .row { display: flex; align-items: baseline; gap: 5px; }
    .value { font-size: 1.69rem; font-weight: 600; font-family: var(--font-mono); color: var(--text);
             letter-spacing: -.02em; }
    .unit { font-size: .78rem; color: var(--muted-2); }
    .note { font-size: .75rem; font-weight: 600; }
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
}
