import { Component, input } from '@angular/core';

export type IconName =
  | 'dashboard' | 'messages' | 'play' | 'refresh' | 'search' | 'download'
  | 'chevron-left' | 'chevron-right' | 'close' | 'copy' | 'replay' | 'menu' | 'trash' | 'tune';

/**
 * Jeu d'icônes inline (SVG stroke, currentColor) — évite une dépendance à une
 * police d'icônes et garde le rendu identique à la maquette.
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 18 18" fill="none" aria-hidden="true">
      @switch (name()) {
        @case ('dashboard') {
          <rect x="1" y="1" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.6"/>
          <rect x="10" y="1" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.6"/>
          <rect x="1" y="10" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.6"/>
          <rect x="10" y="10" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.6"/>
        }
        @case ('messages') {
          <rect x="1.5" y="3" width="15" height="12" rx="2" stroke="currentColor" stroke-width="1.6"/>
          <line x1="4.5" y1="7" x2="13.5" y2="7" stroke="currentColor" stroke-width="1.6"/>
          <line x1="4.5" y1="10.5" x2="10.5" y2="10.5" stroke="currentColor" stroke-width="1.6"/>
        }
        @case ('play') {
          <polygon points="3,2 16.5,9 3,16" stroke="currentColor" stroke-width="1.6" fill="none" stroke-linejoin="round"/>
        }
        @case ('refresh') {
          <path d="M15 4v4h-4M3 14v-4h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M14.5 8a5.6 5.6 0 0 0-10-1.5M3.5 10a5.6 5.6 0 0 0 10 1.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        }
        @case ('replay') {
          <path d="M14 4.5v3.4h-3.4M4 13.5v-3.4h3.4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M13.6 7.9a5.2 5.2 0 0 0-9.3-1.3M4.4 10.1a5.2 5.2 0 0 0 9.3 1.3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        }
        @case ('search') {
          <circle cx="8" cy="8" r="5.5" stroke="currentColor" stroke-width="1.6"/>
          <line x1="12.2" y1="12.2" x2="16" y2="16" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        }
        @case ('download') {
          <path d="M9 2.5v8m0 0L5.6 7.1M9 10.5l3.4-3.4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M3 12.5v3h12v-3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        }
        @case ('tune') {
          <line x1="3" y1="4.5" x2="15" y2="4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          <line x1="3" y1="9" x2="15" y2="9" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          <line x1="3" y1="13.5" x2="15" y2="13.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
          <circle cx="6.5" cy="4.5" r="1.8" fill="#fff" stroke="currentColor" stroke-width="1.4"/>
          <circle cx="11.5" cy="9" r="1.8" fill="#fff" stroke="currentColor" stroke-width="1.4"/>
          <circle cx="6.5" cy="13.5" r="1.8" fill="#fff" stroke="currentColor" stroke-width="1.4"/>
        }
        @case ('chevron-left') {
          <path d="M11 4L6 9l5 5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>
        }
        @case ('chevron-right') {
          <path d="M7 4l5 5-5 5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>
        }
        @case ('close') {
          <path d="M4 4l10 10M14 4L4 14" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
        }
        @case ('copy') {
          <rect x="6" y="6" width="9" height="10" rx="1.5" stroke="currentColor" stroke-width="1.5"/>
          <path d="M12.5 6V4.5A1.5 1.5 0 0 0 11 3H4.5A1.5 1.5 0 0 0 3 4.5V12" stroke="currentColor" stroke-width="1.5"/>
        }
        @case ('trash') {
          <path d="M3.5 5h11M7.5 5V3.5h3V5M5 5l.8 9.5h6.4L13 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        }
        @case ('menu') {
          <line x1="3" y1="5" x2="15" y2="5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
          <line x1="3" y1="9" x2="15" y2="9" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
          <line x1="3" y1="13" x2="15" y2="13" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
        }
      }
    </svg>
  `,
  styles: [`:host { display: inline-flex; line-height: 0; }`]
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input(18);
}
