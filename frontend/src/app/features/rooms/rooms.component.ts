import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, Room, Zone, Feature } from '../../core/services/api.service';
import { DataQueryToolbarComponent, QuerySortOption, QueryViewOption } from '../../core/query/data-query-toolbar.component';
import { DataQueryState, DEFAULT_QUERY_STATE } from '../../core/query/query-state.model';
import { parseQueryStateFromParams, serializeQueryStateToParams } from '../../core/query/query-state-url.util';
import { QueryViewsService } from '../../core/query/query-views.service';
import { KpiCardItem, KpiCardRowComponent } from '../../core/analytics/kpi-card-row.component';
import { forkJoin } from 'rxjs';

interface RoomQueryViewPayload {
  queryState: DataQueryState;
  activeSortKey: string;
}

@Component({
  selector: 'app-rooms',
  standalone: true,
  imports: [CommonModule, FormsModule, DataQueryToolbarComponent, KpiCardRowComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Rooms</h1>
        <div class="flex gap-2">
          <button (click)="exportToCsv()" class="btn btn-secondary" [disabled]="rooms.length === 0">📤 Export CSV</button>
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="rooms.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Room</button>
        </div>
      </div>

      <app-data-query-toolbar
        [search]="queryState.search"
        [sortKey]="activeSortKey"
        [sortOptions]="sortOptions"
        [savedViews]="savedViewOptions"
        [selectedViewId]="selectedViewId"
        [resultCount]="displayedRooms.length"
        [totalCount]="rooms.length"
        searchPlaceholder="Search by room, zone, or feature"
        (searchChange)="onSearchChange($event)"
        (sortKeyChange)="onSortChange($event)"
        (savedViewChange)="onSavedViewChange($event)"
        (saveViewClick)="onSaveView()"
        (deleteViewClick)="onDeleteSelectedView()"
        (filtersClick)="onFiltersClick()"
        (resetClick)="resetQuery()">
      </app-data-query-toolbar>

      <app-kpi-card-row [items]="kpiCards"></app-kpi-card-row>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div class="card p-4">
          <h3 class="text-sm font-semibold mb-3">Capacity Buckets (Visible)</h3>
          <div class="space-y-2 text-sm">
            <div *ngFor="let bucket of capacityBuckets" class="flex items-center justify-between">
              <span class="text-secondary-700 dark:text-secondary-300">{{ bucket.label }}</span>
              <span class="font-medium">{{ bucket.count }}</span>
            </div>
          </div>
        </div>
        <div class="card p-4">
          <h3 class="text-sm font-semibold mb-3">Rooms Per Zone (Visible)</h3>
          <div class="space-y-2 text-sm">
            <div *ngFor="let zone of zoneDistribution" class="flex items-center justify-between">
              <span class="text-secondary-700 dark:text-secondary-300">{{ zone.name }}</span>
              <span class="font-medium">{{ zone.count }} rooms</span>
            </div>
          </div>
        </div>
      </div>
      <div class="card p-4">
        <h3 class="text-sm font-semibold mb-3">Quick Actions (Filtered Rooms)</h3>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div class="border border-secondary-200 dark:border-secondary-700 rounded-lg p-3">
            <p class="text-sm font-medium mb-2">Bulk Feature Update</p>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-2">
              <select [(ngModel)]="bulkFeatureAction" class="input">
                <option value="add">Add Feature</option>
                <option value="remove">Remove Feature</option>
              </select>
              <select [(ngModel)]="bulkFeatureName" class="input">
                <option value="">Select feature</option>
                <option *ngFor="let feature of features" [value]="feature.name">{{ feature.name }}</option>
              </select>
              <button class="btn btn-primary" (click)="applyBulkFeatureAction()" [disabled]="bulkApplying || !bulkFeatureName || bulkFeaturePreviewCount === 0">
                {{ bulkApplying ? 'Applying...' : 'Apply' }}
              </button>
            </div>
            <p class="text-xs text-secondary-500 mt-2">
              Will update {{ bulkFeaturePreviewCount }} of {{ displayedRooms.length }} visible rooms.
            </p>
          </div>
          <div class="border border-secondary-200 dark:border-secondary-700 rounded-lg p-3">
            <p class="text-sm font-medium mb-2">Bulk Capacity Adjust</p>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-2">
              <input type="number" [(ngModel)]="bulkCapacityDelta" class="input" placeholder="+/- seats">
              <div class="text-sm text-secondary-600 dark:text-secondary-400 flex items-center">
                Preview: {{ bulkCapacityPreviewCount }} rooms
              </div>
              <button class="btn btn-primary" (click)="applyBulkCapacityAdjust()" [disabled]="bulkApplying || !bulkCapacityDelta || bulkCapacityPreviewCount === 0">
                {{ bulkApplying ? 'Applying...' : 'Apply' }}
              </button>
            </div>
          </div>
        </div>
        <p *ngIf="bulkActionSummary" class="text-xs mt-3 text-secondary-600 dark:text-secondary-300">{{ bulkActionSummary }}</p>
      </div>
      <div *ngIf="queryState.sort.length > 1" class="text-xs text-secondary-500">
        Sort priority:
        <span *ngFor="let sort of queryState.sort; let i = index" class="mr-2">
          {{ i + 1 }}. {{ sort.field }} {{ sort.direction }}
        </span>
      </div>

      <div *ngIf="showFiltersPanel" class="card p-4">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div>
            <label class="label">Filter Match</label>
            <select [(ngModel)]="filterDraft.matchMode" class="input">
              <option value="all">Match all filters</option>
              <option value="any">Match any filter</option>
            </select>
          </div>
          <div>
            <label class="label">Zones</label>
            <div class="max-h-28 overflow-y-auto border border-secondary-300 dark:border-secondary-600 rounded-lg p-2 space-y-1">
              <label *ngFor="let z of zones" class="inline-flex items-center text-sm mr-3">
                <input
                  type="checkbox"
                  class="mr-2"
                  [checked]="isZoneFilterSelected(z.id)"
                  (change)="toggleZoneFilter(z.id)">
                {{ z.name }}
              </label>
            </div>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.zoneExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Capacity</label>
            <input type="number" [(ngModel)]="filterDraft.minCapacity" class="input" min="1">
          </div>
          <div>
            <label class="label">Max Capacity</label>
            <input type="number" [(ngModel)]="filterDraft.maxCapacity" class="input" min="1">
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.capacityExclude">
              Exclude capacity range
            </label>
          </div>
          <div>
            <label class="label">Feature Mode</label>
            <select [(ngModel)]="filterDraft.featureMode" class="input">
              <option value="all">Has all selected</option>
              <option value="any">Has any selected</option>
              <option value="missing">Missing selected</option>
            </select>
          </div>
          <div class="md:col-span-2">
            <label class="label">Feature Set</label>
            <div class="max-h-28 overflow-y-auto border border-secondary-300 dark:border-secondary-600 rounded-lg p-2 space-y-1">
              <label *ngFor="let f of features" class="inline-flex items-center text-sm mr-3">
                <input
                  type="checkbox"
                  class="mr-2"
                  [checked]="isFeatureFilterSelected(f.name)"
                  (change)="toggleFeatureFilter(f.name)">
                {{ f.name }}
              </label>
            </div>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.featureExclude">
              Exclude matches
            </label>
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
            Delete all {{ rooms.length }} rooms? This will also delete all lessons!
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
        <h2 class="text-lg font-semibold mb-4">{{ editingRoom ? 'Edit Room' : 'Add New Room' }}</h2>
        <form (ngSubmit)="saveRoom()" class="space-y-4">
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="label">Name</label>
              <input type="text" [(ngModel)]="formData.name" name="name" class="input" required>
            </div>
            <div>
              <label class="label">Capacity</label>
              <input type="number" [(ngModel)]="formData.capacity" name="capacity" class="input" required>
            </div>
            <div>
              <label class="label">Zone</label>
              <select [(ngModel)]="formData.zoneId" name="zoneId" class="input">
                <option [ngValue]="undefined">Select Zone</option>
                <option *ngFor="let zone of zones" [ngValue]="zone.id">{{ zone.name }}</option>
              </select>
            </div>
          </div>
          
          <!-- Features Selection -->
          <div *ngIf="features.length > 0">
            <label class="label mb-2">Features</label>
            <div class="flex flex-wrap gap-2">
              <label *ngFor="let feature of features" 
                     class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                     [class.bg-primary-100]="isFeatureSelected(feature.name)"
                     [class.border-primary-500]="isFeatureSelected(feature.name)"
                     [class.dark:bg-primary-900]="isFeatureSelected(feature.name)"
                     [class.border-secondary-300]="!isFeatureSelected(feature.name)"
                     [class.dark:border-secondary-600]="!isFeatureSelected(feature.name)">
                <input type="checkbox" 
                       [checked]="isFeatureSelected(feature.name)"
                       (change)="toggleFeature(feature.name)"
                       class="mr-2">
                <span class="text-sm">{{ feature.name }}</span>
              </label>
            </div>
          </div>
          <div *ngIf="features.length === 0" class="text-sm text-secondary-500">
            No features available. <a href="/features" class="text-primary-500 hover:underline">Add features first</a>
          </div>
          
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <!-- Table -->
      <div class="card overflow-hidden">
        <table class="w-full">
          <thead class="bg-secondary-100 dark:bg-secondary-700">
            <tr>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Capacity</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Zone</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Features</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let room of displayedRooms" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium text-secondary-900 dark:text-white">{{ room.name }}</td>
              <td class="px-6 py-4">{{ room.capacity }}</td>
              <td class="px-6 py-4">{{ room.zoneName || '-' }}</td>
              <td class="px-6 py-4">
                <span *ngFor="let f of room.features" class="badge bg-blue-100 text-blue-800 mr-1">{{ f }}</span>
              </td>
              <td class="px-6 py-4 text-right">
                <button (click)="editRoom(room)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteRoom(room.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="displayedRooms.length === 0" class="p-8 text-center text-secondary-500">No rooms found.</div>
      </div>
    </div>
  `,
  styles: []
})
export class RoomsComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private queryViews = inject(QueryViewsService);

  rooms: Room[] = [];
  zones: Zone[] = [];
  features: Feature[] = [];
  showAddForm = false;
  editingRoom: Room | null = null;
  formData = { name: '', capacity: 50, zoneId: undefined as number | undefined, featureNames: [] as string[] };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;
  importResult: any = null;
  bulkFeatureAction: 'add' | 'remove' = 'add';
  bulkFeatureName = '';
  bulkCapacityDelta: number | null = null;
  bulkApplying = false;
  bulkActionSummary = '';
  queryState: DataQueryState = { ...DEFAULT_QUERY_STATE, pagination: { page: 1, size: 1000 } };
  activeSortKey = '';
  showFiltersPanel = false;
  filterDraft = {
    matchMode: 'all' as 'all' | 'any',
    zoneIds: [] as number[],
    zoneExclude: false,
    minCapacity: null as number | null,
    maxCapacity: null as number | null,
    capacityExclude: false,
    featureNames: [] as string[],
    featureMode: 'all' as 'all' | 'any' | 'missing',
    featureExclude: false
  };
  sortDraft: string[] = ['', '', ''];
  sortOptions: QuerySortOption[] = [
    { key: 'name:asc', label: 'Name (A-Z)' },
    { key: 'name:desc', label: 'Name (Z-A)' },
    { key: 'capacity:asc', label: 'Capacity (Low-High)' },
    { key: 'capacity:desc', label: 'Capacity (High-Low)' },
    { key: 'zone:asc', label: 'Zone (A-Z)' },
    { key: 'zone:desc', label: 'Zone (Z-A)' },
    { key: 'featureCount:asc', label: 'Features (Low-High)' },
    { key: 'featureCount:desc', label: 'Features (High-Low)' }
  ];
  savedViewOptions: QueryViewOption[] = [];
  selectedViewId = '';

  get displayedRooms(): Room[] {
    const search = this.queryState.search.trim().toLowerCase();
    let rows = this.rooms;
    if (search) {
      rows = rows.filter(r => this.roomMatchesSearch(r, search));
    }
    rows = rows.filter(r => this.roomMatchesActiveFilters(r));
    const sortStack = this.queryState.sort.length > 0
      ? this.queryState.sort
      : this.parseSortStack([this.activeSortKey]);
    if (sortStack.length === 0) {
      return rows;
    }
    return [...rows].sort((a, b) => this.compareBySortStack(a, b, sortStack));
  }

  get kpiCards(): KpiCardItem[] {
    const displayed = this.displayedRooms;
    const total = displayed.length;
    const totalCapacity = displayed.reduce((sum, room) => sum + room.capacity, 0);
    const noFeatures = displayed.filter(room => (room.features || []).length === 0).length;
    const largestCapacity = displayed.reduce((max, room) => Math.max(max, room.capacity), 0);
    const smallestCapacity = displayed.length > 0
      ? displayed.reduce((min, room) => Math.min(min, room.capacity), displayed[0].capacity)
      : 0;

    return [
      { label: 'Visible Rooms', value: String(total), hint: `${this.rooms.length} total` },
      { label: 'Seat Capacity', value: totalCapacity.toLocaleString(), hint: 'Sum across visible rooms' },
      { label: 'Without Features', value: String(noFeatures), hint: 'Need setup', tone: noFeatures > 0 ? 'warn' : 'good' },
      { label: 'Largest / Smallest', value: `${largestCapacity} / ${smallestCapacity}`, hint: 'Max / min visible capacity' }
    ];
  }

  get bulkFeaturePreviewCount(): number {
    if (!this.bulkFeatureName) {
      return 0;
    }
    return this.displayedRooms.filter(room => {
      const hasFeature = (room.features || []).includes(this.bulkFeatureName);
      return this.bulkFeatureAction === 'add' ? !hasFeature : hasFeature;
    }).length;
  }

  get bulkCapacityPreviewCount(): number {
    if (!this.bulkCapacityDelta) {
      return 0;
    }
    return this.displayedRooms.filter(room => Math.max(1, room.capacity + Number(this.bulkCapacityDelta)) !== room.capacity).length;
  }

  get capacityBuckets(): Array<{ label: string; count: number }> {
    const displayed = this.displayedRooms;
    return [
      { label: '0-49', count: displayed.filter(room => room.capacity <= 49).length },
      { label: '50-99', count: displayed.filter(room => room.capacity >= 50 && room.capacity <= 99).length },
      { label: '100-199', count: displayed.filter(room => room.capacity >= 100 && room.capacity <= 199).length },
      { label: '200+', count: displayed.filter(room => room.capacity >= 200).length }
    ];
  }

  get zoneDistribution(): Array<{ name: string; count: number }> {
    const counts = new Map<string, number>();
    for (const room of this.displayedRooms) {
      const zoneName = room.zoneName || 'Unassigned';
      counts.set(zoneName, (counts.get(zoneName) || 0) + 1);
    }
    return Array.from(counts.entries())
      .map(([name, count]) => ({ name, count }))
      .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
  }

  ngOnInit() {
    this.hydrateQueryStateFromUrl();
    this.loadSavedViews();
    this.loadRooms();
    this.loadZones();
    this.loadFeatures();
  }

  loadRooms() { this.api.getRooms().subscribe({ next: (rooms) => this.rooms = rooms }); }
  loadZones() { this.api.getZones().subscribe({ next: (zones) => this.zones = zones }); }
  loadFeatures() { this.api.getFeatures().subscribe({ next: (features) => this.features = features }); }

  onSearchChange(value: string) {
    this.queryState = { ...this.queryState, search: value };
    this.syncQueryStateToUrl();
  }

  onSortChange(key: string) {
    this.activeSortKey = key;
    this.queryState = {
      ...this.queryState,
      sort: this.parseSortStack([key])
    };
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
    this.clearFilters();
    this.syncQueryStateToUrl();
  }

  onSavedViewChange(viewId: string) {
    this.selectedViewId = viewId;
    if (!viewId) {
      return;
    }
    const savedView = this.queryViews
      .list<RoomQueryViewPayload>('rooms')
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
    this.hydrateFilterDraftFromQueryFilters();
    this.hydrateSortDraftFromQuerySort();
    this.syncQueryStateToUrl();
  }

  onSaveView() {
    const name = window.prompt('Saved view name');
    if (!name || !name.trim()) {
      return;
    }
    const savedView = this.queryViews.save<RoomQueryViewPayload>('rooms', name.trim(), {
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
    this.queryViews.delete('rooms', this.selectedViewId);
    this.selectedViewId = '';
    this.loadSavedViews();
  }

  isFeatureSelected(featureName: string): boolean {
    return this.formData.featureNames.includes(featureName);
  }

  toggleFeature(featureName: string) {
    const index = this.formData.featureNames.indexOf(featureName);
    if (index > -1) {
      this.formData.featureNames.splice(index, 1);
    } else {
      this.formData.featureNames.push(featureName);
    }
  }

  isZoneFilterSelected(zoneId: number): boolean {
    return this.filterDraft.zoneIds.includes(zoneId);
  }

  toggleZoneFilter(zoneId: number) {
    const index = this.filterDraft.zoneIds.indexOf(zoneId);
    if (index > -1) {
      this.filterDraft.zoneIds.splice(index, 1);
    } else {
      this.filterDraft.zoneIds.push(zoneId);
    }
  }

  isFeatureFilterSelected(featureName: string): boolean {
    return this.filterDraft.featureNames.includes(featureName);
  }

  toggleFeatureFilter(featureName: string) {
    const index = this.filterDraft.featureNames.indexOf(featureName);
    if (index > -1) {
      this.filterDraft.featureNames.splice(index, 1);
    } else {
      this.filterDraft.featureNames.push(featureName);
    }
  }

  applyBulkFeatureAction() {
    if (!this.bulkFeatureName) {
      return;
    }
    const targetRooms = this.displayedRooms.filter(room => {
      const hasFeature = (room.features || []).includes(this.bulkFeatureName);
      return this.bulkFeatureAction === 'add' ? !hasFeature : hasFeature;
    });
    if (targetRooms.length === 0) {
      return;
    }

    this.bulkApplying = true;
    const updates = targetRooms.map(room => {
      const current = [...(room.features || [])];
      const nextFeatures = this.bulkFeatureAction === 'add'
        ? [...current, this.bulkFeatureName]
        : current.filter(feature => feature !== this.bulkFeatureName);
      return this.api.updateRoom(room.id, { featureNames: nextFeatures } as Partial<Room>);
    });

    forkJoin(updates).subscribe({
      next: () => {
        this.bulkApplying = false;
        this.bulkActionSummary = `${this.bulkFeatureAction === 'add' ? 'Added' : 'Removed'} "${this.bulkFeatureName}" for ${targetRooms.length} rooms.`;
        this.loadRooms();
      },
      error: (err) => {
        this.bulkApplying = false;
        this.bulkActionSummary = `Bulk feature update failed: ${err.error?.message || 'Unknown error'}`;
      }
    });
  }

  applyBulkCapacityAdjust() {
    if (!this.bulkCapacityDelta) {
      return;
    }
    const delta = Number(this.bulkCapacityDelta);
    const targetRooms = this.displayedRooms.filter(room => Math.max(1, room.capacity + delta) !== room.capacity);
    if (targetRooms.length === 0) {
      return;
    }

    this.bulkApplying = true;
    const updates = targetRooms.map(room => {
      const newCapacity = Math.max(1, room.capacity + delta);
      return this.api.updateRoom(room.id, { capacity: newCapacity });
    });

    forkJoin(updates).subscribe({
      next: () => {
        this.bulkApplying = false;
        this.bulkActionSummary = `Adjusted capacity by ${delta > 0 ? '+' : ''}${delta} for ${targetRooms.length} rooms.`;
        this.loadRooms();
      },
      error: (err) => {
        this.bulkApplying = false;
        this.bulkActionSummary = `Bulk capacity update failed: ${err.error?.message || 'Unknown error'}`;
      }
    });
  }

  saveRoom() {
    if (this.editingRoom) {
      this.api.updateRoom(this.editingRoom.id, this.formData).subscribe({
        next: () => { this.loadRooms(); this.cancelEdit(); }
      });
    } else {
      this.api.createRoom(this.formData).subscribe({
        next: () => { this.loadRooms(); this.cancelEdit(); }
      });
    }
  }

  editRoom(room: Room) {
    this.editingRoom = room;
    this.formData = {
      name: room.name,
      capacity: room.capacity,
      zoneId: room.zoneId,
      featureNames: room.features ? [...room.features] : []
    };
    this.showAddForm = true;
  }

  deleteRoom(id: number) {
    if (confirm('Delete this room?')) {
      this.api.deleteRoom(id).subscribe({ next: () => this.loadRooms() });
    }
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingRoom = null;
    this.formData = { name: '', capacity: 50, zoneId: undefined, featureNames: [] };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/rooms/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadRooms(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    this.importing = true;
    this.http.post<any>('http://localhost:8080/api/v1/bulk/rooms/import', formData).subscribe({
      next: () => { this.importing = false; this.loadRooms(); input.value = ''; },
      error: (err) => { this.importing = false; alert('Import failed: ' + (err.error?.message || 'Unknown error')); input.value = ''; }
    });
  }

  exportToCsv() {
    const headers = ['name', 'capacity', 'zone_name', 'features'];
    const rows = this.rooms.map(r => [
      r.name,
      String(r.capacity),
      r.zoneName || '',
      (r.features || []).join('|')
    ]);
    const csv = [headers.join(','), ...rows.map(r => r.map(v => this.escapeCSV(v)).join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;

    const timestamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
    a.download = `export_rooms_${timestamp}.csv`;

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
    if (this.filterDraft.zoneIds.length > 0) {
      filters.push({ field: 'zoneId', operator: 'in', value: [...this.filterDraft.zoneIds], exclude: this.filterDraft.zoneExclude });
    }
    if (this.filterDraft.minCapacity !== null && Number.isFinite(this.filterDraft.minCapacity)) {
      filters.push({ field: 'capacity', operator: 'gte', value: Number(this.filterDraft.minCapacity), exclude: this.filterDraft.capacityExclude });
    }
    if (this.filterDraft.maxCapacity !== null && Number.isFinite(this.filterDraft.maxCapacity)) {
      filters.push({ field: 'capacity', operator: 'lte', value: Number(this.filterDraft.maxCapacity), exclude: this.filterDraft.capacityExclude });
    }
    if (this.filterDraft.featureNames.length > 0) {
      const featureField =
        this.filterDraft.featureMode === 'all'
          ? 'featuresAll'
          : (this.filterDraft.featureMode === 'any' ? 'featuresAny' : 'featuresMissing');
      filters.push({ field: featureField, operator: 'in', value: [...this.filterDraft.featureNames], exclude: this.filterDraft.featureExclude });
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
    this.hydrateFilterDraftFromQueryFilters();
    this.hydrateSortDraftFromQuerySort();
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

  private hydrateFilterDraftFromQueryFilters() {
    this.resetFilterDraft();
    this.filterDraft.matchMode = this.queryState.matchMode;
    for (const filter of this.queryState.filters) {
      if (filter.field === 'zoneId' && filter.operator === 'eq') {
        this.filterDraft.zoneIds = [Number(filter.value)];
        this.filterDraft.zoneExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'zoneId' && filter.operator === 'in') {
        this.filterDraft.zoneIds = Array.isArray(filter.value)
          ? (filter.value as unknown[]).map(value => Number(value)).filter(Number.isFinite)
          : [];
        this.filterDraft.zoneExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'capacity' && filter.operator === 'gte') {
        this.filterDraft.minCapacity = Number(filter.value);
        this.filterDraft.capacityExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'capacity' && filter.operator === 'lte') {
        this.filterDraft.maxCapacity = Number(filter.value);
        this.filterDraft.capacityExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'features' && filter.operator === 'contains') {
        this.filterDraft.featureNames = [String(filter.value ?? '')];
        this.filterDraft.featureMode = 'any';
        this.filterDraft.featureExclude = Boolean(filter.exclude);
      }
      if ((filter.field === 'featuresAll' || filter.field === 'featuresAny' || filter.field === 'featuresMissing') && filter.operator === 'in') {
        this.filterDraft.featureNames = Array.isArray(filter.value)
          ? (filter.value as unknown[]).map(value => String(value))
          : [];
        this.filterDraft.featureMode =
          filter.field === 'featuresAll'
            ? 'all'
            : (filter.field === 'featuresAny' ? 'any' : 'missing');
        this.filterDraft.featureExclude = Boolean(filter.exclude);
      }
    }
  }

  private resetFilterDraft() {
    this.filterDraft = {
      matchMode: 'all',
      zoneIds: [],
      zoneExclude: false,
      minCapacity: null,
      maxCapacity: null,
      capacityExclude: false,
      featureNames: [],
      featureMode: 'all',
      featureExclude: false
    };
  }

  private roomMatchesSearch(room: Room, search: string): boolean {
    return [
      room.name,
      room.zoneName || '',
      ...(room.features || [])
    ].some(value => value.toLowerCase().includes(search));
  }

  private roomMatchesActiveFilters(room: Room): boolean {
    if (this.queryState.filters.length === 0) {
      return true;
    }
    const evaluations = this.queryState.filters.map(filter => this.evaluateRoomFilter(room, filter));
    return this.queryState.matchMode === 'any'
      ? evaluations.some(Boolean)
      : evaluations.every(Boolean);
  }

  private compareRooms(a: Room, b: Room, field: string, direction: 'asc' | 'desc'): number {
    const sign = direction === 'desc' ? -1 : 1;
    switch (field) {
      case 'name':
        return sign * a.name.localeCompare(b.name);
      case 'capacity':
        return sign * (a.capacity - b.capacity);
      case 'zone':
        return sign * (a.zoneName || '').localeCompare(b.zoneName || '');
      case 'featureCount':
        return sign * ((a.features || []).length - (b.features || []).length);
      default:
        return 0;
    }
  }

  private evaluateRoomFilter(room: Room, filter: DataQueryState['filters'][number]): boolean {
    let matched = true;
    if (filter.field === 'zoneId' && filter.operator === 'eq') {
      matched = room.zoneId === Number(filter.value);
    } else if (filter.field === 'zoneId' && filter.operator === 'in') {
      const zoneIds = Array.isArray(filter.value)
        ? (filter.value as unknown[]).map(value => Number(value)).filter(Number.isFinite)
        : [];
      matched = zoneIds.includes(room.zoneId);
    } else if (filter.field === 'capacity' && filter.operator === 'gte') {
      matched = room.capacity >= Number(filter.value);
    } else if (filter.field === 'capacity' && filter.operator === 'lte') {
      matched = room.capacity <= Number(filter.value);
    } else if (filter.field === 'features' && filter.operator === 'contains') {
      const feature = String(filter.value || '').toLowerCase();
      matched = room.features.some(f => f.toLowerCase().includes(feature));
    } else if (filter.field === 'featuresAll' && filter.operator === 'in') {
      const expected = Array.isArray(filter.value)
        ? (filter.value as unknown[]).map(value => String(value).toLowerCase())
        : [];
      const roomFeatures = (room.features || []).map(value => value.toLowerCase());
      matched = expected.every(feature => roomFeatures.includes(feature));
    } else if (filter.field === 'featuresAny' && filter.operator === 'in') {
      const expected = Array.isArray(filter.value)
        ? (filter.value as unknown[]).map(value => String(value).toLowerCase())
        : [];
      const roomFeatures = (room.features || []).map(value => value.toLowerCase());
      matched = expected.some(feature => roomFeatures.includes(feature));
    } else if (filter.field === 'featuresMissing' && filter.operator === 'in') {
      const expected = Array.isArray(filter.value)
        ? (filter.value as unknown[]).map(value => String(value).toLowerCase())
        : [];
      const roomFeatures = (room.features || []).map(value => value.toLowerCase());
      matched = expected.some(feature => !roomFeatures.includes(feature));
    }
    return filter.exclude ? !matched : matched;
  }

  private parseSortStack(keys: string[]): DataQueryState['sort'] {
    const seen = new Set<string>();
    const stack: DataQueryState['sort'] = [];
    for (const key of keys) {
      if (!key) {
        continue;
      }
      const [field, direction] = key.split(':') as [string, 'asc' | 'desc'];
      if (!field || !direction) {
        continue;
      }
      const id = `${field}:${direction}`;
      if (seen.has(id)) {
        continue;
      }
      seen.add(id);
      stack.push({ field, direction });
    }
    return stack;
  }

  private compareBySortStack(a: Room, b: Room, sortStack: DataQueryState['sort']): number {
    for (const sort of sortStack) {
      const value = this.compareRooms(a, b, sort.field, sort.direction);
      if (value !== 0) {
        return value;
      }
    }
    return 0;
  }

  private hydrateSortDraftFromQuerySort() {
    const sortKeys = this.queryState.sort.map(sort => `${sort.field}:${sort.direction}`);
    this.sortDraft = [
      sortKeys[0] || '',
      sortKeys[1] || '',
      sortKeys[2] || ''
    ];
  }

  private loadSavedViews() {
    this.savedViewOptions = this.queryViews.list('rooms').map(view => ({
      id: view.id,
      name: view.name
    }));
  }
}
