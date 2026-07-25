import { Injectable, signal, inject, computed, WritableSignal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BatchRetryTask, PaymentMessage, PaymentMessageStatus, MessageFilters, MqConfig } from '../models/message.model';
import { Page } from '../models/page.model';
import { API_CONFIG } from '../../../core/config/api.config';
import { NotificationService } from '../../../core/services/notification.service';
import { Router } from '@angular/router';

export const DEFAULT_SORT = 'receivedAt,desc';

/** Intervalle d'interrogation de l'état d'un rejeu massif. */
const BATCH_RETRY_POLL_MS = 1500;

@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);

  readonly messages: WritableSignal<PaymentMessage[]> = signal([]);
  readonly stats: WritableSignal<Record<PaymentMessageStatus, number>> = signal({} as Record<PaymentMessageStatus, number>);
  readonly currentMessage = signal<PaymentMessage | null>(null);
  /** chargement de la liste paginée */
  readonly loading = signal(false);
  /** chargement des statistiques (dashboard) */
  readonly statsLoading = signal(false);
  /** chargement d'un message unitaire (drawer / page détail) */
  readonly detailLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly currentPage = signal<Page<PaymentMessage> | null>(null);
  readonly activitySample = signal<PaymentMessage[]>([]);
  /** configuration MQ (noms de files, gestionnaire, canal) — sans secret */
  readonly mqConfig = signal<MqConfig | null>(null);
  readonly searchTerm = signal('');
  /** nombre de messages reçus sur les dernières 24 h (totalElements de /messages?receivedAfter=…) */
  readonly volume24h = signal<number | null>(null);
  /** horodatage du dernier chargement réussi, affiché dans le bandeau */
  readonly lastUpdated = signal<Date | null>(null);
  /** id du message dont le statut vient de changer — déclenche un flash visuel */
  readonly changedId = signal<number | null>(null);
  /** un rejeu massif est en cours côté serveur */
  readonly batchRetryRunning = signal(false);
  /** nombre de messages déjà rejoués par le rejeu massif en cours */
  readonly batchRetryProcessed = signal(0);

  /** signale un changement pour animer la ligne/carte concernée, puis se réinitialise */
  private flash(id: number) {
    this.changedId.set(id);
    setTimeout(() => { if (this.changedId() === id) this.changedId.set(null); }, 1300);
  }

  readonly total = computed(() =>
    Object.values(this.stats() ?? {}).reduce((a, b) => a + (b ?? 0), 0));

  /**
   * Les réponses de liste ne transportent plus le payload (seule sa taille est renvoyée) :
   * le filtre client porte donc sur les métadonnées, pas sur le contenu du message.
   */
  readonly filteredMessages = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const list = this.messages();
    if (!term) return list;
    return list.filter((m) =>
      m.reference?.toLowerCase().includes(term) ||
      m.messageId?.toLowerCase().includes(term) ||
      m.messageType?.toLowerCase().includes(term));
  });

  /** dernière requête de liste jouée, rejouée par `refreshAll()` */
  private lastQuery: { filters?: MessageFilters; page: number; size: number; sort: string } =
    { page: 0, size: 20, sort: DEFAULT_SORT };

  loadMessages(filters?: MessageFilters, page = 0, size = 20, sort: string = DEFAULT_SORT) {
    this.lastQuery = { filters, page, size, sort };
    this.loading.set(true);
    this.error.set(null);

    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);

    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.receivedAfter) params = params.set('receivedAfter', filters.receivedAfter);

    this.http.get<Page<PaymentMessage>>(API_CONFIG.messages, { params })
      .subscribe({
        next: (res) => {
          this.messages.set(res.content);
          this.currentPage.set(res);
          this.lastUpdated.set(new Date());
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message);
          this.loading.set(false);
          this.notification.error('Erreur lors du chargement des messages');
        }
      });
  }

  /** charge une fois la config MQ non sensible (best-effort). */
  loadConfig() {
    if (this.mqConfig()) return;
    this.http.get<MqConfig>(API_CONFIG.config)
      .subscribe({ next: (res) => this.mqConfig.set(res), error: () => { /* best-effort */ } });
  }

  loadStats() {
    this.statsLoading.set(true);
    this.http.get<Record<PaymentMessageStatus, number>>(API_CONFIG.stats)
      .subscribe({
        next: (res) => {
          this.stats.set(res);
          this.lastUpdated.set(new Date());
          this.statsLoading.set(false);
        },
        error: () => {
          this.statsLoading.set(false);
          this.notification.error('Erreur lors du chargement des statistiques');
        }
      });
  }

  loadActivitySample(size = 200) {
    const params = new HttpParams().set('page', '0').set('size', size.toString()).set('sort', DEFAULT_SORT);
    this.http.get<Page<PaymentMessage>>(API_CONFIG.messages, { params })
      .subscribe({
        next: (res) => this.activitySample.set(res.content),
        error: () => { /* sample is best-effort; ignore */ },
      });
  }

  /** Volume des dernières 24 h : une page de taille 1, seul `totalElements` est exploité. */
  loadVolume24h() {
    // ISO complet, fuseau inclus : le backend attend un OffsetDateTime.
    const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
    const params = new HttpParams().set('page', '0').set('size', '1').set('receivedAfter', since);
    this.http.get<Page<PaymentMessage>>(API_CONFIG.messages, { params })
      .subscribe({
        next: (res) => this.volume24h.set(res.totalElements),
        error: () => this.volume24h.set(null),
      });
  }

  /** Rejoue tout ce que le bandeau supérieur pilote : stats, liste courante, échantillon, volume 24 h. */
  refreshAll() {
    const { filters, page, size, sort } = this.lastQuery;
    this.loadStats();
    this.loadMessages(filters, page, size, sort);
    this.loadActivitySample();
    this.loadVolume24h();
  }

  loadMessage(id: number) {
    this.detailLoading.set(true);
    this.error.set(null);

    this.http.get<PaymentMessage>(`${API_CONFIG.messages}/${id}`)
      .subscribe({
        next: (res) => {
          this.currentMessage.set(res);
          this.detailLoading.set(false);
        },
        error: (err) => {
          this.error.set(err.message);
          this.detailLoading.set(false);
          this.notification.error('Erreur lors du chargement du message');
        }
      });
  }

  clearCurrent() {
    this.currentMessage.set(null);
  }

  retry(id: number) {
    this.http.post<PaymentMessage>(`${API_CONFIG.messages}/${id}/retry`, {})
      .subscribe({
        next: (res) => {
          this.currentMessage.set(res);
          this.patchLocal(res);
          this.flash(res.id);
          this.loadStats();
          this.notification.success('Message relancé');
        },
        error: () => this.notification.error('Erreur lors de la relance')
      });
  }

  /**
   * Le rejeu massif s'exécute côté serveur par lots bornés : l'API répond 202 avec un
   * taskId, dont l'avancement est interrogé jusqu'à la fin du traitement.
   */
  batchRetryFailed() {
    this.batchRetryRunning.set(true);
    this.http.post<BatchRetryTask>(API_CONFIG.batchRetry, {})
      .subscribe({
        next: (task) => {
          this.notification.success('Rejeu massif démarré');
          this.pollBatchRetry(task.taskId);
        },
        error: (err: { status?: number }) => {
          this.batchRetryRunning.set(false);
          this.notification.error(err.status === 403
            ? 'Rejeu massif réservé au rôle ADMIN'
            : 'Erreur lors de la relance batch');
        }
      });
  }

  private pollBatchRetry(taskId: string) {
    this.http.get<BatchRetryTask>(`${API_CONFIG.batchRetry}/${taskId}`)
      .subscribe({
        next: (task) => {
          this.batchRetryProcessed.set(task.processed);
          if (task.state === 'RUNNING') {
            setTimeout(() => this.pollBatchRetry(taskId), BATCH_RETRY_POLL_MS);
            return;
          }
          this.batchRetryRunning.set(false);
          this.loadStats();
          if (task.state === 'COMPLETED') {
            this.notification.success(`${task.processed} message(s) relancé(s)`
              + (task.truncated ? ' — plafond atteint, relancer pour poursuivre' : ''));
          } else {
            this.notification.error('Le rejeu massif a échoué');
          }
        },
        error: () => {
          this.batchRetryRunning.set(false);
          this.notification.error('Suivi du rejeu massif interrompu');
        }
      });
  }

  /**
   * Le serveur attend un objet `{ status, reason? }` et non plus une chaîne JSON brute.
   * Il refuse par ailleurs les transitions incohérentes (422) : le message d'erreur du
   * serveur est repris tel quel, lui seul connaît la règle violée.
   */
  updateStatus(id: number, status: PaymentMessageStatus, reason?: string) {
    this.http.put<PaymentMessage>(`${API_CONFIG.messages}/${id}/status`, { status, reason }).subscribe({
      next: (res) => {
        this.currentMessage.set(res);
        this.patchLocal(res);
        this.flash(res.id);
        this.loadStats();
        this.notification.success('Statut mis à jour');
      },
      error: (err: { status?: number; error?: { detail?: string } }) => {
        if (err.status === 422) {
          this.notification.error(err.error?.detail ?? 'Changement de statut refusé');
          return;
        }
        if (err.status === 403) {
          this.notification.error('Opération réservée au rôle ADMIN');
          return;
        }
        this.notification.error('Erreur lors de la mise à jour du statut');
      }
    });
  }

  deleteMessage(id: number, redirect = true) {
    this.http.delete(`${API_CONFIG.messages}/${id}`)
      .subscribe({
        next: () => {
          this.notification.success('Message supprimé');
          this.messages.update((list) => list.filter((m) => m.id !== id));
          this.currentMessage.set(null);
          this.loadStats();
          if (redirect) this.router.navigate(['/messages']);
        },
        error: (err: { status?: number }) => this.notification.error(
          err.status === 403 ? 'Suppression réservée au rôle ADMIN' : 'Erreur lors de la suppression')
      });
  }

  /** Reflète dans la liste déjà chargée un message modifié côté serveur. */
  private patchLocal(updated: PaymentMessage) {
    this.messages.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
  }
}
