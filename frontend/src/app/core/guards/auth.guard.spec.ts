import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../auth/auth.service';

describe('authGuard', () => {
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
  });

  function run(url: string) {
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() =>
      authGuard({} as never, state)) as boolean | UrlTree;
  }

  it('redirects to the login page without a session, keeping the requested URL', () => {
    const result = run('/messages/42');

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree))
      .toBe('/login?returnUrl=%2Fmessages%2F42');
  });

  it('lets an open session through', () => {
    sessionStorage.setItem('pm.session',
      JSON.stringify({ token: 'jeton', username: 'admin', roles: ['ADMIN'] }));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    expect(run('/dashboard')).toBe(true);
  });

  /** Une entrée illisible ne doit pas ouvrir de session fantôme. */
  it('treats a corrupted stored session as absent', () => {
    sessionStorage.setItem('pm.session', 'pas du JSON');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    expect(run('/dashboard')).toBeInstanceOf(UrlTree);
    expect(sessionStorage.getItem('pm.session')).toBeNull();
  });
});
