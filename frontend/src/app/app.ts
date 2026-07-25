import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';

/**
 * Hors session, le gabarit applicatif (barre latérale, bandeau, bouton d'actualisation)
 * n'a rien à afficher et ses appels API échoueraient tous : la vue de connexion est donc
 * rendue seule, directement dans le `router-outlet`.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [MainLayoutComponent, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (auth.isAuthenticated()) {
      <app-main-layout />
    } @else {
      <router-outlet />
    }
  `
})
export class App {
  protected readonly auth = inject(AuthService);
}
