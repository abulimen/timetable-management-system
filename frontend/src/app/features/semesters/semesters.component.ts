import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, SemesterArchive } from '../../core/services/api.service';

@Component({
    selector: 'app-semesters',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Semester Archives</h1>
        <button (click)="showArchiveForm = true" class="btn btn-primary">Archive Current</button>
      </div>

      <div *ngIf="showArchiveForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">Archive Current Semester</h2>
        <form (ngSubmit)="archiveSemester()" class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">Code (e.g., 2024_2025_S1)</label>
              <input type="text" [(ngModel)]="archiveForm.code" name="code" class="input" required>
            </div>
            <div>
              <label class="label">Name</label>
              <input type="text" [(ngModel)]="archiveForm.name" name="name" class="input">
            </div>
            <div>
              <label class="label">Academic Year</label>
              <input type="text" [(ngModel)]="archiveForm.academicYear" name="academicYear" class="input" placeholder="2024/2025">
            </div>
            <div>
              <label class="label">Semester Number</label>
              <select [(ngModel)]="archiveForm.semesterNumber" name="semesterNumber" class="input">
                <option [ngValue]="1">1st Semester</option>
                <option [ngValue]="2">2nd Semester</option>
              </select>
            </div>
          </div>
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary">Archive</button>
            <button type="button" (click)="showArchiveForm = false" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <div class="card overflow-hidden">
        <table class="w-full">
          <thead class="bg-secondary-100 dark:bg-secondary-700">
            <tr>
              <th class="text-left px-6 py-3 text-sm font-medium">Code</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Academic Year</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Archived At</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Stats</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let archive of archives" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium text-primary-600">{{ archive.code }}</td>
              <td class="px-6 py-4">{{ archive.name }}</td>
              <td class="px-6 py-4">{{ archive.academicYear }} S{{ archive.semesterNumber }}</td>
              <td class="px-6 py-4 text-secondary-500">{{ archive.archivedAt | date:'short' }}</td>
              <td class="px-6 py-4 text-sm">
                {{ archive.courseCount }} courses, {{ archive.lessonCount }} lessons
              </td>
              <td class="px-6 py-4 text-right">
                <button (click)="deleteArchive(archive.code)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
            <tr *ngIf="archives.length === 0">
              <td colspan="6" class="px-6 py-8 text-center text-secondary-500">No archives found</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class SemestersComponent implements OnInit {
    private api = inject(ApiService);
    archives: SemesterArchive[] = [];
    showArchiveForm = false;
    archiveForm = { code: '', name: '', academicYear: '', semesterNumber: 1 };

    ngOnInit() { this.loadArchives(); }

    loadArchives() {
        this.api.getSemesterArchives().subscribe({ next: (a) => this.archives = a });
    }

    archiveSemester() {
        this.api.archiveSemester(this.archiveForm).subscribe({
            next: () => { this.loadArchives(); this.showArchiveForm = false; this.archiveForm = { code: '', name: '', academicYear: '', semesterNumber: 1 }; },
            error: (err) => alert('Failed to archive: ' + err.message)
        });
    }

    deleteArchive(code: string) {
        if (confirm('Delete this archive? This cannot be undone.')) {
            this.api.deleteSemesterArchive(code).subscribe({ next: () => this.loadArchives() });
        }
    }
}
