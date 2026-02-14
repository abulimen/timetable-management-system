import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, Room, Zone, ZoneInsightsSummary } from '../../core/services/api.service';
import { DataQueryToolbarComponent, QuerySortOption, QueryViewOption } from '../../core/query/data-query-toolbar.component';
import { DataPaginationComponent } from '../../core/query/data-pagination.component';
import { DataQueryState, DEFAULT_QUERY_STATE } from '../../core/query/query-state.model';
import { parseQueryStateFromParams, serializeQueryStateToParams } from '../../core/query/query-state-url.util';
import { QueryViewsService } from '../../core/query/query-views.service';

interface ZoneQueryViewPayload {
  queryState: DataQueryState;
  activeSortKey: string;
}

@Component({
  selector: 'app-zones',
  standalone: true,
  imports: [CommonModule, FormsModule, DataQueryToolbarComponent, DataPaginationComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Zones</h1>
        <div class="flex gap-2">
          <button (click)="exportToCsv()" class="btn btn-secondary" [disabled]="zones.length === 0">📤 Export CSV</button>
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="zones.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Zone</button>
        </div>
      </div>

      <app-data-query-toolbar
        [search]="queryState.search"
        [sortKey]="activeSortKey"
        [sortOptions]="sortOptions"
        [savedViews]="savedViewOptions"
        [selectedViewId]="selectedViewId"
        [resultCount]="filteredZones.length"
        [totalCount]="zones.length"
        searchPlaceholder="Search zones by name"
        (searchChange)="onSearchChange($event)"
        (sortKeyChange)="onSortChange($event)"
        (savedViewChange)="onSavedViewChange($event)"
        (saveViewClick)="onSaveView()"
        (deleteViewClick)="onDeleteSelectedView()"
        (filtersClick)="onFiltersClick()"
        (resetClick)="resetQuery()">
      </app-data-query-toolbar>
      <app-data-pagination
        [page]="queryState.pagination.page"
        [pageSize]="queryState.pagination.size"
        [totalItems]="filteredZones.length"
        (pageSizeChange)="onPageSizeChange($event)"
        (firstPage)="goToFirstPage()"
        (prevPage)="goToPrevPage()"
        (nextPage)="goToNextPage()"
        (lastPage)="goToLastPage()">
      </app-data-pagination>
      <div *ngIf="queryState.sort.length > 1" class="text-xs text-secondary-500">
        Sort priority:
        <span *ngFor="let sort of queryState.sort; let i = index" class="mr-2">
          {{ i + 1 }}. {{ sort.field }} {{ sort.direction }}
        </span>
      </div>
      <div *ngIf="activeZoneSnapshot" class="card p-4 text-sm">
        <p class="font-semibold mb-2">Backend Snapshot</p>
        <div class="grid grid-cols-2 md:grid-cols-5 gap-3">
          <div>Total Zones: {{ activeZoneSnapshot.totalZones }}</div>
          <div>Used: {{ activeZoneSnapshot.usedZones }}</div>
          <div>Unused: {{ activeZoneSnapshot.unusedZones }}</div>
          <div>Total Rooms: {{ activeZoneSnapshot.totalRooms }}</div>
          <div>Total Capacity: {{ activeZoneSnapshot.totalCapacity }}</div>
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
            <input type="text" [(ngModel)]="filterDraft.namePrefix" class="input" placeholder="e.g., Main">
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
            <label class="label">Usage</label>
            <select [(ngModel)]="filterDraft.usageMode" class="input">
              <option value="all">All</option>
              <option value="used">Used by rooms</option>
              <option value="unused">Unused</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.usageExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Room Count</label>
            <input type="number" [(ngModel)]="filterDraft.minRoomCount" class="input" min="0">
          </div>
          <div>
            <label class="label">Max Room Count</label>
            <input type="number" [(ngModel)]="filterDraft.maxRoomCount" class="input" min="0">
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

      <div *ngIf="showDeleteAllConfirm" class="card p-4 bg-red-500/10 border border-red-500/50">
        <div class="flex items-center justify-between">
          <p class="text-sm text-red-600 dark:text-red-400">Delete all {{ zones.length }} zones? This will also delete all rooms and lessons!</p>
          <div class="flex gap-2">
            <button (click)="showDeleteAllConfirm = false" class="btn btn-secondary btn-sm">Cancel</button>
            <button (click)="deleteAll()" [disabled]="deleting" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              {{ deleting ? 'Deleting...' : 'Yes, Delete All' }}
            </button>
          </div>
        </div>
      </div>

      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingZone ? 'Edit Zone' : 'Add New Zone' }}</h2>
        <form (ngSubmit)="saveZone()" class="space-y-4">
          <div>
            <label class="label">Name</label>
            <input type="text" [(ngModel)]="formData.name" name="name" class="input w-full max-w-md" required>
          </div>
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <div class="card overflow-hidden">
        <table class="w-full">
          <thead class="bg-secondary-100 dark:bg-secondary-700">
            <tr>
              <th class="text-left px-6 py-3 text-sm font-medium">ID</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Rooms</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Est. Capacity</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let zone of displayedZones" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4">{{ zone.id }}</td>
              <td class="px-6 py-4">{{ zone.name }}</td>
              <td class="px-6 py-4">{{ getZoneRoomCount(zone.id) }}</td>
              <td class="px-6 py-4">{{ getZoneCapacity(zone.id) }}</td>
              <td class="px-6 py-4 text-right">
                <button (click)="editZone(zone)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteZone(zone.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="displayedZones.length === 0" class="p-8 text-center text-secondary-500">No zones found.</div>
      </div>
    </div>
  `,
  styles: []
})
export class ZonesComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private queryViews = inject(QueryViewsService);

  zones: Zone[] = [];
  rooms: Room[] = [];
  zoneInsights: ZoneInsightsSummary | null = null;
  showAddForm = false;
  editingZone: Zone | null = null;
  formData = { name: '' };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;
  queryState: DataQueryState = { ...DEFAULT_QUERY_STATE };
  activeSortKey = '';
  showFiltersPanel = false;
  filterDraft = {
    matchMode: 'all' as 'all' | 'any',
    namePrefix: '',
    namePrefixExclude: false,
    minNameLength: null as number | null,
    usageMode: 'all' as 'all' | 'used' | 'unused',
    usageExclude: false,
    minRoomCount: null as number | null,
    maxRoomCount: null as number | null
  };
  sortDraft: string[] = ['', '', ''];
  sortOptions: QuerySortOption[] = [
    { key: 'name:asc', label: 'Name (A-Z)' },
    { key: 'name:desc', label: 'Name (Z-A)' },
    { key: 'id:asc', label: 'ID (Low-High)' },
    { key: 'id:desc', label: 'ID (High-Low)' },
    { key: 'roomCount:asc', label: 'Rooms (Low-High)' },
    { key: 'roomCount:desc', label: 'Rooms (High-Low)' },
    { key: 'capacity:asc', label: 'Capacity (Low-High)' },
    { key: 'capacity:desc', label: 'Capacity (High-Low)' }
  ];
  savedViewOptions: QueryViewOption[] = [];
  selectedViewId = '';

  get filteredZones(): Zone[] {
    const search = this.queryState.search.trim().toLowerCase();
    let rows = this.zones;
    if (search) {
      rows = rows.filter(z => z.name.toLowerCase().includes(search));
    }
    rows = rows.filter(z => this.zoneMatchesFilters(z));
    const sortStack = this.queryState.sort.length > 0
      ? this.queryState.sort
      : this.parseSortStack([this.activeSortKey]);
    if (sortStack.length === 0) {
      return rows;
    }
    return [...rows].sort((a, b) => this.compareBySortStack(a, b, sortStack));
  }

  get displayedZones(): Zone[] {
    const rows = this.filteredZones;
    const page = this.queryState.pagination.page;
    const size = this.queryState.pagination.size;
    const start = (page - 1) * size;
    return rows.slice(start, start + size);
  }

  get activeZoneSnapshot(): ZoneInsightsSummary | null {
    if (this.shouldUseServerSnapshot() && this.zoneInsights) {
      return this.zoneInsights;
    }
    const visible = this.filteredZones;
    const usedZones = visible.filter(zone => this.getZoneRoomCount(zone.id) > 0).length;
    const totalRooms = visible.reduce((sum, zone) => sum + this.getZoneRoomCount(zone.id), 0);
    const totalCapacity = visible.reduce((sum, zone) => sum + this.getZoneCapacity(zone.id), 0);
    return {
      totalZones: visible.length,
      usedZones,
      unusedZones: visible.length - usedZones,
      totalRooms,
      totalCapacity,
      zones: visible.map(zone => ({
        id: zone.id,
        name: zone.name,
        roomCount: this.getZoneRoomCount(zone.id),
        capacity: this.getZoneCapacity(zone.id)
      }))
    };
  }

  ngOnInit() {
    this.hydrateQueryStateFromUrl();
    this.loadSavedViews();
    this.loadZones();
    this.loadRooms();
    this.loadZoneInsights();
  }

  loadZones() { this.api.getZones().subscribe({ next: (zones) => this.zones = zones }); }
  loadRooms() { this.api.getRooms().subscribe({ next: (rooms) => this.rooms = rooms }); }
  loadZoneInsights() { this.api.getZoneInsights().subscribe({ next: (summary) => this.zoneInsights = summary }); }

  getZoneRoomCount(zoneId: number): number {
    return this.rooms.filter(room => room.zoneId === zoneId).length;
  }

  getZoneCapacity(zoneId: number): number {
    return this.rooms
      .filter(room => room.zoneId === zoneId)
      .reduce((sum, room) => sum + room.capacity, 0);
  }

  onSearchChange(value: string) {
    this.queryState = { ...this.queryState, search: value, pagination: { ...this.queryState.pagination, page: 1 } };
    this.syncQueryStateToUrl();
  }

  onSortChange(key: string) {
    this.activeSortKey = key;
    this.queryState = {
      ...this.queryState,
      sort: this.parseSortStack([key]),
      pagination: { ...this.queryState.pagination, page: 1 }
    };
    this.hydrateSortDraftFromQuerySort();
    this.syncQueryStateToUrl();
  }

  onFiltersClick() {
    this.showFiltersPanel = !this.showFiltersPanel;
  }

  resetQuery() {
    this.queryState = { ...DEFAULT_QUERY_STATE };
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
      .list<ZoneQueryViewPayload>('zones')
      .find(view => view.id === viewId);
    if (!savedView) {
      return;
    }

    this.queryState = {
      ...DEFAULT_QUERY_STATE,
      ...savedView.payload.queryState
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
    const savedView = this.queryViews.save<ZoneQueryViewPayload>('zones', name.trim(), {
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
    this.queryViews.delete('zones', this.selectedViewId);
    this.selectedViewId = '';
    this.loadSavedViews();
  }

  saveZone() {
    if (this.editingZone) {
      this.api.updateZone(this.editingZone.id, this.formData).subscribe({
        next: () => { this.loadZones(); this.cancelEdit(); }
      });
    } else {
      this.api.createZone(this.formData).subscribe({
        next: () => { this.loadZones(); this.cancelEdit(); }
      });
    }
  }

  editZone(zone: Zone) {
    this.editingZone = zone;
    this.formData = { name: zone.name };
    this.showAddForm = true;
  }

  deleteZone(id: number) {
    if (confirm('Delete this zone?')) {
      this.api.deleteZone(id).subscribe({ next: () => this.loadZones() });
    }
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingZone = null;
    this.formData = { name: '' };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/zones/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadZones(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    this.importing = true;
    this.http.post<any>('http://localhost:8080/api/v1/bulk/zones/import', formData).subscribe({
      next: () => { this.importing = false; this.loadZones(); input.value = ''; },
      error: (err) => { this.importing = false; alert('Import failed: ' + (err.error?.message || 'Unknown error')); input.value = ''; }
    });
  }

  exportToCsv() {
    const headers = ['name'];
    const rows = this.zones.map(z => [z.name]);
    const csv = [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;

    const timestamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
    a.download = `export_zones_${timestamp}.csv`;

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
      filters.push({ field: 'roomUsage', operator: 'eq', value: this.filterDraft.usageMode, exclude: this.filterDraft.usageExclude });
    }
    if (this.filterDraft.minRoomCount !== null && Number.isFinite(this.filterDraft.minRoomCount)) {
      filters.push({ field: 'roomCount', operator: 'gte', value: Number(this.filterDraft.minRoomCount) });
    }
    if (this.filterDraft.maxRoomCount !== null && Number.isFinite(this.filterDraft.maxRoomCount)) {
      filters.push({ field: 'roomCount', operator: 'lte', value: Number(this.filterDraft.maxRoomCount) });
    }
    const sort = this.parseSortStack(this.sortDraft);
    this.queryState = {
      ...this.queryState,
      filters,
      matchMode: this.filterDraft.matchMode,
      sort,
      pagination: { ...this.queryState.pagination, page: 1 }
    };
    this.activeSortKey = sort[0] ? `${sort[0].field}:${sort[0].direction}` : '';
    this.syncQueryStateToUrl();
  }

  clearFilters() {
    this.resetFilterDraft();
    this.sortDraft = ['', '', ''];
    this.queryState = {
      ...this.queryState,
      filters: [],
      matchMode: 'all',
      sort: [],
      pagination: { ...this.queryState.pagination, page: 1 }
    };
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
      minRoomCount: null,
      maxRoomCount: null
    };
  }

  private hydrateQueryStateFromUrl() {
    const parsed = parseQueryStateFromParams(this.route.snapshot.queryParams);
    this.queryState = {
      ...this.queryState,
      search: parsed.search,
      filters: parsed.filters,
      matchMode: parsed.matchMode,
      sort: parsed.sort,
      pagination: parsed.pagination
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
      if (filter.field === 'roomUsage' && filter.operator === 'eq') {
        this.filterDraft.usageMode = String(filter.value) as 'all' | 'used' | 'unused';
        this.filterDraft.usageExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'roomCount' && filter.operator === 'gte') {
        this.filterDraft.minRoomCount = Number(filter.value);
      }
      if (filter.field === 'roomCount' && filter.operator === 'lte') {
        this.filterDraft.maxRoomCount = Number(filter.value);
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

  private zoneMatchesFilters(zone: Zone): boolean {
    if (this.queryState.filters.length === 0) {
      return true;
    }
    const evaluations = this.queryState.filters.map(filter => this.evaluateZoneFilter(zone, filter));
    return this.queryState.matchMode === 'any'
      ? evaluations.some(Boolean)
      : evaluations.every(Boolean);
  }

  private compareZones(a: Zone, b: Zone, field: string, direction: 'asc' | 'desc'): number {
    const sign = direction === 'desc' ? -1 : 1;
    switch (field) {
      case 'name':
        return sign * a.name.localeCompare(b.name);
      case 'id':
        return sign * (a.id - b.id);
      case 'roomCount':
        return sign * (this.getZoneRoomCount(a.id) - this.getZoneRoomCount(b.id));
      case 'capacity':
        return sign * (this.getZoneCapacity(a.id) - this.getZoneCapacity(b.id));
      default:
        return 0;
    }
  }

  private evaluateZoneFilter(zone: Zone, filter: DataQueryState['filters'][number]): boolean {
    let matched = true;
    if (filter.field === 'name' && filter.operator === 'startsWith') {
      matched = zone.name.toLowerCase().startsWith(String(filter.value || '').toLowerCase());
    } else if (filter.field === 'nameLength' && filter.operator === 'gte') {
      matched = zone.name.length >= Number(filter.value);
    } else if (filter.field === 'roomUsage' && filter.operator === 'eq') {
      const roomCount = this.getZoneRoomCount(zone.id);
      const mode = String(filter.value || '');
      matched = mode === 'used' ? roomCount > 0 : roomCount === 0;
    } else if (filter.field === 'roomCount' && filter.operator === 'gte') {
      matched = this.getZoneRoomCount(zone.id) >= Number(filter.value);
    } else if (filter.field === 'roomCount' && filter.operator === 'lte') {
      matched = this.getZoneRoomCount(zone.id) <= Number(filter.value);
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

  private compareBySortStack(a: Zone, b: Zone, sortStack: DataQueryState['sort']): number {
    for (const sort of sortStack) {
      const value = this.compareZones(a, b, sort.field, sort.direction);
      if (value !== 0) return value;
    }
    return 0;
  }

  onPageSizeChange(size: number) {
    this.queryState = { ...this.queryState, pagination: { page: 1, size } };
    this.syncQueryStateToUrl();
  }

  goToFirstPage() {
    this.queryState = { ...this.queryState, pagination: { ...this.queryState.pagination, page: 1 } };
    this.syncQueryStateToUrl();
  }

  goToPrevPage() {
    const page = Math.max(1, this.queryState.pagination.page - 1);
    this.queryState = { ...this.queryState, pagination: { ...this.queryState.pagination, page } };
    this.syncQueryStateToUrl();
  }

  goToNextPage() {
    const totalPages = Math.max(1, Math.ceil(this.filteredZones.length / this.queryState.pagination.size));
    const page = Math.min(totalPages, this.queryState.pagination.page + 1);
    this.queryState = { ...this.queryState, pagination: { ...this.queryState.pagination, page } };
    this.syncQueryStateToUrl();
  }

  goToLastPage() {
    const totalPages = Math.max(1, Math.ceil(this.filteredZones.length / this.queryState.pagination.size));
    this.queryState = { ...this.queryState, pagination: { ...this.queryState.pagination, page: totalPages } };
    this.syncQueryStateToUrl();
  }

  private hydrateSortDraftFromQuerySort() {
    const sortKeys = this.queryState.sort.map(sort => `${sort.field}:${sort.direction}`);
    this.sortDraft = [sortKeys[0] || '', sortKeys[1] || '', sortKeys[2] || ''];
  }

  private loadSavedViews() {
    this.savedViewOptions = this.queryViews.list('zones').map(view => ({
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
