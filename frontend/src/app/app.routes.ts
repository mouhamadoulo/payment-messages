import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    data: {
      title: 'Tableau de bord',
      subtitle: 'Supervision du routage des messages de paiement'
    },
    loadComponent: () =>
      import('./features/messages/pages/dashboard/dashboard.page').then((m) => m.DashboardPage)
  },
  {
    path: 'messages',
    data: {
      title: 'Messages',
      subtitle: 'Consultation des messages consommés depuis IBM MQ'
    },
    loadComponent: () =>
      import('./features/messages/pages/message-list/message-list.page').then((m) => m.MessageListPage)
  },
  {
    path: 'messages/:id',
    data: {
      title: 'Détail du message',
      subtitle: 'Métadonnées et payload MQ brut'
    },
    loadComponent: () =>
      import('./features/messages/pages/message-detail/message-detail.page').then((m) => m.MessageDetailPage)
  }
];
