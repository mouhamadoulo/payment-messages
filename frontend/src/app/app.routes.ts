import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    // Seule route publique : l'API est fermée, tout le reste exige un jeton.
    path: 'login',
    data: {
      title: 'Connexion',
      subtitle: ''
    },
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage)
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    data: {
      title: 'Tableau de bord',
      subtitle: 'Supervision des messages'
    },
    loadComponent: () =>
      import('./features/messages/pages/dashboard/dashboard.page').then((m) => m.DashboardPage)
  },
  {
    path: 'messages',
    canActivate: [authGuard],
    data: {
      title: 'Messages',
      subtitle: 'Consultation des messages consommés depuis IBM MQ'
    },
    loadComponent: () =>
      import('./features/messages/pages/message-list/message-list.page').then((m) => m.MessageListPage)
  },
  {
    path: 'messages/:id',
    canActivate: [authGuard],
    data: {
      title: 'Détail du message',
      subtitle: 'Métadonnées et payload MQ brut'
    },
    loadComponent: () =>
      import('./features/messages/pages/message-detail/message-detail.page').then((m) => m.MessageDetailPage)
  }
];
