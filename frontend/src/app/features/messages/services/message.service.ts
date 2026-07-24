import { Injectable, signal, inject, computed, WritableSignal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { PaymentMessage, PaymentMessageStatus, MessageFilters } from '../models/message.model';
import { Page } from '../models/page.model';
import { API_CONFIG } from '../../../core/config/api.config';
import { NotificationService } from '../../../core/services/notification.service';
import { Router } from '@angular/router';

export const DEFAULT_SORT = 'receivedAt,desc';

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
  /** chargement d'un message unitaire (drawer / page détail) */
  readonly detailLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly currentPage = signal<Page<PaymentMessage> | null>(null);
  readonly activitySample = signal<PaymentMessage[]>([]);
  readonly searchTerm = signal('');
  /** nombre de messages reçus sur les dernières 24 h (totalElements de /messages?receivedAfter=…) */
  readonly volume24h = signal<number | null>(null);
  /** horodatage du dernier chargement réussi, affiché dans le bandeau */
  readonly lastUpdated = signal<Date | null>(null);

  readonly total = computed(() =>
    Object.values(this.stats() ?? {}).reduce((a, b) => a + (b ?? 0), 0));

  readonly filteredMessages = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const list = this.messages();
    if (!term) return list;
    return list.filter((m) =>
      m.reference?.toLowerCase().includes(term) ||
      m.messageId?.toLowerCase().includes(term) ||
      m.messageType?.toLowerCase().includes(term) ||
      m.payload?.toLowerCase().includes(term));
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

  loadStats() {
    this.http.get<Record<PaymentMessageStatus, number>>(API_CONFIG.stats)
      .subscribe({
        next: (res) => {
          this.stats.set(res);
          this.lastUpdated.set(new Date());
        },
        error: () => this.notification.error('Erreur lors du chargement des statistiques')
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
    const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString().slice(0, 19);
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
          this.loadStats();
          this.notification.success('Message relancé');
        },
        error: () => this.notification.error('Erreur lors de la relance')
      });
  }

  batchRetryFailed() {
    this.http.post<{ affected: number; status: string }>(API_CONFIG.batchRetry, {})
      .subscribe({
        next: (res) => {
          this.notification.success(`${res.affected} message(s) relancé(s)`);
          this.loadStats();
        },
        error: () => this.notification.error('Erreur lors de la relance batch')
      });
  }

  updateStatus(id: number, status: PaymentMessageStatus) {
    this.http.put<PaymentMessage>(`${API_CONFIG.messages}/${id}/status`, `"${status}"`, {
      headers: { 'Content-Type': 'application/json' }
    }).subscribe({
      next: (res) => {
        this.currentMessage.set(res);
        this.patchLocal(res);
        this.loadStats();
        this.notification.success('Statut mis à jour');
      },
      error: () => this.notification.error('Erreur lors de la mise à jour du statut')
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
        error: () => this.notification.error('Erreur lors de la suppression')
      });
  }

  /** Reflète dans la liste déjà chargée un message modifié côté serveur. */
  private patchLocal(updated: PaymentMessage) {
    this.messages.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
  }
}
