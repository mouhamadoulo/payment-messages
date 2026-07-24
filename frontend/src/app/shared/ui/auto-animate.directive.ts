import { Directive, ElementRef, afterNextRender, inject } from '@angular/core';
import autoAnimate from '@formkit/auto-animate';

/**
 * Anime automatiquement l'ajout / suppression / réordonnancement des enfants
 * directs de l'élément hôte (tri, pagination, filtres, alertes…).
 * Respecte `prefers-reduced-motion` (géré par la lib).
 */
@Directive({
  selector: '[appAutoAnimate]',
  standalone: true,
})
export class AutoAnimateDirective {
  constructor() {
    const el = inject(ElementRef<HTMLElement>);
    afterNextRender(() => autoAnimate(el.nativeElement));
  }
}
