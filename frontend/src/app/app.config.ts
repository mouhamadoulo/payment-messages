import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideNativeDateAdapter } from '@angular/material/core';
import { routes } from './app.routes';
import { apiInterceptor } from './core/interceptors/api.interceptor';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // apiInterceptor préfixe l'URL, authInterceptor pose le jeton : dans cet ordre, le second
    // voit l'URL finale et peut reconnaître l'appel d'authentification.
    provideHttpClient(withInterceptors([apiInterceptor, authInterceptor])),
    provideAnimations(),
    provideNativeDateAdapter()
  ]
};
