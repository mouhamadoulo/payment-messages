import { Injectable, signal, effect } from '@angular/core';

export type Theme = 'light' | 'dark';
const STORAGE_KEY = 'pm-theme';

/**
 * Gère le thème clair/sombre : lit la préférence stockée (ou celle du système),
 * l'applique sur <html data-theme> et la persiste dans localStorage.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.initial());

  constructor() {
    effect(() => {
      const t = this.theme();
      document.documentElement.setAttribute('data-theme', t);
      try { localStorage.setItem(STORAGE_KEY, t); } catch { /* stockage indisponible */ }
    });
  }

  toggle() {
    this.theme.update((t) => (t === 'dark' ? 'light' : 'dark'));
  }

  private initial(): Theme {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') return saved;
    } catch { /* stockage indisponible */ }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
