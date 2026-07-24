import { Component, input, computed } from '@angular/core';
import { NgApexchartsModule, ApexChart, ApexPlotOptions } from 'ng-apexcharts';

@Component({
  selector: 'app-apex-gauge',
  standalone: true,
  imports: [NgApexchartsModule],
  template: `
    <apx-chart [series]="series()" [chart]="chart" [labels]="labels()"
               [colors]="colors()" [plotOptions]="plotOptions" />
  `,
})
export class ApexGaugeComponent {
  readonly value = input.required<number>();
  readonly label = input<string>('');
  readonly color = input<string>('#22C55E');
  protected readonly series = computed(() => [this.value()]);
  protected readonly labels = computed(() => [this.label()]);
  protected readonly colors = computed(() => [this.color()]);
  protected readonly chart: ApexChart = {
    type: 'radialBar', height: 300, fontFamily: 'Inter, sans-serif',
    animations: { enabled: true, speed: 600 },
  };
  protected readonly plotOptions: ApexPlotOptions = {
    radialBar: {
      hollow: { size: '62%' },
      dataLabels: {
        name: { fontSize: '13px', color: '#6F767E' },
        value: { fontSize: '30px', fontWeight: 700, color: '#1A1D1F',
                 formatter: (v: number) => `${Math.round(+v)}%` },
      },
    },
  };
}
