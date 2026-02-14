import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-data-pagination',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="card p-3 flex flex-wrap items-center justify-between gap-3">
      <div class="text-xs text-secondary-500">
        Showing {{ startIndex }}-{{ endIndex }} of {{ totalItems }}
      </div>

      <div class="flex items-center gap-2">
        <label class="text-xs text-secondary-500">Rows</label>
        <select class="input h-9 min-w-[84px]" [ngModel]="pageSize" (ngModelChange)="pageSizeChange.emit(+($event))">
          <option [ngValue]="10">10</option>
          <option [ngValue]="25">25</option>
          <option [ngValue]="50">50</option>
          <option [ngValue]="100">100</option>
          <option [ngValue]="250">250</option>
        </select>
      </div>

      <div class="flex items-center gap-2">
        <button type="button" class="btn btn-secondary btn-sm" (click)="firstPage.emit()" [disabled]="page <= 1">First</button>
        <button type="button" class="btn btn-secondary btn-sm" (click)="prevPage.emit()" [disabled]="page <= 1">Prev</button>
        <span class="text-xs text-secondary-500 min-w-[96px] text-center">Page {{ page }} / {{ totalPages }}</span>
        <button type="button" class="btn btn-secondary btn-sm" (click)="nextPage.emit()" [disabled]="page >= totalPages">Next</button>
        <button type="button" class="btn btn-secondary btn-sm" (click)="lastPage.emit()" [disabled]="page >= totalPages">Last</button>
      </div>
    </div>
  `
})
export class DataPaginationComponent {
  @Input() page = 1;
  @Input() pageSize = 25;
  @Input() totalItems = 0;

  @Output() pageSizeChange = new EventEmitter<number>();
  @Output() firstPage = new EventEmitter<void>();
  @Output() prevPage = new EventEmitter<void>();
  @Output() nextPage = new EventEmitter<void>();
  @Output() lastPage = new EventEmitter<void>();

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.totalItems / Math.max(1, this.pageSize)));
  }

  get startIndex(): number {
    if (this.totalItems === 0) {
      return 0;
    }
    return (this.page - 1) * this.pageSize + 1;
  }

  get endIndex(): number {
    if (this.totalItems === 0) {
      return 0;
    }
    return Math.min(this.totalItems, this.page * this.pageSize);
  }
}
