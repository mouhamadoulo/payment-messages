import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { PaymentMessageStatus } from '../../models/message.model';
import { allowedTransitions } from '../../../../shared/config/status.config';

export interface SelectStatusData {
  /** Statut courant : détermine les cibles proposées. */
  current: PaymentMessageStatus;
}

export interface SelectStatusResult {
  status: PaymentMessageStatus;
  reason?: string;
}

/**
 * Ne propose que les statuts atteignables depuis l'état courant, et recueille le motif de
 * l'intervention — attendu par `PUT /{id}/status` pour tracer une reprise manuelle.
 */
@Component({
  standalone: true,
  imports: [MatDialogModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule, FormsModule],
  template: `
    <h2 mat-dialog-title>Changer le statut</h2>
    <mat-dialog-content>
      @if (statuses.length === 0) {
        <p class="terminal">
          {{ data.current }} est un statut terminal : aucun changement n'est possible.
        </p>
      } @else {
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nouveau statut</mat-label>
          <mat-select [ngModel]="selectedStatus()" (ngModelChange)="selectedStatus.set($event)">
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Motif (optionnel)</mat-label>
          <input matInput [ngModel]="reason()" (ngModelChange)="reason.set($event)" maxlength="500"
                 placeholder="Ex. rejeu après correction du référentiel" />
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-raised-button color="primary" [disabled]="!selectedStatus()" (click)="confirm()">
        Confirmer
      </button>
    </mat-dialog-actions>
  `,
  styles: ['.full-width { width: 100%; }', '.terminal { margin: 0; color: var(--muted); font-size: .88rem; }'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SelectStatusDialog {
  private readonly ref = inject(MatDialogRef<SelectStatusDialog, SelectStatusResult>);
  protected readonly data = inject<SelectStatusData>(MAT_DIALOG_DATA);
  protected readonly statuses = allowedTransitions(this.data.current);
  /** Saisie en signaux : en `OnPush`, l'état lu par le gabarit doit être réactif. */
  protected readonly selectedStatus = signal<PaymentMessageStatus | undefined>(undefined);
  protected readonly reason = signal('');

  protected confirm() {
    const status = this.selectedStatus();
    if (!status) return;
    this.ref.close({
      status,
      reason: this.reason().trim() || undefined
    });
  }
}
