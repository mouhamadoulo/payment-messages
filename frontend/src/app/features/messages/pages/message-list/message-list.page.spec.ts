import { describe, it, expect, beforeEach } from 'vitest';
import { Component, provideZonelessChangeDetection } from '@angular/core';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { MessageListPage } from './message-list.page';
import { MessageService } from '../../services/message.service';

const emptyPage = {
  content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, first: true, last: true,
};

/** Hôte minimal : la page est montée par le routeur, ses query params sont donc réels. */
@Component({ selector: 'app-test-host', standalone: true, imports: [RouterOutlet], template: '<router-outlet />' })
class TestHostComponent {}

describe('MessageListPage', () => {
  let ctrl: HttpTestingController;
  let router: Router;
  let fixture: ComponentFixture<TestHostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'messages', component: MessageListPage }]),
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    // Le rafraîchissement périodique ne doit pas parasiter les assertions.
    TestBed.inject(MessageService).autoRefresh.set(false);
    fixture = TestBed.createComponent(TestHostComponent);
  });

  /** Laisse filer la microtâche du routeur et des ressources avant d'observer. */
  const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

  /** Répond à tout ce qui est en vol, sans rien affirmer sur le contenu. */
  async function settle() {
    ctrl.match((r) => r.url === '/messages').forEach((r) => r.flush(emptyPage));
    ctrl.match((r) => r.url === '/messages/stats').forEach((r) => r.flush({ RECEIVED: 5, FAILED: 2 }));
    ctrl.match((r) => r.url === '/messages/types').forEach((r) => r.flush(['pacs.008']));
    await tick();
    fixture.detectChanges();
  }

  function chips(): HTMLButtonElement[] {
    return [...fixture.nativeElement.querySelectorAll('.chip')] as HTMLButtonElement[];
  }

  async function open(queryParams: Record<string, string> = {}) {
    await router.navigate(['/messages'], { queryParams });
    fixture.detectChanges();
    await settle();
    await settle();
  }

  it('re-queries the list and records the status in the URL when a chip is clicked', async () => {
    await open();
    expect(chips()[0].textContent).toContain('Tous');

    chips()[3].click(); // FAILED
    fixture.detectChanges();

    const list = ctrl.match((r) => r.url === '/messages');
    expect(list.at(-1)!.request.params.get('status')).toBe('FAILED');
    await settle();

    // L'URL porte le filtre : rechargement, favori et retour arrière le retrouvent.
    expect(router.url).toContain('status=FAILED');
    expect(fixture.nativeElement.querySelector('.chip.active').textContent).toContain('FAILED');
  });

  it('clears the filter when « Tous » is clicked on a list opened with ?status=', async () => {
    await open({ status: 'FAILED' });
    expect(fixture.nativeElement.querySelector('.chip.active').textContent).toContain('FAILED');

    chips()[0].click(); // Tous
    fixture.detectChanges();

    const list = ctrl.match((r) => r.url === '/messages');
    expect(list.at(-1)!.request.params.has('status')).toBe(false);
    await settle();
    expect(router.url).not.toContain('status=');
  });

  /**
   * La navigation déclenchée par la pastille ré-émet les query params : sans garde, elle
   * réécrivait les critères de la page et perdait le type, la date et la recherche.
   */
  it('keeps the other filters when the chip navigation replays the query params', async () => {
    await open();

    const select = fixture.nativeElement.querySelector('select.select') as HTMLSelectElement;
    select.value = 'pacs.008';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await settle();

    chips()[1].click(); // RECEIVED
    fixture.detectChanges();

    const list = ctrl.match((r) => r.url === '/messages');
    expect(list.at(-1)!.request.params.get('status')).toBe('RECEIVED');
    expect(list.at(-1)!.request.params.get('type')).toBe('pacs.008');
    await settle();
  });

  it('shows a retryable alert when the list request fails', async () => {
    await open();

    chips()[3].click();
    fixture.detectChanges();
    ctrl.match((r) => r.url === '/messages')
      .forEach((r) => r.flush('boom', { status: 500, statusText: 'Server Error' }));
    await settle();

    const alert = fixture.nativeElement.querySelector('.alert');
    expect(alert).not.toBeNull();

    (alert.querySelector('.retry') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(ctrl.match((r) => r.url === '/messages').length).toBeGreaterThan(0);
    await settle();

    expect(fixture.nativeElement.querySelector('.alert')).toBeNull();
  });
});
