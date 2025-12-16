import { Component, OnInit, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, Lecturer } from '../../core/services/api.service';

@Component({
  selector: 'app-lecturers',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Lecturers</h1>
        <div class="flex gap-2">
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="lecturers.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Lecturer</button>
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
              <th class="text-left px-6 py-3 text-sm font-medium">Unavailability</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let lecturer of lecturers" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium text-secondary-900 dark:text-white">{{ lecturer.name }}</td>
              <td class="px-6 py-4 text-secondary-500">{{ lecturer.email || '-' }}</td>
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
        <div *ngIf="lecturers.length === 0" class="p-8 text-center text-secondary-500">
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

  lecturers: Lecturer[] = [];
  showAddForm = false;
  editingLecturer: Lecturer | null = null;
  formData = { name: '', email: '' };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;
  importResult: any = null;

  ngOnInit() { this.loadLecturers(); }

  loadLecturers() {
    this.api.getLecturers().subscribe({ next: (lecturers) => this.lecturers = lecturers });
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
}


