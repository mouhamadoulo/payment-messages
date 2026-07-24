import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { apiInterceptor } from './api.interceptor';

describe('apiInterceptor', () => {
  let http: HttpClient; let ctrl: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
  });
  it('prefixes /api/v1 to relative paths', () => {
    http.get('/messages').subscribe();
    ctrl.expectOne('/api/v1/messages').flush([]);
  });
  it('leaves absolute URLs untouched', () => {
    http.get('https://x.test/y').subscribe();
    ctrl.expectOne('https://x.test/y').flush({});
  });
  it('does not double-prefix already-prefixed paths', () => {
    http.get('/api/v1/messages/stats').subscribe();
    ctrl.expectOne('/api/v1/messages/stats').flush({});
  });
});
