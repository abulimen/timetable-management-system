import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  ApiService,
  TimetableEntry,
  StudentGroup,
  Lecturer,
  Room,
  SpecialEventEntry,
  Setting,
  TimetableChangeStatus
} from '../../core/services/api.service';

interface PositionedEntry extends TimetableEntry {
  column: number;
  totalColumns: number;
}

interface VisibleEvent extends SpecialEventEntry {
  topPx: number;
  heightPx: number;
}

@Component({
  selector: 'app-timetable',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">
          {{ isArchiveMode ? ('Archived Timetable: ' + archiveCode) : 'Timetable' }}
        </h1>
      </div>

      <div *ngIf="!isArchiveMode && changeStatus?.pendingChanges" class="card p-4 border border-amber-300 bg-amber-50/80">
        <div class="flex items-center justify-between gap-3 flex-wrap">
          <div>
            <p class="text-sm font-medium text-amber-900">Unreplanned timetable changes detected</p>
            <p class="text-xs text-amber-800">
              {{ changeStatus?.reason || 'Post-generation changes pending replan.' }}
              <span *ngIf="changeStatus?.changedAt">({{ changeStatus?.changedAt | date:'medium' }})</span>
            </p>
          </div>
        </div>
      </div>

      <!-- Filters -->
      <div class="card p-4">
        <div class="flex gap-4 items-end flex-wrap">
          <div>
            <label class="label">Student Group</label>
            <select [(ngModel)]="filters.studentGroupId" (change)="onFilterChange()" class="input w-48">
              <option [ngValue]="undefined">All Groups</option>
              <option *ngFor="let g of studentGroups" [ngValue]="g.id">{{ g.name }}</option>
            </select>
          </div>
          <div>
            <label class="label">Lecturer</label>
            <select [(ngModel)]="filters.lecturerId" (change)="onFilterChange()" class="input w-48">
              <option [ngValue]="undefined">All Lecturers</option>
              <option *ngFor="let l of lecturers" [ngValue]="l.id">{{ l.name }}</option>
            </select>
          </div>
          <div>
            <label class="label">Room</label>
            <select [(ngModel)]="filters.roomId" (change)="onFilterChange()" class="input w-48">
              <option [ngValue]="undefined">All Rooms</option>
              <option *ngFor="let r of rooms" [ngValue]="r.id">{{ r.name }}</option>
            </select>
          </div>
          <button (click)="clearFilters()" class="btn btn-secondary">Clear</button>
        </div>
      </div>

      <!-- Timetable Grid -->
      <div class="card overflow-x-auto">
        <div class="flex" [style.min-width.px]="getMinTotalWidth()">
          <!-- Time column (fixed width) -->
          <div class="flex-shrink-0 w-16 bg-secondary-100 dark:bg-secondary-800">
            <div class="h-12 p-2 font-medium text-center text-sm border-b border-secondary-200 dark:border-secondary-700">Time</div>
            <div *ngFor="let hour of hours"
                 class="h-20 p-2 text-sm text-secondary-500 text-center border-b border-secondary-200 dark:border-secondary-700 flex flex-col items-center justify-center"
                 [ngClass]="isLunchHour(hour) ? 'bg-amber-100/80 dark:bg-amber-900/40 text-amber-900 dark:text-amber-200' : ''">
              <span>{{ hour }}:00</span>
              <span *ngIf="isLunchHour(hour)" class="text-[10px] font-semibold">Lunch</span>
            </div>
          </div>
          
          <!-- Day columns - flex-grow to fill space, but with minimum width -->
          <div *ngFor="let day of days" 
               class="flex-grow border-l border-secondary-200 dark:border-secondary-700"
               [style.min-width.px]="getDayMinWidth(day)">
            <!-- Day header -->
            <div class="h-12 p-2 font-medium text-center text-sm bg-secondary-100 dark:bg-secondary-800 border-b border-secondary-200 dark:border-secondary-700">
              {{ day }}
            </div>
            
            <!-- Day content with lessons -->
            <div class="relative bg-white dark:bg-secondary-800 h-full">
              <!-- Hour grid lines -->
              <div *ngFor="let hour of hours"
                   class="h-20 border-b border-secondary-200 dark:border-secondary-700 relative"
                   [ngClass]="isLunchHour(hour) ? 'bg-amber-50/80 dark:bg-amber-900/20' : ''">
                <div *ngIf="isLunchHour(hour)"
                     class="absolute inset-0 flex items-center justify-center text-[10px] font-semibold text-amber-700/70 dark:text-amber-200/60 pointer-events-none">
                  LUNCH BREAK
                </div>
              </div>
              
              <!-- Lessons - use percentage-based positioning -->
              <ng-container *ngFor="let event of getVisibleEventsForDay(day)">
                <div
                  class="absolute p-2 rounded text-xs overflow-hidden z-0 border border-red-200 bg-red-100/90 text-red-900"
                  [style.top.px]="event.topPx"
                  [style.height.px]="event.heightPx"
                  [style.left]="'2px'"
                  [style.right]="'2px'"
                  [title]="getEventTooltip(event)">
                  <div class="font-bold text-[11px] leading-tight truncate">📌 {{ event.name }}</div>
                  <div class="truncate text-[10px]">
                    {{ event.online ? '🌐 Online Event' : (event.roomName || 'No Location') }}
                  </div>
                </div>
              </ng-container>

              <ng-container *ngFor="let entry of getPositionedLessonsForDay(day)">
                <div 
                  class="absolute p-2 rounded text-xs cursor-pointer hover:opacity-90 overflow-hidden z-10 border border-white/20"
                  [ngClass]="{'ring-2 ring-yellow-400': entry.pinned, 'ring-2 ring-blue-400': entry.online}"
                  [style.background-color]="entry.online ? '#3b82f6' : getColor(entry.courseCode)"
                  [style.color]="'white'"
                  [style.top.px]="getTopPosition(entry)"
                  [style.height.px]="getHeight(entry)"
                  [style.left]="getLeftPercent(entry)"
                  [style.width]="getWidthPercent(entry)"
                  [title]="getLessonTooltip(entry)"
                  (click)="togglePin(entry)">
                  <div class="font-bold text-[11px] leading-tight truncate">
                    {{ entry.courseCode }}
                    <span *ngIf="entry.durationHours > 1" class="opacity-70">({{ entry.durationHours }}h)</span>
                  </div>
                  <div class="truncate text-[10px] opacity-90">{{ entry.online ? '🌐 Online' : entry.roomName }}</div>
                  <div class="opacity-75 truncate text-[10px]">{{ entry.lecturerName }}</div>
                  <div *ngIf="entry.combined" class="text-[9px] opacity-70 mt-0.5 truncate">
                    👥 {{ entry.combinedGroupNames.join(', ') }}
                  </div>
                </div>
              </ng-container>
            </div>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-4 text-sm text-secondary-500 flex-wrap">
        <span>🖱️ Click lesson to toggle pin</span>
        <span class="flex items-center gap-1"><span class="w-3 h-3 bg-yellow-400 rounded"></span> Pinned</span>
        <span class="flex items-center gap-1"><span class="w-3 h-3 bg-blue-400 rounded"></span> Online</span>
        <span>💡 Hover for full details</span>
      </div>
    </div>
  `
})
export class TimetableComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);

  entries: TimetableEntry[] = [];
  activeEvents: SpecialEventEntry[] = [];
  allStudentGroups: StudentGroup[] = [];
  studentGroups: StudentGroup[] = [];
  lecturers: Lecturer[] = [];
  rooms: Room[] = [];

  days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
  hours = [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17];
  hourHeight = 80;
  minColumnWidth = 120; // Minimum width per lesson column to ensure readability
  filters = { studentGroupId: undefined as number | undefined, lecturerId: undefined as number | undefined, roomId: undefined as number | undefined };
  archiveCode: string | null = null;
  isArchiveMode = false;
  lunchBreakStartHour = 12;
  lunchBreakEndHour = 13;
  lunchBreakConfigured = true;
  changeStatus: TimetableChangeStatus | null = null;

  private positionCache: Map<string, PositionedEntry[]> = new Map();
  private maxColumnsCache: Map<string, number> = new Map();

  ngOnInit() {
    this.archiveCode = this.route.snapshot.queryParamMap.get('archiveCode');
    this.isArchiveMode = !!this.archiveCode;
    this.loadLunchBreakSettings();
    this.refreshChangeStatus();
    this.loadTimetable();
  }

  loadTimetable() {
    if (this.isArchiveMode && this.archiveCode) {
      forkJoin({
        entries: this.api.getArchivedSemesterTimetable(this.archiveCode),
        events: this.api.getArchivedSemesterSpecialEvents(this.archiveCode),
        groups: this.api.getArchivedSemesterGroups(this.archiveCode)
      }).subscribe({
        next: ({ entries, events, groups }) => {
          this.entries = entries || [];
          this.activeEvents = events || [];
          this.allStudentGroups = groups || [];
          this.studentGroups = this.allStudentGroups
            .filter(group => group.parentGroupId !== null)
            .sort((a, b) => a.name.localeCompare(b.name));
          this.updateArchiveFilterOptions();
          this.positionCache.clear();
          this.maxColumnsCache.clear();
        }
      });
      return;
    }

    const params: any = {};
    if (this.filters.studentGroupId) params.student_group_id = this.filters.studentGroupId;
    if (this.filters.lecturerId) params.lecturer_id = this.filters.lecturerId;
    if (this.filters.roomId) params.room_id = this.filters.roomId;
    forkJoin({
      entries: this.api.getTimetable(params),
      events: this.api.getActiveSpecialEvents(),
      groups: this.api.getStudentGroups(),
      lecturers: this.api.getLecturers(),
      rooms: this.api.getRooms()
    }).subscribe({
      next: ({ entries, events, groups, lecturers, rooms }) => {
        this.entries = entries;
        this.activeEvents = events || [];
        this.allStudentGroups = groups || [];
        this.studentGroups = this.allStudentGroups
          .filter(group => group.parentGroupId !== null)
          .sort((a, b) => a.name.localeCompare(b.name));
        this.lecturers = lecturers || [];
        this.rooms = rooms || [];
        this.positionCache.clear();
        this.maxColumnsCache.clear();
        this.refreshChangeStatus();
      }
    });
  }

  getPositionedLessonsForDay(day: string): PositionedEntry[] {
    if (this.positionCache.has(day)) {
      return this.positionCache.get(day)!;
    }

    const dayLessons = this.getFilteredEntries().filter(e => e.dayOfWeek === day);

    // Sort by start time, then by duration (longer first)
    dayLessons.sort((a, b) => {
      const aStart = this.getTimeInMinutes(a.startTime);
      const bStart = this.getTimeInMinutes(b.startTime);
      if (aStart !== bStart) return aStart - bStart;
      return (b.durationHours || 1) - (a.durationHours || 1);
    });

    const positioned: PositionedEntry[] = [];
    const columns: { end: number }[] = [];

    for (const lesson of dayLessons) {
      const start = this.getTimeInMinutes(lesson.startTime);
      const end = this.getTimeInMinutes(lesson.endTime);

      // Find first available column
      let placed = false;
      for (let col = 0; col < columns.length; col++) {
        if (columns[col].end <= start) {
          const posEntry: PositionedEntry = { ...lesson, column: col, totalColumns: 1 };
          columns[col].end = end;
          positioned.push(posEntry);
          placed = true;
          break;
        }
      }

      if (!placed) {
        const posEntry: PositionedEntry = { ...lesson, column: columns.length, totalColumns: 1 };
        columns.push({ end });
        positioned.push(posEntry);
      }
    }

    // Update total columns for overlapping entries
    for (const entry of positioned) {
      const start = this.getTimeInMinutes(entry.startTime);
      const end = this.getTimeInMinutes(entry.endTime);

      let maxCol = entry.column + 1;
      for (const other of positioned) {
        if (other === entry) continue;
        const otherStart = this.getTimeInMinutes(other.startTime);
        const otherEnd = this.getTimeInMinutes(other.endTime);

        if (start < otherEnd && end > otherStart) {
          maxCol = Math.max(maxCol, other.column + 1);
        }
      }
      entry.totalColumns = maxCol;
    }

    this.maxColumnsCache.set(day, columns.length);
    this.positionCache.set(day, positioned);
    return positioned;
  }

  getMaxColumnsForDay(day: string): number {
    if (!this.maxColumnsCache.has(day)) {
      this.getPositionedLessonsForDay(day);
    }
    return Math.max(1, this.maxColumnsCache.get(day) || 1);
  }

  getVisibleEventsForDay(day: string): VisibleEvent[] {
    const selectedGroupId = this.filters.studentGroupId;
    const selectedLecturerId = this.filters.lecturerId;
    const selectedRoomId = this.filters.roomId;
    return this.activeEvents
      .filter((event) => event.dayOfWeek === day)
      .filter((event) => {
        if (selectedLecturerId && event.lecturerId !== selectedLecturerId) return false;
        if (selectedRoomId && event.roomId !== selectedRoomId) return false;
        if (selectedGroupId && !this.eventTouchesGroup(event, selectedGroupId)) return false;
        return true;
      })
      .map((event) => ({
        ...event,
        topPx: this.getTopPosition(event),
        heightPx: this.getHeight(event)
      }));
  }

  getDayMinWidth(day: string): number {
    const cols = this.getMaxColumnsForDay(day);
    return cols * this.minColumnWidth;
  }

  getMinTotalWidth(): number {
    let total = 64; // Time column
    for (const day of this.days) {
      total += this.getDayMinWidth(day);
    }
    return total;
  }

  getTimeInMinutes(time: string): number {
    if (!time) return 0;
    const [hours, minutes] = time.split(':').map(Number);
    return hours * 60 + (minutes || 0);
  }

  getTopPosition(entry: { startTime: string }): number {
    const startHour = parseInt(entry.startTime?.split(':')[0] || '0');
    const startMinute = parseInt(entry.startTime?.split(':')[1] || '0');
    const hoursFromStart = (startHour - this.hours[0]) + (startMinute / 60);
    return hoursFromStart * this.hourHeight;
  }

  getHeight(entry: { durationHours: number }): number {
    return (entry.durationHours || 1) * this.hourHeight - 4;
  }

  getLeftPercent(entry: PositionedEntry): string {
    const percent = (entry.column / entry.totalColumns) * 100;
    return `calc(${percent}% + 2px)`;
  }

  getWidthPercent(entry: PositionedEntry): string {
    const percent = (1 / entry.totalColumns) * 100;
    return `calc(${percent}% - 4px)`;
  }

  getLessonTooltip(entry: TimetableEntry): string {
    let tooltip = `${entry.courseCode}: ${entry.courseName}\n`;
    tooltip += `${entry.startTime} - ${entry.endTime} (${entry.durationHours}hr)\n`;
    tooltip += `${entry.lecturerName}\n`;
    tooltip += entry.online ? 'Online' : entry.roomName;
    if (entry.combined) {
      tooltip += `\nCombined: ${entry.combinedGroupNames.join(', ')}`;
    }
    return tooltip;
  }

  getEventTooltip(event: SpecialEventEntry): string {
    let tooltip = `Special Event: ${event.name}\n`;
    tooltip += `${event.startTime} - ${event.endTime} (${event.durationHours}hr)\n`;
    tooltip += event.online ? 'Online Event' : `Location: ${event.roomName || 'N/A'}`;
    if (event.lecturerName) {
      tooltip += `\nLecturer: ${event.lecturerName}`;
    }
    const displayGroups = this.getDisplayGroupNamesForEvent(event);
    if (displayGroups.length) {
      tooltip += `\nGroups: ${displayGroups.join(', ')}`;
    }
    return tooltip;
  }

  private eventTouchesGroup(event: SpecialEventEntry, selectedGroupId: number): boolean {
    return (event.studentGroupIds || []).some((eventGroupId) => this.groupsConflict(eventGroupId, selectedGroupId));
  }

  private groupsConflict(groupAId: number, groupBId: number): boolean {
    if (groupAId === groupBId) return true;
    const groupA = this.allStudentGroups.find((g) => g.id === groupAId);
    const groupB = this.allStudentGroups.find((g) => g.id === groupBId);
    if (!groupA || !groupB) return false;
    return groupA.parentGroupId === groupB.id || groupB.parentGroupId === groupA.id;
  }

  private getDisplayGroupNamesForEvent(event: SpecialEventEntry): string[] {
    const names = new Set<string>();
    const ids = event.studentGroupIds || [];
    for (const id of ids) {
      const group = this.allStudentGroups.find(g => g.id === id);
      if (!group) {
        continue;
      }
      if (group.parentGroupId !== null) {
        names.add(group.name);
        continue;
      }
      const children = this.allStudentGroups.filter(child => child.parentGroupId === group.id);
      if (children.length > 0) {
        for (const child of children) {
          names.add(child.name);
        }
      } else {
        names.add(group.name);
      }
    }
    return Array.from(names).sort((a, b) => a.localeCompare(b));
  }

  togglePin(entry: TimetableEntry) {
    this.api.updateLesson(entry.lessonId, { pinned: !entry.pinned }).subscribe({
      next: () => this.loadTimetable()
    });
  }

  clearFilters() {
    this.filters = { studentGroupId: undefined, lecturerId: undefined, roomId: undefined };
    this.onFilterChange();
  }

  onFilterChange() {
    if (this.isArchiveMode) {
      this.positionCache.clear();
      this.maxColumnsCache.clear();
      return;
    }
    this.loadTimetable();
  }

  getColor(code: string): string {
    const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16', '#f97316'];
    let hash = 0;
    for (let i = 0; i < code.length; i++) hash = code.charCodeAt(i) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
  }

  isLunchHour(hour: number): boolean {
    if (!this.lunchBreakConfigured) {
      return false;
    }
    return hour >= this.lunchBreakStartHour && hour < this.lunchBreakEndHour;
  }

  private getFilteredEntries(): TimetableEntry[] {
    if (!this.isArchiveMode) {
      return this.entries;
    }
    return this.entries.filter(entry => {
      if (this.filters.lecturerId && entry.lecturerId !== this.filters.lecturerId) return false;
      if (this.filters.roomId && entry.roomId !== this.filters.roomId) return false;
      if (this.filters.studentGroupId) {
        const selectedGroupId = this.filters.studentGroupId;
        const matchesPrimary = entry.studentGroupId && this.groupsConflict(entry.studentGroupId, selectedGroupId);
        const matchesCombined = (entry.combinedGroupNames || []).some(name => {
          const group = this.allStudentGroups.find(g => g.name === name);
          return group ? this.groupsConflict(group.id, selectedGroupId) : false;
        });
        if (!matchesPrimary && !matchesCombined) return false;
      }
      return true;
    });
  }

  private updateArchiveFilterOptions() {
    const lecturerMap = new Map<number, Lecturer>();
    const roomMap = new Map<number, Room>();
    for (const entry of this.entries) {
      if (entry.lecturerId && !lecturerMap.has(entry.lecturerId)) {
        lecturerMap.set(entry.lecturerId, { id: entry.lecturerId, name: entry.lecturerName, email: '', unavailabilities: [] });
      }
      if (entry.roomId && !roomMap.has(entry.roomId)) {
        roomMap.set(entry.roomId, {
          id: entry.roomId,
          name: entry.roomName,
          capacity: entry.roomCapacity || 0,
          zoneId: 0,
          zoneName: '',
          features: [],
          featureIds: []
        });
      }
    }
    for (const event of this.activeEvents) {
      if (event.lecturerId && !lecturerMap.has(event.lecturerId)) {
        lecturerMap.set(event.lecturerId, { id: event.lecturerId, name: event.lecturerName || 'Unknown', email: '', unavailabilities: [] });
      }
      if (event.roomId && !roomMap.has(event.roomId)) {
        roomMap.set(event.roomId, {
          id: event.roomId,
          name: event.roomName || 'Unknown',
          capacity: 0,
          zoneId: 0,
          zoneName: '',
          features: [],
          featureIds: []
        });
      }
    }
    this.lecturers = Array.from(lecturerMap.values()).sort((a, b) => a.name.localeCompare(b.name));
    this.rooms = Array.from(roomMap.values()).sort((a, b) => a.name.localeCompare(b.name));
  }

  private loadLunchBreakSettings() {
    this.api.getSettings().subscribe({
      next: (settings) => this.applyLunchSettings(settings),
      error: () => {
        // Keep defaults when settings API fails.
      }
    });
  }

  private applyLunchSettings(settings: Setting[]) {
    const startRaw = this.findSettingValue(settings, 'lunch_break_start');
    const endRaw = this.findSettingValue(settings, 'lunch_break_end');
    const startHour = this.parseHour(startRaw);
    const endHour = this.parseHour(endRaw);

    if (startHour === null || endHour === null || endHour <= startHour) {
      this.lunchBreakConfigured = false;
      return;
    }

    this.lunchBreakStartHour = startHour;
    this.lunchBreakEndHour = endHour;
    this.lunchBreakConfigured = true;
  }

  private findSettingValue(settings: Setting[], key: string): string | null {
    const found = settings.find(s => s.key === key);
    return found?.value ?? null;
  }

  private parseHour(timeValue: string | null): number | null {
    if (!timeValue) {
      return null;
    }
    const [hourRaw] = timeValue.split(':');
    const hour = Number(hourRaw);
    if (!Number.isFinite(hour) || hour < 0 || hour > 23) {
      return null;
    }
    return hour;
  }

  private refreshChangeStatus() {
    if (this.isArchiveMode) {
      this.changeStatus = null;
      return;
    }
    this.api.getTimetableChangeStatus().subscribe({
      next: status => this.changeStatus = status,
      error: () => {
        // Keep UI usable if status endpoint fails.
      }
    });
  }

}
