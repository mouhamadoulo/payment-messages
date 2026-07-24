import { Component, input, computed } from '@angular/core';
import { PaymentMessageStatus } from '../../../features/messages/models/message.model';
import { statusMeta } from '../../config/status.config';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `
    <span class="badge" [style.color]="meta().color"
          [style.background]="meta().color + '1A'">
      {{ meta().label }}
    </span>
  `,
  styles: [`
    .badge {
      display: inline-flex; align-items: center;
      padding: 2px 10px; border-radius: var(--radius-pill);
      font-size: .75rem; font-weight: 600; white-space: nowrap;
    }
  `]
})
export class StatusBadgeComponent {
  readonly status = input.required<PaymentMessageStatus>();
  protected readonly meta = computed(() => statusMeta(this.status()));
}
