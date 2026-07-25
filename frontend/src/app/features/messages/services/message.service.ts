import { Injectable, signal, inject, computed, WritableSignal, DOCUMENT } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, Subject, catchError, filter, fromEvent, interval, merge, switchMap, tap } from 'rxjs';
import {
  BatchRetryTask, DashboardStats, PaymentMessage, PaymentMessageStatus, MessageFilters, MqConfig,
} from '../models/message.model';
import { Page } from '../models/page.model';
import { API_CONFIG } from '../../../core/config/api.config';
import { NotificationService } from '../../../core/services/notification.service';
import { Router } from '@angular/router';

export const DEFAULT_SORT = 'receivedAt,desc';

/** Intervalle d'interrogation de l'état d'un rejeu massif. */
const BATCH_RETRY_POLL_MS = 1500;

/** Cadence du rafraîchissement automatique, suspendu quand l'onglet est masqué. */
export const AUTO_REFRESH_MS = 30_000;

/** Requête de liste : ce que le serveur doit recevoir, plus le mode d'affichage. */
interface ListQuery {
  filters: MessageFilters;
  page: number;
  size: number;
  sort: string;
  /** rafraîchissement de fond : ne pas repasser la vue en squelette */
  silent: boolean;
}

@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

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
  /** agrégats du dashboard, calculés en SQL (cf. GET /messages/stats/dashboard) */
  readonly dashboard = signal<DashboardStats | null>(null);
  /** types présents en base, pour le sélecteur de la barre de filtres */
  readonly messageTypes = signal<string[]>([]);
  /** configuration MQ (noms de files, gestionnaire, canal) — sans secret */
  readonly mqConfig = signal<MqConfig | null>(null);
  /** horodatage du dernier chargement réussi, affiché dans le bandeau */
  readonly lastUpdated = signal<Date | null>(null);
  /** id du message dont le statut vient de changer — déclenche un flash visuel */
  readonly changedId = signal<number | null>(null);
  /** un rejeu massif est en cours côté serveur */
  readonly batchRetryRunning = signal(false);
  /** nombre de messages déjà rejoués par le rejeu massif en cours */
  readonly batchRetryProcessed = signal(0);
  /** rafraîchissement périodique des vues déjà chargées */
  readonly autoRefresh = signal(true);

  readonly total = computed(() =>
    Object.values(this.stats() ?? {}).reduce((a, b) => a + (b ?? 0), 0));

  /**
   * Chaque flux passe par un `Subject` unique consommé en `switchMap` : une requête en vol
   * est annulée dès que la suivante part. Sans cela, deux clics rapides sur « page suivante »
   * ou sur deux en-têtes de tri laissaient gagner la **dernière réponse arrivée**, pas la
   * dernière demandée, et le tableau pouvait afficher une page ne correspondant ni au numéro
   * ni au tri sélectionnés.
   */
  private readonly listRequests = new Subject<ListQuery>();
  private readonly statsRequests = new Subject<{ filters: MessageFilters; silent: boolean }>();
  private readonly dashboardRequests = new Subject<{ silent: boolean }>();

  /** dernière requête de liste jouée, rejouée par `refreshAll()` */
  private lastQuery: ListQuery = { filters: {}, page: 0, size: 20, sort: DEFAULT_SORT, silent: false };
  /**
   * Filtres des derniers compteurs demandés — distincts de ceux de la liste : le dashboard
   * veut des compteurs globaux, et il ne doit pas hériter des filtres laissés par la liste.
   */
  private lastStatsFilters: MessageFilters = {};
  /** ce que `refreshAll()` doit rejouer : seules les vues déjà chargées sont interrogées */
  private listLoaded = false;
  private dashboardLoaded = false;

  constructor() {
    this.listRequests.pipe(
      tap((query) => {
        if (!query.silent) this.loading.set(true);
        this.error.set(null);
      }),
      switchMap((query) => this.http.get<Page<PaymentMessage>>(API_CONFIG.messages, { params: listParams(query) })
        .pipe(catchError((err: { message?: string }) => {
          this.error.set(err.message ?? 'Erreur inconnue');
          this.loading.set(false);
          this.notification.error('Erreur lors du chargement des messages');
          return EMPTY;
        }))),
      takeUntilDestroyed(),
    ).subscribe((res) => {
      this.messages.set(res.content);
      this.currentPage.set(res);
      this.lastUpdated.set(new Date());
      this.loading.set(false);
    });

    this.statsRequests.pipe(
      tap(({ silent }) => { if (!silent) this.statsLoading.set(true); }),
      switchMap(({ filters }) => this.http.get<Record<PaymentMessageStatus, number>>(
        API_CONFIG.stats, { params: statsParams(filters) })
        .pipe(catchError(() => {
          this.statsLoading.set(false);
          this.notification.error('Erreur lors du chargement des statistiques');
          return EMPTY;
        }))),
      takeUntilDestroyed(),
    ).subscribe((res) => {
      this.stats.set(res);
      this.lastUpdated.set(new Date());
      this.statsLoading.set(false);
    });

    this.dashboardRequests.pipe(
      switchMap(() => this.http.get<DashboardStats>(API_CONFIG.dashboardStats)
        .pipe(catchError(() => {
          this.notification.error('Erreur lors du chargement des agrégats');
          return EMPTY;
        }))),
      takeUntilDestroyed(),
    ).subscribe((res) => {
      this.dashboard.set(res);
      this.lastUpdated.set(new Date());
    });

    // Le bandeau affiche une pastille « Données <heure> » qui suggérait un flux temps réel
    // alors que le rafraîchissement était exclusivement manuel : sur un flux à fort débit,
    // l'écran restait périmé en permanence. Les ticks ne partent que si l'onglet est visible
    // — un onglet en arrière-plan n'a personne à informer — et la reprise de visibilité
    // rattrape immédiatement les ticks manqués.
    const visible = () => this.document.visibilityState === 'visible';
    merge(
      interval(AUTO_REFRESH_MS),
      fromEvent(this.document, 'visibilitychange'),
    ).pipe(
      filter(() => this.autoRefresh() && visible()),
      takeUntilDestroyed(),
    ).subscribe(() => this.refreshAll(true));
  }

  /** signale un changement pour animer la ligne/carte concernée, puis se réinitialise */
  private flash(id: number) {
    this.changedId.set(id);
    setTimeout(() => { if (this.changedId() === id) this.changedId.set(null); }, 1300);
  }

  loadMessages(filters: MessageFilters = {}, page = 0, size = 20, sort: string = DEFAULT_SORT, silent = false) {
    this.lastQuery = { filters, page, size, sort, silent };
    this.listLoaded = true;
    this.listRequests.next(this.lastQuery);
  }

  /** charge une fois la config MQ non sensible (best-effort). */
  loadConfig() {
    if (this.mqConfig()) return;
    this.http.get<MqConfig>(API_CONFIG.config)
      .subscribe({ next: (res) => this.mqConfig.set(res), error: () => { /* best-effort */ } });
  }

  /** charge une fois la liste des types (best-effort : sans elle, le sélecteur reste vide). */
  loadMessageTypes() {
    if (this.messageTypes().length) return;
    this.http.get<string[]>(API_CONFIG.messageTypes)
      .subscribe({ next: (res) => this.messageTypes.set(res), error: () => { /* best-effort */ } });
  }

  /**
   * Compteurs par statut. Les filtres actifs autres que le statut sont transmis : les
   * pastilles annoncent ainsi ce que donnerait un clic dessus, au lieu d'un total global
   * sans rapport avec la liste affichée.
   */
  loadStats(filters: MessageFilters = {}, silent = false) {
    this.lastStatsFilters = filters;
    this.statsRequests.next({ filters, silent });
  }

  /** Agrégats du dashboard : une requête de quelques centaines d'octets, chiffres exacts. */
  loadDashboard(silent = false) {
    this.dashboardLoaded = true;
    this.dashboardRequests.next({ silent });
  }

  /**
   * Rejoue ce qui est affiché : compteurs, liste courante et agrégats du dashboard, mais
   * uniquement les vues déjà chargées — inutile d'interroger un endpoint qu'aucune vue
   * n'exploite.
   *
   * @param silent rafraîchissement de fond : pas de squelette de chargement
   */
  refreshAll(silent = false) {
    const { filters, page, size, sort } = this.lastQuery;
    this.loadStats(this.lastStatsFilters, silent);
    if (this.listLoaded) this.loadMessages(filters, page, size, sort, silent);
    if (this.dashboardLoaded) this.loadDashboard(silent);
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
          this.afterWrite();
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
          this.afterWrite();
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
        this.afterWrite();
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
          this.afterWrite();
          if (redirect) this.router.navigate(['/messages']);
        },
        error: (err: { status?: number }) => this.notification.error(
          err.status === 403 ? 'Suppression réservée au rôle ADMIN' : 'Erreur lors de la suppression')
      });
  }

  /**
   * Une écriture invalide les compteurs et, quand le dashboard est affiché, ses agrégats :
   * le serveur les a déjà purgés de son cache, autant les relire tout de suite.
   */
  private afterWrite() {
    this.loadStats(this.lastStatsFilters, true);
    if (this.dashboardLoaded) this.loadDashboard(true);
  }

  /** Reflète dans la liste déjà chargée un message modifié côté serveur. */
  private patchLocal(updated: PaymentMessage) {
    this.messages.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
  }
}

function listParams(query: ListQuery): HttpParams {
  let params = new HttpParams()
    .set('page', query.page.toString())
    .set('size', query.size.toString())
    .set('sort', query.sort);
  return appendFilters(params, query.filters);
}

/** Le statut est porté par les pastilles elles-mêmes : il n'entre pas dans leurs compteurs. */
function statsParams(filters: MessageFilters): HttpParams {
  return appendFilters(new HttpParams(), { ...filters, status: undefined });
}

function appendFilters(params: HttpParams, filters: MessageFilters): HttpParams {
  if (filters.status) params = params.set('status', filters.status);
  if (filters.receivedAfter) params = params.set('receivedAfter', filters.receivedAfter);
  if (filters.type) params = params.set('type', filters.type);
  if (filters.q) params = params.set('q', filters.q);
  return params;
}
