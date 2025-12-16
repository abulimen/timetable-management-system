import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, Feature } from '../../core/services/api.service';

@Component({
    selector: 'app-features',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Room Features</h1>
          <p class="text-secondary-500 text-sm mt-1">Capabilities that rooms can have (Projector, Lab Equipment, etc.)</p>
        </div>
        <div class="flex gap-2">
          <button (click)="confirmDeleteAll()" class="btn btn-danger" [disabled]="features.length === 0">Delete All</button>
          <button (click)="showAddForm = true" class="btn btn-primary">Add Feature</button>
        </div>
      </div>

      <!-- Bulk Delete Confirmation -->
      <div *ngIf="showDeleteAllConfirm" class="card p-4 bg-red-500/10 border border-red-500/50">
        <div class="flex items-center justify-between">
          <p class="text-sm text-red-600 dark:text-red-400">
            Delete all {{ features.length }} features? This will remove them from all rooms.
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
        <h2 class="text-lg font-semibold mb-4">{{ editingFeature ? 'Edit Feature' : 'Add New Feature' }}</h2>
        <form (ngSubmit)="saveFeature()" class="space-y-4">
          <div>
            <label class="label">Feature Name</label>
            <input type="text" [(ngModel)]="formData.name" name="name" class="input" required 
                   placeholder="e.g., Projector, Whiteboard, Lab Equipment">
          </div>
          <div class="flex gap-2">
            <button type="submit" class="btn btn-primary" [disabled]="!formData.name">Save</button>
            <button type="button" (click)="cancelEdit()" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>

      <!-- Features Grid -->
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
        <div *ngFor="let feature of features" 
             class="card p-4 hover:shadow-lg transition-shadow">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-xl">🏷️</span>
              <span class="font-medium">{{ feature.name }}</span>
            </div>
            <div class="flex gap-1">
              <button (click)="editFeature(feature)" class="p-1 text-blue-600 hover:bg-blue-100 rounded">
                ✏️
              </button>
              <button (click)="deleteFeature(feature.id)" class="p-1 text-red-600 hover:bg-red-100 rounded">
                🗑️
              </button>
            </div>
          </div>
        </div>
      </div>

      <div *ngIf="features.length === 0" class="card p-8 text-center text-secondary-500">
        <p class="text-4xl mb-4">🏷️</p>
        <p>No features defined yet.</p>
        <p class="text-sm mt-2">Features represent room capabilities like projectors, lab equipment, computers, etc.</p>
        <button (click)="showAddForm = true" class="btn btn-primary mt-4">Add Your First Feature</button>
      </div>

      <!-- Usage Info -->
      <div class="card p-4 bg-blue-50 dark:bg-blue-900/20">
        <h3 class="font-semibold text-blue-800 dark:text-blue-200">💡 How Features Work</h3>
        <ul class="text-sm text-blue-700 dark:text-blue-300 mt-2 space-y-1">
          <li>• Features are capabilities that rooms can have (e.g., Projector, Wet Lab)</li>
          <li>• Assign features to rooms in the Rooms page</li>
          <li>• Courses can require specific features for scheduling</li>
          <li>• The solver will only schedule lessons in rooms with required features</li>
        </ul>
      </div>
    </div>
  `
})
export class FeaturesComponent implements OnInit {
    private api = inject(ApiService);
    private http = inject(HttpClient);

    features: Feature[] = [];
    showAddForm = false;
    editingFeature: Feature | null = null;
    formData = { name: '' };
    showDeleteAllConfirm = false;
    deleting = false;

    ngOnInit() { this.loadFeatures(); }

    loadFeatures() {
        this.api.getFeatures().subscribe({ next: (f) => this.features = f });
    }

    saveFeature() {
        if (this.editingFeature) {
            this.http.put<Feature>(`http://localhost:8080/api/v1/features/${this.editingFeature.id}`, this.formData)
                .subscribe({ next: () => { this.loadFeatures(); this.cancelEdit(); } });
        } else {
            this.api.createFeature(this.formData).subscribe({
                next: () => { this.loadFeatures(); this.cancelEdit(); }
            });
        }
    }

    editFeature(feature: Feature) {
        this.editingFeature = feature;
        this.formData = { name: feature.name };
        this.showAddForm = true;
    }

    deleteFeature(id: number) {
        if (confirm('Delete this feature?')) {
            this.api.deleteFeature(id).subscribe({ next: () => this.loadFeatures() });
        }
    }

    cancelEdit() {
        this.showAddForm = false;
        this.editingFeature = null;
        this.formData = { name: '' };
    }

    confirmDeleteAll() { this.showDeleteAllConfirm = true; }

    deleteAll() {
        this.deleting = true;
        this.http.delete<any>('http://localhost:8080/api/v1/bulk/features/all', { body: { confirm: true } }).subscribe({
            next: () => { this.deleting = false; this.showDeleteAllConfirm = false; this.loadFeatures(); },
            error: (err) => { this.deleting = false; alert('Failed: ' + (err.error?.message || 'Unknown error')); }
        });
    }
}
