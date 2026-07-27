import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SimulationService } from '../../services/simulation.service';
import { SimulationSendRequest } from '../../models/simulation.model';
import { SendFormComponent } from '../../components/send-form/send-form.component';
import { SendProgressComponent } from '../../components/send-progress/send-progress.component';
import { SendHistoryComponent } from '../../components/send-history/send-history.component';

/**
 * Simulation d'envoi : dépôt de messages de test sur la file d'entrée, dans le rôle que
 * tiennent les applications de back-office du flux réel.
 *
 * Rien n'est court-circuité — pas d'écriture directe en base : les messages repartent par le
 * broker et repassent par le consommateur, avec la même validation et les mêmes rejets. C'est
 * ce qui rend l'écran utile, et c'est aussi pourquoi il se coupe par configuration
 * (`app.simulation.enabled`) là où la file porte un vrai flux.
 */
@Component({
  selector: 'app-simulation',
  standalone: true,
  imports: [RouterLink, SendFormComponent, SendProgressComponent, SendHistoryComponent],
  template: `
    <div class="page">
      @if (disabled()) {
        <div class="banner ko">
          <strong>Simulation désactivée sur cet environnement.</strong>
          Le dépôt de messages de test est coupé par configuration
          (<code>app.simulation.enabled</code>).
        </div>
      }

      @if (svc.error(); as error) {
        <div class="banner err">{{ error }}</div>
      }

      <div class="grid">
        <app-send-form [config]="svc.config()" [queue]="svc.queue()" [running]="svc.running()"
                       (send)="send($event)" />

        <div class="side">
          <app-send-progress [task]="svc.task()" [running]="svc.running()"
                             [progress]="svc.progress()" />

          <!-- Sous la ligne de flottaison sur la plupart des écrans : son rendu attend. -->
          @defer (on viewport) {
            <app-send-history [entries]="svc.history()" />
          } @placeholder {
            <div class="sk-block"></div>
          }

          <div class="card tips">
            <h3>Ce que fait cet écran</h3>
            <ul>
              <li>Le payload est publié <strong>tel quel</strong> sur la file : il repasse par
                  le consommateur, sa validation et ses rejets.</li>
              <li>Un message accepté apparaît en <code>RECEIVED</code> dans
                  <a routerLink="/messages">la liste des messages</a> ; un payload invalide y
                  apparaît en <code>FAILED</code>, avec son texte brut.</li>
              <li>La destination est la file d'entrée configurée côté serveur : elle est
                  affichée, jamais choisie.</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page { display: flex; flex-direction: column; gap: 18px; }
    @media (prefers-reduced-motion: no-preference) { .page { animation: mq-up .3s ease; } }

    .grid { display: grid; grid-template-columns: 1.15fr 1fr; gap: 18px; align-items: start; }
    .side { display: flex; flex-direction: column; gap: 18px; }
    /* Réserve la hauteur du bloc différé : son arrivée ne doit pas décaler la page. */
    .sk-block { min-height: 180px; background: var(--surface); border: 1px solid var(--border);
                border-radius: var(--radius-card); }

    .banner { padding: 12px 16px; border-radius: var(--radius-ctl); font-size: .82rem;
              line-height: 1.5; border: 1px solid transparent; }
    .banner code { font-family: var(--font-mono); font-size: .95em; }
    .banner.ko { background: var(--warning-soft); border-color: var(--warning-soft); color: var(--warning); }
    .banner.err { background: var(--danger-soft); border-color: var(--danger-border); color: var(--danger); }

    .card { background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); padding: 20px 22px; }
    .tips h3 { margin: 0 0 12px; font-size: .875rem; font-weight: 600; color: var(--text); }
    .tips ul { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 8px; }
    .tips li { font-size: .78rem; color: var(--text-2); line-height: 1.55; }
    .tips code { font-family: var(--font-mono); font-size: .95em; }

    @media (max-width: 1100px) { .grid { grid-template-columns: 1fr; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SimulationPage implements OnInit {
  protected readonly svc = inject(SimulationService);

  ngOnInit() {
    this.svc.loadConfig();
  }

  /**
   * `false` seulement quand le serveur l'a dit : tant que la configuration n'est pas arrivée,
   * l'écran ne s'annonce pas coupé.
   */
  protected readonly disabled = computed(() => this.svc.config()?.enabled === false);

  protected send(request: SimulationSendRequest) {
    this.svc.send(request);
  }
}
