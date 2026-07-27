import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from './message.service';
import { PaymentMessageStatus } from '../models/message.model';

const emptyPage = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true };

describe('MessageService', () => {
  let svc: MessageService;
  let ctrl: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    svc = TestBed.inject(MessageService);
    ctrl = TestBed.inject(HttpTestingController);
    // Le rafraîchissement périodique ne doit pas parasiter les assertions.
    svc.autoRefresh.set(false);
  });

  afterEach(() => ctrl.verify());

  it('sends every filter to the server, not just status', () => {
    svc.loadMessages({ status: PaymentMessageStatus.FAILED, type: 'pacs.008', q: 'REF-42' }, 0, 20);

    const req = ctrl.expectOne((r) => r.url === '/messages');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.get('type')).toBe('pacs.008');
    expect(req.request.params.get('q')).toBe('REF-42');
    req.flush(emptyPage);
  });

  it('asks the stats endpoint for counts under the other filters, status excluded', () => {
    svc.loadStats({ status: PaymentMessageStatus.FAILED, type: 'pacs.008' });

    const req = ctrl.expectOne((r) => r.url === '/messages/stats');
    // Le statut est porté par les pastilles : il n'entre pas dans leurs propres compteurs.
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.get('type')).toBe('pacs.008');
    req.flush({ RECEIVED: 1 });
  });

  it('cancels an in-flight list request so the last one asked for wins', () => {
    svc.loadMessages({}, 0, 20);
    svc.loadMessages({}, 3, 20);

    const requests = ctrl.match((r) => r.url === '/messages');
    expect(requests).toHaveLength(2);
    expect(requests[0].cancelled).toBe(true);

    // La réponse tardive de la requête annulée ne doit pas écraser la page demandée.
    requests[1].flush({ ...emptyPage, number: 3 });
    expect(svc.currentPage()?.number).toBe(3);
  });

  it('exposes the server-side dashboard aggregates', () => {
    svc.loadDashboard();

    ctrl.expectOne('/messages/stats/dashboard').flush({
      windowFrom: '2026-07-24T15:00:00+02:00',
      windowTo: '2026-07-25T15:00:00+02:00',
      windowTotal: 42,
      lastReceivedAt: '2026-07-25T14:58:00+02:00',
      hourly: [{ bucketStart: '2026-07-24T15:00:00+02:00', hour: 15, count: 42 }],
      types: [{ messageType: 'pacs.008', count: 42 }],
      retries: { none: 40, one: 1, two: 0, threeOrMore: 1 },
      recentFailures: [],
    });

    expect(svc.dashboard()?.windowTotal).toBe(42);
    expect(svc.dashboard()?.hourly).toHaveLength(1);
  });

  it('loads the type list once', async () => {
    svc.loadMessageTypes();
    // `httpResource` décrit la requête à partir d'un signal : elle part au cycle suivant, pas
    // dans l'appel lui-même, et la valeur reçue est publiée de façon asynchrone.
    TestBed.tick();
    ctrl.expectOne('/messages/types').flush(['pacs.002', 'pacs.008']);
    await TestBed.inject(ApplicationRef).whenStable();

    svc.loadMessageTypes();
    TestBed.tick();
    ctrl.expectNone('/messages/types');
    expect(svc.messageTypes()).toEqual(['pacs.002', 'pacs.008']);
  });

  it('cancels the previous message request when another one is opened', async () => {
    svc.loadMessage(1);
    TestBed.tick();
    svc.loadMessage(2);
    TestBed.tick();

    const requests = ctrl.match((r) => r.url.startsWith('/messages/'));
    expect(requests.map((r) => r.request.url)).toEqual(['/messages/1', '/messages/2']);
    expect(requests[0].cancelled).toBe(true);

    requests[1].flush({ id: 2, reference: 'REF-2' });
    await TestBed.inject(ApplicationRef).whenStable();
    expect(svc.currentMessage()?.id).toBe(2);
  });

  it('only replays views that have been loaded at least once', () => {
    svc.refreshAll();

    // Aucune liste ni agrégat chargés : seuls les compteurs sont rejoués.
    ctrl.expectOne((r) => r.url === '/messages/stats').flush({});
    ctrl.expectNone((r) => r.url === '/messages');
    ctrl.expectNone('/messages/stats/dashboard');
  });
});
