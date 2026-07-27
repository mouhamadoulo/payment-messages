import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { API_CONFIG } from '../../../core/config/api.config';
import { NotificationService } from '../../../core/services/notification.service';
import { MessageService } from '../../messages/services/message.service';
import {
  SendHistoryEntry, SimulationConfig, SimulationSendRequest, SimulationTask,
} from '../models/simulation.model';

/** Cadence d'interrogation de l'avancement : assez fine pour que la barre bouge. */
const POLL_MS = 500;

/** Lignes conservées dans l'historique de session. */
const MAX_HISTORY = 12;

/**
 * Pilotage des envois de test. Le serveur cadence la publication en tâche de fond et rend un
 * `taskId` : cet état est donc *interrogé*, pas calculé ici — deux onglets ouverts voient le
 * même envoi, et un rafraîchissement de page n'en invente pas un second.
 *
 * L'historique, lui, est propre à l'onglet : le serveur ne garde que les quelques derniers
 * envois et rien n'est persisté.
 */
@Injectable({ providedIn: 'root' })
export class SimulationService {
  private readonly http = inject(HttpClient);
  private readonly notification = inject(NotificationService);
  private readonly messages = inject(MessageService);

  /** Bornes et files admises : chargées une fois, à la demande (cf. `loadConfig`). */
  private readonly configRequested = signal(false);
  private readonly configResource = httpResource<SimulationConfig | null>(
    () => (this.configRequested() ? { url: API_CONFIG.simulationConfig } : undefined),
    { defaultValue: null });
  readonly config = this.configResource.value;
  readonly configLoading = this.configResource.isLoading;

  /**
   * File sur laquelle les messages partent — affichée, jamais choisie.
   *
   * Deux sources pour la même propriété serveur (`ibm.mq.queue`) : celle de la simulation, et
   * à défaut la configuration MQ que le bandeau latéral charge déjà. Le repli n'est pas
   * décoratif : il garde le nom de la file lisible quand `/simulation/config` n'a pas encore
   * répondu, ou lorsqu'il est servi par un backend plus ancien qui ignore cet endpoint.
   */
  readonly queue = computed(() =>
    this.config()?.queue ?? this.messages.mqConfig()?.queue ?? '');

  /** Envoi suivi actuellement — terminé ou non, il reste affiché jusqu'au suivant. */
  readonly task = signal<SimulationTask | null>(null);
  /** Un envoi est en vol : la demande est partie et n'a pas encore rendu son état final. */
  readonly running = signal(false);
  readonly history = signal<SendHistoryEntry[]>([]);
  /** Refus du serveur (bornes, file inconnue, simulation coupée) affiché sous le formulaire. */
  readonly error = signal<string | null>(null);

  /** Avancement en pourcentage, borné : un envoi sans total resterait à zéro. */
  readonly progress = computed(() => {
    const task = this.task();
    if (!task || !task.total) return 0;
    return Math.min(100, Math.round((task.sent / task.total) * 100));
  });

  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());

    // La ressource n'a pas de `subscribe` où signaler son échec : sans configuration, l'écran
    // n'a ni file ni bornes à proposer, il faut le dire.
    effect(() => {
      if (this.configResource.error()) {
        this.error.set("Configuration de la simulation indisponible");
      }
    });
  }

  loadConfig() {
    this.configRequested.set(true);
    // Sert de repli au nom de la file : les deux ressources sont idempotentes, un écran ouvert
    // après le bandeau latéral ne relance rien.
    this.messages.loadConfig();
  }

  /**
   * Lance un envoi. Le serveur répond 202 avec l'état initial ; la suite est interrogée
   * jusqu'à l'état terminal. Un refus (400/503) est rendu tel quel : lui seul connaît la
   * borne franchie.
   */
  send(request: SimulationSendRequest) {
    if (this.running()) return;

    this.running.set(true);
    this.error.set(null);

    this.http.post<SimulationTask>(API_CONFIG.simulationSends, request).subscribe({
      next: (task) => {
        this.task.set(task);
        this.poll(task.taskId);
      },
      error: (err: { error?: { detail?: string } }) => {
        this.running.set(false);
        const detail = err.error?.detail ?? "Erreur lors du démarrage de l'envoi";
        this.error.set(detail);
        this.notification.error(detail);
      }
    });
  }

  /** Efface le suivi affiché (bouton « Réinitialiser »), sans toucher à l'historique. */
  reset() {
    if (this.running()) return;
    this.task.set(null);
    this.error.set(null);
  }

  private poll(taskId: string) {
    this.http.get<SimulationTask>(`${API_CONFIG.simulationSends}/${taskId}`).subscribe({
      next: (task) => {
        this.task.set(task);

        if (task.state === 'RUNNING') {
          this.pollTimer = setTimeout(() => this.poll(taskId), POLL_MS);
          return;
        }

        this.stopPolling();
        this.running.set(false);
        this.remember(task);

        // Les messages publiés viennent d'être consommés : compteurs et agrégats déjà
        // affichés ailleurs sont périmés.
        this.messages.refreshAll(true);

        if (task.state === 'COMPLETED' && task.failed === 0) {
          this.notification.success(`Envoi terminé · ${task.published}/${task.total} publié(s)`);
        } else if (task.state === 'COMPLETED') {
          this.notification.error(`Envoi terminé · ${task.failed} publication(s) en échec`);
        } else {
          this.notification.error(task.error ?? "L'envoi a échoué");
        }
      },
      error: () => {
        this.stopPolling();
        this.running.set(false);
        this.error.set("Suivi de l'envoi interrompu");
        this.notification.error("Suivi de l'envoi interrompu");
      }
    });
  }

  private stopPolling() {
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private remember(task: SimulationTask) {
    const entry: SendHistoryEntry = {
      taskId: task.taskId,
      title: task.total === 1 ? '1 message' : `Envoi en masse · ${task.total} messages`,
      destination: task.destination,
      time: new Date(task.finishedAt ?? task.startedAt).toLocaleTimeString('fr-FR'),
      published: task.published,
      total: task.total,
      outcome: outcomeOf(task),
    };
    this.history.update((list) => [entry, ...list].slice(0, MAX_HISTORY));
  }
}

function outcomeOf(task: SimulationTask): SendHistoryEntry['outcome'] {
  if (task.state === 'FAILED' || task.published === 0) return 'ko';
  return task.failed === 0 ? 'ok' : 'partial';
}
