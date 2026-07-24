import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string) {
    this.snackBar.open(message, 'Fermer', { duration: 3000, panelClass: 'snackbar-success' });
  }

  error(message: string) {
    this.snackBar.open(message, 'Fermer', { duration: 5000, panelClass: 'snackbar-error' });
  }
}
