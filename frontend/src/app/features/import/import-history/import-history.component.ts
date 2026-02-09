import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface ImportHistoryItem {
  id: number;
  timestamp: string;
  entityType: string;
  importMode: string;
  fileName: string;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  canRollback: boolean;
  rolledBack: boolean;
  userName: string;
}

@Component({
  selector: 'app-import-history',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="history-container">
      <div class="history-header">
        <h3>📜 Import History</h3>
        <button class="btn btn-sm btn-ghost" (click)="loadHistory()">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"></polyline>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
          </svg>
          Refresh
        </button>
      </div>

      <div *ngIf="loading" class="loading">
        <div class="spinner"></div>
        Loading history...
      </div>

      <div *ngIf="!loading && history.length === 0" class="empty-state">
        <span>📭</span>
        <p>No import history yet</p>
      </div>

      <div *ngIf="!loading && history.length > 0" class="history-list">
        <div *ngFor="let item of history" class="history-item" [class.rolled-back]="item.rolledBack">
          <div class="item-main">
            <div class="item-info">
              <span class="entity-badge">{{ item.entityType }}</span>
              <span class="timestamp">{{ formatDate(item.timestamp) }}</span>
              <span class="user">by {{ item.userName }}</span>
            </div>
            <div class="item-stats">
              <span class="stat created" *ngIf="item.createdCount > 0">
                +{{ item.createdCount }} created
              </span>
              <span class="stat updated" *ngIf="item.updatedCount > 0">
                ✏️ {{ item.updatedCount }} updated
              </span>
              <span class="stat skipped" *ngIf="item.skippedCount > 0">
                ⏭️ {{ item.skippedCount }} skipped
              </span>
            </div>
          </div>
          <div class="item-actions">
            <span *ngIf="item.rolledBack" class="status-badge rolled-back">
              ↩️ Rolled Back
            </span>
            <button 
              *ngIf="item.canRollback && !item.rolledBack" 
              class="btn btn-sm btn-danger"
              (click)="confirmRollback(item)"
              [disabled]="rollingBack === item.id">
              {{ rollingBack === item.id ? 'Rolling back...' : '↩️ Rollback' }}
            </button>
            <span *ngIf="!item.canRollback && !item.rolledBack" class="status-badge expired">
              ⏰ Expired
            </span>
          </div>
        </div>
      </div>

      <!-- Rollback Confirmation Modal -->
      <div *ngIf="rollbackConfirm" class="modal-overlay" (click)="rollbackConfirm = null">
        <div class="confirm-modal" (click)="$event.stopPropagation()">
          <h4>⚠️ Confirm Rollback</h4>
          <p>Are you sure you want to rollback this import?</p>
          <div class="confirm-details">
            <strong>{{ rollbackConfirm.entityType }}</strong>
            <span>{{ rollbackConfirm.createdCount }} records will be deleted</span>
          </div>
          <p class="warning">This action cannot be undone!</p>
          <div class="modal-actions">
            <button class="btn btn-ghost" (click)="rollbackConfirm = null">Cancel</button>
            <button class="btn btn-danger" (click)="executeRollback()">
              Yes, Rollback
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .history-container {
      background: white;
      border-radius: 8px;
      padding: 16px;
      border: 1px solid #e5e7eb;
    }

    .history-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .history-header h3 {
      margin: 0;
      font-size: 1.1rem;
      color: #374151;
    }

    .loading, .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 32px;
      color: #6b7280;
    }

    .empty-state span {
      font-size: 2rem;
    }

    .spinner {
      width: 24px;
      height: 24px;
      border: 3px solid #e5e7eb;
      border-top-color: #3b82f6;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .history-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .history-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      background: #f9fafb;
      border-radius: 8px;
      border: 1px solid #e5e7eb;
    }

    .history-item.rolled-back {
      opacity: 0.6;
      background: #fef2f2;
    }

    .item-main {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .item-info {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .entity-badge {
      background: #3b82f6;
      color: white;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .timestamp {
      color: #374151;
      font-size: 0.875rem;
    }

    .user {
      color: #9ca3af;
      font-size: 0.75rem;
    }

    .item-stats {
      display: flex;
      gap: 12px;
    }

    .stat {
      font-size: 0.8rem;
    }

    .stat.created {
      color: #10b981;
    }

    .stat.updated {
      color: #f59e0b;
    }

    .stat.skipped {
      color: #6b7280;
    }

    .item-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .status-badge {
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 0.75rem;
    }

    .status-badge.rolled-back {
      background: #fef2f2;
      color: #dc2626;
    }

    .status-badge.expired {
      background: #f3f4f6;
      color: #6b7280;
    }

    .btn-danger {
      background: #dc2626;
      color: white;
    }

    .btn-danger:hover {
      background: #b91c1c;
    }

    /* Modal */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .confirm-modal {
      background: white;
      border-radius: 12px;
      padding: 24px;
      max-width: 400px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
    }

    .confirm-modal h4 {
      margin: 0 0 12px 0;
      font-size: 1.25rem;
    }

    .confirm-details {
      background: #f3f4f6;
      padding: 12px;
      border-radius: 8px;
      margin: 12px 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .warning {
      color: #dc2626;
      font-size: 0.875rem;
    }

    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      margin-top: 16px;
    }
  `]
})
export class ImportHistoryComponent implements OnInit {
  @Output() historyUpdated = new EventEmitter<void>();

  history: ImportHistoryItem[] = [];
  loading = true;
  rollingBack: number | null = null;
  rollbackConfirm: ImportHistoryItem | null = null;

  private apiUrl = 'http://localhost:8080/api/v1/bulk';

  constructor(private http: HttpClient) { }

  ngOnInit() {
    this.loadHistory();
  }

  loadHistory() {
    this.loading = true;
    this.http.get<ImportHistoryItem[]>(`${this.apiUrl}/history`).subscribe({
      next: (data) => {
        this.history = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load history:', err);
        this.loading = false;
      }
    });
  }

  formatDate(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  confirmRollback(item: ImportHistoryItem) {
    this.rollbackConfirm = item;
  }

  executeRollback() {
    if (!this.rollbackConfirm) return;

    const id = this.rollbackConfirm.id;
    this.rollingBack = id;
    this.rollbackConfirm = null;

    this.http.post(`${this.apiUrl}/history/${id}/rollback`, {}).subscribe({
      next: () => {
        this.rollingBack = null;
        this.loadHistory();
        this.historyUpdated.emit();
      },
      error: (err) => {
        console.error('Rollback failed:', err);
        this.rollingBack = null;
        alert('Rollback failed: ' + (err.error?.message || 'Unknown error'));
      }
    });
  }
}
