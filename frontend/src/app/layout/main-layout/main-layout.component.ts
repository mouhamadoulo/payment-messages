import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '../header/header.component';
import { SidebarComponent } from '../sidebar/sidebar.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, SidebarComponent],
  template: `
    <div class="shell" [class.menu-open]="menuOpen()">
      <aside class="rail"><app-sidebar (navigate)="menuOpen.set(false)" /></aside>
      <div class="scrim" (click)="menuOpen.set(false)"></div>
      <div class="col">
        <app-header (toggleMenu)="menuOpen.set(!menuOpen())" />
        <main class="content"><router-outlet /></main>
      </div>
    </div>
  `,
  styles: [`
    .shell { display: grid; grid-template-columns: 248px 1fr; height: 100dvh; }
    .rail { background: var(--surface); border-right: 1px solid var(--border); overflow-y: auto; }
    .col { display: flex; flex-direction: column; min-width: 0; }
    .content { flex: 1; overflow-y: auto; padding: var(--space-5); background: var(--bg); }
    .scrim { display: none; }
    @media (max-width: 900px) {
      .shell { grid-template-columns: 1fr; }
      .rail { position: fixed; z-index: 30; width: 248px; height: 100dvh;
              transform: translateX(-100%); transition: transform .2s ease; }
      .menu-open .rail { transform: none; }
      .menu-open .scrim { display: block; position: fixed; inset: 0; z-index: 20;
                          background: rgba(0,0,0,.3); }
    }
  `]
})
export class MainLayoutComponent {
  protected readonly menuOpen = signal(false);
}
