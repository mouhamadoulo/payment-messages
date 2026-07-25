import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';

/**
 * Point d'entrée de l'application : l'API étant fermée, aucune vue n'est exploitable sans
 * jeton. Rendue hors du gabarit principal (ni barre latérale ni bandeau), qui n'aurait
 * rien à afficher.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <form class="card" (ngSubmit)="submit()">
        <div class="brand">
          <span class="mark"><app-icon name="messages" [size]="18" /></span>
          <div>
            <h1>Payment Messages</h1>
            <p>Supervision des messages IBM MQ</p>
          </div>
        </div>

        @if (expired()) {
          <div class="notice">Session expirée, reconnectez-vous.</div>
        }
        @if (error()) {
          <div class="notice error">{{ error() }}</div>
        }

        <label>
          <span>Identifiant</span>
          <input name="username" autocomplete="username" required
                 [(ngModel)]="username" [disabled]="loading()" />
        </label>

        <label>
          <span>Mot de passe</span>
          <input name="password" type="password" autocomplete="current-password" required
                 [(ngModel)]="password" [disabled]="loading()" />
        </label>

        <button type="submit" [disabled]="loading() || !username || !password">
          {{ loading() ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
    </div>
  `,
  styles: [`
    .wrap { min-height: 100dvh; display: grid; place-items: center; background: var(--bg);
            padding: var(--space-4); }
    .card { width: 100%; max-width: 372px; background: var(--surface); border: 1px solid var(--border);
            border-radius: var(--radius-card); box-shadow: var(--shadow-card);
            padding: 30px 28px; display: flex; flex-direction: column; gap: var(--space-4); }

    .brand { display: flex; align-items: center; gap: 12px; margin-bottom: var(--space-2); }
    .mark { width: 38px; height: 38px; flex: none; border-radius: var(--radius-ctl);
            background: var(--primary); color: #fff; display: grid; place-items: center; }
    .brand h1 { margin: 0; font-size: 1.06rem; font-weight: 600; color: var(--text); letter-spacing: -.01em; }
    .brand p { margin: 2px 0 0; font-size: .78rem; color: var(--muted-2); }

    label { display: flex; flex-direction: column; gap: 6px; }
    label span { font-size: .78rem; font-weight: 600; color: var(--muted); }
    input { height: 40px; padding: 0 12px; font: inherit; font-size: .9rem; color: var(--text);
            background: var(--surface); border: 1px solid var(--ctl-border);
            border-radius: var(--radius-ctl); }
    input:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-soft); }

    button { height: 42px; margin-top: var(--space-2); font: inherit; font-size: .9rem; font-weight: 600;
             color: #fff; background: var(--primary); border: none; border-radius: var(--radius-ctl);
             cursor: pointer; }
    button:hover:not(:disabled) { background: var(--primary-dark); }
    button:disabled { opacity: .6; cursor: default; }

    .notice { padding: 10px 12px; border-radius: var(--radius-ctl); font-size: .8rem;
              background: var(--warning-soft); color: var(--warning); }
    .notice.error { background: var(--danger-soft); color: var(--danger); }
  `]
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected username = '';
  protected password = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expired = signal(this.route.snapshot.queryParamMap.has('expired'));

  protected submit() {
    if (this.loading()) return;

    this.loading.set(true);
    this.error.set(null);

    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        // Retour à la vue initialement demandée, sinon accueil.
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err: { status?: number }) => {
        this.loading.set(false);
        this.expired.set(false);
        this.error.set(err.status === 401
          ? 'Identifiants invalides.'
          : 'Service indisponible, réessayez.');
      }
    });
  }
}
