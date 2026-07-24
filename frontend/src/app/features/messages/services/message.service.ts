import { Injectable, signal, inject, computed, WritableSignal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { PaymentMessage, PaymentMessageStatus, MessageFilters } from '../models/message.model';
import { Page } from '../models/page.model';
import { API_CONFIG } from '../../../core/config/api.config';
import { NotificationService } from '../../../core/services/notification.service';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);

  readonly messages: WritableSignal<PaymentMessage[]> = signal([]);
  readonly stats: WritableSignal<Record<PaymentMessageStatus, number>> = signal({} as Record<PaymentMessageStatus, number>);
  readonly currentMessage = signal<PaymentMessage | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly currentPage = signal<Page<PaymentMessage> | null>(null);
  readonly activitySample = signal<PaymentMessage[]>([]);
  readonly searchTerm = signal('');
  readonly filteredMessages = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    const list = this.messages();
    if (!term) return list;
    return list.filter((m) =>
      m.reference?.toLowerCase().includes(term) ||
      m.messageId?.toLowerCase().includes(term));
  });

  loadMessages(filters?: MessageFilters, page = 0, size = 20) {
    this.loading.set(true);
    this.error.set(null);

    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.receivedAfter) params = params.set('receivedAfter', filters.receivedAfter);

    this.http.get<Page<PaymentMessage>>(API_CONFIG.messages, { params })
      .subscribe({
        next: (res) => {
          this.messages.set(res.content);
          this.currentPage.set(res);
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
        next: (res) => this.stats.set(res),
        error: () => this.notification.error('Erreur lors du chargement des statistiques')
      });
  }

  loadActivitySample(size = 200) {
    const params = new HttpParams().set('page', '0').set('size', size.toString());
    this.http.get<Page<PaymentMessage>>(API_CONFIG.messages, { params })
      .subscribe({
        next: (res) => this.activitySample.set(res.content),
        error: () => { /* sample is best-effort; ignore */ },
      });
  }

  loadMessage(id: number) {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<PaymentMessage>(`${API_CONFIG.messages}/${id}`)
      .subscribe({
        next: (res) => {
          this.currentMessage.set(res);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message);
          this.loading.set(false);
          this.notification.error('Erreur lors du chargement du message');
        }
      });
  }

  retry(id: number) {
    this.http.post<PaymentMessage>(`${API_CONFIG.messages}/${id}/retry`, {})
      .subscribe({
        next: (res) => {
          this.currentMessage.set(res);
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
        this.notification.success('Statut mis à jour');
      },
      error: () => this.notification.error('Erreur lors de la mise à jour du statut')
    });
  }

  deleteMessage(id: number) {
    this.http.delete(`${API_CONFIG.messages}/${id}`)
      .subscribe({
        next: () => {
          this.notification.success('Message supprimé');
          this.router.navigate(['/messages']);
        },
        error: () => this.notification.error('Erreur lors de la suppression')
      });
  }
}
