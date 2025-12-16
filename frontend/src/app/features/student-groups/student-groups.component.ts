import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, StudentGroup } from '../../core/services/api.service';

@Component({
  selector: 'app-student-groups',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Student Groups</h1>
        <div class="flex gap-2">
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="groups.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Group</button>
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
        <form (ngSubmit)="saveGroup()" class="space-y-4">
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="label">Name (e.g., COSC_1_A)</label>
              <input type="text" [(ngModel)]="formData.name" name="name" class="input" required>
            </div>
            <div>
              <label class="label">Size</label>
              <input type="number" [(ngModel)]="formData.size" name="size" class="input" required>
            </div>
            <div>
              <label class="label">Parent Group</label>
              <select [(ngModel)]="formData.parentGroupId" name="parentGroupId" class="input">
                <option [ngValue]="null">None</option>
                <option *ngFor="let g of groups" [ngValue]="g.id">{{ g.name }}</option>
              </select>
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
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Size</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Parent</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Children</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let group of groups" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium">{{ group.name }}</td>
              <td class="px-6 py-4">{{ group.size }}</td>
              <td class="px-6 py-4">{{ group.parentGroupName || '-' }}</td>
              <td class="px-6 py-4">{{ group.childCount }}</td>
              <td class="px-6 py-4 text-right">
                <button (click)="editGroup(group)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteGroup(group.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="groups.length === 0" class="p-8 text-center text-secondary-500">No student groups found.</div>
      </div>
    </div>
  `
})
export class StudentGroupsComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  groups: StudentGroup[] = [];
  showAddForm = false;
  editingGroup: StudentGroup | null = null;
  formData = { name: '', size: 50, parentGroupId: null as number | null };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;

  ngOnInit() { this.loadGroups(); }
  loadGroups() { this.api.getStudentGroups().subscribe({ next: (g) => this.groups = g }); }

  saveGroup() {
    const obs = this.editingGroup
      ? this.api.updateStudentGroup(this.editingGroup.id, this.formData)
      : this.api.createStudentGroup(this.formData);
    obs.subscribe({ next: () => { this.loadGroups(); this.cancelEdit(); } });
  }

  editGroup(g: StudentGroup) {
    this.editingGroup = g;
    this.formData = { name: g.name, size: g.size, parentGroupId: g.parentGroupId };
    this.showAddForm = true;
  }

  deleteGroup(id: number) {
    if (confirm('Delete?')) this.api.deleteStudentGroup(id).subscribe({ next: () => this.loadGroups() });
  }

  cancelEdit() { this.showAddForm = false; this.editingGroup = null; this.formData = { name: '', size: 50, parentGroupId: null }; }

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
}


