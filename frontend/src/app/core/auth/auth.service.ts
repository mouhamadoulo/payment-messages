import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_CONFIG } from '../config/api.config';

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  roles: string[];
}

interface StoredSession {
  token: string;
  username: string;
  roles: string[];
}

/** Clé de stockage de la session. */
const STORAGE_KEY = 'pm.session';

/**
 * Session applicative : jeton, identité et rôles.
 *
 * Le jeton est conservé dans `sessionStorage` et non dans `localStorage` : il disparaît à
 * la fermeture de l'onglet, ce qui limite la fenêtre d'exploitation en cas de XSS et évite
 * qu'un poste partagé garde une session ouverte. L'état est exposé en signaux, comme le
 * reste de l'application.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<StoredSession | null>(readSession());

  readonly token = computed(() => this.session()?.token ?? null);
  readonly username = computed(() => this.session()?.username ?? null);
  readonly roles = computed(() => this.session()?.roles ?? []);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(API_CONFIG.login, { username, password })
      .pipe(tap((response) => this.open(response)));
  }

  /**
   * Ferme la session. `expired` distingue une déconnexion volontaire d'un jeton refusé par
   * le serveur, pour que la page de login puisse l'expliquer.
   */
  logout(expired = false) {
    this.session.set(null);
    sessionStorage.removeItem(STORAGE_KEY);
    this.router.navigate(['/login'], expired ? { queryParams: { expired: 1 } } : {});
  }

  private open(response: LoginResponse) {
    const session: StoredSession = {
      token: response.token,
      username: response.username,
      roles: response.roles ?? []
    };
    this.session.set(session);
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

/** Une entrée illisible (format changé, altérée) est traitée comme une absence de session. */
function readSession(): StoredSession | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as StoredSession;
    return parsed?.token ? parsed : null;
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}
