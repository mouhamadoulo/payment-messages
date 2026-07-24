import { Component, output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from '../../features/messages/services/message.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [FormsModule],
  template: `
    <header class="bar">
      <button class="hamburger" (click)="toggleMenu.emit()" aria-label="Menu">
        <span></span><span></span><span></span>
      </button>

      <div class="search">
        <input type="text" placeholder="Rechercher une référence…"
               [ngModel]="svc.searchTerm()" (ngModelChange)="svc.searchTerm.set($event)" />
      </div>

      <div class="spacer"></div>

      <button class="text-btn" (click)="refresh()">Rafraîchir</button>
      <div class="avatar">PM</div>
    </header>
  `,
  styles: [`
    .bar { display: flex; align-items: center; gap: var(--space-3);
           padding: var(--space-3) var(--space-5); background: var(--surface);
           border-bottom: 1px solid var(--border); }
    .search { display: flex; align-items: center; background: var(--bg);
              border-radius: var(--radius-pill); padding: 8px var(--space-4); flex: 1; max-width: 420px; }
    .search input { border: none; background: none; outline: none; width: 100%;
                    font-size: .9rem; color: var(--text); font-family: inherit; }
    .spacer { flex: 1; }
    .text-btn { border: 1px solid var(--border); border-radius: 10px; background: var(--surface);
                color: var(--text); font-weight: 600; font-size: .82rem; padding: 9px var(--space-4);
                cursor: pointer; font-family: inherit; }
    .text-btn:hover { background: var(--bg); }
    .hamburger { display: none; flex-direction: column; gap: 4px; width: 38px; height: 38px;
                 align-items: center; justify-content: center; border: 1px solid var(--border);
                 border-radius: 10px; background: var(--surface); cursor: pointer; }
    .hamburger span { display: block; width: 18px; height: 2px; background: var(--muted); border-radius: 2px; }
    .avatar { width: 38px; height: 38px; border-radius: 50%; background: var(--primary-soft);
              color: var(--primary); display: grid; place-items: center; font-weight: 700; font-size: .8rem; }
    @media (max-width: 900px) { .hamburger { display: flex; } }
  `]
})
export class HeaderComponent {
  readonly toggleMenu = output<void>();
  protected readonly svc = inject(MessageService);
  protected refresh() {
    this.svc.loadStats();
    this.svc.loadMessages();
  }
}
