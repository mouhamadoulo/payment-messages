import { Component, input, computed } from '@angular/core';
import { NgApexchartsModule, ApexChart, ApexAxisChartSeries, ApexXAxis, ApexPlotOptions, ApexDataLabels, ApexGrid } from 'ng-apexcharts';

@Component({
  selector: 'app-apex-bar',
  standalone: true,
  imports: [NgApexchartsModule],
  template: `
    <apx-chart [series]="series()" [chart]="chart" [xaxis]="xaxis()" [colors]="colors()"
               [plotOptions]="plotOptions" [dataLabels]="dataLabels" [grid]="grid" />
  `,
})
export class ApexBarComponent {
  readonly data = input.required<number[]>();
  readonly categories = input.required<string[]>();
  readonly name = input<string>('Messages');
  readonly color = input<string>('#2F6BFF');
  protected readonly series = computed<ApexAxisChartSeries>(() => [{ name: this.name(), data: this.data() }]);
  protected readonly xaxis = computed<ApexXAxis>(() => ({ categories: this.categories() }));
  protected readonly colors = computed(() => [this.color()]);
  protected readonly chart: ApexChart = {
    type: 'bar', height: 300, fontFamily: 'Inter, sans-serif',
    toolbar: { show: false }, animations: { enabled: true, speed: 500 },
  };
  protected readonly plotOptions: ApexPlotOptions = { bar: { borderRadius: 6, columnWidth: '60%' } };
  protected readonly dataLabels: ApexDataLabels = { enabled: false };
  protected readonly grid: ApexGrid = { borderColor: '#ECEDF0', strokeDashArray: 4 };
}
