import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [MainLayoutComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-main-layout />`
})
export class App {}
