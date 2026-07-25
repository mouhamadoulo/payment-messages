import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { DEFAULT_TEMPLATE, PAYLOAD_TEMPLATES, PayloadTemplate } from '../../config/templates';
import { SendMode, SimulationConfig, SimulationSendRequest } from '../../models/simulation.model';

/** Repli tant que le serveur n'a pas rendu ses bornes : la saisie reste utilisable. */
const FALLBACK_MAX_COUNT = 1000;
const FALLBACK_MAX_RATE = 200;

/**
 * Formulaire d'envoi : mode, file, modèle de payload, éditeur JSON et paramètres de masse.
 *
 * Le composant ne valide rien d'autre que la syntaxe JSON — et encore, à titre indicatif :
 * un payload illisible est un cas de test légitime, l'envoyer doit rester possible. Les
 * bornes affichées viennent du serveur, qui refuse de toute façon ce qui les dépasse.
 */
@Component({
  selector: 'app-send-form',
  standalone: true,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="card">
      <div class="field">
        <span class="cap">Mode d'envoi</span>
        <div class="segmented" role="group" aria-label="Mode d'envoi">
          <button type="button" [class.on]="mode() === 'single'" (click)="mode.set('single')"
                  [attr.aria-pressed]="mode() === 'single'">1 message</button>
          <button type="button" [class.on]="mode() === 'bulk'" (click)="mode.set('bulk')"
                  [attr.aria-pressed]="mode() === 'bulk'">Envoi en masse</button>
        </div>
      </div>

      <!--
        Destination affichée, jamais choisie : l'API ne prend pas de file en paramètre et
        publie sur celle que l'application consomme. Un sélecteur laisserait croire à un
        choix qui n'existe pas, et un dépôt ailleurs ne produirait rien d'observable.
      -->
      <div class="field">
        <span class="cap" id="sim-destination-label">File de destination</span>
        <div class="readonly mono" aria-labelledby="sim-destination-label">
          <span class="lock" aria-hidden="true">
            <svg width="13" height="13" viewBox="0 0 14 14" fill="none">
              <rect x="2.5" y="6" width="9" height="6.5" rx="1.5" stroke="currentColor" stroke-width="1.4"/>
              <path d="M4.75 6V4.25a2.25 2.25 0 0 1 4.5 0V6" stroke="currentColor" stroke-width="1.4"/>
            </svg>
          </span>
          @if (queue()) { {{ queue() }} } @else { <span class="pending">chargement…</span> }
        </div>
        <p class="hint">File d'entrée consommée par l'application (<code>ibm.mq.queue</code>) —
           seule destination possible, elle n'est pas modifiable.</p>
      </div>

      <div class="field">
        <span class="cap">Modèles de message</span>
        <div class="templates">
          @for (t of templates; track t.key) {
            <button type="button" class="tpl" [class.on]="templateKey() === t.key"
                    [class.ko]="t.invalid" [title]="t.description" (click)="applyTemplate(t)">
              {{ t.name }}
            </button>
          }
        </div>
        @if (activeTemplate(); as t) { <p class="hint">{{ t.description }}</p> }
      </div>

      <div class="field">
        <div class="field-head">
          <label class="cap" for="sim-payload">Payload JSON</label>
          <div class="field-actions">
            <span class="validity mono" [class.ko]="!jsonValid()">
              {{ jsonValid() ? '✓ JSON valide' : '✗ JSON invalide' }}
            </span>
            <button type="button" class="link" [disabled]="!jsonValid()" (click)="format()">Formater</button>
          </div>
        </div>
        <textarea id="sim-payload" spellcheck="false" [class.ko]="!jsonValid()"
                  [ngModel]="payload()" (ngModelChange)="payload.set($event)"
                  aria-describedby="sim-payload-note"></textarea>
        <p class="hint" id="sim-payload-note">
          Contrat de la file d'entrée : <code>messageId</code>, <code>messageType</code>,
          <code>reference</code>, <code>payment</code> et <code>status</code> sont obligatoires.
          Un payload rejeté est conservé en base au statut <code>FAILED</code>, avec son texte brut.
        </p>
      </div>

      @if (mode() === 'bulk') {
        <div class="bulk">
          <div class="bulk-field">
            <label class="cap" for="sim-count">Nombre de messages</label>
            <input id="sim-count" type="number" min="1" [max]="maxCount()"
                   [ngModel]="count()" (ngModelChange)="setCount($event)" />
          </div>
          <div class="bulk-field">
            <label class="cap" for="sim-rate">Débit (msg/s)</label>
            <input id="sim-rate" type="number" min="1" [max]="maxRate()"
                   [ngModel]="rate()" (ngModelChange)="setRate($event)" />
          </div>
          <div class="bulk-field">
            <span class="cap">Durée estimée</span>
            <div class="estimate mono">{{ estimate() }}</div>
          </div>
          <p class="hint bounds">Plafonds serveur : {{ maxCount() }} messages, {{ maxRate() }} msg/s.</p>
        </div>
      }

      <label class="check">
        <input type="checkbox" [ngModel]="uniqueIds()" (ngModelChange)="uniqueIds.set($event)" />
        <span>
          Réécrire <code>messageId</code> avant publication
          <em>— l'ingestion est idempotente sur ce champ : sans réécriture, une seconde
          publication du même payload est ignorée comme une redélivrance.</em>
        </span>
      </label>

      @if (activeTemplate()?.invalid) {
        <p class="warn">
          Ce modèle est invalide à dessein : le consommateur le rejettera définitivement et
          la ligne apparaîtra en <code>FAILED</code>.
        </p>
      }

      <div class="actions">
        <button type="button" class="btn primary" [disabled]="!canSend()" (click)="submit()">
          <app-icon name="play" [size]="16" />
          {{ running() ? 'Envoi en cours…' : (mode() === 'single' ? 'Envoyer le message' : "Lancer l'envoi") }}
        </button>
        <button type="button" class="btn ghost" [disabled]="running()" (click)="resetForm()">
          Réinitialiser
        </button>
      </div>
    </div>
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); padding: 20px 22px;
            display: flex; flex-direction: column; gap: 18px; }

    .field { display: flex; flex-direction: column; gap: 8px; }
    .field-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
    .field-actions { display: flex; align-items: center; gap: 10px; }
    .cap { font-size: .69rem; color: var(--muted-2); font-weight: 600;
           letter-spacing: .03em; text-transform: uppercase; }
    .hint { margin: 0; font-size: .72rem; color: var(--muted-2); line-height: 1.5; }
    .hint code, .warn code, .check code { font-family: var(--font-mono); font-size: .95em; color: var(--text-2); }
    .mono { font-family: var(--font-mono); }

    .segmented { display: inline-flex; background: var(--border-soft); border-radius: var(--radius-ctl);
                 padding: 3px; gap: 3px; align-self: flex-start; }
    .segmented button { padding: 8px 18px; border: 0; border-radius: 7px; font-size: .81rem;
                        font-weight: 600; cursor: pointer; background: transparent; color: var(--muted-2); }
    .segmented button.on { background: var(--surface); color: var(--primary); box-shadow: var(--shadow-card); }

    /* Contrôle en lecture seule : même gabarit qu'un champ, mais ni focus ni curseur texte. */
    .readonly { display: flex; align-items: center; gap: 9px; width: 100%;
                padding: 10px 12px; border: 1px solid var(--border);
                border-radius: var(--radius-ctl); font-size: .84rem; color: var(--text-2);
                background: var(--surface-alt); }
    .lock { display: inline-flex; color: var(--muted-2); flex: none; }
    .pending { color: var(--muted-2); font-style: italic; }

    .templates { display: flex; flex-wrap: wrap; gap: 8px; }
    .tpl { padding: 7px 13px; border: 1px solid var(--ctl-border); border-radius: 8px;
           background: var(--surface); color: var(--text-2); font-size: .78rem;
           font-weight: 500; cursor: pointer; }
    .tpl:hover { border-color: var(--primary); color: var(--primary); }
    .tpl.on { border-color: var(--primary); background: var(--primary-soft); color: var(--primary-dark); }
    .tpl.ko { color: var(--warning); }
    .tpl.ko.on { border-color: var(--warning); background: var(--warning-soft); color: var(--warning); }

    .validity { font-size: .72rem; font-weight: 600; color: var(--success); }
    .validity.ko { color: var(--danger); }
    .link { font-size: .72rem; font-weight: 500; color: var(--primary); background: none;
            border: 0; cursor: pointer; padding: 0; }
    .link:disabled { color: var(--faint); cursor: not-allowed; }

    textarea { width: 100%; min-height: 240px; padding: 13px 15px; border: 1px solid var(--ctl-border);
               border-radius: var(--radius-ctl); font-family: var(--font-mono); font-size: .78rem;
               line-height: 1.55; color: var(--text); background: var(--surface-head); resize: vertical; }
    textarea.ko { border-color: var(--danger); }

    .bulk { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px;
            padding: 15px 16px; background: var(--surface-alt);
            border: 1px solid var(--border-soft); border-radius: 10px; }
    .bulk-field { display: flex; flex-direction: column; gap: 6px; }
    .bulk-field input { padding: 9px 12px; border: 1px solid var(--ctl-border); border-radius: 8px;
                        font-size: .87rem; font-family: var(--font-mono); color: var(--text);
                        background: var(--surface); width: 100%; }
    .estimate { font-size: .87rem; font-weight: 600; color: var(--text-2); padding: 9px 0; }
    .check { display: flex; align-items: flex-start; gap: 9px; line-height: 1.5;
             font-size: .76rem; color: var(--text-2); cursor: pointer; }
    .check input { margin-top: 2px; accent-color: var(--primary); }
    .check em { font-style: normal; color: var(--muted-2); }
    .bounds { grid-column: 1 / -1; }

    .warn { margin: 0; padding: 11px 14px; border-radius: var(--radius-ctl); font-size: .76rem;
            color: var(--warning); background: var(--warning-soft);
            border: 1px solid var(--warning-soft); line-height: 1.5; }

    .actions { display: flex; gap: 12px; }
    .btn { display: flex; align-items: center; justify-content: center; gap: 9px;
           border-radius: 10px; font-weight: 600; cursor: pointer; }
    .btn.primary { flex: 1; padding: 13px; border: 0; background: var(--primary);
                   color: #fff; font-size: .9rem; }
    .btn.primary:hover:not(:disabled) { filter: brightness(1.07); }
    .btn.ghost { padding: 13px 20px; border: 1px solid var(--ctl-border);
                 background: var(--surface); color: var(--muted); font-size: .87rem; font-weight: 500; }
    .btn.ghost:hover:not(:disabled) { background: var(--bg); }
    .btn:disabled { opacity: .5; cursor: not-allowed; }

    @media (max-width: 640px) {
      .bulk { grid-template-columns: 1fr; }
      .actions { flex-direction: column; }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SendFormComponent {
  /** Bornes ; absentes tant que `/simulation/config` n'a pas répondu. */
  readonly config = input<SimulationConfig | null>(null);
  /** File d'entrée visée, résolue par le service. Affichage seul. */
  readonly queue = input('');
  readonly running = input(false);

  readonly send = output<SimulationSendRequest>();

  protected readonly templates = PAYLOAD_TEMPLATES;

  protected readonly mode = signal<SendMode>('single');
  protected readonly templateKey = signal(DEFAULT_TEMPLATE.key);
  protected readonly payload = signal(DEFAULT_TEMPLATE.build(new Date()));
  protected readonly count = signal(100);
  protected readonly rate = signal(20);
  protected readonly uniqueIds = signal(true);

  protected readonly maxCount = computed(() => this.config()?.maxCount ?? FALLBACK_MAX_COUNT);
  protected readonly maxRate = computed(() => this.config()?.maxRate ?? FALLBACK_MAX_RATE);

  protected readonly activeTemplate = computed(() =>
    this.templates.find((t) => t.key === this.templateKey()) ?? null);

  protected readonly jsonValid = computed(() => {
    try {
      JSON.parse(this.payload());
      return true;
    } catch {
      return false;
    }
  });

  protected readonly estimate = computed(() => {
    const seconds = this.count() / Math.max(1, this.rate());
    if (seconds < 1) return '< 1 s';
    if (seconds < 60) return `${seconds.toFixed(1).replace('.', ',')} s`;
    return `${Math.floor(seconds / 60)} min ${Math.round(seconds % 60)} s`;
  });

  protected readonly canSend = computed(() =>
    !this.running() && this.payload().trim().length > 0);

  protected applyTemplate(template: PayloadTemplate) {
    this.templateKey.set(template.key);
    this.payload.set(template.build(new Date()));
  }

  /** Ne touche pas à un payload illisible : le reformater effacerait le cas testé. */
  protected format() {
    try {
      this.payload.set(JSON.stringify(JSON.parse(this.payload()), null, 2));
    } catch {
      /* le bouton est désactivé dans ce cas ; garde-fou si l'état change entre-temps */
    }
  }

  protected setCount(value: number | string) {
    this.count.set(clamp(value, 1, this.maxCount()));
  }

  protected setRate(value: number | string) {
    this.rate.set(clamp(value, 1, this.maxRate()));
  }

  protected resetForm() {
    this.mode.set('single');
    this.templateKey.set(DEFAULT_TEMPLATE.key);
    this.payload.set(DEFAULT_TEMPLATE.build(new Date()));
    this.count.set(100);
    this.rate.set(20);
    this.uniqueIds.set(true);
  }

  protected submit() {
    if (!this.canSend()) return;
    const single = this.mode() === 'single';
    this.send.emit({
      payload: this.payload(),
      count: single ? 1 : this.count(),
      // Un message unique n'a pas de cadence à tenir ; la valeur reste envoyée, le serveur
      // l'exige positive.
      ratePerSecond: single ? 1 : this.rate(),
      uniqueIds: this.uniqueIds(),
    });
  }
}

function clamp(value: number | string, min: number, max: number): number {
  const parsed = typeof value === 'number' ? value : Number.parseInt(value, 10);
  if (!Number.isFinite(parsed)) return min;
  return Math.min(max, Math.max(min, Math.trunc(parsed)));
}
