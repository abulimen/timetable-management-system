import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ImportConflict } from '../../../core/services/api.service';

@Component({
  selector: 'app-conflict-resolution',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h2 class="text-xl font-bold text-secondary-900 dark:text-white">
          ⚠️ Conflicts Detected ({{ conflicts.length }})
        </h2>
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" [(ngModel)]="applyToAll" class="w-4 h-4">
            Apply to all conflicts:
          </label>
          <select [(ngModel)]="globalResolution" [disabled]="!applyToAll" class="input input-sm w-48"
                  (change)="applyGlobalResolution()">
            <option value="">-- Select --</option>
            <option value="KEEP_EXISTING">Keep Existing</option>
            <option value="UPDATE">Update with New</option>
            <option value="SKIP">Skip</option>
          </select>
        </div>
      </div>

      <p class="text-sm text-secondary-600 dark:text-secondary-400">
        The following rows conflict with existing records. Choose how to resolve each conflict.
      </p>

      <!-- Conflict Cards -->
      <div class="space-y-4 max-h-[60vh] overflow-y-auto">
        <div *ngFor="let conflict of conflicts; let i = index" 
             class="card p-4 border-2"
             [class.border-amber-400]="!conflict.resolution"
             [class.border-green-400]="conflict.resolution">
          
          <div class="flex items-start justify-between mb-3">
            <div>
              <span class="text-sm text-secondary-500">Row {{ conflict.rowNumber }}</span>
              <h3 class="font-medium">{{ conflict.keyType }}: <code class="text-primary-600">{{ conflict.key }}</code></h3>
            </div>
            <span *ngIf="conflict.resolution" class="px-2 py-1 bg-green-100 text-green-700 text-xs rounded-full">
              {{ getResolutionLabel(conflict.resolution) }}
            </span>
          </div>

          <!-- Side-by-side comparison -->
          <div class="grid grid-cols-2 gap-4 mb-4">
            <div class="p-3 bg-secondary-50 dark:bg-secondary-800 rounded-lg">
              <div class="text-xs font-medium text-secondary-500 mb-2">📦 EXISTING DATA</div>
              <div *ngFor="let field of conflict.conflictingFields" class="flex justify-between text-sm py-1 border-b border-secondary-200 dark:border-secondary-700 last:border-0">
                <span class="text-secondary-600 dark:text-secondary-400">{{ field }}:</span>
                <span class="font-medium">{{ conflict.existingData[field] ?? '(empty)' }}</span>
              </div>
            </div>
            <div class="p-3 bg-primary-50 dark:bg-primary-900/20 rounded-lg">
              <div class="text-xs font-medium text-primary-600 mb-2">📄 NEW DATA</div>
              <div *ngFor="let field of conflict.conflictingFields" class="flex justify-between text-sm py-1 border-b border-primary-200 dark:border-primary-700 last:border-0">
                <span class="text-secondary-600 dark:text-secondary-400">{{ field }}:</span>
                <span class="font-medium text-primary-700 dark:text-primary-300">{{ conflict.newData[field] ?? '(empty)' }}</span>
              </div>
            </div>
          </div>

          <!-- Resolution options -->
          <div class="flex flex-wrap gap-2">
            <button (click)="setResolution(i, 'KEEP_EXISTING')" 
                    class="btn btn-sm"
                    [class.btn-secondary]="conflict.resolution !== 'KEEP_EXISTING'"
                    [class.btn-primary]="conflict.resolution === 'KEEP_EXISTING'">
              Keep Existing
            </button>
            <button (click)="setResolution(i, 'UPDATE')" 
                    class="btn btn-sm"
                    [class.btn-secondary]="conflict.resolution !== 'UPDATE'"
                    [class.btn-primary]="conflict.resolution === 'UPDATE'">
              Update with New
            </button>
            <button (click)="setResolution(i, 'SKIP')" 
                    class="btn btn-sm"
                    [class.btn-secondary]="conflict.resolution !== 'SKIP'"
                    [class.btn-primary]="conflict.resolution === 'SKIP'">
              Skip Row
            </button>
          </div>
        </div>
      </div>

      <!-- Action buttons -->
      <div class="flex items-center justify-between pt-4 border-t border-secondary-200 dark:border-secondary-700">
        <div class="text-sm text-secondary-600">
          {{ getResolvedCount() }} of {{ conflicts.length }} resolved
        </div>
        <div class="flex gap-2">
          <button (click)="onCancel.emit()" class="btn btn-secondary">Cancel</button>
          <button (click)="submitResolutions()" 
                  [disabled]="!allResolved || submitting"
                  class="btn btn-primary">
            {{ submitting ? 'Importing...' : 'Continue Import' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class ConflictResolutionComponent {
  @Input() conflicts: ImportConflict[] = [];
  @Output() onResolve = new EventEmitter<Map<number, string>>();
  @Output() onCancel = new EventEmitter<void>();

  applyToAll = false;
  globalResolution = '';
  submitting = false;

  get allResolved(): boolean {
    return this.conflicts.every(c => c.resolution);
  }

  setResolution(index: number, resolution: 'KEEP_EXISTING' | 'UPDATE' | 'SKIP') {
    this.conflicts[index].resolution = resolution;
  }

  applyGlobalResolution() {
    if (this.applyToAll && this.globalResolution) {
      this.conflicts.forEach(c => {
        c.resolution = this.globalResolution as any;
      });
    }
  }

  getResolutionLabel(resolution: string): string {
    switch (resolution) {
      case 'KEEP_EXISTING': return 'Keep Existing';
      case 'UPDATE': return 'Update';
      case 'SKIP': return 'Skip';
      default: return resolution;
    }
  }

  getResolvedCount(): number {
    return this.conflicts.filter(c => c.resolution).length;
  }

  submitResolutions() {
    if (!this.allResolved) return;

    const resolutions = new Map<number, string>();
    this.conflicts.forEach(c => {
      if (c.resolution) {
        resolutions.set(c.rowNumber, c.resolution);
      }
    });

    this.submitting = true;
    this.onResolve.emit(resolutions);
  }
}
