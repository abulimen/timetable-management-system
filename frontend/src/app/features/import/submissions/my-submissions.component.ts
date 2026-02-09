import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { ApiService, ImportBatch } from '../../../core/services/api.service';

@Component({
  selector: 'app-my-submissions',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="container mx-auto px-4 py-8">
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-100">My Submissions</h1>
        <a routerLink="/import" class="btn btn-primary">Upload New</a>
      </div>

      <div *ngIf="loading" class="flex justify-center py-12">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>

      <div *ngIf="!loading && submissions.length === 0" class="card p-12 text-center">
        <svg class="w-16 h-16 text-gray-400 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
        </svg>
        <h3 class="text-xl font-medium text-gray-900 dark:text-gray-100 mb-2">No submissions yet</h3>
        <p class="text-gray-500 dark:text-gray-400">Submit an import for approval to see it here.</p>
      </div>

      <div *ngIf="!loading && submissions.length > 0" class="space-y-8">
        
        <!-- PENDING -->
        <div *ngIf="pendingSubmissions.length > 0">
          <h2 class="text-lg font-semibold text-yellow-600 dark:text-yellow-400 mb-4 flex items-center gap-2">
            <span class="text-xl">⏳</span> Pending Approval ({{ pendingSubmissions.length }})
          </h2>
          <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <div *ngFor="let sub of pendingSubmissions" class="card p-4 border-l-4 border-yellow-500">
              <div class="flex justify-between items-start mb-2">
                <span class="badge badge-yellow">{{ sub.entityType }}</span>
                <span class="text-xs text-gray-500">{{ sub.createdAt | date:'short' }}</span>
              </div>
              <h3 class="font-medium text-gray-900 dark:text-gray-100 truncate" [title]="sub.originalFilename">
                {{ sub.originalFilename }}
              </h3>
              <p *ngIf="sub.submissionNote" class="text-sm text-gray-500 mt-2 italic">
                "{{ sub.submissionNote }}"
              </p>
            </div>
          </div>
        </div>

        <!-- REJECTED -->
        <div *ngIf="rejectedSubmissions.length > 0">
          <h2 class="text-lg font-semibold text-red-600 dark:text-red-400 mb-4 flex items-center gap-2">
            <span class="text-xl">❌</span> Rejected ({{ rejectedSubmissions.length }})
          </h2>
          <div class="space-y-4">
            <div *ngFor="let sub of rejectedSubmissions" class="card p-4 border-l-4 border-red-500 bg-red-50 dark:bg-red-900/20">
              <div class="flex justify-between items-start mb-2">
                <span class="badge badge-red">{{ sub.entityType }}</span>
                <span class="text-xs text-gray-500">{{ sub.approvalDate | date:'short' }}</span>
              </div>
              <h3 class="font-medium text-gray-900 dark:text-gray-100 truncate" [title]="sub.originalFilename">
                {{ sub.originalFilename }}
              </h3>
              <div *ngIf="sub.rejectionReason" class="mt-3 p-3 bg-red-100 dark:bg-red-900/40 rounded-lg">
                <p class="text-sm font-medium text-red-800 dark:text-red-300 mb-1">⚠️ Rejection Reason:</p>
                <p class="text-sm text-red-700 dark:text-red-400">{{ sub.rejectionReason }}</p>
              </div>
              <div class="mt-3 flex items-center justify-between">
                <span *ngIf="sub.approvedBy" class="text-xs text-gray-500">
                  Rejected by {{ sub.approvedBy.firstName }} {{ sub.approvedBy.lastName }}
                </span>
                <button (click)="revertToDraft(sub)" 
                        [disabled]="revertingId === sub.id"
                        class="btn btn-sm bg-blue-600 hover:bg-blue-700 text-white">
                  {{ revertingId === sub.id ? 'Moving...' : '✏️ Re-edit' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- APPROVED -->
        <div *ngIf="approvedSubmissions.length > 0">
          <h2 class="text-lg font-semibold text-green-600 dark:text-green-400 mb-4 flex items-center gap-2">
            <span class="text-xl">✅</span> Approved ({{ approvedSubmissions.length }})
          </h2>
          <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <div *ngFor="let sub of approvedSubmissions" class="card p-4 border-l-4 border-green-500">
              <div class="flex justify-between items-start mb-2">
                <span class="badge badge-green">{{ sub.entityType }}</span>
                <span class="text-xs text-gray-500">{{ sub.approvalDate | date:'short' }}</span>
              </div>
              <h3 class="font-medium text-gray-900 dark:text-gray-100 truncate" [title]="sub.originalFilename">
                {{ sub.originalFilename }}
              </h3>
              <div *ngIf="sub.approvedBy" class="mt-2 text-xs text-gray-500">
                Approved by {{ sub.approvedBy.firstName }} {{ sub.approvedBy.lastName }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
      .badge-yellow { @apply bg-yellow-100 text-yellow-800 dark:bg-yellow-900/50 dark:text-yellow-300; }
      .badge-red { @apply bg-red-100 text-red-800 dark:bg-red-900/50 dark:text-red-300; }
      .badge-green { @apply bg-green-100 text-green-800 dark:bg-green-900/50 dark:text-green-300; }
    `]
})
export class MySubmissionsComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);

  loading = true;
  submissions: ImportBatch[] = [];
  revertingId: number | null = null;

  get pendingSubmissions(): ImportBatch[] {
    return this.submissions.filter(s => s.status === 'PENDING');
  }

  get rejectedSubmissions(): ImportBatch[] {
    return this.submissions.filter(s => s.status === 'REJECTED');
  }

  get approvedSubmissions(): ImportBatch[] {
    return this.submissions.filter(s => s.status === 'APPROVED');
  }

  ngOnInit() {
    this.loadSubmissions();
  }

  loadSubmissions() {
    this.loading = true;
    this.api.getMySubmissions().subscribe({
      next: (subs) => {
        this.submissions = subs;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load submissions:', err);
        this.loading = false;
      }
    });
  }

  revertToDraft(sub: ImportBatch) {
    this.revertingId = sub.id;
    this.api.revertToDraft(sub.id).subscribe({
      next: (res: any) => {
        this.revertingId = null;
        // Navigate to new draft editor using the new ID
        this.router.navigate(['/import/draft', res.id]);
      },
      error: (err) => {
        this.revertingId = null;
        alert('Failed to revert: ' + (err.error?.error || err.message));
      }
    });
  }
}
