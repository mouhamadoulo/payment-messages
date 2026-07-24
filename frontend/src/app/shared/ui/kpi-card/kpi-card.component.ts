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
}
