import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, Course, Feature, FeatureInsightsSummary, Room } from '../../core/services/api.service';
import { DataQueryToolbarComponent, QuerySortOption, QueryViewOption } from '../../core/query/data-query-toolbar.component';
import { DataQueryState, DEFAULT_QUERY_STATE } from '../../core/query/query-state.model';
import { parseQueryStateFromParams, serializeQueryStateToParams } from '../../core/query/query-state-url.util';
import { QueryViewsService } from '../../core/query/query-views.service';

interface FeatureQueryViewPayload {
  queryState: DataQueryState;
  activeSortKey: string;
}

@Component({
  selector: 'app-features',
  standalone: true,
  imports: [CommonModule, FormsModule, DataQueryToolbarComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Room Features</h1>
          <p class="text-secondary-500 text-sm mt-1">Capabilities that rooms can have (Projector, Lab Equipment, etc.)</p>
        </div>
        <div class="flex gap-2">
          <button (click)="exportToCsv()" class="btn btn-secondary" [disabled]="features.length === 0">📤 Export CSV</button>
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="features.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Feature</button>
        </div>
      </div>

      <app-data-query-toolbar
        [search]="queryState.search"
        [sortKey]="activeSortKey"
        [sortOptions]="sortOptions"
        [savedViews]="savedViewOptions"
        [selectedViewId]="selectedViewId"
        [resultCount]="displayedFeatures.length"
        [totalCount]="features.length"
        searchPlaceholder="Search by feature name"
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
      <div *ngIf="activeFeatureSnapshot" class="card p-4 text-sm">
        <p class="font-semibold mb-2">Backend Snapshot</p>
        <div class="grid grid-cols-2 md:grid-cols-3 gap-3">
          <div>Total Features: {{ activeFeatureSnapshot.totalFeatures }}</div>
          <div>Orphaned: {{ activeFeatureSnapshot.orphanedFeatures }}</div>
          <div>Scarcity Tracked: {{ activeFeatureSnapshot.features.length }}</div>
        </div>
      </div>

      <div *ngIf="showFiltersPanel" class="card p-4">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label class="label">Filter Match</label>
            <select [(ngModel)]="filterDraft.matchMode" class="input">
              <option value="all">Match all filters</option>
              <option value="any">Match any filters</option>
            </select>
          </div>
          <div>
            <label class="label">Name Prefix</label>
            <input type="text" [(ngModel)]="filterDraft.namePrefix" class="input" placeholder="e.g., Lab">
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.namePrefixExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Name Length</label>
            <input type="number" [(ngModel)]="filterDraft.minNameLength" class="input" min="1">
          </div>
          <div>
            <label class="label">Usage Status</label>
            <select [(ngModel)]="filterDraft.usageMode" class="input">
              <option value="all">All</option>
              <option value="usedByRooms">Used by rooms</option>
              <option value="requiredByCourses">Required by courses</option>
              <option value="orphaned">Orphaned (no room, no course)</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.usageExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Demand (courses)</label>
            <input type="number" [(ngModel)]="filterDraft.minDemandCount" class="input" min="0">
          </div>
          <div>
            <label class="label">Min Supply (rooms)</label>
            <input type="number" [(ngModel)]="filterDraft.minSupplyCount" class="input" min="0">
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
            Delete all {{ features.length }} features? This will remove them from all rooms.
          </p>
          <div class="flex gap-2">
            <button (click)="showDeleteAllConfirm = false" class="btn btn-secondary btn-sm">Cancel</button>
            <button (click)="deleteAll()" [disabled]="deleting" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              {{ deleting ? 'Deleting...' : 'Yes, Delete All' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Add/Edit Form -->
      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingFeature ? 'Edit Feature' : 'Add New Feature' }}</h2>
        <form (ngSubmit)="saveFeature()" class="space-y-4">
          <div>
            <label class="label">Feature Name</label>
            <input type="text" [(ngModel)]="formData.name" name="name" class="input" required 
                   placeholder="e.g., Projector, Whiteboard, Lab Equipment">
          </div>
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary" [disabled]="!formData.name">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <!-- Features Grid -->
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
        <div *ngFor="let feature of displayedFeatures" 
             class="card p-4 hover:shadow-lg transition-shadow">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-xl">🏷️</span>
              <span class="font-medium">{{ feature.name }}</span>
            </div>
            <div class="flex gap-1">
              <button (click)="editFeature(feature)" class="p-1 text-blue-600 hover:bg-blue-100 rounded">
                ✏️
              </button>
              <button (click)="deleteFeature(feature.id)" class="p-1 text-red-600 hover:bg-red-100 rounded">
                🗑️
              </button>
            </div>
          </div>
          <div class="mt-3 flex flex-wrap gap-1 text-xs">
            <span class="badge bg-blue-100 text-blue-800">Supply: {{ getSupplyCount(feature.name) }}</span>
            <span class="badge bg-emerald-100 text-emerald-800">Demand: {{ getDemandCount(feature.name) }}</span>
            <span class="badge bg-amber-100 text-amber-800">Scarcity: {{ formatScarcity(feature.name) }}</span>
          </div>
        </div>
      </div>

      <div *ngIf="displayedFeatures.length === 0" class="card p-8 text-center text-secondary-500">
        <p class="text-4xl mb-4">🏷️</p>
        <p>No features defined yet.</p>
        <p class="text-sm mt-2">Features represent room capabilities like projectors, lab equipment, computers, etc.</p>
        <button (click)="showAddForm = true" class="btn btn-primary mt-4">Add Your First Feature</button>
      </div>

      <!-- Usage Info -->
      <div class="card p-4 bg-blue-50 dark:bg-blue-900/20">
        <h3 class="font-semibold text-blue-800 dark:text-blue-200">💡 How Features Work</h3>
        <ul class="text-sm text-blue-700 dark:text-blue-300 mt-2 space-y-1">
          <li>• Features are capabilities that rooms can have (e.g., Projector, Wet Lab)</li>
          <li>• Assign features to rooms in the Rooms page</li>
          <li>• Courses can require specific features for scheduling</li>
          <li>• The solver will only schedule lessons in rooms with required features</li>
        </ul>
      </div>
    </div>
  `
})
export class FeaturesComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private queryViews = inject(QueryViewsService);

  features: Feature[] = [];
  rooms: Room[] = [];
  courses: Course[] = [];
  featureInsights: FeatureInsightsSummary | null = null;
  showAddForm = false;
  editingFeature: Feature | null = null;
  formData = { name: '' };
  showDeleteAllConfirm = false;
  deleting = false;
  queryState: DataQueryState = { ...DEFAULT_QUERY_STATE, pagination: { page: 1, size: 1000 } };
  activeSortKey = '';
  showFiltersPanel = false;
  filterDraft = {
    matchMode: 'all' as 'all' | 'any',
    namePrefix: '',
    namePrefixExclude: false,
    minNameLength: null as number | null,
    usageMode: 'all' as 'all' | 'usedByRooms' | 'requiredByCourses' | 'orphaned',
    usageExclude: false,
    minDemandCount: null as number | null,
    minSupplyCount: null as number | null
  };
  sortDraft: string[] = ['', '', ''];
  sortOptions: QuerySortOption[] = [
    { key: 'name:asc', label: 'Name (A-Z)' },
    { key: 'name:desc', label: 'Name (Z-A)' },
    { key: 'id:asc', label: 'ID (Low-High)' },
    { key: 'id:desc', label: 'ID (High-Low)' },
    { key: 'demandCount:asc', label: 'Demand (Low-High)' },
    { key: 'demandCount:desc', label: 'Demand (High-Low)' },
    { key: 'supplyCount:asc', label: 'Supply (Low-High)' },
    { key: 'supplyCount:desc', label: 'Supply (High-Low)' },
    { key: 'scarcityRatio:asc', label: 'Scarcity (Low-High)' },
    { key: 'scarcityRatio:desc', label: 'Scarcity (High-Low)' }
  ];
  savedViewOptions: QueryViewOption[] = [];
  selectedViewId = '';

  get displayedFeatures(): Feature[] {
    const search = this.queryState.search.trim().toLowerCase();
    let rows = this.features;
    if (search) {
      rows = rows.filter(f => f.name.toLowerCase().includes(search));
    }
    rows = rows.filter(f => this.featureMatchesFilters(f));
    const sortStack = this.queryState.sort.length > 0
      ? this.queryState.sort
      : this.parseSortStack([this.activeSortKey]);
    if (sortStack.length === 0) {
      return rows;
    }
    return [...rows].sort((a, b) => this.compareBySortStack(a, b, sortStack));
  }

  get activeFeatureSnapshot(): FeatureInsightsSummary | null {
    if (this.shouldUseServerSnapshot() && this.featureInsights) {
      return this.featureInsights;
    }
    const visible = this.displayedFeatures;
    const items = visible.map(feature => {
      const supplyCount = this.getSupplyCount(feature.name);
      const demandCount = this.getDemandCount(feature.name);
      const ratio = this.getScarcityRatio(feature.name);
      return {
        id: feature.id,
        name: feature.name,
        supplyCount,
        demandCount,
        scarcityRatio: Number.isFinite(ratio) ? ratio : null,
        unboundedScarcity: !Number.isFinite(ratio) && demandCount > 0
      };
    });
    return {
      totalFeatures: visible.length,
      orphanedFeatures: items.filter(item => item.supplyCount === 0 && item.demandCount === 0).length,
      features: items
    };
  }

  ngOnInit() {
    this.hydrateQueryStateFromUrl();
    this.loadSavedViews();
    this.loadFeatures();
    this.loadRooms();
    this.loadCourses();
    this.loadFeatureInsights();
  }

  loadFeatures() {
    this.api.getFeatures().subscribe({ next: (f) => this.features = f });
  }

  loadRooms() {
    this.api.getRooms().subscribe({ next: (rooms) => this.rooms = rooms });
  }

  loadCourses() {
    this.api.getCourses().subscribe({ next: (courses) => this.courses = courses });
  }

  loadFeatureInsights() {
    this.api.getFeatureInsights().subscribe({ next: (summary) => this.featureInsights = summary });
  }

  getSupplyCount(featureName: string): number {
    const key = featureName.toLowerCase();
    return this.rooms.filter(room => (room.features || []).some(feature => feature.toLowerCase() === key)).length;
  }

  getDemandCount(featureName: string): number {
    const key = featureName.toLowerCase();
    return this.courses.filter(course => (course.requiredFeatures || []).some(feature => feature.toLowerCase() === key)).length;
  }

  private getScarcityRatio(featureName: string): number {
    const supply = this.getSupplyCount(featureName);
    const demand = this.getDemandCount(featureName);
    if (supply <= 0) {
      return demand > 0 ? Number.POSITIVE_INFINITY : 0;
    }
    return demand / supply;
  }

  formatScarcity(featureName: string): string {
    const ratio = this.getScarcityRatio(featureName);
    if (!Number.isFinite(ratio)) {
      return 'INF';
    }
    return ratio.toFixed(2);
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
      .list<FeatureQueryViewPayload>('features')
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
    const savedView = this.queryViews.save<FeatureQueryViewPayload>('features', name.trim(), {
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
    this.queryViews.delete('features', this.selectedViewId);
    this.selectedViewId = '';
    this.loadSavedViews();
  }

  saveFeature() {
    if (this.editingFeature) {
      this.http.put<Feature>(`http://localhost:8080/api/v1/features/${this.editingFeature.id}`, this.formData)
        .subscribe({ next: () => { this.loadFeatures(); this.cancelEdit(); } });
    } else {
      this.api.createFeature(this.formData).subscribe({
        next: () => { this.loadFeatures(); this.cancelEdit(); }
      });
    }
  }

  editFeature(feature: Feature) {
    this.editingFeature = feature;
    this.formData = { name: feature.name };
    this.showAddForm = true;
  }

  deleteFeature(id: number) {
    if (confirm('Delete this feature?')) {
      this.api.deleteFeature(id).subscribe({ next: () => this.loadFeatures() });
    }
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingFeature = null;
    this.formData = { name: '' };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/features/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadFeatures(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  exportToCsv() {
    const headers = ['name'];
    const rows = this.features.map(f => [f.name]);
    const csv = [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;

    const timestamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
    a.download = `export_features_${timestamp}.csv`;

    a.click();
    URL.revokeObjectURL(url);
  }

  applyFilters() {
    const filters: DataQueryState['filters'] = [];
    if (this.filterDraft.namePrefix.trim()) {
      filters.push({ field: 'name', operator: 'startsWith', value: this.filterDraft.namePrefix.trim(), exclude: this.filterDraft.namePrefixExclude });
    }
    if (this.filterDraft.minNameLength !== null && Number.isFinite(this.filterDraft.minNameLength)) {
      filters.push({ field: 'nameLength', operator: 'gte', value: Number(this.filterDraft.minNameLength) });
    }
    if (this.filterDraft.usageMode !== 'all') {
      filters.push({ field: 'usageClass', operator: 'eq', value: this.filterDraft.usageMode, exclude: this.filterDraft.usageExclude });
    }
    if (this.filterDraft.minDemandCount !== null && Number.isFinite(this.filterDraft.minDemandCount)) {
      filters.push({ field: 'demandCount', operator: 'gte', value: Number(this.filterDraft.minDemandCount) });
    }
    if (this.filterDraft.minSupplyCount !== null && Number.isFinite(this.filterDraft.minSupplyCount)) {
      filters.push({ field: 'supplyCount', operator: 'gte', value: Number(this.filterDraft.minSupplyCount) });
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
      namePrefix: '',
      namePrefixExclude: false,
      minNameLength: null,
      usageMode: 'all',
      usageExclude: false,
      minDemandCount: null,
      minSupplyCount: null
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
      if (filter.field === 'name' && filter.operator === 'startsWith') {
        this.filterDraft.namePrefix = String(filter.value || '');
        this.filterDraft.namePrefixExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'nameLength' && filter.operator === 'gte') {
        this.filterDraft.minNameLength = Number(filter.value);
      }
      if (filter.field === 'usageClass' && filter.operator === 'eq') {
        this.filterDraft.usageMode = String(filter.value) as 'all' | 'usedByRooms' | 'requiredByCourses' | 'orphaned';
        this.filterDraft.usageExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'demandCount' && filter.operator === 'gte') {
        this.filterDraft.minDemandCount = Number(filter.value);
      }
      if (filter.field === 'supplyCount' && filter.operator === 'gte') {
        this.filterDraft.minSupplyCount = Number(filter.value);
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

  private featureMatchesFilters(feature: Feature): boolean {
    if (this.queryState.filters.length === 0) {
      return true;
    }
    const evaluations = this.queryState.filters.map(filter => this.evaluateFeatureFilter(feature, filter));
    return this.queryState.matchMode === 'any'
      ? evaluations.some(Boolean)
      : evaluations.every(Boolean);
  }

  private compareFeatures(a: Feature, b: Feature, field: string, direction: 'asc' | 'desc'): number {
    const sign = direction === 'desc' ? -1 : 1;
    switch (field) {
      case 'name':
        return sign * a.name.localeCompare(b.name);
      case 'id':
        return sign * (a.id - b.id);
      case 'demandCount':
        return sign * (this.getDemandCount(a.name) - this.getDemandCount(b.name));
      case 'supplyCount':
        return sign * (this.getSupplyCount(a.name) - this.getSupplyCount(b.name));
      case 'scarcityRatio': {
        const ratioA = this.getScarcityRatio(a.name);
        const ratioB = this.getScarcityRatio(b.name);
        if (!Number.isFinite(ratioA) && !Number.isFinite(ratioB)) {
          return 0;
        }
        if (!Number.isFinite(ratioA)) {
          return sign;
        }
        if (!Number.isFinite(ratioB)) {
          return -sign;
        }
        return sign * (ratioA - ratioB);
      }
      default:
        return 0;
    }
  }

  private evaluateFeatureFilter(feature: Feature, filter: DataQueryState['filters'][number]): boolean {
    let matched = true;
    if (filter.field === 'name' && filter.operator === 'startsWith') {
      matched = feature.name.toLowerCase().startsWith(String(filter.value || '').toLowerCase());
    } else if (filter.field === 'nameLength' && filter.operator === 'gte') {
      matched = feature.name.length >= Number(filter.value);
    } else if (filter.field === 'usageClass' && filter.operator === 'eq') {
      const usageClass = String(filter.value || '');
      const supply = this.getSupplyCount(feature.name);
      const demand = this.getDemandCount(feature.name);
      if (usageClass === 'usedByRooms') {
        matched = supply > 0;
      } else if (usageClass === 'requiredByCourses') {
        matched = demand > 0;
      } else if (usageClass === 'orphaned') {
        matched = supply === 0 && demand === 0;
      } else {
        matched = true;
      }
    } else if (filter.field === 'demandCount' && filter.operator === 'gte') {
      matched = this.getDemandCount(feature.name) >= Number(filter.value);
    } else if (filter.field === 'supplyCount' && filter.operator === 'gte') {
      matched = this.getSupplyCount(feature.name) >= Number(filter.value);
    }
    return filter.exclude ? !matched : matched;
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

  private compareBySortStack(a: Feature, b: Feature, sortStack: DataQueryState['sort']): number {
    for (const sort of sortStack) {
      const value = this.compareFeatures(a, b, sort.field, sort.direction);
      if (value !== 0) return value;
    }
    return 0;
  }

  private hydrateSortDraftFromQuerySort() {
    const sortKeys = this.queryState.sort.map(sort => `${sort.field}:${sort.direction}`);
    this.sortDraft = [sortKeys[0] || '', sortKeys[1] || '', sortKeys[2] || ''];
  }

  private loadSavedViews() {
    this.savedViewOptions = this.queryViews.list('features').map(view => ({
      id: view.id,
      name: view.name
    }));
  }

  private shouldUseServerSnapshot(): boolean {
    const hasSearch = this.queryState.search.trim().length > 0;
    const hasFilters = this.queryState.filters.length > 0;
    return !hasSearch && !hasFilters;
  }
}
