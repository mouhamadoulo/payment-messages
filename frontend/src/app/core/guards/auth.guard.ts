import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * Empêche l'affichage d'une vue qui ne pourrait de toute façon rien charger : sans jeton,
 * toutes les requêtes API répondent 401. L'URL demandée est conservée pour y revenir après
 * connexion.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
