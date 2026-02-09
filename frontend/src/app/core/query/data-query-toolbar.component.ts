import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface QuerySortOption {
    key: string;
    label: string;
}

export interface QueryViewOption {
    id: string;
    name: string;
}

@Component({
    selector: 'app-data-query-toolbar',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
      <div class="card p-4">
        <div class="flex flex-wrap items-center gap-3">
          <input
            type="text"
            class="input min-w-[220px] flex-1"
            [ngModel]="search"
            (ngModelChange)="searchChange.emit($event)"
            [placeholder]="searchPlaceholder">

          <button type="button" class="btn btn-secondary" (click)="filtersClick.emit()">
            Filters
          </button>

          <select class="input min-w-[180px]" [ngModel]="sortKey" (ngModelChange)="sortKeyChange.emit($event)">
            <option [ngValue]="''">Sort</option>
            <option *ngFor="let opt of sortOptions" [ngValue]="opt.key">{{ opt.label }}</option>
          </select>

          <select
            *ngIf="savedViews.length > 0"
            class="input min-w-[180px]"
            [ngModel]="selectedViewId"
            (ngModelChange)="savedViewChange.emit($event)">
            <option [ngValue]="''">Saved Views</option>
            <option *ngFor="let view of savedViews" [ngValue]="view.id">{{ view.name }}</option>
          </select>

          <button type="button" class="btn btn-secondary" (click)="saveViewClick.emit()">Save View</button>
          <button
            type="button"
            class="btn btn-secondary"
            [disabled]="!selectedViewId"
            (click)="deleteViewClick.emit()">
            Delete View
          </button>

          <button type="button" class="btn btn-secondary" (click)="resetClick.emit()">Reset</button>
        </div>

        <p class="text-xs text-secondary-500 mt-2">{{ resultCount }} of {{ totalCount }} records</p>
      </div>
    `
})
export class DataQueryToolbarComponent {
    @Input() search = '';
    @Input() searchPlaceholder = 'Search...';
    @Input() sortKey = '';
    @Input() sortOptions: QuerySortOption[] = [];
    @Input() savedViews: QueryViewOption[] = [];
    @Input() selectedViewId = '';
    @Input() resultCount = 0;
    @Input() totalCount = 0;

    @Output() searchChange = new EventEmitter<string>();
    @Output() sortKeyChange = new EventEmitter<string>();
    @Output() savedViewChange = new EventEmitter<string>();
    @Output() saveViewClick = new EventEmitter<void>();
    @Output() deleteViewClick = new EventEmitter<void>();
    @Output() filtersClick = new EventEmitter<void>();
    @Output() resetClick = new EventEmitter<void>();
}
