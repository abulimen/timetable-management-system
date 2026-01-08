import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, Room, Zone, Feature } from '../../core/services/api.service';

@Component({
  selector: 'app-rooms',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Rooms</h1>
        <div class="flex gap-2">
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="rooms.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Room</button>
        </div>
      </div>

      <!-- Bulk Delete Confirmation -->
      <div *ngIf="showDeleteAllConfirm" class="card p-4 bg-red-500/10 border border-red-500/50">
        <div class="flex items-center justify-between">
          <p class="text-sm text-red-600 dark:text-red-400">
            Delete all {{ rooms.length }} rooms? This will also delete all lessons!
          </p>
          <div class="flex gap-2">
            <button (click)="showDeleteAllConfirm = false" class="btn btn-secondary btn-sm">Cancel</button>
            <button (click)="deleteAll()" [disabled]="deleting" class="btn bg-red-600 hover:bg-red-700 text-white btn-sm">
              {{ deleting ? 'Deleting...' : 'Yes, Delete All' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Add/Edit Form -->
      <div *ngIf="showAddForm" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">{{ editingRoom ? 'Edit Room' : 'Add New Room' }}</h2>
        <form (ngSubmit)="saveRoom()" class="space-y-4">
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="label">Name</label>
              <input type="text" [(ngModel)]="formData.name" name="name" class="input" required>
            </div>
            <div>
              <label class="label">Capacity</label>
              <input type="number" [(ngModel)]="formData.capacity" name="capacity" class="input" required>
            </div>
            <div>
              <label class="label">Zone</label>
              <select [(ngModel)]="formData.zoneId" name="zoneId" class="input">
                <option [ngValue]="undefined">Select Zone</option>
                <option *ngFor="let zone of zones" [ngValue]="zone.id">{{ zone.name }}</option>
              </select>
            </div>
          </div>
          
          <!-- Features Selection -->
          <div *ngIf="features.length > 0">
            <label class="label mb-2">Features</label>
            <div class="flex flex-wrap gap-2">
              <label *ngFor="let feature of features" 
                     class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                     [class.bg-primary-100]="isFeatureSelected(feature.name)"
                     [class.border-primary-500]="isFeatureSelected(feature.name)"
                     [class.dark:bg-primary-900]="isFeatureSelected(feature.name)"
                     [class.border-secondary-300]="!isFeatureSelected(feature.name)"
                     [class.dark:border-secondary-600]="!isFeatureSelected(feature.name)">
                <input type="checkbox" 
                       [checked]="isFeatureSelected(feature.name)"
                       (change)="toggleFeature(feature.name)"
                       class="mr-2">
                <span class="text-sm">{{ feature.name }}</span>
              </label>
            </div>
          </div>
          <div *ngIf="features.length === 0" class="text-sm text-secondary-500">
            No features available. <a href="/features" class="text-primary-500 hover:underline">Add features first</a>
          </div>
          
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <!-- Table -->
      <div class="card overflow-hidden">
        <table class="w-full">
          <thead class="bg-secondary-100 dark:bg-secondary-700">
            <tr>
              <th class="text-left px-6 py-3 text-sm font-medium">Name</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Capacity</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Zone</th>
              <th class="text-left px-6 py-3 text-sm font-medium">Features</th>
              <th class="text-right px-6 py-3 text-sm font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let room of rooms" class="border-t border-secondary-200 dark:border-secondary-700">
              <td class="px-6 py-4 font-medium text-secondary-900 dark:text-white">{{ room.name }}</td>
              <td class="px-6 py-4">{{ room.capacity }}</td>
              <td class="px-6 py-4">{{ room.zoneName || '-' }}</td>
              <td class="px-6 py-4">
                <span *ngFor="let f of room.features" class="badge bg-blue-100 text-blue-800 mr-1">{{ f }}</span>
              </td>
              <td class="px-6 py-4 text-right">
                <button (click)="editRoom(room)" class="text-blue-600 hover:underline mr-4">Edit</button>
                <button (click)="deleteRoom(room.id)" class="text-red-600 hover:underline">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div *ngIf="rooms.length === 0" class="p-8 text-center text-secondary-500">No rooms found.</div>
      </div>
    </div>
  `,
  styles: []
})
export class RoomsComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);

  rooms: Room[] = [];
  zones: Zone[] = [];
  features: Feature[] = [];
  showAddForm = false;
  editingRoom: Room | null = null;
  formData = { name: '', capacity: 50, zoneId: undefined as number | undefined, featureNames: [] as string[] };
  showDeleteAllConfirm = false;
  deleting = false;
  importing = false;
  importResult: any = null;

  ngOnInit() {
    this.loadRooms();
    this.loadZones();
    this.loadFeatures();
  }

  loadRooms() { this.api.getRooms().subscribe({ next: (rooms) => this.rooms = rooms }); }
  loadZones() { this.api.getZones().subscribe({ next: (zones) => this.zones = zones }); }
  loadFeatures() { this.api.getFeatures().subscribe({ next: (features) => this.features = features }); }

  isFeatureSelected(featureName: string): boolean {
    return this.formData.featureNames.includes(featureName);
  }

  toggleFeature(featureName: string) {
    const index = this.formData.featureNames.indexOf(featureName);
    if (index > -1) {
      this.formData.featureNames.splice(index, 1);
    } else {
      this.formData.featureNames.push(featureName);
    }
  }

  saveRoom() {
    if (this.editingRoom) {
      this.api.updateRoom(this.editingRoom.id, this.formData).subscribe({
        next: () => { this.loadRooms(); this.cancelEdit(); }
      });
    } else {
      this.api.createRoom(this.formData).subscribe({
        next: () => { this.loadRooms(); this.cancelEdit(); }
      });
    }
  }

  editRoom(room: Room) {
    this.editingRoom = room;
    this.formData = {
      name: room.name,
      capacity: room.capacity,
      zoneId: room.zoneId,
      featureNames: room.features ? [...room.features] : []
    };
    this.showAddForm = true;
  }

  deleteRoom(id: number) {
    if (confirm('Delete this room?')) {
      this.api.deleteRoom(id).subscribe({ next: () => this.loadRooms() });
    }
  }

  cancelEdit() {
    this.showAddForm = false;
    this.editingRoom = null;
    this.formData = { name: '', capacity: 50, zoneId: undefined, featureNames: [] };
  }

  confirmDeleteAll() { this.showDeleteAllConfirm = true; }

  deleteAll() {
    this.deleting = true;
    this.http.delete<any>('http://localhost:8080/api/v1/bulk/rooms/all', { body: { confirm: true } }).subscribe({
      next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadRooms(); },
      error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    this.importing = true;
    this.http.post<any>('http://localhost:8080/api/v1/bulk/rooms/import', formData).subscribe({
      next: () => { this.importing = false; this.loadRooms(); input.value = ''; },
      error: (err) => { this.importing = false; alert('Import failed: ' + (err.error?.message || 'Unknown error')); input.value = ''; }
    });
  }
}

