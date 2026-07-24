import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/messages/pages/dashboard/dashboard.page').then((m) => m.DashboardPage)
  },
  {
    path: 'messages',
    loadComponent: () =>
      import('./features/messages/pages/message-list/message-list.page').then((m) => m.MessageListPage)
  },
  {
    path: 'messages/:id',
    loadComponent: () =>
      import('./features/messages/pages/message-detail/message-detail.page').then((m) => m.MessageDetailPage)
  }
];
