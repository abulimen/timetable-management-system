import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, Setting } from '../../core/services/api.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Settings</h1>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div *ngFor="let category of categories" class="card p-6">
          <h2 class="text-lg font-semibold mb-4 capitalize">{{ category }}</h2>
          <div class="space-y-4">
            <div *ngFor="let setting of getSettingsByCategory(category)" class="flex items-center justify-between">
              <div class="flex-1">
                <p class="font-medium text-sm">{{ setting.key }}</p>
                <p class="text-xs text-secondary-500">{{ setting.description }}</p>
              </div>
              <input type="text" [ngModel]="setting.value" (blur)="updateSetting(setting.key, $event)" class="input w-32 text-right">
            </div>
          </div>
        </div>
      </div>

      <div class="card p-6 bg-gradient-to-r from-blue-500/10 to-purple-500/10 border border-blue-500/30">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold text-lg">Apply Timing Changes</h3>
            <p class="text-sm text-secondary-500">Click to regenerate timeslots from current settings.</p>
          </div>
          <button (click)="regenerateTimeslots()" [disabled]="regenerating" class="btn btn-primary">
            {{ regenerating ? 'Regenerating...' : 'Regenerate Timeslots' }}
          </button>
        </div>
        <div *ngIf="regenerateMessage" class="mt-4 p-3 rounded-lg" [ngClass]="{'bg-green-500/20': regenerateSuccess, 'bg-red-500/20': !regenerateSuccess}">
          {{ regenerateMessage }}
        </div>
      </div>

      <!-- DANGER ZONE -->
      <div class="card p-6 border-2 border-red-500/50 bg-red-500/5">
        <h2 class="text-lg font-bold text-red-500 mb-4">⚠️ Danger Zone</h2>
        
        <div class="space-y-4">
          <div class="flex items-center justify-between p-4 bg-red-500/10 rounded-lg">
            <div>
              <h3 class="font-semibold">Wipe All Data</h3>
              <p class="text-sm text-secondary-500">Delete all courses, lessons, lecturers, rooms, zones, and features. This cannot be undone!</p>
            </div>
            <button (click)="showWipeConfirmation = true" class="btn bg-red-600 hover:bg-red-700 text-white">
              Wipe All Data
            </button>
          </div>
        </div>

        <!-- Confirmation Modal -->
        <div *ngIf="showWipeConfirmation" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div class="bg-white dark:bg-secondary-800 rounded-xl p-6 max-w-md w-full mx-4 shadow-2xl">
            <h3 class="text-xl font-bold text-red-500 mb-4">⚠️ Confirm System Wipe</h3>
            <p class="text-secondary-600 dark:text-secondary-300 mb-4">
              This will permanently delete ALL data including:
            </p>
            <ul class="list-disc list-inside text-sm text-secondary-500 mb-4">
              <li>All lessons and timetable schedules</li>
              <li>All courses and their configurations</li>
              <li>All lecturers and student groups</li>
              <li>All rooms, zones, and features</li>
            </ul>
            <p class="text-sm font-medium mb-2">Type <span class="text-red-500 font-bold">DELETE</span> to confirm:</p>
            <input 
              type="text" 
              [(ngModel)]="wipeConfirmText" 
              class="input w-full mb-4" 
              placeholder="Type DELETE to confirm">
            <div class="flex gap-3 justify-end">
              <button (click)="cancelWipe()" class="btn btn-secondary">Cancel</button>
              <button 
                (click)="executeSystemWipe()" 
                [disabled]="wipeConfirmText !== 'DELETE' || wiping"
                class="btn bg-red-600 hover:bg-red-700 text-white disabled:opacity-50">
                {{ wiping ? 'Wiping...' : 'Confirm Wipe' }}
              </button>
            </div>
            <div *ngIf="wipeMessage" class="mt-4 p-3 rounded-lg" [ngClass]="{'bg-green-500/20': wipeSuccess, 'bg-red-500/20': !wipeSuccess}">
              {{ wipeMessage }}
            </div>
          </div>
        </div>
      </div>

      <div class="flex gap-2">
        <button (click)="loadSettings()" class="btn btn-secondary">Reload</button>
      </div>
    </div>
  `
})
export class SettingsComponent implements OnInit {
  private api = inject(ApiService);
  private http = inject(HttpClient);
  settings: Setting[] = [];
  categories: string[] = [];
  regenerating = false;
  regenerateMessage = '';
  regenerateSuccess = false;

  // Danger zone
  showWipeConfirmation = false;
  wipeConfirmText = '';
  wiping = false;
  wipeMessage = '';
  wipeSuccess = false;

  ngOnInit() { this.loadSettings(); }

  loadSettings() {
    this.api.getSettings().subscribe({
      next: (s) => {
        this.settings = s;
        this.categories = [...new Set(s.map(st => st.category))];
      }
    });
  }

  getSettingsByCategory(category: string): Setting[] {
    return this.settings.filter(s => s.category === category);
  }

  updateSetting(key: string, event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.api.updateSetting(key, value).subscribe({
      next: () => console.log('Setting updated'),
      error: (err) => console.error('Failed to update setting', err)
    });
  }

  regenerateTimeslots() {
    this.regenerating = true;
    this.regenerateMessage = '';
    this.http.post<any>('http://localhost:8080/api/v1/settings/regenerate-timeslots', {}).subscribe({
      next: (res) => {
        this.regenerating = false;
        this.regenerateSuccess = true;
        this.regenerateMessage = `✓ ${res.message}`;
      },
      error: (err) => {
        this.regenerating = false;
        this.regenerateSuccess = false;
        this.regenerateMessage = '✗ Failed to regenerate timeslots';
        console.error('Failed to regenerate timeslots', err);
      }
    });
  }

  cancelWipe() {
    this.showWipeConfirmation = false;
    this.wipeConfirmText = '';
    this.wipeMessage = '';
  }

  executeSystemWipe() {
    if (this.wipeConfirmText !== 'DELETE') return;

    this.wiping = true;
    this.wipeMessage = '';

    this.http.delete<any>('http://localhost:8080/api/v1/bulk/system-wipe', {
      body: { confirmationToken: 'DELETE' }
    }).subscribe({
      next: (res) => {
        this.wiping = false;
        this.wipeSuccess = true;
        this.wipeMessage = `✓ ${res.message} (${res.totalDeleted} records deleted)`;
        setTimeout(() => {
          this.showWipeConfirmation = false;
          this.wipeConfirmText = '';
        }, 2000);
      },
      error: (err) => {
        this.wiping = false;
        this.wipeSuccess = false;
        this.wipeMessage = '✗ Failed to wipe data: ' + (err.error?.message || 'Unknown error');
        console.error('Failed to wipe data', err);
      }
    });
  }
}



