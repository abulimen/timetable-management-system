import { Component, OnInit, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, Course, Lecturer, LecturerInsightsSummary } from '../../core/services/api.service';
import { DataQueryToolbarComponent, QuerySortOption, QueryViewOption } from '../../core/query/data-query-toolbar.component';
import { DataQueryState, DEFAULT_QUERY_STATE } from '../../core/query/query-state.model';
import { parseQueryStateFromParams, serializeQueryStateToParams } from '../../core/query/query-state-url.util';
import { QueryViewsService } from '../../core/query/query-views.service';
import { KpiCardItem, KpiCardRowComponent } from '../../core/analytics/kpi-card-row.component';

interface LecturerQueryViewPayload {
  queryState: DataQueryState;
  activeSortKey: string;
}

@Component({
  selector: 'app-lecturers',
  standalone: true,
  imports: [CommonModule, FormsModule, DataQueryToolbarComponent, KpiCardRowComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Lecturers</h1>
        <div class="flex gap-2">
          <button (click)="exportToCsv()" class="btn btn-secondary" [disabled]="lecturers.length === 0">📤 Export CSV</button>
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="lecturers.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Lecturer</button>
        </div>
      </div>

      <app-data-query-toolbar
        [search]="queryState.search"
        [sortKey]="activeSortKey"
        [sortOptions]="sortOptions"
        [savedViews]="savedViewOptions"
        [selectedViewId]="selectedViewId"
        [resultCount]="displayedLecturers.length"
        [totalCount]="lecturers.length"
        searchPlaceholder="Search by lecturer, email, or assignment load"
        (searchChange)="onSearchChange($event)"
        (sortKeyChange)="onSortChange($event)"
        (savedViewChange)="onSavedViewChange($event)"
        (saveViewClick)="onSaveView()"
        (deleteViewClick)="onDeleteSelectedView()"
        (filtersClick)="onFiltersClick()"
        (resetClick)="resetQuery()">
      </app-data-query-toolbar>
      <div *ngIf="queryState.sort.length > 1" class="text-xs text-secondary-500">
        Sort priority:
        <span *ngFor="let sort of queryState.sort; let i = index" class="mr-2">
          {{ i + 1 }}. {{ sort.field }} {{ sort.direction }}
        </span>
      </div>
      <app-kpi-card-row [items]="kpiCards"></app-kpi-card-row>
      <div *ngIf="lecturerInsights" class="card p-4 text-sm">
        <p class="font-semibold mb-2">Backend Snapshot</p>
        <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
          <div>Total: {{ lecturerInsights.totalLecturers }}</div>
          <div>No Email: {{ lecturerInsights.noEmailCount }}</div>
          <div>Unassigned: {{ lecturerInsights.unassignedCount }}</div>
          <div>Overloaded: {{ lecturerInsights.overloadedCount }}</div>
          <div>Threshold: {{ lecturerInsights.overloadThreshold }}</div>
        </div>
      </div>
      <div class="card p-4">
        <h3 class="text-sm font-semibold mb-3">Unavailability Density (Visible Lecturers)</h3>
        <div class="overflow-x-auto">
          <table class="w-full text-xs">
            <thead>
              <tr class="text-secondary-500">
                <th class="text-left px-2 py-1">Day</th>
                <th *ngFor="let slot of heatmapSlots" class="text-center px-2 py-1">{{ slot }}</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let day of heatmapDays" class="border-t border-secondary-200 dark:border-secondary-700">
                <td class="px-2 py-1 font-medium">{{ day }}</td>
                <td *ngFor="let slot of heatmapSlots" class="px-2 py-1">
                  <div
                    class="rounded px-1 py-0.5 text-center"
                    [ngClass]="densityToneClass(activeUnavailabilityDensity[day + '|' + slot])">
                    {{ activeUnavailabilityDensity[day + '|' + slot] || 0 }}
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div *ngIf="showFiltersPanel" class="card p-4">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div>
            <label class="label">Filter Match</label>
            <select [(ngModel)]="filterDraft.matchMode" class="input">
              <option value="all">Match all filters</option>
              <option value="any">Match any filters</option>
            </select>
          </div>
          <div>
            <label class="label">Email</label>
            <select [(ngModel)]="filterDraft.emailMode" class="input">
              <option value="all">All</option>
              <option value="has">Has Email</option>
              <option value="missing">Missing Email</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.emailExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Unavailability</label>
            <select [(ngModel)]="filterDraft.unavailabilityMode" class="input">
              <option value="all">All</option>
              <option value="none">None</option>
              <option value="has">Has entries</option>
              <option value="heavy">Heavy load (>= {{ heavyUnavailabilityThreshold }})</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.unavailabilityExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Assignment Status</label>
            <select [(ngModel)]="filterDraft.assignmentMode" class="input">
              <option value="all">All</option>
              <option value="assigned">Assigned courses</option>
              <option value="unassigned">Unassigned</option>
              <option value="heavy">Heavy load (>= {{ heavyAssignmentThreshold }})</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.assignmentExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Unavailability Count</label>
            <input type="number" [(ngModel)]="filterDraft.minUnavailability" class="input" min="0">
          </div>
        </div>
        <div class="mt-4 border-t border-secondary-200 dark:border-secondary-700 pt-4">
          <p class="text-sm font-medium mb-2">Advanced Sorting Priority</p>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div>
              <label class="label">Priority 1</label>
              <select [(ngModel)]="sortDraft[0]" class="input">
                <option value="">None</option>
                <option *ngFor="let option of sortOptions" [value]="option.key">{{ option.label }}</option>
              </select>
            </div>
            <div>
              <label class="label">Priority 2</label>
              <select [(ngModel)]="sortDraft[1]" class="input">
                <option value="">None</option>
                <option *ngFor="let option of sortOptions" [value]="option.key">{{ option.label }}</option>
              </select>
            </div>
            <div>
              <label class="label">Priority 3</label>
              <select [(ngModel)]="sortDraft[2]" class="input">
                <option value="">None</option>
                <option *ngFor="let option of sortOptions" [value]="option.key">{{ option.label }}</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex gap-2 mt-4">
          <button class="btn btn-primary" (click)="applyFilters()">Apply Filters</button>
          <button class="btn btn-secondary" (click)="clearFilters()">Clear Filters</button>
          <button class="btn btn-secondary" (click)="showFiltersPanel = false">Close</button>
        </div>
      </div>

      <!-- Bulk Delete Confirmation -->
      <div *ngIf="showDeleteAllConfirm" class="card p-4 bg-red-500/10 border border-red-500/50">
        <div class="flex items-center justify-between">
          <p class="text-sm text-red-600 dark:text-red-400">
            Delete all {{ lecturers.length }} lecturers? This will also delete courses and lessons!
          </p>
          <div class="flex gap-2">
            <button (click)="showDeleteAllConfirm = false" class="btn btn-secondary btn-sm">Cancel</button>
            <button (click)="deleteAllLecturers()" [disabled]="deleting" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              {{ deleting ? 'Deleting...' : 'Yes, Delete All' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Add/Edit Form -->
      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingLecturer ? 'Edit Lecturer' : 'Add New Lecturer' }}</h2>
        <form (ngSubmit)="saveLecturer()" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">Name</label>
              <input type="text" [(ngModel)]="formData.name" name="name" class="input" required>
            </div>
            <div>
              <label class="label">Email</label>
              <input type="email" [(ngModel)]="formData.email" name="email" class="input">
            </div>
          </div>
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <!-- CSV Format Help -->
      <div class="text-xs text-secondary-400">
        CSV Format: <code class="bg-secondary-100 dark:bg-secondary-700 px-1 rounded">name,email</code> (first row is header, skipped)
      </div>

      <!-- Table -->
      <div class="card overflow-hidden">
        <table class="w-full">
          <thead class="bg-secondary-100 dark:bg-secondary-700">
            <tr>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Email</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Assigned Courses</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Unavailability</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let lecturer of displayedLecturers" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium text-secondary-900 dark:text-white">{{ lecturer.name }}</td>
              <td class="px-6 py-4 text-secondary-500">{{ lecturer.email || '-' }}</td>
              <td class="px-6 py-4">{{ getAssignedCourseCount(lecturer.id) }}</td>
              <td class="px-6 py-4">
                <span *ngFor="let u of lecturer.unavailabilities" class="badge bg-red-100 text-red-800 mr-1 mb-1">
                  {{ u.dayOfWeek }} {{ u.startTime }}-{{ u.endTime }}
                </span>
                <span *ngIf="!lecturer.unavailabilities?.length" class="text-secondary-400">None</span>
              </td>
              <td class="px-6 py-4 text-right">
                <button (click)="editLecturer(lecturer)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteLecturer(lecturer.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="displayedLecturers.length === 0" class="p-8 text-center text-secondary-500">
          No lecturers found. Click "Add Lecturer" or "Import CSV" to add data.
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class LecturersComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private queryViews = inject(QueryViewsService);

  lecturers: Lecturer[] = [];
  courses: Course[] = [];
  lecturerInsights: LecturerInsightsSummary | null = null;
  showAddForm = false;
  editingLecturer: Lecturer | null = null;
  formData = { name: '', email: '' };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;
  importResult: any = null;
  queryState: DataQueryState = { ...DEFAULT_QUERY_STATE, pagination: { page: 1, size: 1000 } };
  activeSortKey = '';
  showFiltersPanel = false;
  filterDraft = {
    matchMode: 'all' as 'all' | 'any',
    emailMode: 'all' as 'all' | 'has' | 'missing',
    emailExclude: false,
    unavailabilityMode: 'all' as 'all' | 'none' | 'has' | 'heavy',
    unavailabilityExclude: false,
    assignmentMode: 'all' as 'all' | 'assigned' | 'unassigned' | 'heavy',
    assignmentExclude: false,
    minUnavailability: null as number | null
  };
  sortDraft: string[] = ['', '', ''];
  sortOptions: QuerySortOption[] = [
    { key: 'name:asc', label: 'Name (A-Z)' },
    { key: 'name:desc', label: 'Name (Z-A)' },
    { key: 'email:asc', label: 'Email (A-Z)' },
    { key: 'email:desc', label: 'Email (Z-A)' },
    { key: 'assignedCourses:asc', label: 'Assigned Courses (Low-High)' },
    { key: 'assignedCourses:desc', label: 'Assigned Courses (High-Low)' },
    { key: 'unavailability:asc', label: 'Unavailability (Low-High)' },
    { key: 'unavailability:desc', label: 'Unavailability (High-Low)' }
  ];
  savedViewOptions: QueryViewOption[] = [];
  selectedViewId = '';
  heavyUnavailabilityThreshold = 3;
  heavyAssignmentThreshold = 4;
  overloadedAssignmentThreshold = 5;
  readonly heatmapDays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  readonly heatmapSlots = ['08:00', '10:00', '12:00', '14:00', '16:00', '18:00'];

  get displayedLecturers(): Lecturer[] {
    const search = this.queryState.search.trim().toLowerCase();
    let rows = this.lecturers;
    if (search) {
      rows = rows.filter(l => this.lecturerMatchesSearch(l, search));
    }
    rows = rows.filter(l => this.lecturerMatchesFilters(l));
    const sortStack = this.queryState.sort.length > 0
      ? this.queryState.sort
      : this.parseSortStack([this.activeSortKey]);
    if (sortStack.length === 0) {
      return rows;
    }
    return [...rows].sort((a, b) => this.compareBySortStack(a, b, sortStack));
  }

  get kpiCards(): KpiCardItem[] {
    const visible = this.displayedLecturers;
    const noEmail = visible.filter(lecturer => !(lecturer.email || '').trim()).length;
    const unassigned = visible.filter(lecturer => this.getAssignedCourseCount(lecturer.id) === 0).length;
    const overloaded = visible.filter(lecturer => this.getAssignedCourseCount(lecturer.id) >= this.overloadedAssignmentThreshold).length;
    return [
      { label: 'Unassigned Lecturers', value: String(unassigned), hint: 'No assigned courses', tone: unassigned > 0 ? 'warn' : 'good' },
      { label: 'Overloaded Lecturers', value: String(overloaded), hint: `>= ${this.overloadedAssignmentThreshold} courses`, tone: overloaded > 0 ? 'warn' : 'good' },
      { label: 'Missing Email', value: String(noEmail), hint: 'Data quality', tone: noEmail > 0 ? 'warn' : 'good' },
      { label: 'Visible Lecturers', value: String(visible.length), hint: `${this.lecturers.length} total` }
    ];
  }

  get unavailabilityDensity(): Record<string, number> {
    const density: Record<string, number> = {};
    for (const day of this.heatmapDays) {
      for (const slot of this.heatmapSlots) {
        density[`${day}|${slot}`] = 0;
      }
    }
    for (const lecturer of this.displayedLecturers) {
      for (const entry of lecturer.unavailabilities || []) {
        const day = entry.dayOfWeek;
        if (!this.heatmapDays.includes(day)) {
          continue;
        }
        for (const slot of this.heatmapSlots) {
          if (this.isSlotCovered(entry.startTime, entry.endTime, slot)) {
            const key = `${day}|${slot}`;
            density[key] = (density[key] || 0) + 1;
          }
        }
      }
    }
    return density;
  }

  get activeUnavailabilityDensity(): Record<string, number> {
    if (this.shouldUseServerDensity() && this.lecturerInsights?.unavailabilityDensity) {
      return this.lecturerInsights.unavailabilityDensity;
    }
    return this.unavailabilityDensity;
  }

  ngOnInit() {
    this.hydrateQueryStateFromUrl();
    this.loadSavedViews();
    this.loadLecturers();
    this.loadCourses();
    this.loadLecturerInsights();
  }

  loadLecturers() {
    this.api.getLecturers().subscribe({ next: (lecturers) => this.lecturers = lecturers });
  }

  loadCourses() {
    this.api.getCourses().subscribe({ next: (courses) => this.courses = courses });
  }

  loadLecturerInsights() {
    this.api.getLecturerInsights().subscribe({ next: (summary) => this.lecturerInsights = summary });
  }

  getAssignedCourseCount(lecturerId: number): number {
    return this.courses.filter(course => course.lecturerId === lecturerId).length;
  }

  onSearchChange(value: string) {
    this.queryState = { ...this.queryState, search: value };
    this.syncQueryStateToUrl();
  }

  onSortChange(key: string) {
    this.activeSortKey = key;
    this.queryState = { ...this.queryState, sort: this.parseSortStack([key]) };
    this.hydrateSortDraftFromQuerySort();
    this.syncQueryStateToUrl();
  }

  onFiltersClick() {
    this.showFiltersPanel = !this.showFiltersPanel;
  }

  resetQuery() {
    this.queryState = { ...DEFAULT_QUERY_STATE, pagination: { page: 1, size: 1000 } };
    this.activeSortKey = '';
    this.selectedViewId = '';
    this.hydrateSortDraftFromQuerySort();
    this.resetFilterDraft();
    this.syncQueryStateToUrl();
  }

  onSavedViewChange(viewId: string) {
    this.selectedViewId = viewId;
    if (!viewId) {
      return;
    }
    const savedView = this.queryViews
      .list<LecturerQueryViewPayload>('lecturers')
      .find(view => view.id === viewId);
    if (!savedView) {
      return;
    }

    this.queryState = {
      ...DEFAULT_QUERY_STATE,
      ...savedView.payload.queryState,
      pagination: { page: 1, size: 1000 }
    };
    this.activeSortKey = savedView.payload.activeSortKey || '';
    this.hydrateFilterDraftFromFilters();
    this.hydrateSortDraftFromQuerySort();
    this.syncQueryStateToUrl();
  }

  onSaveView() {
    const name = window.prompt('Saved view name');
    if (!name || !name.trim()) {
      return;
    }
    const savedView = this.queryViews.save<LecturerQueryViewPayload>('lecturers', name.trim(), {
      queryState: this.queryState,
      activeSortKey: this.activeSortKey
    });
    this.loadSavedViews();
    this.selectedViewId = savedView.id;
  }

  onDeleteSelectedView() {
    if (!this.selectedViewId) {
      return;
    }
    this.queryViews.delete('lecturers', this.selectedViewId);
    this.selectedViewId = '';
    this.loadSavedViews();
  }

  saveLecturer() {
    if (this.editingLecturer) {
      this.api.updateLecturer(this.editingLecturer.id, this.formData).subscribe({
        next: () => { this.loadLecturers(); this.cancelEdit(); }
      });
    } else {
      this.api.createLecturer(this.formData).subscribe({
        next: () => { this.loadLecturers(); this.cancelEdit(); }
      });
    }
  }

  editLecturer(lecturer: Lecturer) {
    this.editingLecturer = lecturer;
    this.formData = { name: lecturer.name, email: lecturer.email || '' };
    this.showAddForm = true;
  }

  deleteLecturer(id: number) {
    if (confirm('Delete this lecturer?')) {
      this.api.deleteLecturer(id).subscribe({ next: () => this.loadLecturers() });
    }
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingLecturer = null;
    this.formData = { name: '', email: '' };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAllLecturers() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/lecturers/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadLecturers(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const formData = new FormData();
    formData.append('file', file);

    this.importing = true;
    this.importResult = null;

    this.http.post<any>('http://localhost:8080/api/v1/bulk/lecturers/import', formData).subscribe({
      next: (res) => {
        this.importing = false;
        this.importResult = res;
        this.loadLecturers();
        input.value = ''; // Reset file input
      },
      error: (err) => {
        this.importing = false;
        this.importResult = { status: 'FAILED', message: err.error?.message || 'Import failed' };
        input.value = '';
      }
    });
  }

  exportToCsv() {
    const headers = ['name', 'email'];
    const rows = this.lecturers.map(l => [l.name, l.email || '']);
    const csv = [headers.join(','), ...rows.map(r => r.map(v => this.escapeCSV(v)).join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;

    const timestamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
    a.download = `export_lecturers_${timestamp}.csv`;

    a.click();
    URL.revokeObjectURL(url);
  }

  private escapeCSV(value: string): string {
    if (value.includes(',') || value.includes('"') || value.includes('\n')) {
      return '"' + value.replace(/"/g, '""') + '"';
    }
    return value;
  }

  applyFilters() {
    const filters: DataQueryState['filters'] = [];
    if (this.filterDraft.emailMode === 'has') {
      filters.push({ field: 'email', operator: 'isNotNull', exclude: this.filterDraft.emailExclude });
    } else if (this.filterDraft.emailMode === 'missing') {
      filters.push({ field: 'email', operator: 'isNull', exclude: this.filterDraft.emailExclude });
    }

    if (this.filterDraft.unavailabilityMode === 'none') {
      filters.push({ field: 'unavailabilityCount', operator: 'eq', value: 0, exclude: this.filterDraft.unavailabilityExclude });
    } else if (this.filterDraft.unavailabilityMode === 'has') {
      filters.push({ field: 'unavailabilityCount', operator: 'gt', value: 0, exclude: this.filterDraft.unavailabilityExclude });
    } else if (this.filterDraft.unavailabilityMode === 'heavy') {
      filters.push({ field: 'unavailabilityCount', operator: 'gte', value: this.heavyUnavailabilityThreshold, exclude: this.filterDraft.unavailabilityExclude });
    }

    if (this.filterDraft.assignmentMode === 'assigned') {
      filters.push({ field: 'assignedCourseCount', operator: 'gt', value: 0, exclude: this.filterDraft.assignmentExclude });
    } else if (this.filterDraft.assignmentMode === 'unassigned') {
      filters.push({ field: 'assignedCourseCount', operator: 'eq', value: 0, exclude: this.filterDraft.assignmentExclude });
    } else if (this.filterDraft.assignmentMode === 'heavy') {
      filters.push({ field: 'assignedCourseCount', operator: 'gte', value: this.heavyAssignmentThreshold, exclude: this.filterDraft.assignmentExclude });
    }

    if (this.filterDraft.minUnavailability !== null && Number.isFinite(this.filterDraft.minUnavailability)) {
      filters.push({ field: 'unavailabilityCount', operator: 'gte', value: Number(this.filterDraft.minUnavailability) });
    }

    const sort = this.parseSortStack(this.sortDraft);
    this.queryState = { ...this.queryState, filters, matchMode: this.filterDraft.matchMode, sort };
    this.activeSortKey = sort[0] ? `${sort[0].field}:${sort[0].direction}` : '';
    this.syncQueryStateToUrl();
  }

  clearFilters() {
    this.resetFilterDraft();
    this.sortDraft = ['', '', ''];
    this.queryState = { ...this.queryState, filters: [], matchMode: 'all', sort: [] };
    this.activeSortKey = '';
    this.syncQueryStateToUrl();
  }

  private resetFilterDraft() {
    this.filterDraft = {
      matchMode: 'all',
      emailMode: 'all',
      emailExclude: false,
      unavailabilityMode: 'all',
      unavailabilityExclude: false,
      assignmentMode: 'all',
      assignmentExclude: false,
      minUnavailability: null
    };
  }

  private hydrateQueryStateFromUrl() {
    const parsed = parseQueryStateFromParams(this.route.snapshot.queryParams);
    this.queryState = {
      ...this.queryState,
      search: parsed.search,
      filters: parsed.filters,
      matchMode: parsed.matchMode,
      sort: parsed.sort
    };
    const firstSort = parsed.sort[0];
    this.activeSortKey = firstSort ? `${firstSort.field}:${firstSort.direction}` : '';
    this.hydrateFilterDraftFromFilters();
    this.hydrateSortDraftFromQuerySort();
  }

  private hydrateFilterDraftFromFilters() {
    this.resetFilterDraft();
    this.filterDraft.matchMode = this.queryState.matchMode;
    for (const filter of this.queryState.filters) {
      if (filter.field === 'email' && filter.operator === 'isNotNull') {
        this.filterDraft.emailMode = 'has';
        this.filterDraft.emailExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'email' && filter.operator === 'isNull') {
        this.filterDraft.emailMode = 'missing';
        this.filterDraft.emailExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'unavailabilityCount' && filter.operator === 'eq' && Number(filter.value) === 0) {
        this.filterDraft.unavailabilityMode = 'none';
        this.filterDraft.unavailabilityExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'unavailabilityCount' && filter.operator === 'gt') {
        this.filterDraft.unavailabilityMode = 'has';
        this.filterDraft.unavailabilityExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'unavailabilityCount' && filter.operator === 'gte' && Number(filter.value) === this.heavyUnavailabilityThreshold) {
        this.filterDraft.unavailabilityMode = 'heavy';
        this.filterDraft.unavailabilityExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'assignedCourseCount' && filter.operator === 'gt' && Number(filter.value) === 0) {
        this.filterDraft.assignmentMode = 'assigned';
        this.filterDraft.assignmentExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'assignedCourseCount' && filter.operator === 'eq' && Number(filter.value) === 0) {
        this.filterDraft.assignmentMode = 'unassigned';
        this.filterDraft.assignmentExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'assignedCourseCount' && filter.operator === 'gte' && Number(filter.value) === this.heavyAssignmentThreshold) {
        this.filterDraft.assignmentMode = 'heavy';
        this.filterDraft.assignmentExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'unavailabilityCount' && filter.operator === 'gte') {
        if (Number(filter.value) !== this.heavyUnavailabilityThreshold) {
          this.filterDraft.minUnavailability = Number(filter.value);
        }
      }
    }
  }

  private syncQueryStateToUrl() {
    const sort = this.queryState.sort.length > 0
      ? this.queryState.sort
      : (this.activeSortKey ? this.parseSortStack([this.activeSortKey]) : []);
    const params = serializeQueryStateToParams({
      ...this.queryState,
      sort
    });
    const queryParams: Record<string, string> = {};
    params.forEach((value, key) => {
      queryParams[key] = value;
    });
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      replaceUrl: true
    });
  }

  private lecturerMatchesSearch(lecturer: Lecturer, search: string): boolean {
    return [lecturer.name, lecturer.email || '', String(this.getAssignedCourseCount(lecturer.id))]
      .some(value => value.toLowerCase().includes(search));
  }

  private lecturerMatchesFilters(lecturer: Lecturer): boolean {
    if (this.queryState.filters.length === 0) {
      return true;
    }
    const evaluations = this.queryState.filters.map(filter => this.evaluateLecturerFilter(lecturer, filter));
    return this.queryState.matchMode === 'any'
      ? evaluations.some(Boolean)
      : evaluations.every(Boolean);
  }

  private evaluateLecturerFilter(lecturer: Lecturer, filter: DataQueryState['filters'][number]): boolean {
    const unavailabilityCount = lecturer.unavailabilities?.length || 0;
    const assignedCourseCount = this.getAssignedCourseCount(lecturer.id);
    let matched = true;
    if (filter.field === 'email' && filter.operator === 'isNull') {
      matched = (lecturer.email || '').trim().length === 0;
    } else if (filter.field === 'email' && filter.operator === 'isNotNull') {
      matched = (lecturer.email || '').trim().length > 0;
    } else if (filter.field === 'unavailabilityCount' && filter.operator === 'eq') {
      matched = unavailabilityCount === Number(filter.value);
    } else if (filter.field === 'unavailabilityCount' && filter.operator === 'gt') {
      matched = unavailabilityCount > Number(filter.value);
    } else if (filter.field === 'unavailabilityCount' && filter.operator === 'gte') {
      matched = unavailabilityCount >= Number(filter.value);
    } else if (filter.field === 'assignedCourseCount' && filter.operator === 'eq') {
      matched = assignedCourseCount === Number(filter.value);
    } else if (filter.field === 'assignedCourseCount' && filter.operator === 'gt') {
      matched = assignedCourseCount > Number(filter.value);
    } else if (filter.field === 'assignedCourseCount' && filter.operator === 'gte') {
      matched = assignedCourseCount >= Number(filter.value);
    }
    return filter.exclude ? !matched : matched;
  }

  private compareLecturers(a: Lecturer, b: Lecturer, field: string, direction: 'asc' | 'desc'): number {
    const sign = direction === 'desc' ? -1 : 1;
    switch (field) {
      case 'name':
        return sign * a.name.localeCompare(b.name);
      case 'email':
        return sign * (a.email || '').localeCompare(b.email || '');
      case 'assignedCourses':
        return sign * (this.getAssignedCourseCount(a.id) - this.getAssignedCourseCount(b.id));
      case 'unavailability':
        return sign * ((a.unavailabilities?.length || 0) - (b.unavailabilities?.length || 0));
      default:
        return 0;
    }
  }

  private parseSortStack(keys: string[]): DataQueryState['sort'] {
    const seen = new Set<string>();
    const stack: DataQueryState['sort'] = [];
    for (const key of keys) {
      if (!key) continue;
      const [field, direction] = key.split(':') as [string, 'asc' | 'desc'];
      if (!field || !direction) continue;
      const id = `${field}:${direction}`;
      if (seen.has(id)) continue;
      seen.add(id);
      stack.push({ field, direction });
    }
    return stack;
  }

  private compareBySortStack(a: Lecturer, b: Lecturer, sortStack: DataQueryState['sort']): number {
    for (const sort of sortStack) {
      const value = this.compareLecturers(a, b, sort.field, sort.direction);
      if (value !== 0) return value;
    }
    return 0;
  }

  private hydrateSortDraftFromQuerySort() {
    const sortKeys = this.queryState.sort.map(sort => `${sort.field}:${sort.direction}`);
    this.sortDraft = [sortKeys[0] || '', sortKeys[1] || '', sortKeys[2] || ''];
  }

  private loadSavedViews() {
    this.savedViewOptions = this.queryViews.list('lecturers').map(view => ({
      id: view.id,
      name: view.name
    }));
  }

  densityToneClass(count: number | undefined): string {
    const value = count || 0;
    if (value >= 8) {
      return 'bg-red-100 text-red-800';
    }
    if (value >= 4) {
      return 'bg-amber-100 text-amber-800';
    }
    if (value > 0) {
      return 'bg-emerald-100 text-emerald-800';
    }
    return 'bg-secondary-100 text-secondary-500';
  }

  private isSlotCovered(startTime: string, endTime: string, slot: string): boolean {
    const start = this.timeToMinutes(startTime);
    const end = this.timeToMinutes(endTime);
    const point = this.timeToMinutes(slot);
    return point >= start && point < end;
  }

  private shouldUseServerDensity(): boolean {
    const hasSearch = this.queryState.search.trim().length > 0;
    const hasFilters = this.queryState.filters.length > 0;
    return !hasSearch && !hasFilters;
  }

  private timeToMinutes(value: string): number {
    const [h, m] = value.split(':').map(Number);
    return (h || 0) * 60 + (m || 0);
  }
}
