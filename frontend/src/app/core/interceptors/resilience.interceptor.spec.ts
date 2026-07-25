import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import {
  HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RETRY_ATTEMPTS, REQUEST_TIMEOUT_MS, resilienceInterceptor } from './resilience.interceptor';

describe('resilienceInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([resilienceInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    ctrl.verify();
    vi.useRealTimers();
  });

  /** Rejoue chaque tentative en échec en avançant la temporisation exponentielle. */
  function failAll(url: string, status: number, times: number) {
    for (let i = 0; i < times; i++) {
      ctrl.expectOne(url).flush('boom', { status, statusText: 'Server Error' });
      vi.advanceTimersByTime(2_000);
    }
  }

  it('retries an idempotent GET on a 5xx, then succeeds', () => {
    let body: unknown;
    http.get('/messages').subscribe((res) => (body = res));

    failAll('/messages', 503, 1);
    ctrl.expectOne('/messages').flush({ ok: true });

    expect(body).toEqual({ ok: true });
  });

  it('gives up after the configured number of attempts', () => {
    let failure: HttpErrorResponse | undefined;
    http.get('/messages').subscribe({ error: (e: HttpErrorResponse) => (failure = e) });

    // Tentative initiale + RETRY_ATTEMPTS reprises, toutes en échec.
    failAll('/messages', 500, RETRY_ATTEMPTS + 1);

    expect(failure?.status).toBe(500);
  });

  it('does not retry a 4xx: the request itself is at fault', () => {
    let failure: HttpErrorResponse | undefined;
    http.get('/messages/1').subscribe({ error: (e: HttpErrorResponse) => (failure = e) });

    ctrl.expectOne('/messages/1').flush('nope', { status: 404, statusText: 'Not Found' });
    vi.advanceTimersByTime(2_000);

    expect(failure?.status).toBe(404);
    ctrl.expectNone('/messages/1');
  });

  it('never retries a mutation, even on a 5xx', () => {
    let failure: HttpErrorResponse | undefined;
    http.post('/messages/1/retry', {}).subscribe({ error: (e: HttpErrorResponse) => (failure = e) });

    ctrl.expectOne('/messages/1/retry').flush('boom', { status: 502, statusText: 'Bad Gateway' });
    vi.advanceTimersByTime(2_000);

    expect(failure?.status).toBe(502);
    ctrl.expectNone('/messages/1/retry');
  });

  it('aborts a request that never answers', () => {
    let failed = false;
    http.get('/messages').subscribe({ error: () => (failed = true) });

    ctrl.expectOne('/messages');
    vi.advanceTimersByTime(REQUEST_TIMEOUT_MS + 1);

    expect(failed).toBe(true);
  });
});
