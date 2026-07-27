import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideNativeDateAdapter } from '@angular/material/core';
import { routes } from './app.routes';
import { apiInterceptor } from './core/interceptors/api.interceptor';
import { resilienceInterceptor } from './core/interceptors/resilience.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // apiInterceptor préfixe l'URL, resilienceInterceptor ferme la marche : il rejoue la
    // requête définitive, et son délai maximal borne le temps réellement passé sur le réseau.
    // `withFetch()` : l'API fetch remplace XHR — annulation effective de la requête (et non du
    // seul abonnement) quand un `switchMap` ou une ressource abandonne, et prérequis d'une
    // éventuelle hydratation SSR.
    provideHttpClient(
      withFetch(),
      withInterceptors([apiInterceptor, resilienceInterceptor]),
    ),
    // Pas de `provideAnimations()` : toutes les animations du projet sont en CSS pur et
    // Angular Material 22 n'importe plus `@angular/animations`. Le moteur d'animations (et sa
    // dépendance) sont donc retirés plutôt que chargés en différé — `provideAnimationsAsync()`
    // est lui-même déprécié depuis la 20.2, avec suppression annoncée en v23.
    provideNativeDateAdapter()
  ]
};
