import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SimulationTask } from '../../models/simulation.model';

/**
 * Suivi de l'envoi en cours.
 *
 * Les trois compteurs portent sur la **publication**, pas sur le traitement : « publiés »
 * signifie que le broker a accepté le message. Le libellé le dit et le pied de carte renvoie
 * vers la liste, seul endroit où se lit le sort applicatif des messages.
 */
@Component({
  selector: 'app-send-progress',
  standalone: true,
  template: `
    <div class="card">
      <div class="card-head">
        <h3>Suivi de l'envoi</h3>
        <span class="state" [style.color]="state().color">
          @if (running()) { <span class="spinner" aria-hidden="true"></span> }
          {{ state().label }}
        </span>
      </div>

      <div class="track" role="progressbar" [attr.aria-valuenow]="progress()"
           aria-valuemin="0" aria-valuemax="100" aria-label="Avancement de l'envoi">
        <div class="fill" [style.width.%]="progress()"></div>
      </div>
      <p class="legend mono">{{ legend() }}</p>

      <div class="tiles">
        <div class="tile">
          <div class="tile-value mono">{{ task()?.sent ?? 0 }}</div>
          <div class="tile-label">Traités</div>
        </div>
        <div class="tile ok">
          <div class="tile-value mono">{{ task()?.published ?? 0 }}</div>
          <div class="tile-label">Publiés</div>
        </div>
        <div class="tile ko">
          <div class="tile-value mono">{{ task()?.failed ?? 0 }}</div>
          <div class="tile-label">Échecs</div>
        </div>
      </div>

      @if (task()?.error; as error) {
        <p class="error">{{ error }}</p>
      }

      <p class="note">
        Ces compteurs mesurent la publication sur la file. Le résultat du traitement —
        message persisté ou rejeté — se lit dans l'onglet Messages.
      </p>
    </div>
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); padding: 20px 22px; }
    .card-head { display: flex; align-items: center; justify-content: space-between;
                 gap: var(--space-3); margin-bottom: 16px; }
    .card-head h3 { margin: 0; font-size: .875rem; font-weight: 600; color: var(--text); }
    .mono { font-family: var(--font-mono); }

    .state { display: flex; align-items: center; gap: 7px; font-size: .75rem; font-weight: 600; }
    .spinner { width: 13px; height: 13px; border: 2px solid var(--border); border-top-color: var(--primary);
               border-radius: 50%; display: inline-block; }
    @media (prefers-reduced-motion: no-preference) {
      .spinner { animation: mq-spin .7s linear infinite; }
    }
    @keyframes mq-spin { to { transform: rotate(360deg); } }

    .track { height: 10px; background: var(--border-soft); border-radius: 6px;
             overflow: hidden; margin-bottom: 8px; }
    .fill { height: 100%; border-radius: 6px; transition: width .15s linear;
            background: linear-gradient(90deg, #4A86D8, var(--primary)); }
    .legend { margin: 0 0 18px; font-size: .74rem; color: var(--muted-2); }

    .tiles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
    .tile { text-align: center; padding: 14px 8px; border-radius: 10px;
            background: var(--surface-alt); border: 1px solid var(--border-soft); }
    .tile.ok { background: var(--success-soft); border-color: var(--success-soft); }
    .tile.ko { background: var(--danger-soft); border-color: var(--danger-soft); }
    .tile-value { font-size: 1.4rem; font-weight: 600; color: var(--text); }
    .tile.ok .tile-value { color: var(--success); }
    .tile.ko .tile-value { color: var(--danger); }
    .tile-label { font-size: .68rem; font-weight: 600; text-transform: uppercase;
                  color: var(--muted-2); margin-top: 2px; }

    .error { margin: 14px 0 0; font-size: .76rem; color: var(--danger); }
    .note { margin: 14px 0 0; font-size: .72rem; color: var(--muted-2); line-height: 1.5; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SendProgressComponent {
  readonly task = input<SimulationTask | null>(null);
  readonly running = input(false);
  readonly progress = input(0);

  protected readonly state = computed(() => {
    if (this.running()) return { label: 'Envoi en cours…', color: 'var(--primary)' };

    const task = this.task();
    if (!task) return { label: 'En attente', color: 'var(--muted-2)' };
    if (task.state === 'FAILED') return { label: 'Interrompu', color: 'var(--danger)' };
    if (task.failed > 0) return { label: 'Terminé avec erreurs', color: 'var(--warning)' };
    return { label: 'Terminé ✓', color: 'var(--success)' };
  });

  protected readonly legend = computed(() => {
    const task = this.task();
    if (!task) return 'Aucun envoi lancé';
    return `${task.sent} / ${task.total} traités · ${task.destination}`;
  });
}
