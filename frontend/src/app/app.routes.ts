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
      subtitle: 'Supervision des messages'
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
    path: 'simulation',
    data: {
      title: "Simulation d'envoi",
      subtitle: 'Déposer des messages de test sur une file IBM MQ'
    },
    loadComponent: () =>
      import('./features/simulation/pages/simulation/simulation.page').then((m) => m.SimulationPage)
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
