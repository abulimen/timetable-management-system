import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ApiService, ImportBatch, BulkImportResult, ImportConflict } from '../../../core/services/api.service';
import { FormsModule } from '@angular/forms';
import { ConflictResolutionComponent } from '../conflict-resolution/conflict-resolution.component';

@Component({
  selector: 'app-pending-approvals',
  standalone: true,
  imports: [CommonModule, DatePipe, FormsModule, ConflictResolutionComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Pending Approvals</h1>
          <p class="text-secondary-500 mt-1">Review and approve import requests from Coordinators</p>
        </div>
        <button (click)="loadBatches()" class="btn btn-ghost" [disabled]="loading">
          🔄 Refresh
        </button>
      </div>

      <!-- Loading State -->
      <div *ngIf="loading" class="text-center py-8 text-secondary-500">
        Loading pending approvals...
      </div>

      <!-- Empty State -->
      <div *ngIf="!loading && batches.length === 0" class="text-center py-12 card bg-secondary-50 dark:bg-secondary-800 border-dashed border-2 border-secondary-300 dark:border-secondary-700">
        <div class="text-4xl mb-3">✅</div>
        <h3 class="text-lg font-medium text-secondary-900 dark:text-white">All Caught Up!</h3>
        <p class="text-secondary-500">There are no pending import requests.</p>
      </div>

      <!-- Batch List -->
      <div *ngIf="!loading && batches.length > 0" class="card overflow-hidden p-0">
        <table class="w-full text-left">
          <thead class="bg-secondary-50 dark:bg-secondary-800 border-b border-secondary-200 dark:border-secondary-700">
            <tr>
              <th class="px-6 py-3 text-xs font-semibold text-secondary-500 uppercase">Date</th>
              <th class="px-6 py-3 text-xs font-semibold text-secondary-500 uppercase">Coordinator</th>
              <th class="px-6 py-3 text-xs font-semibold text-secondary-500 uppercase">Entity Type</th>
              <th class="px-6 py-3 text-xs font-semibold text-secondary-500 uppercase">File</th>
              <th class="px-6 py-3 text-xs font-semibold text-secondary-500 uppercase text-right">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-secondary-200 dark:divide-secondary-700">
            <tr *ngFor="let batch of batches" class="hover:bg-secondary-50 dark:hover:bg-secondary-800/50 transition-colors">
              <td class="px-6 py-4 text-sm text-secondary-600 dark:text-secondary-400">
                {{ batch.createdAt | date:'medium' }}
              </td>
              <td class="px-6 py-4">
                <div class="flex items-center gap-2">
                  <div class="w-8 h-8 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center font-bold text-xs">
                    {{ (batch.createdBy?.firstName || 'U')[0] }}{{ (batch.createdBy?.lastName || 'U')[0] }}
                  </div>
                  <div>
                    <div class="text-sm font-medium text-secondary-900 dark:text-white">
                      {{ batch.createdBy?.firstName }} {{ batch.createdBy?.lastName }}
                    </div>
                    <div class="text-xs text-secondary-500">{{ batch.createdBy?.email }}</div>
                  </div>
                </div>
              </td>
              <td class="px-6 py-4">
                <span class="px-2 py-1 text-xs font-medium rounded-full bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
                  {{ batch.entityType }}
                </span>
                <div *ngIf="batch.submissionNote" class="mt-1 text-xs px-2 py-0.5 bg-yellow-100 text-yellow-800 rounded inline-block max-w-[150px] truncate" title="{{batch.submissionNote}}">
                    📝 {{ batch.submissionNote }}
                </div>
              </td>
              <td class="px-6 py-4 text-sm text-secondary-600 dark:text-secondary-400 font-mono">
                {{ batch.originalFilename }}
              </td>
              <td class="px-6 py-4 text-right">
                <div class="flex justify-end items-center gap-2">
                  <button (click)="viewPreview(batch)" 
                          class="btn btn-sm btn-secondary"
                          [disabled]="processingId === batch.id">
                    👁️ View
                  </button>
                  <button (click)="reject(batch)" 
                          class="btn btn-sm btn-ghost text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20"
                          [disabled]="processingId === batch.id">
                    Reject
                  </button>

                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Preview Modal -->
      <div *ngIf="viewingBatch" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
        <div class="bg-white dark:bg-secondary-800 rounded-xl shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col">
          <div class="p-6 border-b border-secondary-200 dark:border-secondary-700 flex justify-between items-start">
            <div>
              <h3 class="text-xl font-bold text-secondary-900 dark:text-white">
                Preview: {{ viewingBatch.originalFilename }}
              </h3>
              <p class="text-sm text-secondary-500">
                Uploaded by {{ viewingBatch.createdBy?.firstName }} {{ viewingBatch.createdBy?.lastName }}
                • {{ viewingBatch.entityType }}
              </p>
              <p *ngIf="viewingBatch.submissionNote" class="mt-2 text-sm bg-amber-50 dark:bg-amber-900/20 p-2 rounded text-amber-800 dark:text-amber-200 border border-amber-200 dark:border-amber-800">
                <strong>📝 Note:</strong> {{ viewingBatch.submissionNote }}
              </p>
            </div>
            <button (click)="closePreview()" class="text-secondary-400 hover:text-secondary-600 text-2xl">
              &times;
            </button>
          </div>
          
          <div class="p-6 overflow-y-auto flex-1">
            <div *ngIf="previewLoading" class="text-center py-12">
               <span class="text-2xl animate-spin inline-block">⏳</span>
               <p class="mt-2 text-secondary-500">Loading preview data...</p>
            </div>

            <div *ngIf="!previewLoading && previewResult" class="space-y-4">
              <!-- Stats -->
              <div class="flex gap-4 mb-4">
                <div class="bg-green-50 dark:bg-green-900/20 px-3 py-2 rounded border border-green-200 dark:border-green-800">
                   <div class="text-xs text-green-600 dark:text-green-400 uppercase font-bold">Valid (New)</div>
                   <div class="text-xl font-bold text-green-700 dark:text-green-300">
                     {{ getNewCount(previewResult) }}
                   </div>
                </div>
                <!-- Skipped Stat -->
                 <div *ngIf="previewResult.skippedCount > 0" class="bg-slate-50 dark:bg-slate-800 px-3 py-2 rounded border border-slate-200 dark:border-slate-700">
                   <div class="text-xs text-slate-600 dark:text-slate-400 uppercase font-bold">Skipped (Exist)</div>
                   <div class="text-xl font-bold text-slate-700 dark:text-slate-300">
                     {{ previewResult.skippedCount }}
                   </div>
                </div>
                <div *ngIf="previewResult.errorCount > 0" class="bg-red-50 dark:bg-red-900/20 px-3 py-2 rounded border border-red-200 dark:border-red-800">
                   <div class="text-xs text-red-600 dark:text-red-400 uppercase font-bold">Errors</div>
                   <div class="text-xl font-bold text-red-700 dark:text-red-300">
                     {{ previewResult.errorCount }}
                   </div>
                </div>
                 <div *ngIf="previewResult.conflicts && previewResult.conflicts.length > 0" class="bg-amber-50 dark:bg-amber-900/20 px-3 py-2 rounded border border-amber-200 dark:border-amber-800">
                   <div class="text-xs text-amber-600 dark:text-amber-400 uppercase font-bold">Conflicts</div>
                   <div class="text-xl font-bold text-amber-700 dark:text-amber-300">
                     {{ previewResult.conflicts.length || 0 }}
                   </div>
                </div>
              </div>

              <!-- Conflicts Alert in Preview -->
              <div *ngIf="previewResult.conflicts?.length" class="mb-3 p-4 bg-amber-50 dark:bg-amber-900/20 rounded-lg border border-amber-300 dark:border-amber-700">
                  <div class="flex items-center justify-between">
                    <div>
                      <span class="font-bold text-amber-800 dark:text-amber-200 text-lg">⚠️ {{ previewResult.conflicts?.length || 0 }} Conflicts Detected</span>
                      <p class="text-sm text-amber-700 dark:text-amber-300 mt-1">Some records already exist with different values. You must resolve these to approve.</p>
                    </div>
                  </div>
              </div>

              <!-- Global Errors -->
              <div *ngIf="previewResult.globalErrors?.length" class="bg-red-50 dark:bg-red-900/20 p-3 rounded border border-red-200 dark:border-red-800 mb-4">
                <h4 class="font-bold text-red-700 dark:text-red-400 text-sm mb-1">Global Errors</h4>
                <ul class="list-disc list-inside text-sm text-red-600 dark:text-red-300">
                  <li *ngFor="let err of previewResult.globalErrors">{{ err }}</li>
                </ul>
              </div>

              <!-- Data Table -->
               <div *ngIf="previewResult.validRows?.length" class="overflow-x-auto border border-secondary-200 dark:border-secondary-700 rounded-lg">
                <table class="w-full text-sm">
                  <thead class="bg-secondary-50 dark:bg-secondary-900/50">
                    <tr>
                      <th class="px-3 py-2 text-left text-xs font-semibold text-secondary-500 uppercase w-16">Row</th>
                      <th class="px-3 py-2 text-left text-xs font-semibold text-secondary-500 uppercase w-24 border-l border-secondary-200 dark:border-secondary-700">Status</th>
                      <th *ngFor="let key of getHeaders(previewResult)" class="px-3 py-2 text-left text-xs font-semibold text-secondary-500 uppercase border-l border-secondary-200 dark:border-secondary-700">
                        {{ key }}
                      </th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-secondary-100 dark:divide-secondary-800">
                    <tr *ngFor="let row of previewResult.validRows" [class.bg-slate-50]="row.status === 'SKIPPED'" [class.dark:bg-slate-900]="row.status === 'SKIPPED'">
                      <td class="px-3 py-2 font-mono text-xs text-secondary-400 bg-secondary-50/50 dark:bg-secondary-900/30">
                        {{ row.rowNumber }}
                      </td>
                      <td class="px-3 py-2 text-xs border-l border-secondary-100 dark:border-secondary-800">
                        <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                              [ngClass]="getBadgeClass(row.status)">
                          {{ row.status }}
                        </span>
                      </td>
                      <td *ngFor="let key of getHeaders(previewResult)" 
                          class="px-3 py-2 border-l border-secondary-100 dark:border-secondary-800"
                          [class.text-slate-500]="row.status === 'SKIPPED'"
                          [class.italic]="row.status === 'SKIPPED'">
                         {{ row.data[key] }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            
            <div *ngIf="!previewLoading && !previewResult" class="text-center text-red-500">
              Failed to load preview.
            </div>
          </div>

          <div class="p-4 border-t border-secondary-200 dark:border-secondary-700 flex justify-end gap-2 bg-secondary-50 dark:bg-secondary-900/30">
            <button (click)="closePreview()" class="btn btn-secondary">Close</button>
            <button (click)="reject(viewingBatch)" 
                    class="btn btn-ghost text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 border border-red-200 dark:border-red-800">
               Reject
             </button>
             <button (click)="approve(viewingBatch)" class="btn btn-primary">
               {{ (previewResult?.conflicts?.length || 0) > 0 ? 'Resolve & Approve' : 'Approve & Import' }}
             </button>
          </div>
        </div>
      </div>

      <!-- Conflict Resolution Modal -->
      <div *ngIf="showConflictResolution" 
           class="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4">
        <div class="bg-white dark:bg-secondary-800 rounded-xl shadow-2xl max-w-4xl w-full max-h-[90vh] overflow-hidden">
          <div class="p-6 overflow-y-auto max-h-[calc(90vh-2rem)]">
            <app-conflict-resolution
              [conflicts]="currentConflicts"
              (onResolve)="handleConflictResolution($event)"
              (onCancel)="cancelConflictResolution()">
            </app-conflict-resolution>
          </div>
        </div>
      </div>

    </div>
  `
})
export class PendingApprovalsComponent implements OnInit {
  private apiService = inject(ApiService);

  batches: ImportBatch[] = [];
  loading = true;
  processingId: number | null = null;

  viewingBatch: ImportBatch | null = null;
  previewLoading = false;
  previewResult: BulkImportResult | null = null;

  // Conflict Resolution State
  showConflictResolution = false;
  currentConflicts: ImportConflict[] = [];
  targetBatch: ImportBatch | null = null;

  ngOnInit() {
    this.loadBatches();
  }

  loadBatches() {
    this.loading = true;
    this.apiService.getPendingBatches().subscribe({
      next: (data) => {
        this.batches = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load batches', err);
        this.loading = false;
      }
    });
  }

  viewPreview(batch: ImportBatch) {
    this.viewingBatch = batch;
    this.previewLoading = true;
    this.previewResult = null;

    this.apiService.previewBatch(batch.id).subscribe({
      next: (res) => {
        this.previewResult = res;
        this.previewLoading = false;
      },
      error: (err) => {
        alert('Failed to load preview: ' + (err.error?.message || err.message));
        this.previewLoading = false;
      }
    });
  }

  closePreview() {
    this.viewingBatch = null;
    this.previewResult = null;
  }

  getHeaders(result: BulkImportResult): string[] {
    if (result.validRows && result.validRows.length > 0) {
      return Object.keys(result.validRows[0].data);
    }
    return [];
  }

  getNewCount(result: BulkImportResult): number {
    return result.validRows?.filter(r => r.status !== 'SKIPPED').length || 0;
  }

  getBadgeClass(status: string): string {
    if (status === 'SKIPPED') {
      return 'bg-slate-100 text-slate-800 dark:bg-slate-700 dark:text-slate-300';
    } else if (status === 'NEW') {
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
    }
    return 'bg-secondary-100 text-secondary-800';
  }

  approve(batch: ImportBatch) {
    // If preview is open and has conflicts, trigger resolution
    if (this.viewingBatch?.id === batch.id && this.previewResult?.conflicts && this.previewResult.conflicts.length > 0) {
      this.targetBatch = batch;
      this.currentConflicts = this.previewResult.conflicts;
      this.showConflictResolution = true;
      return;
    }

    if (!confirm(`Are you sure you want to approve the import of ${batch.entityType} from ${batch.originalFilename}? This will modify live data.`)) return;
    this.executeApprove(batch);
  }

  executeApprove(batch: ImportBatch, resolutions?: Record<number, string>) {
    this.processingId = batch.id;
    this.apiService.approveBatch(batch.id, resolutions).subscribe({
      next: (res) => {
        alert(`Batch approved successfully! ${res.createdCount} created, ${res.updatedCount} updated.`);
        this.loadBatches(); // Refresh list
        this.processingId = null;
        this.closePreview();
        this.cancelConflictResolution();
      },
      error: (err) => {
        alert('Failed to approve batch: ' + (err.error?.message || err.message));
        this.processingId = null;
      }
    });
  }

  handleConflictResolution(resolutionsMap: Map<number, string>) {
    // Convert Map to Record
    const resolutions: Record<number, string> = {};
    resolutionsMap.forEach((v, k) => resolutions[k] = v);

    if (this.targetBatch) {
      this.executeApprove(this.targetBatch, resolutions);
    }
  }

  cancelConflictResolution() {
    this.showConflictResolution = false;
    this.targetBatch = null;
    this.currentConflicts = [];
  }

  reject(batch: ImportBatch) {
    const reason = prompt(`Reject import from ${batch.originalFilename}?\nEnter rejection reason:`);
    if (reason === null) return;

    this.processingId = batch.id;
    this.apiService.rejectBatch(batch.id, reason).subscribe({
      next: () => {
        this.loadBatches(); // Refresh list
        this.processingId = null;
        if (this.viewingBatch?.id === batch.id) {
          this.closePreview();
        }
      },
      error: (err) => {
        alert('Failed to reject batch: ' + (err.error?.message || err.message));
        this.processingId = null;
      }
    });
  }
}
