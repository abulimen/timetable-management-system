import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, Course, Lecturer, StudentGroup, Zone, Feature, Room } from '../../core/services/api.service';
import { DataQueryToolbarComponent, QuerySortOption, QueryViewOption } from '../../core/query/data-query-toolbar.component';
import { DataPaginationComponent } from '../../core/query/data-pagination.component';
import { DataQueryState, DEFAULT_QUERY_STATE } from '../../core/query/query-state.model';
import { parseQueryStateFromParams, serializeQueryStateToParams } from '../../core/query/query-state-url.util';
import { QueryViewsService } from '../../core/query/query-views.service';
import { KpiCardItem, KpiCardRowComponent } from '../../core/analytics/kpi-card-row.component';

interface CourseQueryViewPayload {
  queryState: DataQueryState;
  activeSortKey: string;
}

@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [CommonModule, FormsModule, DataQueryToolbarComponent, DataPaginationComponent, KpiCardRowComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Courses</h1>
        <div class="flex gap-2">
          <button (click)="exportToCsv()" class="btn btn-secondary" [disabled]="courses.length === 0">📤 Export CSV</button>
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="courses.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Course</button>
        </div>
      </div>

      <app-data-query-toolbar
        [search]="queryState.search"
        [sortKey]="activeSortKey"
        [sortOptions]="sortOptions"
        [savedViews]="savedViewOptions"
        [selectedViewId]="selectedViewId"
        [resultCount]="filteredCourses.length"
        [totalCount]="courses.length"
        searchPlaceholder="Search by code, name, lecturer, or group"
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
        [totalItems]="filteredCourses.length"
        (pageSizeChange)="onPageSizeChange($event)"
        (firstPage)="goToFirstPage()"
        (prevPage)="goToPrevPage()"
        (nextPage)="goToNextPage()"
        (lastPage)="goToLastPage()">
      </app-data-pagination>

      <app-kpi-card-row [items]="kpiCards"></app-kpi-card-row>
      <div *ngIf="queryState.sort.length > 1" class="text-xs text-secondary-500">
        Sort priority:
        <span *ngFor="let sort of queryState.sort; let i = index" class="mr-2">
          {{ i + 1 }}. {{ sort.field }} {{ sort.direction }}
        </span>
      </div>
      <div class="flex flex-wrap gap-2">
        <button class="btn btn-secondary btn-sm" (click)="applyPreset('no-lecturer')">Needs Attention: No Lecturer</button>
        <button class="btn btn-secondary btn-sm" (click)="applyPreset('constraint-risk')">Needs Attention: Constraint Risk</button>
        <button class="btn btn-secondary btn-sm" (click)="applyPreset('online-only')">Preset: Online Only</button>
        <button class="btn btn-secondary btn-sm" (click)="applyPreset('heavy-hours')">Preset: Heavy Hours</button>
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
            <label class="label">Course Type</label>
            <select [(ngModel)]="filterDraft.onlineMode" class="input">
              <option value="all">All</option>
              <option value="online">Online only</option>
              <option value="inperson">In-person only</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.onlineExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Lecturer</label>
            <select [(ngModel)]="filterDraft.lecturerMode" class="input">
              <option value="all">All</option>
              <option value="assigned">Assigned only</option>
              <option value="unassigned">Unassigned only</option>
            </select>
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.lecturerExclude">
              Exclude matches
            </label>
          </div>
          <div>
            <label class="label">Min Hours</label>
            <input type="number" [(ngModel)]="filterDraft.minHours" class="input" min="1">
          </div>
          <div>
            <label class="label">Max Hours</label>
            <input type="number" [(ngModel)]="filterDraft.maxHours" class="input" min="1">
            <label class="mt-2 inline-flex items-center text-xs gap-2">
              <input type="checkbox" [(ngModel)]="filterDraft.hoursExclude">
              Exclude hour range
            </label>
          </div>
          <div>
            <label class="label">Group Level</label>
            <select [(ngModel)]="filterDraft.groupLevel" class="input">
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
            <label class="label">Required Feature</label>
            <select [(ngModel)]="filterDraft.requiredFeature" class="input">
              <option value="">Any Feature</option>
              <option *ngFor="let feature of features" [value]="feature.name">{{ feature.name }}</option>
            </select>
          </div>
          <div>
            <label class="label">Allowed Zone</label>
            <select [(ngModel)]="filterDraft.allowedZone" class="input">
              <option value="">Any Zone</option>
              <option *ngFor="let zone of zones" [value]="zone.name">{{ zone.name }}</option>
            </select>
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

      <!-- Batch Selection Toolbar -->
      <div *ngIf="selectedIds.size > 0" class="card p-4 bg-primary-50 dark:bg-primary-900/20 border border-primary-300 dark:border-primary-700 sticky top-0 z-10 transition-all">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div class="flex items-center gap-3">
            <span class="font-medium text-primary-700 dark:text-primary-300">{{ selectedIds.size }} selected</span>
            <button (click)="clearSelection()" class="text-sm text-primary-600 hover:underline">Clear</button>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <button (click)="showBatchLecturerDialog = true" class="btn btn-secondary btn-sm">
              👤 Change Lecturer
            </button>
            <button (click)="showBatchHoursDialog = true" class="btn btn-secondary btn-sm">
              ⏱️ Update Hours
            </button>
            <button (click)="showBatchDeleteDialog = true" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              🗑️ Delete Selected
            </button>
          </div>
        </div>
      </div>

      <!-- Batch Lecturer Dialog -->
      <div *ngIf="showBatchLecturerDialog" class="card p-4 border-2 border-primary-400 mb-6">
        <h3 class="font-medium mb-3">Change Lecturer for {{ selectedIds.size }} courses</h3>
        <div class="flex items-end gap-3">
          <div class="flex-1">
            <label class="label">New Lecturer</label>
            <select [(ngModel)]="batchLecturerId" class="input">
              <option [ngValue]="-1">-- Clear Lecturer --</option>
              <option *ngFor="let l of lecturers" [ngValue]="l.id">{{ l.name }}</option>
            </select>
          </div>
          <button (click)="applyBatchLecturer()" [disabled]="batchUpdating" class="btn btn-primary">
            {{ batchUpdating ? 'Applying...' : 'Apply' }}
          </button>
          <button (click)="showBatchLecturerDialog = false" class="btn btn-secondary">Cancel</button>
        </div>
      </div>

      <!-- Batch Hours Dialog -->
      <div *ngIf="showBatchHoursDialog" class="card p-4 border-2 border-primary-400 mb-6">
        <h3 class="font-medium mb-3">Update Weekly Hours for {{ selectedIds.size }} courses</h3>
        <div class="flex items-end gap-3">
          <div class="flex-1">
            <label class="label">New Weekly Hours</label>
            <input type="number" [(ngModel)]="batchHours" class="input" min="1" max="20">
          </div>
          <button (click)="applyBatchHours()" [disabled]="batchUpdating" class="btn btn-primary">
            {{ batchUpdating ? 'Applying...' : 'Apply' }}
          </button>
          <button (click)="showBatchHoursDialog = false" class="btn btn-secondary">Cancel</button>
        </div>
      </div>

      <!-- Batch Delete Dialog -->
      <div *ngIf="showBatchDeleteDialog" class="card p-4 bg-red-500/10 border-2 border-red-500 mb-6">
        <h3 class="font-medium mb-3 text-red-600">Delete {{ selectedIds.size }} courses?</h3>
        <p class="text-sm text-secondary-600 dark:text-secondary-400 mb-4">This will also delete all associated lessons. This action cannot be undone.</p>
        <div class="flex gap-2">
          <button (click)="applyBatchDelete()" [disabled]="batchUpdating" class="btn bg-red-600 hover:bg-red-700 text-white">
            {{ batchUpdating ? 'Deleting...' : 'Yes, Delete All' }}
          </button>
          <button (click)="showBatchDeleteDialog = false" class="btn btn-secondary">Cancel</button>
        </div>
      </div>

      <!-- Bulk Delete All Confirmation -->
      <div *ngIf="showDeleteAllConfirm" class="card p-4 bg-red-500/10 border border-red-500/50 mb-6">
        <div class="flex items-center justify-between">
          <p class="text-sm text-red-600 dark:text-red-400">Delete all {{ courses.length }} courses? This will also delete all lessons!</p>
          <div class="flex gap-2">
            <button (click)="showDeleteAllConfirm = false" class="btn btn-secondary btn-sm">Cancel</button>
            <button (click)="deleteAll()" [disabled]="deleting" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              {{ deleting ? 'Deleting...' : 'Yes, Delete All' }}
            </button>
          </div>
        </div>
      </div>

      <div *ngIf="showAddForm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" (click)="cancelEdit()">
        <div class="card w-full max-w-5xl max-h-[90vh] overflow-y-auto p-6" (click)="$event.stopPropagation()">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold">{{ editingCourse ? 'Edit' : 'Add' }} Course</h2>
          <button type="button" (click)="cancelEdit()" class="btn btn-secondary btn-sm">Close</button>
        </div>
        <form (ngSubmit)="saveCourse()" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">Code</label>
              <input type="text" [(ngModel)]="formData.code" name="code" class="input" required>
            </div>
            <div>
              <label class="label">Name</label>
              <input type="text" [(ngModel)]="formData.name" name="name" class="input" required>
            </div>
            <div>
              <label class="label">Weekly Hours</label>
              <input type="number" [(ngModel)]="formData.totalWeeklyHours" name="hours" class="input" required>
            </div>
            <div>
              <label class="label">Lecturer</label>
              <select [(ngModel)]="formData.lecturerId" name="lecturerId" class="input">
                <option [ngValue]="null">Select Lecturer</option>
                <option *ngFor="let l of lecturers" [ngValue]="l.id">{{ l.name }}</option>
              </select>
            </div>
          </div>
          
          <!-- Student Groups -->
          <div>
            <label class="label mb-2">Student Groups</label>
            <div class="flex flex-wrap gap-2 max-h-32 overflow-y-auto p-2 border border-secondary-300 dark:border-secondary-600 rounded-lg">
              <label *ngFor="let g of studentGroups" 
                     class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                     [ngClass]="{
                       'bg-primary-100 border-primary-500 dark:bg-primary-900': isGroupSelected(g.id),
                       'border-secondary-300 dark:border-secondary-600': !isGroupSelected(g.id)
                     }">
                <input type="checkbox" [checked]="isGroupSelected(g.id)" (change)="toggleGroup(g.id)" class="mr-2">
                <span class="text-sm">{{ g.name }} ({{ g.size }})</span>
              </label>
            </div>
          </div>
          
          <!-- Allowed Zones -->
          <div *ngIf="!formData.online">
            <label class="label mb-2">📍 Allowed Zones</label>
            <div class="flex flex-wrap gap-2 max-h-24 overflow-y-auto p-2 border border-secondary-300 dark:border-secondary-600 rounded-lg">
              <label *ngFor="let z of zones" 
                     class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                     [ngClass]="{
                       'bg-amber-100 border-amber-500': isZoneSelected(z.id),
                       'border-secondary-300 dark:border-secondary-600': !isZoneSelected(z.id)
                     }">
                <input type="checkbox" [checked]="isZoneSelected(z.id)" (change)="toggleZone(z.id)" class="mr-2">
                <span class="text-sm">{{ z.name }}</span>
              </label>
            </div>
          </div>
          
          <!-- Required Features -->
          <div *ngIf="!formData.online">
            <label class="label mb-2">🔧 Required Features</label>
            <div class="flex flex-wrap gap-2 max-h-24 overflow-y-auto p-2 border border-secondary-300 dark:border-secondary-600 rounded-lg">
              <label *ngFor="let f of features" 
                     class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                     [ngClass]="{
                       'bg-purple-100 border-purple-500': isFeatureSelected(f.id),
                       'border-secondary-300 dark:border-secondary-600': !isFeatureSelected(f.id)
                     }">
                <input type="checkbox" [checked]="isFeatureSelected(f.id)" (change)="toggleFeature(f.id)" class="mr-2">
                <span class="text-sm">{{ f.name }}</span>
              </label>
            </div>
          </div>
          
          <div class="flex items-center gap-4">
            <label class="flex items-center gap-2">
              <input type="checkbox" [(ngModel)]="formData.online" name="online" class="w-4 h-4">
              <span class="text-sm">🌐 Online Course</span>
            </label>
            <label class="flex items-center gap-2">
              <input type="checkbox" [(ngModel)]="formData.generateLessons" name="generateLessons">
              <span class="text-sm">Generate lessons on save</span>
            </label>
          </div>
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
        </div>
      </div>

      <div class="card overflow-hidden">
        <table class="w-full">
          <thead class="bg-secondary-100 dark:bg-secondary-700">
            <tr>
              <th class="text-left px-4 py-3 w-12">
                <input type="checkbox" [checked]="allSelected" (change)="toggleSelectAll()" class="w-4 h-4 cursor-pointer" />
              </th>
              <th class="text-left px-6 py-3 text-sm font-medium">Code</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Type</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Hours/Week</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Lecturer</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Groups</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let course of displayedCourses" 
                class="border-t border-secondary-200 dark:border-secondary-700"
                [ngClass]="{'bg-primary-50 dark:bg-primary-900/20': selectedIds.has(course.id)}">
              <td class="px-4 py-4">
                <input type="checkbox" [checked]="selectedIds.has(course.id)" (change)="toggleSelect(course.id)" class="w-4 h-4 cursor-pointer" />
              </td>
              <td class="px-6 py-4 font-medium text-primary-600">{{ course.code }}</td>
              <td class="px-6 py-4">
                <div>{{ course.name }}</div>
                <div class="mt-1 flex flex-wrap gap-1 text-xs" *ngIf="!course.online">
                  <span
                    class="px-2 py-0.5 rounded-full"
                    [title]="getRiskPillTooltip(course)"
                    [ngClass]="{
                      'bg-red-100 text-red-700': getCourseRiskLevel(course) === 'high',
                      'bg-amber-100 text-amber-700': getCourseRiskLevel(course) === 'medium',
                      'bg-emerald-100 text-emerald-700': getCourseRiskLevel(course) === 'low'
                    }">
                    {{ getCourseRiskLevel(course) | uppercase }} RISK
                  </span>
                  <span class="px-2 py-0.5 rounded-full bg-secondary-100 text-secondary-700"
                    [title]="getCandidateRoomsTooltip(course)">
                    {{ getCandidateRoomCount(course) }} candidate rooms
                  </span>
                  <span *ngIf="populationExceedsAnyRoom(course)" class="px-2 py-0.5 rounded-full bg-red-100 text-red-700"
                    title="Combined student population is larger than every available room capacity.">
                    Group size exceeds every room
                  </span>
                  <span *ngIf="requiredFeaturesHaveNoRoomCoverage(course)" class="px-2 py-0.5 rounded-full bg-red-100 text-red-700"
                    title="At least one required feature is not available in any room.">
                    Required feature(s) has no room match
                  </span>
                  <span *ngIf="hasConstraintDensityHigh(course)" class="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800"
                    title="This course has many hard constraints (capacity, feature, and zone restrictions), so small data changes can make it unschedulable.">
                    Constraint density high
                  </span>
                  <span *ngIf="hasSingleRoomDependency(course)" class="px-2 py-0.5 rounded-full bg-red-100 text-red-700"
                    title="Only one feasible room currently matches this course. If that room is occupied, this course may fail scheduling.">
                    Single-room dependency
                  </span>
                  <span *ngIf="hasLowRoomDiversity(course)" class="px-2 py-0.5 rounded-full bg-amber-100 text-amber-800"
                    title="Only a few rooms match this course, increasing timetable collision risk.">
                    Low room diversity
                  </span>
                </div>
              </td>
              <td class="px-6 py-4">
                <span *ngIf="course.online" class="px-2 py-1 bg-blue-100 text-blue-700 text-xs rounded-full">🌐 Online</span>
                <span *ngIf="!course.online" class="px-2 py-1 bg-secondary-100 text-secondary-600 text-xs rounded-full">🏫 In-Person</span>
              </td>
              <td class="px-6 py-4">{{ course.totalWeeklyHours }}</td>
              <td class="px-6 py-4">{{ course.lecturerName || '-' }}</td>
              <td class="px-6 py-4">
                <span *ngIf="course.studentGroupNames && course.studentGroupNames.length > 0">
                  <span *ngFor="let gn of course.studentGroupNames" class="inline-block px-2 py-0.5 bg-green-100 text-green-800 text-xs rounded-full mr-1">{{ gn }}</span>
                </span>
                <span *ngIf="!course.studentGroupNames || course.studentGroupNames.length === 0">{{ course.studentGroupName || '-' }}</span>
              </td>
              <td class="px-6 py-4 text-right">
                <button (click)="editCourse(course)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteCourse(course.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="displayedCourses.length === 0" class="p-8 text-center text-secondary-500">No courses found.</div>
      </div>
    </div>
  `
})
export class CoursesComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private queryViews = inject(QueryViewsService);
  courses: Course[] = [];
  lecturers: Lecturer[] = [];
  studentGroups: StudentGroup[] = [];
  zones: Zone[] = [];
  features: Feature[] = [];
  rooms: Room[] = [];
  showAddForm = false;
  editingCourse: Course | null = null;
  formData = {
    code: '',
    name: '',
    totalWeeklyHours: 2,
    lecturerId: null as number | null,
    studentGroupIds: [] as number[],
    allowedZoneIds: [] as number[],
    requiredFeatureIds: [] as number[],
    generateLessons: true,
    online: false
  };
  showDeleteAllConfirm = false;
  deleting = false;

  // Batch operations state
  selectedIds = new Set<number>();
  showBatchLecturerDialog = false;
  showBatchHoursDialog = false;
  showBatchDeleteDialog = false;
  batchLecturerId: number | null = null;
  batchHours = 2;
  batchUpdating = false;
  queryState: DataQueryState = { ...DEFAULT_QUERY_STATE };
  activeSortKey = '';
  showFiltersPanel = false;
  filterDraft = {
    matchMode: 'all' as 'all' | 'any',
    onlineMode: 'all' as 'all' | 'online' | 'inperson',
    onlineExclude: false,
    lecturerMode: 'all' as 'all' | 'assigned' | 'unassigned',
    lecturerExclude: false,
    minHours: null as number | null,
    maxHours: null as number | null,
    hoursExclude: false,
    groupLevel: null as number | null,
    requiredFeature: '',
    allowedZone: ''
  };
  sortDraft: string[] = ['', '', ''];
  sortOptions: QuerySortOption[] = [
    { key: 'code:asc', label: 'Code (A-Z)' },
    { key: 'code:desc', label: 'Code (Z-A)' },
    { key: 'name:asc', label: 'Name (A-Z)' },
    { key: 'name:desc', label: 'Name (Z-A)' },
    { key: 'hours:asc', label: 'Hours (Low-High)' },
    { key: 'hours:desc', label: 'Hours (High-Low)' },
    { key: 'groups:asc', label: 'Groups (Low-High)' },
    { key: 'groups:desc', label: 'Groups (High-Low)' },
    { key: 'features:asc', label: 'Features (Low-High)' },
    { key: 'features:desc', label: 'Features (High-Low)' }
  ];
  savedViewOptions: QueryViewOption[] = [];
  selectedViewId = '';

  get allSelected(): boolean {
    const ids = this.filteredCourses.map(course => course.id);
    return ids.length > 0 && ids.every(id => this.selectedIds.has(id));
  }

  get filteredCourses(): Course[] {
    const search = this.queryState.search.trim().toLowerCase();
    let rows = this.courses;
    if (search) {
      rows = rows.filter(c => this.courseMatchesSearch(c, search));
    }
    rows = rows.filter(c => this.courseMatchesActiveFilters(c));
    const sortStack = this.queryState.sort.length > 0
      ? this.queryState.sort
      : this.parseSortStack([this.activeSortKey]);
    if (sortStack.length === 0) {
      return rows;
    }
    return [...rows].sort((a, b) => this.compareBySortStack(a, b, sortStack));
  }

  get displayedCourses(): Course[] {
    const rows = this.filteredCourses;
    const page = this.queryState.pagination.page;
    const size = this.queryState.pagination.size;
    const start = (page - 1) * size;
    return rows.slice(start, start + size);
  }

  get kpiCards(): KpiCardItem[] {
    const displayed = this.filteredCourses;
    const total = displayed.length;
    const online = displayed.filter(course => Boolean(course.online)).length;
    const noLecturer = displayed.filter(course => course.lecturerId === null).length;
    const highRisk = displayed.filter(course => this.getCourseRiskLevel(course) === 'high').length;

    return [
      { label: 'Visible Courses', value: String(total), hint: `${this.courses.length} total` },
      { label: 'Online Courses', value: String(online), hint: `${total - online} in-person`, tone: 'good' },
      { label: 'No Lecturer', value: String(noLecturer), hint: 'Needs assignment', tone: noLecturer > 0 ? 'warn' : 'good' },
      { label: 'High-Risk Constraints', value: String(highRisk), hint: 'Few or no feasible rooms', tone: highRisk > 0 ? 'warn' : 'good' }
    ];
  }

  ngOnInit() {
    this.hydrateQueryStateFromUrl();
    this.loadSavedViews();
    this.loadCourses();
    this.api.getLecturers().subscribe({ next: (l) => this.lecturers = l });
    this.api.getStudentGroups().subscribe({ next: (g) => this.studentGroups = g });
    this.api.getZones().subscribe({ next: (z) => this.zones = z });
    this.api.getFeatures().subscribe({ next: (f) => this.features = f });
    this.api.getRooms().subscribe({ next: (r) => this.rooms = r });
  }

  loadCourses() {
    this.api.getCourses().subscribe({ next: (c) => this.courses = c });
  }

  // Selection methods
  toggleSelect(id: number) {
    if (this.selectedIds.has(id)) {
      this.selectedIds.delete(id);
    } else {
      this.selectedIds.add(id);
    }
  }

  toggleSelectAll() {
    if (this.allSelected) {
      this.filteredCourses.forEach(c => this.selectedIds.delete(c.id));
    } else {
      this.filteredCourses.forEach(c => this.selectedIds.add(c.id));
    }
  }

  clearSelection() {
    this.selectedIds.clear();
  }

  onSearchChange(value: string) {
    this.queryState = { ...this.queryState, search: value, pagination: { ...this.queryState.pagination, page: 1 } };
    this.clearSelection();
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
    this.clearSelection();
    this.syncQueryStateToUrl();
  }

  resetQuery() {
    this.queryState = { ...DEFAULT_QUERY_STATE };
    this.activeSortKey = '';
    this.selectedViewId = '';
    this.hydrateSortDraftFromQuerySort();
    this.clearSelection();
    this.syncQueryStateToUrl();
  }

  onSavedViewChange(viewId: string) {
    this.selectedViewId = viewId;
    if (!viewId) {
      return;
    }
    const savedView = this.queryViews
      .list<CourseQueryViewPayload>('courses')
      .find(view => view.id === viewId);
    if (!savedView) {
      return;
    }

    this.queryState = {
      ...DEFAULT_QUERY_STATE,
      ...savedView.payload.queryState
    };
    this.activeSortKey = savedView.payload.activeSortKey || '';
    this.hydrateFilterDraftFromQueryFilters();
    this.hydrateSortDraftFromQuerySort();
    this.clearSelection();
    this.syncQueryStateToUrl();
  }

  onSaveView() {
    const name = window.prompt('Saved view name');
    if (!name || !name.trim()) {
      return;
    }
    const savedView = this.queryViews.save<CourseQueryViewPayload>('courses', name.trim(), {
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
    this.queryViews.delete('courses', this.selectedViewId);
    this.selectedViewId = '';
    this.loadSavedViews();
  }

  onFiltersClick() {
    this.showFiltersPanel = !this.showFiltersPanel;
  }

  applyPreset(preset: 'no-lecturer' | 'constraint-risk' | 'online-only' | 'heavy-hours') {
    const baseState: DataQueryState = {
      ...this.queryState,
      filters: [],
      matchMode: 'all',
      sort: [],
      pagination: { ...this.queryState.pagination, page: 1 }
    };

    if (preset === 'no-lecturer') {
      baseState.filters = [{ field: 'lecturerId', operator: 'isNull' }];
      baseState.sort = [{ field: 'code', direction: 'asc' }];
    }
    if (preset === 'constraint-risk') {
      baseState.filters = [{ field: 'riskLevel', operator: 'eq', value: 'high' }];
      baseState.sort = [{ field: 'hours', direction: 'desc' }];
    }
    if (preset === 'online-only') {
      baseState.filters = [{ field: 'online', operator: 'eq', value: true }];
      baseState.sort = [{ field: 'name', direction: 'asc' }];
    }
    if (preset === 'heavy-hours') {
      baseState.filters = [{ field: 'totalWeeklyHours', operator: 'gte', value: 4 }];
      baseState.sort = [{ field: 'hours', direction: 'desc' }];
    }

    this.queryState = baseState;
    this.activeSortKey = baseState.sort[0] ? `${baseState.sort[0].field}:${baseState.sort[0].direction}` : '';
    this.selectedViewId = '';
    this.hydrateFilterDraftFromQueryFilters();
    this.hydrateSortDraftFromQuerySort();
    this.clearSelection();
    this.syncQueryStateToUrl();
  }

  // Batch operations
  applyBatchLecturer() {
    if (this.batchLecturerId === null) return;
    this.batchUpdating = true;
    this.http.patch<any>('/api/v1/courses/batch', {
      ids: Array.from(this.selectedIds),
      lecturerId: this.batchLecturerId
    }).subscribe({
      next: (res) => {
        this.batchUpdating = false;
        this.showBatchLecturerDialog = false;
        this.clearSelection();
        this.loadCourses();
        alert(`Updated ${res.updated} courses` + (res.failed > 0 ? `, ${res.failed} failed` : ''));
      },
      error: (err) => {
        this.batchUpdating = false;
        alert('Batch update failed: ' + (err.error?.message || 'Unknown error'));
      }
    });
  }

  applyBatchHours() {
    if (!this.batchHours || this.batchHours < 1) return;
    this.batchUpdating = true;
    this.http.patch<any>('/api/v1/courses/batch', {
      ids: Array.from(this.selectedIds),
      totalWeeklyHours: this.batchHours
    }).subscribe({
      next: (res) => {
        this.batchUpdating = false;
        this.showBatchHoursDialog = false;
        this.clearSelection();
        this.loadCourses();
        alert(`Updated ${res.updated} courses` + (res.failed > 0 ? `, ${res.failed} failed` : ''));
      },
      error: (err) => {
        this.batchUpdating = false;
        alert('Batch update failed: ' + (err.error?.message || 'Unknown error'));
      }
    });
  }

  applyBatchDelete() {
    this.batchUpdating = true;
    this.http.delete<any>('/api/v1/courses/batch', {
      body: { ids: Array.from(this.selectedIds) }
    }).subscribe({
      next: (res) => {
        this.batchUpdating = false;
        this.showBatchDeleteDialog = false;
        this.clearSelection();
        this.loadCourses();
        alert(`Deleted ${res.updated} courses` + (res.failed > 0 ? `, ${res.failed} failed` : ''));
      },
      error: (err) => {
        this.batchUpdating = false;
        alert('Batch delete failed: ' + (err.error?.message || 'Unknown error'));
      }
    });
  }

  isGroupSelected(groupId: number): boolean {
    return this.formData.studentGroupIds.includes(groupId);
  }

  toggleGroup(groupId: number) {
    const index = this.formData.studentGroupIds.indexOf(groupId);
    if (index > -1) {
      this.formData.studentGroupIds.splice(index, 1);
    } else {
      this.formData.studentGroupIds.push(groupId);
    }
  }

  isZoneSelected(zoneId: number): boolean {
    return this.formData.allowedZoneIds.includes(zoneId);
  }

  toggleZone(zoneId: number) {
    const index = this.formData.allowedZoneIds.indexOf(zoneId);
    if (index > -1) {
      this.formData.allowedZoneIds.splice(index, 1);
    } else {
      this.formData.allowedZoneIds.push(zoneId);
    }
  }

  isFeatureSelected(featureId: number): boolean {
    return this.formData.requiredFeatureIds.includes(featureId);
  }

  toggleFeature(featureId: number) {
    const index = this.formData.requiredFeatureIds.indexOf(featureId);
    if (index > -1) {
      this.formData.requiredFeatureIds.splice(index, 1);
    } else {
      this.formData.requiredFeatureIds.push(featureId);
    }
  }

  saveCourse() {
    const obs = this.editingCourse
      ? this.api.updateCourse(this.editingCourse.id, this.formData)
      : this.api.createCourse(this.formData);
    obs.subscribe({
      next: () => { this.loadCourses(); this.cancelEdit(); },
      error: (err) => {
        alert('Failed to save course: ' + (err.error?.message || 'Unknown error'));
      }
    });
  }

  editCourse(c: Course) {
    this.editingCourse = c;
    const groupIds = c.studentGroupIds?.length > 0
      ? [...c.studentGroupIds]
      : (c.studentGroupId ? [c.studentGroupId] : []);
    const zoneIds = (c.allowedZoneIds && c.allowedZoneIds.length > 0)
      ? [...c.allowedZoneIds]
      : this.zones
        .filter(z => (c.allowedZones || []).includes(z.name))
        .map(z => z.id);
    const featureIds = (c.requiredFeatureIds && c.requiredFeatureIds.length > 0)
      ? [...c.requiredFeatureIds]
      : this.features
        .filter(f => (c.requiredFeatures || []).includes(f.name))
        .map(f => f.id);
    this.formData = {
      code: c.code,
      name: c.name,
      totalWeeklyHours: c.totalWeeklyHours,
      lecturerId: c.lecturerId,
      studentGroupIds: groupIds,
      allowedZoneIds: [...zoneIds],
      requiredFeatureIds: [...featureIds],
      generateLessons: false,
      online: c.online || false
    };
    this.showAddForm = true;
  }

  deleteCourse(id: number) {
    if (confirm('Delete?')) this.api.deleteCourse(id).subscribe({ next: () => this.loadCourses() });
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingCourse = null;
    this.formData = { code: '', name: '', totalWeeklyHours: 2, lecturerId: null, studentGroupIds: [], allowedZoneIds: [], requiredFeatureIds: [], generateLessons: true, online: false };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/courses/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadCourses(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  exportToCsv() {
    const headers = ['code', 'name', 'weekly_hours', 'lecturer_email', 'student_group_names', 'is_online', 'required_features', 'allowed_zones'];
    const rows = this.courses.map(c => {
      // Look up lecturer email
      const lecturer = this.lecturers.find(l => l.id === c.lecturerId);
      const lecturerEmail = lecturer?.email || '';
      // Student groups - use studentGroupNames array or fallback to single studentGroupName
      const groupNames = c.studentGroupNames?.length > 0
        ? c.studentGroupNames.join('|')
        : (c.studentGroupName || '');
      return [
        c.code,
        c.name,
        String(c.totalWeeklyHours),
        lecturerEmail,
        groupNames,
        c.online ? 'true' : 'false',
        (c.requiredFeatures || []).join('|'),
        (c.allowedZones || []).join('|')
      ];
    });
    const csv = [headers.join(','), ...rows.map(r => r.map(v => this.escapeCSV(v)).join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;

    const timestamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-');
    a.download = `export_courses_${timestamp}.csv`;

    a.click();
    URL.revokeObjectURL(url);
  }

  private escapeCSV(value: string): string {
    if (value.includes(',') || value.includes('"') || value.includes('\n')) {
      return '"' + value.replace(/"/g, '""') + '"';
    }
    return value;
  }

  private courseMatchesSearch(c: Course, search: string): boolean {
    return [
      c.code,
      c.name,
      c.lecturerName || '',
      c.studentGroupName || '',
      ...(c.studentGroupNames || []),
      ...(c.requiredFeatures || []),
      ...(c.allowedZones || [])
    ].some(value => value.toLowerCase().includes(search));
  }

  private compareCourses(a: Course, b: Course, field: string, direction: 'asc' | 'desc'): number {
    const sign = direction === 'desc' ? -1 : 1;
    switch (field) {
      case 'code':
        return sign * a.code.localeCompare(b.code);
      case 'name':
        return sign * a.name.localeCompare(b.name);
      case 'hours':
        return sign * (a.totalWeeklyHours - b.totalWeeklyHours);
      case 'groups':
        return sign * (this.getCourseGroupCount(a) - this.getCourseGroupCount(b));
      case 'features':
        return sign * ((a.requiredFeatures || []).length - (b.requiredFeatures || []).length);
      default:
        return 0;
    }
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

  applyFilters() {
    const filters: DataQueryState['filters'] = [];
    if (this.filterDraft.onlineMode === 'online') {
      filters.push({ field: 'online', operator: 'eq', value: true, exclude: this.filterDraft.onlineExclude });
    } else if (this.filterDraft.onlineMode === 'inperson') {
      filters.push({ field: 'online', operator: 'eq', value: false, exclude: this.filterDraft.onlineExclude });
    }

    if (this.filterDraft.lecturerMode === 'assigned') {
      filters.push({ field: 'lecturerId', operator: 'isNotNull', exclude: this.filterDraft.lecturerExclude });
    } else if (this.filterDraft.lecturerMode === 'unassigned') {
      filters.push({ field: 'lecturerId', operator: 'isNull', exclude: this.filterDraft.lecturerExclude });
    }

    if (this.filterDraft.minHours !== null && Number.isFinite(this.filterDraft.minHours)) {
      filters.push({ field: 'totalWeeklyHours', operator: 'gte', value: Number(this.filterDraft.minHours), exclude: this.filterDraft.hoursExclude });
    }
    if (this.filterDraft.maxHours !== null && Number.isFinite(this.filterDraft.maxHours)) {
      filters.push({ field: 'totalWeeklyHours', operator: 'lte', value: Number(this.filterDraft.maxHours), exclude: this.filterDraft.hoursExclude });
    }
    if (this.filterDraft.groupLevel !== null) {
      filters.push({ field: 'groupLevel', operator: 'eq', value: Number(this.filterDraft.groupLevel) });
    }
    if (this.filterDraft.requiredFeature.trim()) {
      filters.push({ field: 'requiredFeatures', operator: 'contains', value: this.filterDraft.requiredFeature.trim() });
    }
    if (this.filterDraft.allowedZone.trim()) {
      filters.push({ field: 'allowedZones', operator: 'contains', value: this.filterDraft.allowedZone.trim() });
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
    this.clearSelection();
    this.syncQueryStateToUrl();
  }

  clearFilters() {
    this.filterDraft = {
      matchMode: 'all',
      onlineMode: 'all',
      onlineExclude: false,
      lecturerMode: 'all',
      lecturerExclude: false,
      minHours: null,
      maxHours: null,
      hoursExclude: false,
      groupLevel: null,
      requiredFeature: '',
      allowedZone: ''
    };
    this.sortDraft = ['', '', ''];
    this.queryState = {
      ...this.queryState,
      filters: [],
      matchMode: 'all',
      sort: [],
      pagination: { ...this.queryState.pagination, page: 1 }
    };
    this.activeSortKey = '';
    this.clearSelection();
    this.syncQueryStateToUrl();
  }

  private hydrateFilterDraftFromQueryFilters() {
    const filters = this.queryState.filters || [];
    this.filterDraft.onlineMode = 'all';
    this.filterDraft.onlineExclude = false;
    this.filterDraft.lecturerMode = 'all';
    this.filterDraft.lecturerExclude = false;
    this.filterDraft.minHours = null;
    this.filterDraft.maxHours = null;
    this.filterDraft.hoursExclude = false;
    this.filterDraft.groupLevel = null;
    this.filterDraft.requiredFeature = '';
    this.filterDraft.allowedZone = '';
    this.filterDraft.matchMode = this.queryState.matchMode;

    for (const filter of filters) {
      if (filter.field === 'online' && filter.operator === 'eq') {
        this.filterDraft.onlineMode = filter.value === true ? 'online' : 'inperson';
        this.filterDraft.onlineExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'lecturerId' && filter.operator === 'isNotNull') {
        this.filterDraft.lecturerMode = 'assigned';
        this.filterDraft.lecturerExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'lecturerId' && filter.operator === 'isNull') {
        this.filterDraft.lecturerMode = 'unassigned';
        this.filterDraft.lecturerExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'totalWeeklyHours' && filter.operator === 'gte') {
        this.filterDraft.minHours = Number(filter.value);
        this.filterDraft.hoursExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'totalWeeklyHours' && filter.operator === 'lte') {
        this.filterDraft.maxHours = Number(filter.value);
        this.filterDraft.hoursExclude = Boolean(filter.exclude);
      }
      if (filter.field === 'groupLevel' && filter.operator === 'eq') {
        this.filterDraft.groupLevel = Number(filter.value);
      }
      if (filter.field === 'requiredFeatures' && filter.operator === 'contains') {
        this.filterDraft.requiredFeature = String(filter.value || '');
      }
      if (filter.field === 'allowedZones' && filter.operator === 'contains') {
        this.filterDraft.allowedZone = String(filter.value || '');
      }
    }
  }

  private courseMatchesActiveFilters(c: Course): boolean {
    if (this.queryState.filters.length === 0) {
      return true;
    }
    const evaluations = this.queryState.filters.map(filter => this.evaluateCourseFilter(c, filter));
    return this.queryState.matchMode === 'any'
      ? evaluations.some(Boolean)
      : evaluations.every(Boolean);
  }

  private evaluateCourseFilter(course: Course, filter: DataQueryState['filters'][number]): boolean {
    let matched = true;
    if (filter.field === 'online' && filter.operator === 'eq') {
      matched = Boolean(course.online) === Boolean(filter.value);
    } else if (filter.field === 'lecturerId' && filter.operator === 'isNull') {
      matched = course.lecturerId === null;
    } else if (filter.field === 'lecturerId' && filter.operator === 'isNotNull') {
      matched = course.lecturerId !== null;
    } else if (filter.field === 'totalWeeklyHours' && filter.operator === 'gte') {
      matched = course.totalWeeklyHours >= Number(filter.value);
    } else if (filter.field === 'totalWeeklyHours' && filter.operator === 'lte') {
      matched = course.totalWeeklyHours <= Number(filter.value);
    } else if (filter.field === 'groupLevel' && filter.operator === 'eq') {
      const level = Number(filter.value);
      const levels = this.getCourseGroupLevels(course);
      matched = levels.includes(level);
    } else if (filter.field === 'requiredFeatures' && filter.operator === 'contains') {
      const needle = String(filter.value || '').toLowerCase();
      matched = (course.requiredFeatures || []).some(feature => feature.toLowerCase().includes(needle));
    } else if (filter.field === 'allowedZones' && filter.operator === 'contains') {
      const needle = String(filter.value || '').toLowerCase();
      matched = (course.allowedZones || []).some(zone => zone.toLowerCase().includes(needle));
    } else if (filter.field === 'riskLevel' && filter.operator === 'eq') {
      matched = this.getCourseRiskLevel(course) === String(filter.value || 'low');
    }
    return filter.exclude ? !matched : matched;
  }

  private getCourseGroupCount(course: Course): number {
    if (course.studentGroupIds && course.studentGroupIds.length > 0) {
      return course.studentGroupIds.length;
    }
    if (course.studentGroupNames && course.studentGroupNames.length > 0) {
      return course.studentGroupNames.length;
    }
    return course.studentGroupId ? 1 : 0;
  }

  private getCourseGroupLevels(course: Course): number[] {
    const names = course.studentGroupNames && course.studentGroupNames.length > 0
      ? course.studentGroupNames
      : (course.studentGroupName ? [course.studentGroupName] : []);
    const levels = new Set<number>();
    for (const name of names) {
      const matched = name.match(/\b(\d{3})\b/);
      if (matched) {
        levels.add(Number(matched[1]));
      }
    }
    return Array.from(levels);
  }

  private getCoursePopulation(course: Course): number {
    const ids = course.studentGroupIds && course.studentGroupIds.length > 0
      ? course.studentGroupIds
      : (course.studentGroupId ? [course.studentGroupId] : []);
    if (ids.length > 0) {
      return ids.reduce((sum, id) => {
        const group = this.studentGroups.find(item => item.id === id);
        return sum + (group?.size || 0);
      }, 0);
    }

    const names = course.studentGroupNames && course.studentGroupNames.length > 0
      ? course.studentGroupNames
      : (course.studentGroupName ? [course.studentGroupName] : []);
    if (names.length > 0) {
      return names.reduce((sum, name) => {
        const group = this.studentGroups.find(item => item.name === name);
        return sum + (group?.size || 0);
      }, 0);
    }

    return 0;
  }

  private getCandidateRooms(course: Course): Room[] {
    if (course.online) {
      return [];
    }
    const requiredFeatures = (course.requiredFeatures || []).map(value => value.toLowerCase());
    const allowedZones = (course.allowedZones || []).map(value => value.toLowerCase());
    const population = this.getCoursePopulation(course);

    return this.rooms.filter(room => {
      if (population > 0 && room.capacity < population) {
        return false;
      }
      if (requiredFeatures.length > 0) {
        const roomFeatures = (room.features || []).map(value => value.toLowerCase());
        if (!requiredFeatures.every(feature => roomFeatures.some(roomFeature => roomFeature === feature))) {
          return false;
        }
      }
      if (allowedZones.length > 0) {
        const zoneName = (room.zoneName || '').toLowerCase();
        if (!allowedZones.includes(zoneName)) {
          return false;
        }
      }
      return true;
    });
  }

  getCandidateRoomCount(course: Course): number {
    return this.getCandidateRooms(course).length;
  }

  getCandidateRoomsTooltip(course: Course): string {
    if (course.online) {
      return 'Online course: no physical room required.';
    }
    const rooms = this.getCandidateRooms(course);
    if (rooms.length === 0) {
      return 'No feasible candidate rooms.';
    }
    const names = rooms.map(room => room.name).filter(Boolean);
    return `Candidate rooms (${rooms.length}): ${names.join(', ')}`;
  }

  getRiskPillTooltip(course: Course): string {
    const risk = this.getCourseRiskLevel(course);
    if (course.online) {
      return 'Low risk: online course, no room allocation needed.';
    }
    if (risk === 'high') {
      return 'High risk: zero feasible candidate rooms.';
    }
    if (risk === 'medium') {
      return 'Medium risk: limited room options.';
    }
    return 'Low risk: healthy number of candidate rooms.';
  }

  getCourseRiskLevel(course: Course): 'high' | 'medium' | 'low' {
    if (course.online) {
      return 'low';
    }
    const candidates = this.getCandidateRoomCount(course);
    if (candidates === 0) {
      return 'high';
    }
    if (candidates <= 3) {
      return 'medium';
    }
    return 'low';
  }

  populationExceedsAnyRoom(course: Course): boolean {
    if (course.online || this.rooms.length === 0) {
      return false;
    }
    const maxCapacity = this.rooms.reduce((max, room) => Math.max(max, room.capacity), 0);
    return this.getCoursePopulation(course) > maxCapacity;
  }

  requiredFeaturesHaveNoRoomCoverage(course: Course): boolean {
    if (course.online || (course.requiredFeatures || []).length === 0) {
      return false;
    }
    const requiredFeatures = (course.requiredFeatures || []).map(value => value.toLowerCase());
    return !this.rooms.some(room => {
      const roomFeatures = (room.features || []).map(value => value.toLowerCase());
      return requiredFeatures.every(feature => roomFeatures.includes(feature));
    });
  }

  hasConstraintDensityHigh(course: Course): boolean {
    if (course.online) {
      return false;
    }

    const requiredFeatureCount = (course.requiredFeatures || []).length;
    const hasAllowedZoneConstraint = (course.allowedZones || []).length > 0;
    const hasCapacityConstraint = this.getCoursePopulation(course) > 0;

    // Course is brittle when multiple hard constraints stack up.
    const densityScore = (requiredFeatureCount >= 2 ? 2 : requiredFeatureCount > 0 ? 1 : 0)
      + (hasAllowedZoneConstraint ? 1 : 0)
      + (hasCapacityConstraint ? 1 : 0);
    return densityScore >= 4;
  }

  hasSingleRoomDependency(course: Course): boolean {
    if (course.online) {
      return false;
    }
    return this.getCandidateRoomCount(course) === 1;
  }

  hasLowRoomDiversity(course: Course): boolean {
    if (course.online) {
      return false;
    }
    const candidateCount = this.getCandidateRoomCount(course);
    return candidateCount > 1 && candidateCount <= 2;
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

  private compareBySortStack(a: Course, b: Course, sortStack: DataQueryState['sort']): number {
    for (const sort of sortStack) {
      const value = this.compareCourses(a, b, sort.field, sort.direction);
      if (value !== 0) {
        return value;
      }
    }
    return 0;
  }

  onPageSizeChange(size: number) {
    this.queryState = { ...this.queryState, pagination: { page: 1, size } };
    this.clearSelection();
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
    const totalPages = Math.max(1, Math.ceil(this.filteredCourses.length / this.queryState.pagination.size));
    const page = Math.min(totalPages, this.queryState.pagination.page + 1);
    this.queryState = { ...this.queryState, pagination: { ...this.queryState.pagination, page } };
    this.syncQueryStateToUrl();
  }

  goToLastPage() {
    const totalPages = Math.max(1, Math.ceil(this.filteredCourses.length / this.queryState.pagination.size));
    this.queryState = { ...this.queryState, pagination: { ...this.queryState.pagination, page: totalPages } };
    this.syncQueryStateToUrl();
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
    this.savedViewOptions = this.queryViews.list('courses').map(view => ({
      id: view.id,
      name: view.name
    }));
  }

}
