import { Component, input, output } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { DatePipe } from '@angular/common';
import { PaymentMessage } from '../../models/message.model';
import { Page } from '../../models/page.model';
import { StatusBadgeComponent } from '../../../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-message-table',
  standalone: true,
  imports: [MatTableModule, MatPaginatorModule, DatePipe, StatusBadgeComponent],
  template: `
    <div class="card">
      <table mat-table [dataSource]="messages()" class="table">
        <ng-container matColumnDef="reference">
          <th mat-header-cell *matHeaderCellDef>Référence</th>
          <td mat-cell *matCellDef="let msg">{{ msg.reference }}</td>
        </ng-container>

        <ng-container matColumnDef="messageId">
          <th mat-header-cell *matHeaderCellDef class="hide-sm">Message ID</th>
          <td mat-cell *matCellDef="let msg" class="hide-sm muted">{{ msg.messageId }}</td>
        </ng-container>

        <ng-container matColumnDef="messageType">
          <th mat-header-cell *matHeaderCellDef class="hide-sm">Type</th>
          <td mat-cell *matCellDef="let msg" class="hide-sm muted">{{ msg.messageType }}</td>
        </ng-container>

        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Statut</th>
          <td mat-cell *matCellDef="let msg"><app-status-badge [status]="msg.status" /></td>
        </ng-container>

        <ng-container matColumnDef="receivedAt">
          <th mat-header-cell *matHeaderCellDef class="hide-md">Reçu le</th>
          <td mat-cell *matCellDef="let msg" class="hide-md muted">{{ msg.receivedAt | date:'dd/MM/yyyy HH:mm' }}</td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let msg">
            <button class="view-btn" (click)="viewMessage(msg.id); $event.stopPropagation()">Voir</button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns;"
            (click)="viewMessage(row.id)" class="clickable-row"></tr>
      </table>

      @if (page(); as p) {
        <mat-paginator [length]="p.totalElements" [pageSize]="p.size" [pageIndex]="p.number"
                       [pageSizeOptions]="[10, 20, 50]" (page)="pageChange.emit($event)" showFirstLastButtons />
      }
    </div>
  `,
  styles: [`
    .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-card);
            box-shadow: var(--shadow-card); overflow: hidden; }
    .table { width: 100%; background: transparent; }
    .table th.mat-mdc-header-cell { font-size: .72rem; text-transform: uppercase; letter-spacing: .04em;
            color: var(--muted); font-weight: 600; border-bottom: 1px solid var(--border); background: var(--surface); }
    .table td.mat-mdc-cell { border-bottom: 1px solid var(--border); font-size: .88rem; color: var(--text); }
    .muted { color: var(--muted); }
    .view-btn { border: 1px solid var(--border); border-radius: 8px; background: var(--surface);
                color: var(--primary); font-weight: 600; font-size: .78rem; padding: 5px var(--space-3);
                cursor: pointer; font-family: inherit; }
    .view-btn:hover { background: var(--primary-soft); }
    .clickable-row { cursor: pointer; }
    .clickable-row:hover { background: var(--bg); }
    @media (max-width: 768px)  { .hide-sm { display: none !important; } }
    @media (max-width: 1024px) { .hide-md { display: none !important; } }
  `]
})
export class MessageTableComponent {
  readonly messages = input.required<PaymentMessage[]>();
  readonly page = input<Page<PaymentMessage> | null>(null);
  readonly pageChange = output<PageEvent>();
  protected readonly displayedColumns = ['reference', 'messageId', 'messageType', 'status', 'receivedAt', 'actions'];
  constructor(private readonly router: Router) {}
  protected viewMessage(id: number) { this.router.navigate(['/messages', id]); }
}
