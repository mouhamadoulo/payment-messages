import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { API_CONFIG } from '../config/api.config';

/**
 * Porte le jeton sur les appels API et centralise le traitement du 401.
 *
 * Le 401 est traité ici et nulle part ailleurs : sans cela, chaque service devrait gérer
 * l'expiration du jeton et afficherait un message d'erreur métier pour un problème de
 * session. La requête d'authentification est exclue, elle n'a pas de jeton à présenter.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();

  const isLogin = req.url.includes(API_CONFIG.login);
  const authorized =
    token && !isLogin
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authorized).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isLogin) {
        auth.logout(true);
      }
      return throwError(() => error);
    })
  );
};
