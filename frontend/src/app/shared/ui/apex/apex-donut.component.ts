import { Component, input } from '@angular/core';
import { NgApexchartsModule, ApexChart, ApexLegend, ApexDataLabels, ApexStroke, ApexTooltip } from 'ng-apexcharts';

@Component({
  selector: 'app-apex-donut',
  standalone: true,
  imports: [NgApexchartsModule],
  template: `
    <apx-chart [series]="series()" [chart]="chart" [labels]="labels()" [colors]="colors()"
               [legend]="legend" [dataLabels]="dataLabels" [stroke]="stroke" [tooltip]="tooltip" />
  `,
})
export class ApexDonutComponent {
  readonly series = input.required<number[]>();
  readonly labels = input.required<string[]>();
  readonly colors = input.required<string[]>();
  protected readonly chart: ApexChart = {
    type: 'donut', height: 300, fontFamily: 'Inter, sans-serif',
    animations: { enabled: true, speed: 500 },
  };
  protected readonly legend: ApexLegend = { position: 'bottom', fontSize: '13px' };
  protected readonly dataLabels: ApexDataLabels = { enabled: false };
  protected readonly stroke: ApexStroke = { width: 0 };
  protected readonly tooltip: ApexTooltip = { enabled: true };
}
