import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, TimetableEntry, StudentGroup, Lecturer, Room } from '../../core/services/api.service';

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
        <div class="flex gap-4 items-end">
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
        <div class="grid grid-cols-6 gap-px bg-secondary-200 dark:bg-secondary-700 min-w-[800px]">
          <!-- Header -->
          <div class="bg-secondary-100 dark:bg-secondary-800 p-3 font-medium text-center">Time</div>
          <div *ngFor="let day of days" class="bg-secondary-100 dark:bg-secondary-800 p-3 font-medium text-center">{{ day }}</div>

          <!-- Time slots -->
          <ng-container *ngFor="let hour of hours">
            <div class="bg-white dark:bg-secondary-800 p-3 text-sm text-secondary-500 text-center border-t border-secondary-200 dark:border-secondary-700">
              {{ hour }}:00
            </div>
            <div *ngFor="let day of days" class="bg-white dark:bg-secondary-800 p-2 min-h-[80px] border-t border-secondary-200 dark:border-secondary-700">
              <ng-container *ngFor="let entry of getLessonsAt(day, hour)">
                <div 
                  class="p-2 rounded text-xs mb-1 cursor-pointer hover:opacity-80"
                  [ngClass]="{'ring-2 ring-yellow-400': entry.pinned, 'ring-2 ring-blue-400': entry.online}"
                  [style.background-color]="entry.online ? '#3b82f6' : getColor(entry.courseCode)"
                  [style.color]="'white'"
                  (click)="togglePin(entry)">
                  <div class="font-bold">{{ entry.courseCode }}</div>
                  <div>{{ entry.online ? '🌐 Online' : entry.roomName }}</div>
                  <div class="opacity-75">{{ entry.lecturerName | slice:0:15 }}</div>
                </div>
              </ng-container>
            </div>
          </ng-container>
        </div>
      </div>

      <p class="text-sm text-secondary-500">Click a lesson to toggle pin status. Pinned lessons (yellow border) won't move during solving.</p>
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
  hours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17];
  filters = { studentGroupId: undefined as number | undefined, lecturerId: undefined as number | undefined, roomId: undefined as number | undefined };

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
    this.api.getTimetable(params).subscribe({ next: (e) => this.entries = e });
  }

  getLessonsAt(day: string, hour: number): TimetableEntry[] {
    return this.entries.filter(e => {
      if (e.dayOfWeek !== day) return false;
      const startHour = parseInt(e.startTime?.split(':')[0] || '0');
      return startHour === hour;
    });
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
    const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#06b6d4'];
    let hash = 0;
    for (let i = 0; i < code.length; i++) hash = code.charCodeAt(i) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
  }
}
