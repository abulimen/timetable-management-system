import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, Zone } from '../../core/services/api.service';

@Component({
  selector: 'app-zones',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Zones</h1>
        <div class="flex gap-2">
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="zones.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Zone</button>
        </div>
      </div>

      <div *ngIf="showDeleteAllConfirm" class="card p-4 bg-red-500/10 border border-red-500/50">
        <div class="flex items-center justify-between">
          <p class="text-sm text-red-600 dark:text-red-400">Delete all {{ zones.length }} zones? This will also delete all rooms and lessons!</p>
          <div class="flex gap-2">
            <button (click)="showDeleteAllConfirm = false" class="btn btn-secondary btn-sm">Cancel</button>
            <button (click)="deleteAll()" [disabled]="deleting" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              {{ deleting ? 'Deleting...' : 'Yes, Delete All' }}
            </button>
          </div>
        </div>
      </div>

      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingZone ? 'Edit Zone' : 'Add New Zone' }}</h2>
        <form (ngSubmit)="saveZone()" class="space-y-4">
          <div>
            <label class="label">Name</label>
            <input type="text" [(ngModel)]="formData.name" name="name" class="input w-full max-w-md" required>
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
              <th class="text-left px-6 py-3 text-sm font-medium">ID</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let zone of zones" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4">{{ zone.id }}</td>
              <td class="px-6 py-4">{{ zone.name }}</td>
              <td class="px-6 py-4 text-right">
                <button (click)="editZone(zone)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteZone(zone.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="zones.length === 0" class="p-8 text-center text-secondary-500">No zones found.</div>
      </div>
    </div>
  `,
  styles: []
})
export class ZonesComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);

  zones: Zone[] = [];
  showAddForm = false;
  editingZone: Zone | null = null;
  formData = { name: '' };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;

  ngOnInit() { this.loadZones(); }

  loadZones() { this.api.getZones().subscribe({ next: (zones) => this.zones = zones }); }

  saveZone() {
    if (this.editingZone) {
      this.api.updateZone(this.editingZone.id, this.formData).subscribe({
        next: () => { this.loadZones(); this.cancelEdit(); }
      });
    } else {
      this.api.createZone(this.formData).subscribe({
        next: () => { this.loadZones(); this.cancelEdit(); }
      });
    }
  }

  editZone(zone: Zone) {
    this.editingZone = zone;
    this.formData = { name: zone.name };
    this.showAddForm = true;
  }

  deleteZone(id: number) {
    if (confirm('Delete this zone?')) {
      this.api.deleteZone(id).subscribe({ next: () => this.loadZones() });
    }
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingZone = null;
    this.formData = { name: '' };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/zones/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadZones(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    this.importing = true;
    this.http.post<any>('http://localhost:8080/api/v1/bulk/zones/import', formData).subscribe({
      next: () => { this.importing = false; this.loadZones(); input.value = ''; },
      error: (err) => { this.importing = false; alert('Import failed: ' + (err.error?.message || 'Unknown error')); input.value = ''; }
    });
  }
}
