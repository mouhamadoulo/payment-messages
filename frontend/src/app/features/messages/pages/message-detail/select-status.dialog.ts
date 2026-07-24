import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { PaymentMessageStatus } from '../../models/message.model';

@Component({
  standalone: true,
  imports: [MatDialogModule, MatFormFieldModule, MatSelectModule, MatButtonModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Changer le statut</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Nouveau statut</mat-label>
        <mat-select [(ngModel)]="selectedStatus">
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-raised-button color="primary" [disabled]="!selectedStatus" (click)="confirm()">
        Confirmer
      </button>
    </mat-dialog-actions>
  `,
  styles: ['.full-width { width: 100%; }']
})
export class SelectStatusDialog {
  private readonly ref = inject(MatDialogRef<SelectStatusDialog>);
  protected readonly statuses = Object.values(PaymentMessageStatus);
  protected selectedStatus?: PaymentMessageStatus;

  protected confirm() {
    if (this.selectedStatus) this.ref.close(this.selectedStatus);
  }
}
