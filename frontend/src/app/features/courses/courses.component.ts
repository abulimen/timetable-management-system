import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, Course, Lecturer, StudentGroup } from '../../core/services/api.service';

@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Courses</h1>
        <div class="flex gap-2">
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="courses.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Course</button>
        </div>
      </div>

      <!-- Bulk Delete Confirmation -->
      <div *ngIf="showDeleteAllConfirm" class="card p-4 bg-red-500/10 border border-red-500/50">
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

      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingCourse ? 'Edit' : 'Add' }} Course</h2>
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
            <div>
              <label class="label">Student Group</label>
              <select [(ngModel)]="formData.studentGroupId" name="studentGroupId" class="input">
                <option [ngValue]="null">Select Group</option>
                <option *ngFor="let g of studentGroups" [ngValue]="g.id">{{ g.name }}</option>
              </select>
            </div>
            <div class="flex items-end gap-4">
              <label class="flex items-center gap-2">
                <input type="checkbox" [(ngModel)]="formData.online" name="online" class="w-4 h-4">
                <span class="text-sm">🌐 Online Course</span>
              </label>
              <label class="flex items-center gap-2">
                <input type="checkbox" [(ngModel)]="formData.generateLessons" name="generateLessons">
                <span class="text-sm">Generate lessons on save</span>
              </label>
            </div>
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
              <th class="text-left px-6 py-3 text-sm font-medium">Code</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Type</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Hours/Week</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Lecturer</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Group</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let course of courses" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium text-primary-600">{{ course.code }}</td>
              <td class="px-6 py-4">{{ course.name }}</td>
              <td class="px-6 py-4">
                <span *ngIf="course.online" class="px-2 py-1 bg-blue-100 text-blue-700 text-xs rounded-full">🌐 Online</span>
                <span *ngIf="!course.online" class="px-2 py-1 bg-secondary-100 text-secondary-600 text-xs rounded-full">🏫 In-Person</span>
              </td>
              <td class="px-6 py-4">{{ course.totalWeeklyHours }}</td>
              <td class="px-6 py-4">{{ course.lecturerName || '-' }}</td>
              <td class="px-6 py-4">{{ course.studentGroupName || '-' }}</td>
              <td class="px-6 py-4 text-right">
                <button (click)="editCourse(course)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteCourse(course.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="courses.length === 0" class="p-8 text-center text-secondary-500">No courses found.</div>
      </div>
    </div>
  `
})
export class CoursesComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  courses: Course[] = [];
  lecturers: Lecturer[] = [];
  studentGroups: StudentGroup[] = [];
  showAddForm = false;
  editingCourse: Course | null = null;
  formData = { code: '', name: '', totalWeeklyHours: 2, lecturerId: null as number | null, studentGroupId: null as number | null, generateLessons: true, online: false };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;

  ngOnInit() {
    this.loadCourses();
    this.api.getLecturers().subscribe({ next: (l) => this.lecturers = l });
    this.api.getStudentGroups().subscribe({ next: (g) => this.studentGroups = g });
  }

  loadCourses() { this.api.getCourses().subscribe({ next: (c) => this.courses = c }); }

  saveCourse() {
    const obs = this.editingCourse
      ? this.api.updateCourse(this.editingCourse.id, this.formData)
      : this.api.createCourse(this.formData);
    obs.subscribe({ next: () => { this.loadCourses(); this.cancelEdit(); } });
  }

  editCourse(c: Course) {
    this.editingCourse = c;
    this.formData = { code: c.code, name: c.name, totalWeeklyHours: c.totalWeeklyHours, lecturerId: c.lecturerId, studentGroupId: c.studentGroupId, generateLessons: false, online: c.online || false };
    this.showAddForm = true;
  }

  deleteCourse(id: number) {
    if (confirm('Delete?')) this.api.deleteCourse(id).subscribe({ next: () => this.loadCourses() });
  }

  cancelEdit() { this.showAddForm = false; this.editingCourse = null; this.formData = { code: '', name: '', totalWeeklyHours: 2, lecturerId: null, studentGroupId: null, generateLessons: true, online: false }; }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/courses/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadCourses(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    this.importing = true;
    this.http.post<any>('http://localhost:8080/api/v1/bulk/courses/import', formData).subscribe({
      next: () => { this.importing = false; this.loadCourses(); input.value = ''; },
      error: (err) => { this.importing = false; alert('Import failed: ' + (err.error?.message || 'Unknown error')); input.value = ''; }
    });
  }
}
