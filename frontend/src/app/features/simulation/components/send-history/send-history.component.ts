import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AutoAnimateDirective } from '../../../../shared/ui/auto-animate.directive';
import { SendHistoryEntry } from '../../models/simulation.model';

/**
 * Historique des envois de cette session. Il n'est pas persisté — le serveur ne retient que
 * les quelques derniers envois, le temps d'en suivre l'avancement — et l'écran le dit plutôt
 * que de laisser croire à un journal.
 */
@Component({
  selector: 'app-send-history',
  standalone: true,
  imports: [AutoAnimateDirective],
  template: `
    <div class="card">
      <div class="card-head">
        <h3>Envois de cette session</h3>
        <span class="meta">non conservé</span>
      </div>

      @if (entries().length) {
        <div class="list" appAutoAnimate>
          @for (e of entries(); track e.taskId) {
            <div class="entry">
              <span class="dot" [class]="e.outcome"></span>
              <div class="entry-text">
                <div class="entry-title">{{ e.title }}</div>
                <div class="entry-meta mono">{{ e.destination }} · {{ e.time }}</div>
              </div>
              <span class="result mono" [class]="e.outcome">{{ e.published }} / {{ e.total }}</span>
            </div>
          }
        </div>
      } @else {
        <p class="empty">Aucun envoi depuis l'ouverture de l'écran</p>
      }
    </div>
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); padding: 20px 22px; }
    .card-head { display: flex; align-items: center; justify-content: space-between;
                 gap: var(--space-3); margin-bottom: 14px; }
    .card-head h3 { margin: 0; font-size: .875rem; font-weight: 600; color: var(--text); }
    .meta { font-size: .72rem; color: var(--muted-2); }
    .mono { font-family: var(--font-mono); }

    .list { display: flex; flex-direction: column; gap: 9px; max-height: 300px; overflow-y: auto; }
    .entry { display: flex; align-items: center; gap: 12px; padding: 11px 13px;
             background: var(--surface-alt); border: 1px solid var(--border-soft);
             border-radius: var(--radius-ctl); }
    .dot { width: 8px; height: 8px; flex: none; border-radius: 50%; }
    .dot.ok, .result.ok { color: var(--success); }
    .dot.ok { background: var(--success); }
    .dot.partial, .result.partial { color: var(--warning); }
    .dot.partial { background: var(--warning); }
    .dot.ko, .result.ko { color: var(--danger); }
    .dot.ko { background: var(--danger); }

    .entry-text { flex: 1; min-width: 0; }
    .entry-title { font-size: .81rem; font-weight: 600; color: var(--text);
                   overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .entry-meta { font-size: .72rem; color: var(--muted-2); margin-top: 1px;
                  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .result { font-size: .75rem; font-weight: 600; flex: none; }

    .empty { color: var(--muted-2); font-size: .8rem; margin: 0; padding: var(--space-3) 0; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SendHistoryComponent {
  readonly entries = input<SendHistoryEntry[]>([]);
}
