import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, TimetableEntry, StudentGroup, Lecturer, Room } from '../../core/services/api.service';

interface PositionedEntry extends TimetableEntry {
  column: number;
  totalColumns: number;
}

@Component({
  selector: 'app-timetable',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Timetable</h1>
      </div>

      <!-- Filters -->
      <div class="card p-4">
        <div class="flex gap-4 items-end flex-wrap">
          <div>
            <label class="label">Student Group</label>
            <select [(ngModel)]="filters.studentGroupId" (change)="loadTimetable()" class="input w-48">
              <option [ngValue]="undefined">All Groups</option>
              <option *ngFor="let g of studentGroups" [ngValue]="g.id">{{ g.name }}</option>
            </select>
          </div>
          <div>
            <label class="label">Lecturer</label>
            <select [(ngModel)]="filters.lecturerId" (change)="loadTimetable()" class="input w-48">
              <option [ngValue]="undefined">All Lecturers</option>
              <option *ngFor="let l of lecturers" [ngValue]="l.id">{{ l.name }}</option>
            </select>
          </div>
          <div>
            <label class="label">Room</label>
            <select [(ngModel)]="filters.roomId" (change)="loadTimetable()" class="input w-48">
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
                 class="h-20 p-2 text-sm text-secondary-500 text-center border-b border-secondary-200 dark:border-secondary-700 flex items-start justify-center">
              {{ hour }}:00
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
              <div *ngFor="let hour of hours" class="h-20 border-b border-secondary-200 dark:border-secondary-700"></div>
              
              <!-- Lessons - use percentage-based positioning -->
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

  entries: TimetableEntry[] = [];
  studentGroups: StudentGroup[] = [];
  lecturers: Lecturer[] = [];
  rooms: Room[] = [];

  days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
  hours = [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17];
  hourHeight = 80;
  minColumnWidth = 120; // Minimum width per lesson column to ensure readability
  filters = { studentGroupId: undefined as number | undefined, lecturerId: undefined as number | undefined, roomId: undefined as number | undefined };

  private positionCache: Map<string, PositionedEntry[]> = new Map();
  private maxColumnsCache: Map<string, number> = new Map();

  ngOnInit() {
    this.loadTimetable();
    this.api.getStudentGroups().subscribe({ next: (g) => this.studentGroups = g });
    this.api.getLecturers().subscribe({ next: (l) => this.lecturers = l });
    this.api.getRooms().subscribe({ next: (r) => this.rooms = r });
  }

  loadTimetable() {
    const params: any = {};
    if (this.filters.studentGroupId) params.student_group_id = this.filters.studentGroupId;
    if (this.filters.lecturerId) params.lecturer_id = this.filters.lecturerId;
    if (this.filters.roomId) params.room_id = this.filters.roomId;
    this.api.getTimetable(params).subscribe({
      next: (e) => {
        this.entries = e;
        this.positionCache.clear();
        this.maxColumnsCache.clear();
      }
    });
  }

  getPositionedLessonsForDay(day: string): PositionedEntry[] {
    if (this.positionCache.has(day)) {
      return this.positionCache.get(day)!;
    }

    const dayLessons = this.entries.filter(e => e.dayOfWeek === day);

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

  getTopPosition(entry: TimetableEntry): number {
    const startHour = parseInt(entry.startTime?.split(':')[0] || '0');
    const startMinute = parseInt(entry.startTime?.split(':')[1] || '0');
    const hoursFromStart = (startHour - this.hours[0]) + (startMinute / 60);
    return hoursFromStart * this.hourHeight;
  }

  getHeight(entry: TimetableEntry): number {
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

  togglePin(entry: TimetableEntry) {
    this.api.updateLesson(entry.lessonId, { pinned: !entry.pinned }).subscribe({
      next: () => this.loadTimetable()
    });
  }

  clearFilters() {
    this.filters = { studentGroupId: undefined, lecturerId: undefined, roomId: undefined };
    this.loadTimetable();
  }

  getColor(code: string): string {
    const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16', '#f97316'];
    let hash = 0;
    for (let i = 0; i < code.length; i++) hash = code.charCodeAt(i) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
  }
}
