import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, StudentGroup } from '../../core/services/api.service';
import { DataQueryToolbarComponent, QuerySortOption, QueryViewOption } from '../../core/query/data-query-toolbar.component';
import { DataQueryState, DEFAULT_QUERY_STATE } from '../../core/query/query-state.model';
import { parseQueryStateFromParams, serializeQueryStateToParams } from '../../core/query/query-state-url.util';
import { QueryViewsService } from '../../core/query/query-views.service';
import { KpiCardItem, KpiCardRowComponent } from '../../core/analytics/kpi-card-row.component';

interface StudentGroupQueryViewPayload {
  queryState: DataQueryState;
  activeSortKey: string;
}

interface StudentGroupTreeRow {
  group: StudentGroup;
  depth: number;
  isParent: boolean;
  expandable: boolean;
  expanded: boolean;
}

@Component({
  selector: 'app-student-groups',
  standalone: true,
  imports: [CommonModule, FormsModule, DataQueryToolbarComponent, KpiCardRowComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Student Groups</h1>
        <div class="flex gap-2">
          <button (click)="exportToCsv()" class="btn btn-secondary" [disabled]="groups.length === 0">📤 Export CSV</button>
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="groups.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Group</button>
        </div>
      </div>

      <app-data-query-toolbar
        [search]="queryState.search"
        [sortKey]="activeSortKey"
        [sortOptions]="sortOptions"
        [savedViews]="savedViewOptions"
        [selectedViewId]="selectedViewId"
        [resultCount]="displayedRows.length"
        [totalCount]="groups.length"
        searchPlaceholder="Search by name, base name, level, or parent"
        (searchChange)="onSearchChange($event)"
        (sortKeyChange)="onSortChange($event)"
        (savedViewChange)="onSavedViewChange($event)"
        (saveViewClick)="onSaveView()"
        (deleteViewClick)="onDeleteSelectedView()"
        (filtersClick)="onFiltersClick()"
        (resetClick)="resetQuery()">
      </app-data-query-toolbar>
      <div class="flex items-center justify-between">
        <p class="text-xs text-secondary-500">View Mode</p>
        <div class="inline-flex rounded-lg border border-secondary-300 dark:border-secondary-600 overflow-hidden">
          <button
            class="px-3 py-1.5 text-sm"
            [class.bg-primary-600]="viewMode === 'flat'"
            [class.text-white]="viewMode === 'flat'"
            [class.bg-transparent]="viewMode !== 'flat'"
            (click)="viewMode = 'flat'">
            Flat
          </button>
          <button
            class="px-3 py-1.5 text-sm"
            [class.bg-primary-600]="viewMode === 'tree'"
            [class.text-white]="viewMode === 'tree'"
            [class.bg-transparent]="viewMode !== 'tree'"
            (click)="viewMode = 'tree'">
            Tree
          </button>
        </div>
      </div>
      <div *ngIf="queryState.sort.length > 1" class="text-xs text-secondary-500">
        Sort priority:
        <span *ngFor="let sort of queryState.sort; let i = index" class="mr-2">
          {{ i + 1 }}. {{ sort.field }} {{ sort.direction }}
        </span>
      </div>
      <app-kpi-card-row [items]="kpiCards"></app-kpi-card-row>
      <div class="card p-4">
        <div class="flex flex-wrap gap-2 text-xs">
          <span class="badge bg-red-100 text-red-800">Orphan Children: {{ warningCounts.orphanChild }}</span>
          <span class="badge bg-amber-100 text-amber-800">Parents Without Children: {{ warningCounts.parentWithoutChildren }}</span>
          <span class="badge bg-orange-100 text-orange-800">Oversized Children (>{{ oversizedChildThreshold }}): {{ warningCounts.oversizedChild }}</span>
          <span class="badge bg-secondary-100 text-secondary-800">Any Warning Rows: {{ warningCounts.totalRowsWithWarnings }}</span>
        </div>
      </div>

      <div *ngIf="showFiltersPanel" class="card p-4">
        <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
          <div>
            <label class="label">Filter Match</label>
            <select [(ngModel)]="filterDraft.matchMode" class="input">
              <option value="all">Match all filters</option>
              <option value="any">Match any filter</option>
            </select>
          </div>
          <div>
            <label class="label">Group Type</label>
            <select [(ngModel)]="filterDraft.groupType" class="input">
              <option value="all">All</option>
              <option value="parent">Parent only</option>
              <option value="child">Child only</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.groupTypeExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Level</label>
            <select [(ngModel)]="filterDraft.level" class="input">
              <option [ngValue]="null">All Levels</option>
              <option [ngValue]="100">100</option>
              <option [ngValue]="200">200</option>
              <option [ngValue]="300">300</option>
              <option [ngValue]="400">400</option>
              <option [ngValue]="500">500</option>
              <option [ngValue]="600">600</option>
            </select>
          </div>
          <div>
            <label class="label">Base Name</label>
            <input type="text" [(ngModel)]="filterDraft.baseName" class="input" placeholder="e.g., Computer Science">
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.baseNameExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Parent Group</label>
            <select [(ngModel)]="filterDraft.parentGroupId" class="input">
              <option [ngValue]="null">All parents</option>
              <option *ngFor="let g of topLevelGroups" [ngValue]="g.id">{{ g.name }}</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.parentExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Size</label>
            <input type="number" [(ngModel)]="filterDraft.minSize" class="input" min="0">
          </div>
          <div>
            <label class="label">Max Size</label>
            <input type="number" [(ngModel)]="filterDraft.maxSize" class="input" min="0">
          </div>
          <div>
            <label class="label">Children</label>
            <select [(ngModel)]="filterDraft.childrenMode" class="input">
              <option value="all">All</option>
              <option value="has">Has children</option>
              <option value="none">No children</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.childrenExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Child Count</label>
            <input type="number" [(ngModel)]="filterDraft.minChildCount" class="input" min="0">
          </div>
          <div>
            <label class="label">Max Child Count</label>
            <input type="number" [(ngModel)]="filterDraft.maxChildCount" class="input" min="0">
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
          <p class="text-sm text-red-600 dark:text-red-400">Delete all {{ groups.length }} student groups? This will also delete all lessons and courses!</p>
          <div class="flex gap-2">
            <button (click)="showDeleteAllConfirm = false" class="btn btn-secondary btn-sm">Cancel</button>
            <button (click)="deleteAll()" [disabled]="deleting" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              {{ deleting ? 'Deleting...' : 'Yes, Delete All' }}
            </button>
          </div>
        </div>
      </div>

      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingGroup ? 'Edit' : 'Add' }} Student Group</h2>
        <form #form="ngForm" (ngSubmit)="form.valid && saveGroup()" class="space-y-4">
          <!-- Group Type Selection -->
          <div>
            <label class="label mb-2">Group Type</label>
            <div class="flex gap-4">
              <label class="inline-flex items-center cursor-pointer">
                <input type="radio" name="groupType" [value]="true" [(ngModel)]="formData.isParent" class="w-4 h-4 text-primary-600">
                <span class="ml-2 text-sm">📁 Parent Group</span>
              </label>
              <label class="inline-flex items-center cursor-pointer">
                <input type="radio" name="groupType" [value]="false" [(ngModel)]="formData.isParent" class="w-4 h-4 text-primary-600">
                <span class="ml-2 text-sm">👥 Child Group (has students)</span>
              </label>
            </div>
            <p class="text-xs text-secondary-500 mt-1">
              {{ formData.isParent ? 'Parent group size is calculated from children' : 'Child groups have actual students enrolled' }}
            </p>
          </div>

          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div>
              <label class="label">Base Name *</label>
              <!-- Editable for parent groups, Read-only for child groups -->
              <input *ngIf="formData.isParent" type="text" [(ngModel)]="formData.baseName" name="baseName" class="input" required 
                     placeholder="e.g., Computer Science">
              <input *ngIf="!formData.isParent" type="text" [value]="formData.baseName" 
                     class="input bg-secondary-100 dark:bg-secondary-700" readonly
                     title="Base Name is inherited from parent group">
            </div>
            <!-- Parent Group FIRST for child groups (level depends on it) -->
            <div *ngIf="!formData.isParent">
              <label class="label">Parent Group *</label>
              <div *ngIf="parentGroups.length === 0" class="p-2 mb-1 bg-yellow-50 text-yellow-800 border border-yellow-200 rounded text-xs">
                ⚠️ Create a Parent Group first.
              </div>
              <select [(ngModel)]="formData.parentGroupId" (ngModelChange)="onParentChange($event)" 
                      name="parentGroupId" class="input" required [disabled]="parentGroups.length === 0">
                <option [ngValue]="null" disabled>Select parent</option>
                <option *ngFor="let g of parentGroups" [ngValue]="g.id">{{ g.name }}</option>
              </select>
            </div>
            <div>
              <label class="label">Level *</label>
              <!-- For parent groups: editable. For child groups: locked to parent's level -->
              <select *ngIf="formData.isParent" [(ngModel)]="formData.level" name="level" class="input" required>
                <option [ngValue]="100">100</option>
                <option [ngValue]="200">200</option>
                <option [ngValue]="300">300</option>
                <option [ngValue]="400">400</option>
                <option [ngValue]="500">500</option>
                <option [ngValue]="600">600</option>
              </select>
              <input *ngIf="!formData.isParent" type="text" [value]="formData.level" 
                     class="input bg-secondary-100 dark:bg-secondary-700" readonly
                     title="Level is inherited from parent group">
            </div>
            <!-- Group notation required for child groups -->
            <div *ngIf="!formData.isParent">
              <label class="label">Group *</label>
              <input type="text" [(ngModel)]="formData.group" name="group" class="input" required
                     placeholder="e.g., A, B, DE">
            </div>
            <!-- Only show size for child groups -->
            <div *ngIf="!formData.isParent">
              <label class="label">Size *</label>
              <input type="number" [(ngModel)]="formData.size" name="size" class="input" [required]="!formData.isParent" min="1">
            </div>
          </div>

          <!-- Computed name preview -->
          <div class="p-3 bg-secondary-100 dark:bg-secondary-800 rounded-lg">
            <span class="text-sm text-secondary-600 dark:text-secondary-400">Stored as: </span>
            <span class="font-medium">{{ computedName }}</span>
          </div>

          <div class="flex gap-2">
            <button type="submit" [disabled]="!form.valid" class="btn btn-primary disabled:opacity-50 disabled:cursor-not-allowed">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <div class="card overflow-hidden">
        <table class="w-full">
          <thead class="bg-secondary-100 dark:bg-secondary-700">
            <tr>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Size</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Parent</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Children</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Warnings</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let row of displayedRows" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium">
                <div class="flex items-center gap-2" [style.padding-left.px]="row.depth * 20">
                  <button
                    *ngIf="viewMode === 'tree' && row.expandable"
                    type="button"
                    class="w-5 h-5 inline-flex items-center justify-center rounded border border-secondary-300 dark:border-secondary-600 text-xs"
                    (click)="toggleParentExpanded(row.group.id)">
                    {{ row.expanded ? '-' : '+' }}
                  </button>
                  <span *ngIf="viewMode !== 'tree' || !row.expandable" class="inline-block w-5"></span>
                  <span>{{ row.group.name }}</span>
                </div>
              </td>
              <td class="px-6 py-4">{{ row.group.size }}</td>
              <td class="px-6 py-4">{{ row.group.parentGroupName || '-' }}</td>
              <td class="px-6 py-4">{{ row.group.childCount }}</td>
              <td class="px-6 py-4">
                <span *ngIf="groupWarnings(row.group).length === 0" class="text-xs text-secondary-500">-</span>
                <span
                  *ngFor="let warning of groupWarnings(row.group)"
                  class="badge mr-1"
                  [class.bg-red-100]="warning === 'orphan-child'"
                  [class.text-red-800]="warning === 'orphan-child'"
                  [class.bg-amber-100]="warning === 'parent-without-children'"
                  [class.text-amber-800]="warning === 'parent-without-children'"
                  [class.bg-orange-100]="warning === 'oversized-child'"
                  [class.text-orange-800]="warning === 'oversized-child'">
                  {{ warningLabel(warning) }}
                </span>
              </td>
              <td class="px-6 py-4 text-right">
                <button (click)="editGroup(row.group)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteGroup(row.group.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="displayedRows.length === 0" class="p-8 text-center text-secondary-500">No student groups found.</div>
      </div>
    </div>
  `
})
export class StudentGroupsComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private queryViews = inject(QueryViewsService);
  groups: StudentGroup[] = [];
  showAddForm = false;
  editingGroup: StudentGroup | null = null;
  formData = { baseName: '', level: 100, group: '', size: 50, parentGroupId: null as number | null, isParent: false };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;
  queryState: DataQueryState = { ...DEFAULT_QUERY_STATE, pagination: { page: 1, size: 1000 } };
  activeSortKey = '';
  showFiltersPanel = false;
  filterDraft = {
    matchMode: 'all' as 'all' | 'any',
    groupType: 'all' as 'all' | 'parent' | 'child',
    groupTypeExclude: false,
    level: null as number | null,
    baseName: '',
    baseNameExclude: false,
    parentGroupId: null as number | null,
    parentExclude: false,
    minSize: null as number | null,
    maxSize: null as number | null,
    childrenMode: 'all' as 'all' | 'has' | 'none',
    childrenExclude: false,
    minChildCount: null as number | null,
    maxChildCount: null as number | null
  };
  sortDraft: string[] = ['', '', ''];
  sortOptions: QuerySortOption[] = [
    { key: 'name:asc', label: 'Name (A-Z)' },
    { key: 'name:desc', label: 'Name (Z-A)' },
    { key: 'level:asc', label: 'Level (Low-High)' },
    { key: 'level:desc', label: 'Level (High-Low)' },
    { key: 'size:asc', label: 'Size (Low-High)' },
    { key: 'size:desc', label: 'Size (High-Low)' },
    { key: 'children:asc', label: 'Children (Low-High)' },
    { key: 'children:desc', label: 'Children (High-Low)' }
  ];
  savedViewOptions: QueryViewOption[] = [];
  selectedViewId = '';
  viewMode: 'flat' | 'tree' = 'flat';
  expandedParentIds = new Set<number>();
  oversizedChildThreshold = 200;

  // Parent groups: groups with size=0 (designated parents), or groups that already have children
  get parentGroups(): StudentGroup[] {
    return this.groups.filter(g => g.size === 0 || g.childCount > 0);
  }

  get topLevelGroups(): StudentGroup[] {
    return this.groups
      .filter(group => group.parentGroupId === null)
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  get kpiCards(): KpiCardItem[] {
    const scoped = this.displayedGroups;
    const parentCount = scoped.filter(group => this.isParentGroup(group)).length;
    const childGroups = scoped.filter(group => !this.isParentGroup(group));
    const childCount = childGroups.length;
    const enrolledTotal = childGroups.reduce((sum, group) => sum + group.size, 0);
    const avgChildSize = childCount > 0 ? enrolledTotal / childCount : 0;
    return [
      { label: 'Parent Groups', value: String(parentCount), hint: 'Visible scope' },
      { label: 'Child Groups', value: String(childCount), hint: 'Visible scope' },
      { label: 'Total Enrolled', value: enrolledTotal.toLocaleString(), hint: 'Sum of child sizes' },
      { label: 'Avg Child Size', value: avgChildSize.toFixed(1), hint: `${childCount} child groups` }
    ];
  }

  get warningCounts(): { orphanChild: number; parentWithoutChildren: number; oversizedChild: number; totalRowsWithWarnings: number } {
    const rows = this.displayedRows;
    const orphanChild = rows.filter(row => this.groupWarnings(row.group).includes('orphan-child')).length;
    const parentWithoutChildren = rows.filter(row => this.groupWarnings(row.group).includes('parent-without-children')).length;
    const oversizedChild = rows.filter(row => this.groupWarnings(row.group).includes('oversized-child')).length;
    const totalRowsWithWarnings = rows.filter(row => this.groupWarnings(row.group).length > 0).length;
    return { orphanChild, parentWithoutChildren, oversizedChild, totalRowsWithWarnings };
  }

  get displayedGroups(): StudentGroup[] {
    const search = this.queryState.search.trim().toLowerCase();
    let rows = this.groups;
    if (search) {
      rows = rows.filter(g => this.groupMatchesSearch(g, search));
    }
    rows = rows.filter(g => this.groupMatchesActiveFilters(g));
    const sortStack = this.queryState.sort.length > 0
      ? this.queryState.sort
      : this.parseSortStack([this.activeSortKey]);
    if (sortStack.length === 0) {
      return rows;
    }
    return [...rows].sort((a, b) => this.compareBySortStack(a, b, sortStack));
  }

  get displayedRows(): StudentGroupTreeRow[] {
    if (this.viewMode === 'flat') {
      return this.displayedGroups.map(group => ({
        group,
        depth: 0,
        isParent: group.parentGroupId === null,
        expandable: false,
        expanded: false
      }));
    }

    const filteredGroups = this.displayedGroups;
    const filteredIds = new Set(filteredGroups.map(group => group.id));
    const childrenByParent = new Map<number, StudentGroup[]>();
    const groupById = new Map(this.groups.map(group => [group.id, group]));
    for (const group of this.groups) {
      if (group.parentGroupId === null) {
        continue;
      }
      const children = childrenByParent.get(group.parentGroupId) || [];
      children.push(group);
      childrenByParent.set(group.parentGroupId, children);
    }

    const sortStack = this.queryState.sort.length > 0
      ? this.queryState.sort
      : this.parseSortStack([this.activeSortKey]);
    const sortGroups = (groups: StudentGroup[]) => {
      if (sortStack.length === 0) {
        return [...groups];
      }
      return [...groups].sort((a, b) => this.compareBySortStack(a, b, sortStack));
    };

    const parentIds = new Set<number>();
    for (const group of filteredGroups) {
      if (group.parentGroupId === null) {
        parentIds.add(group.id);
      } else if (groupById.has(group.parentGroupId)) {
        parentIds.add(group.parentGroupId);
      }
    }

    const rows: StudentGroupTreeRow[] = [];
    const renderedIds = new Set<number>();
    const sortedParents = sortGroups(
      Array.from(parentIds)
        .map(id => groupById.get(id))
        .filter((group): group is StudentGroup => Boolean(group))
    );

    for (const parent of sortedParents) {
      const allChildren = sortGroups(childrenByParent.get(parent.id) || []);
      const matchingChildren = allChildren.filter(child => filteredIds.has(child.id));
      const expandable = allChildren.length > 0;
      const expanded = expandable && this.expandedParentIds.has(parent.id);

      rows.push({
        group: parent,
        depth: 0,
        isParent: true,
        expandable,
        expanded
      });
      renderedIds.add(parent.id);

      if (expanded) {
        for (const child of matchingChildren) {
          rows.push({
            group: child,
            depth: 1,
            isParent: false,
            expandable: false,
            expanded: false
          });
          renderedIds.add(child.id);
        }
      }
    }

    const orphanChildren = filteredGroups
      .filter(group => group.parentGroupId !== null && !groupById.has(group.parentGroupId) && !renderedIds.has(group.id));
    for (const child of sortGroups(orphanChildren)) {
      rows.push({
        group: child,
        depth: 0,
        isParent: false,
        expandable: false,
        expanded: false
      });
    }

    return rows;
  }

  toggleParentExpanded(groupId: number) {
    if (this.expandedParentIds.has(groupId)) {
      this.expandedParentIds.delete(groupId);
      return;
    }
    this.expandedParentIds.add(groupId);
  }

  groupWarnings(group: StudentGroup): string[] {
    const warnings: string[] = [];
    if (this.isOrphanChild(group)) {
      warnings.push('orphan-child');
    }
    if (this.isParentWithoutChildren(group)) {
      warnings.push('parent-without-children');
    }
    if (this.isOversizedChild(group)) {
      warnings.push('oversized-child');
    }
    return warnings;
  }

  warningLabel(warning: string): string {
    if (warning === 'orphan-child') {
      return 'Orphan Child';
    }
    if (warning === 'parent-without-children') {
      return 'Parent No Children';
    }
    return 'Oversized';
  }

  // Compute the full name from form data
  get computedName(): string {
    let name = this.formData.baseName + ' ' + this.formData.level + ' LEVEL';
    if (!this.formData.isParent && this.formData.group) {
      name += ' (GRP ' + this.formData.group + ')';
    }
    return name;
  }

  ngOnInit() {
    this.hydrateQueryStateFromUrl();
    this.loadSavedViews();
    this.loadGroups();
  }
  loadGroups() { this.api.getStudentGroups().subscribe({ next: (g) => this.groups = g }); }

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
      .list<StudentGroupQueryViewPayload>('student-groups')
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
    const savedView = this.queryViews.save<StudentGroupQueryViewPayload>('student-groups', name.trim(), {
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
    this.queryViews.delete('student-groups', this.selectedViewId);
    this.selectedViewId = '';
    this.loadSavedViews();
  }

  // When parent group is selected, inherit its level and base name
  onParentChange(parentId: number | null) {
    if (parentId) {
      const parent = this.groups.find(g => g.id === parentId);
      if (parent) {
        if ((parent as any).level) this.formData.level = (parent as any).level;
        if ((parent as any).baseName) this.formData.baseName = (parent as any).baseName;
      }
    }
  }

  saveGroup() {
    // For parent groups, set size to 0 and no group notation
    const data = {
      baseName: this.formData.baseName,
      level: this.formData.level,
      groupNotation: this.formData.isParent ? null : (this.formData.group || null),
      size: this.formData.isParent ? 0 : this.formData.size,
      parentGroupId: this.formData.isParent ? null : this.formData.parentGroupId
    };
    const obs = this.editingGroup
      ? this.api.updateStudentGroup(this.editingGroup.id, data)
      : this.api.createStudentGroup(data);
    obs.subscribe({ next: () => { this.loadGroups(); this.cancelEdit(); } });
  }

  editGroup(g: StudentGroup) {
    this.editingGroup = g;
    // Determine if this is a parent group (has children or no parent and size could be 0)
    const isParent = g.childCount > 0 || (g.parentGroupId === null && g.size === 0);
    // Parse name to extract baseName, level, group (format: "BaseName Level[Group]")
    const baseName = (g as any).baseName || g.name.replace(/\s+\d+.*$/, '');
    const level = (g as any).level || 100;
    const groupNotation = (g as any).groupNotation || '';
    this.formData = {
      baseName: baseName,
      level: level,
      group: groupNotation,
      size: g.size || 50,
      parentGroupId: g.parentGroupId,
      isParent: isParent
    };
    this.showAddForm = true;
  }

  deleteGroup(id: number) {
    if (confirm('Delete?')) this.api.deleteStudentGroup(id).subscribe({ next: () => this.loadGroups() });
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingGroup = null;
    this.formData = { baseName: '', level: 100, group: '', size: 50, parentGroupId: null, isParent: false };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/student-groups/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadGroups(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    this.importing = true;
    this.http.post<any>('http://localhost:8080/api/v1/bulk/student-groups/import', formData).subscribe({
      next: () => { this.importing = false; this.loadGroups(); input.value = ''; },
      error: (err) => { this.importing = false; alert('Import failed: ' + (err.error?.message || 'Unknown error')); input.value = ''; }
    });
  }

  exportToCsv() {
    const headers = ['base_name', 'is_parent', 'level', 'group', 'size', 'parent_group_name'];
    const rows = this.groups.map(g => {
      // Determine if this is a parent group
      const isParent = g.size === 0 || g.childCount > 0;
      return [
        g.baseName || '',
        isParent ? 'T' : 'F',
        String(g.level || ''),
        g.groupNotation || '',
        String(g.size || ''),
        g.parentGroupName || ''
      ];
    });
    const csv = [headers.join(','), ...rows.map(r => r.map(v => this.escapeCSV(v)).join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;

    const timestamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
    a.download = `export_student_groups_${timestamp}.csv`;

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
    if (this.filterDraft.groupType === 'parent') {
      filters.push({ field: 'parentGroupId', operator: 'isNull', exclude: this.filterDraft.groupTypeExclude });
    } else if (this.filterDraft.groupType === 'child') {
      filters.push({ field: 'parentGroupId', operator: 'isNotNull', exclude: this.filterDraft.groupTypeExclude });
    }
    if (this.filterDraft.level !== null) {
      filters.push({ field: 'level', operator: 'eq', value: this.filterDraft.level });
    }
    if (this.filterDraft.baseName.trim()) {
      filters.push({ field: 'baseName', operator: 'contains', value: this.filterDraft.baseName.trim(), exclude: this.filterDraft.baseNameExclude });
    }
    if (this.filterDraft.parentGroupId !== null) {
      filters.push({ field: 'parentGroupId', operator: 'eq', value: this.filterDraft.parentGroupId, exclude: this.filterDraft.parentExclude });
    }
    if (this.filterDraft.minSize !== null && Number.isFinite(this.filterDraft.minSize)) {
      filters.push({ field: 'size', operator: 'gte', value: Number(this.filterDraft.minSize) });
    }
    if (this.filterDraft.maxSize !== null && Number.isFinite(this.filterDraft.maxSize)) {
      filters.push({ field: 'size', operator: 'lte', value: Number(this.filterDraft.maxSize) });
    }
    if (this.filterDraft.childrenMode === 'has') {
      filters.push({ field: 'childCount', operator: 'gt', value: 0, exclude: this.filterDraft.childrenExclude });
    } else if (this.filterDraft.childrenMode === 'none') {
      filters.push({ field: 'childCount', operator: 'eq', value: 0, exclude: this.filterDraft.childrenExclude });
    }
    if (this.filterDraft.minChildCount !== null && Number.isFinite(this.filterDraft.minChildCount)) {
      filters.push({ field: 'childCount', operator: 'gte', value: Number(this.filterDraft.minChildCount) });
    }
    if (this.filterDraft.maxChildCount !== null && Number.isFinite(this.filterDraft.maxChildCount)) {
      filters.push({ field: 'childCount', operator: 'lte', value: Number(this.filterDraft.maxChildCount) });
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

  private resetFilterDraft() {
    this.filterDraft = {
      matchMode: 'all',
      groupType: 'all',
      groupTypeExclude: false,
      level: null,
      baseName: '',
      baseNameExclude: false,
      parentGroupId: null,
      parentExclude: false,
      minSize: null,
      maxSize: null,
      childrenMode: 'all',
      childrenExclude: false,
      minChildCount: null,
      maxChildCount: null
    };
  }

  private hydrateFilterDraftFromQueryFilters() {
    this.resetFilterDraft();
    this.filterDraft.matchMode = this.queryState.matchMode;
    for (const filter of this.queryState.filters) {
      if (filter.field === 'parentGroupId' && filter.operator === 'isNull') {
        this.filterDraft.groupType = 'parent';
        this.filterDraft.groupTypeExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'parentGroupId' && filter.operator === 'isNotNull') {
        this.filterDraft.groupType = 'child';
        this.filterDraft.groupTypeExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'level' && filter.operator === 'eq') {
        this.filterDraft.level = Number(filter.value);
      }
      if (filter.field === 'baseName' && filter.operator === 'contains') {
        this.filterDraft.baseName = String(filter.value || '');
        this.filterDraft.baseNameExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'parentGroupId' && filter.operator === 'eq') {
        this.filterDraft.parentGroupId = Number(filter.value);
        this.filterDraft.parentExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'size' && filter.operator === 'gte') {
        this.filterDraft.minSize = Number(filter.value);
      }
      if (filter.field === 'size' && filter.operator === 'lte') {
        this.filterDraft.maxSize = Number(filter.value);
      }
      if (filter.field === 'childCount' && filter.operator === 'gt') {
        this.filterDraft.childrenMode = 'has';
        this.filterDraft.childrenExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'childCount' && filter.operator === 'eq' && Number(filter.value) === 0) {
        this.filterDraft.childrenMode = 'none';
        this.filterDraft.childrenExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'childCount' && filter.operator === 'gte') {
        this.filterDraft.minChildCount = Number(filter.value);
      }
      if (filter.field === 'childCount' && filter.operator === 'lte') {
        this.filterDraft.maxChildCount = Number(filter.value);
      }
    }
  }

  private groupMatchesSearch(group: StudentGroup, search: string): boolean {
    return [
      group.name,
      group.baseName || '',
      group.parentGroupName || '',
      String(group.level || '')
    ].some(value => value.toLowerCase().includes(search));
  }

  private groupMatchesActiveFilters(group: StudentGroup): boolean {
    if (this.queryState.filters.length === 0) {
      return true;
    }
    const evaluations = this.queryState.filters.map(filter => this.evaluateGroupFilter(group, filter));
    return this.queryState.matchMode === 'any'
      ? evaluations.some(Boolean)
      : evaluations.every(Boolean);
  }

  private compareGroups(a: StudentGroup, b: StudentGroup, field: string, direction: 'asc' | 'desc'): number {
    const sign = direction === 'desc' ? -1 : 1;
    switch (field) {
      case 'name':
        return sign * a.name.localeCompare(b.name);
      case 'level':
        return sign * ((a.level || 0) - (b.level || 0));
      case 'size':
        return sign * (a.size - b.size);
      case 'children':
        return sign * (a.childCount - b.childCount);
      default:
        return 0;
    }
  }

  private evaluateGroupFilter(group: StudentGroup, filter: DataQueryState['filters'][number]): boolean {
    let matched = true;
    if (filter.field === 'parentGroupId' && filter.operator === 'isNull') {
      matched = group.parentGroupId === null;
    } else if (filter.field === 'parentGroupId' && filter.operator === 'isNotNull') {
      matched = group.parentGroupId !== null;
    } else if (filter.field === 'level' && filter.operator === 'eq') {
      matched = (group.level || 0) === Number(filter.value);
    } else if (filter.field === 'baseName' && filter.operator === 'contains') {
      const value = String(filter.value || '').toLowerCase();
      matched = (group.baseName || '').toLowerCase().includes(value);
    } else if (filter.field === 'parentGroupId' && filter.operator === 'eq') {
      matched = group.parentGroupId === Number(filter.value);
    } else if (filter.field === 'size' && filter.operator === 'gte') {
      matched = group.size >= Number(filter.value);
    } else if (filter.field === 'size' && filter.operator === 'lte') {
      matched = group.size <= Number(filter.value);
    } else if (filter.field === 'childCount' && filter.operator === 'gt') {
      matched = group.childCount > Number(filter.value);
    } else if (filter.field === 'childCount' && filter.operator === 'gte') {
      matched = group.childCount >= Number(filter.value);
    } else if (filter.field === 'childCount' && filter.operator === 'lte') {
      matched = group.childCount <= Number(filter.value);
    } else if (filter.field === 'childCount' && filter.operator === 'eq') {
      matched = group.childCount === Number(filter.value);
    }
    return filter.exclude ? !matched : matched;
  }

  private isParentGroup(group: StudentGroup): boolean {
    return group.parentGroupId === null;
  }

  private isOrphanChild(group: StudentGroup): boolean {
    return group.parentGroupId !== null && !this.groups.some(candidate => candidate.id === group.parentGroupId);
  }

  private isParentWithoutChildren(group: StudentGroup): boolean {
    return this.isParentGroup(group) && group.childCount === 0 && group.size === 0;
  }

  private isOversizedChild(group: StudentGroup): boolean {
    return !this.isParentGroup(group) && group.size > this.oversizedChildThreshold;
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

  private compareBySortStack(a: StudentGroup, b: StudentGroup, sortStack: DataQueryState['sort']): number {
    for (const sort of sortStack) {
      const value = this.compareGroups(a, b, sort.field, sort.direction);
      if (value !== 0) return value;
    }
    return 0;
  }

  private hydrateSortDraftFromQuerySort() {
    const sortKeys = this.queryState.sort.map(sort => `${sort.field}:${sort.direction}`);
    this.sortDraft = [sortKeys[0] || '', sortKeys[1] || '', sortKeys[2] || ''];
  }

  private loadSavedViews() {
    this.savedViewOptions = this.queryViews.list('student-groups').map(view => ({
      id: view.id,
      name: view.name
    }));
  }
}
