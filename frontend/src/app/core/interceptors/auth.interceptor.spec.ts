import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { apiInterceptor } from './api.interceptor';
import { AuthService } from '../auth/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiInterceptor, authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  function openSession() {
    auth.login('admin', 'admin').subscribe();
    ctrl.expectOne('/api/v1/auth/login').flush({
      token: 'jeton-de-test', tokenType: 'Bearer', expiresIn: 3600,
      username: 'admin', roles: ['ADMIN']
    });
  }

  it('sends no Authorization header without a session', () => {
    http.get('/messages').subscribe();
    expect(ctrl.expectOne('/api/v1/messages').request.headers.has('Authorization')).toBe(false);
  });

  it('carries the token once the session is open', () => {
    openSession();

    http.get('/messages').subscribe();
    expect(ctrl.expectOne('/api/v1/messages').request.headers.get('Authorization'))
      .toBe('Bearer jeton-de-test');
  });

  // Le jeton n'existe pas encore : l'authentification ne doit pas se voir ajouter d'en-tête.
  it('never adds the header to the login request itself', () => {
    openSession();

    auth.login('admin', 'admin').subscribe();
    expect(ctrl.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('closes the session on a 401 answer', () => {
    openSession();
    // Implémentation neutralisée : la vraie navigue vers /login, absente du routeur de test.
    const logout = vi.spyOn(auth, 'logout').mockImplementation(() => undefined);

    http.get('/messages').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/v1/messages').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalledWith(true);
  });

  /** Un 403 est un défaut de rôle, pas de session : la session doit survivre. */
  it('keeps the session on a 403 answer', () => {
    openSession();

    http.delete('/messages/1').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/v1/messages/1').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(auth.isAuthenticated()).toBe(true);
  });
});
