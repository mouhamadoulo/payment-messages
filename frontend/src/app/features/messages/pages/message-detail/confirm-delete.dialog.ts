import { Component, inject } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

@Component({
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Confirmer la suppression</h2>
    <mat-dialog-content>
      Êtes-vous sûr de vouloir supprimer ce message ? Cette action est irréversible.
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-raised-button color="warn" (click)="confirm()">Supprimer</button>
    </mat-dialog-actions>
  `
})
export class ConfirmDeleteDialog {
  private readonly ref = inject(MatDialogRef<ConfirmDeleteDialog>);

  protected confirm() {
    this.ref.close(true);
  }
}
