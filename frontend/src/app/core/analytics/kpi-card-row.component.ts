import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

export interface KpiCardItem {
  label: string;
  value: string;
  hint?: string;
  tone?: 'default' | 'good' | 'warn';
}

@Component({
  selector: 'app-kpi-card-row',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
      <div
        *ngFor="let item of items"
        class="rounded-xl border p-4 bg-white dark:bg-secondary-800"
        [ngClass]="toneClasses(item.tone)">
        <p class="text-xs font-medium uppercase tracking-wide opacity-80">{{ item.label }}</p>
        <p class="mt-2 text-2xl font-semibold">{{ item.value }}</p>
        <p *ngIf="item.hint" class="mt-1 text-xs opacity-80">{{ item.hint }}</p>
      </div>
    </div>
  `
})
export class KpiCardRowComponent {
  @Input() items: KpiCardItem[] = [];

  toneClasses(tone?: KpiCardItem['tone']): string {
    if (tone === 'good') {
      return 'border-emerald-300/80 text-emerald-900 dark:text-emerald-200';
    }
    if (tone === 'warn') {
      return 'border-amber-300/80 text-amber-900 dark:text-amber-200';
    }
    return 'border-secondary-200 dark:border-secondary-700 text-secondary-900 dark:text-white';
  }
}
