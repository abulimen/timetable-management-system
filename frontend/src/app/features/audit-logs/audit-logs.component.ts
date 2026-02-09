import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';

interface AuditLog {
  id: number;
  timestamp: string;
  actorType: string;
  actorId: string;
  actorName: string;
  actorIpAddress: string;
  action: string;
  entityType: string;
  entityId: string;
  entityName: string;
  previousValue: string;
  newValue: string;
  description: string;
  success: boolean;
  errorMessage: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface FilterOptions {
  entityTypes: string[];
  actorIds: string[];
  actions: string[];
}

@Component({
  selector: 'app-audit-logs',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <div class="header">
        <h1>📋 Audit Logs</h1>
        <button class="btn btn-secondary" (click)="exportCsv()" [disabled]="loading">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          Export CSV
        </button>
      </div>

      <!-- Filters -->
      <div class="filters">
        <div class="filter-group">
          <label>Entity Type</label>
          <select [(ngModel)]="filters.entityType" (change)="loadLogs()">
            <option value="">All Entities</option>
            @for (type of filterOptions.entityTypes; track type) {
              <option [value]="type">{{ type }}</option>
            }
          </select>
        </div>
        <div class="filter-group">
          <label>Action</label>
          <select [(ngModel)]="filters.action" (change)="loadLogs()">
            <option value="">All Actions</option>
            @for (action of filterOptions.actions; track action) {
              <option [value]="action">{{ action }}</option>
            }
          </select>
        </div>
        <div class="filter-group">
          <label>Date Range</label>
          <select [(ngModel)]="filters.dateRange" (change)="loadLogs()">
            <option value="7">Last 7 days</option>
            <option value="14">Last 14 days</option>
            <option value="30">Last 30 days</option>
            <option value="all">All time</option>
          </select>
        </div>
        <button class="btn btn-text" (click)="resetFilters()">Clear Filters</button>
      </div>

      @if (loading) {
        <div class="loading">Loading audit logs...</div>
      } @else if (error) {
        <div class="error">{{ error }}</div>
      } @else {
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Entity</th>
                <th>Description</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (log of logs; track log.id) {
                <tr>
                  <td class="timestamp">{{ formatDate(log.timestamp) }}</td>
                  <td>
                    <div class="actor">
                      <span class="actor-name">{{ log.actorName || log.actorId || 'System' }}</span>
                      @if (log.actorIpAddress) {
                        <span class="actor-ip">{{ log.actorIpAddress }}</span>
                      }
                    </div>
                  </td>
                  <td>
                    <span class="badge" [class]="'badge-' + log.action.toLowerCase()">
                      {{ log.action }}
                    </span>
                  </td>
                  <td>
                    <div class="entity">
                      <span class="entity-type">{{ log.entityType }}</span>
                      @if (log.entityName) {
                        <span class="entity-name">{{ log.entityName }}</span>
                      }
                    </div>
                  </td>
                  <td class="description">{{ log.description || '-' }}</td>
                  <td>
                    <span class="status" [class.success]="log.success" [class.failed]="!log.success">
                      {{ log.success ? '✓' : '✗' }}
                    </span>
                  </td>
                  <td>
                    <button class="btn-icon" (click)="viewDetails(log)" title="View Details">
                      👁️
                    </button>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="7" class="empty">No audit logs found</td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        @if (totalPages > 1) {
          <div class="pagination">
            <button class="btn btn-sm" [disabled]="currentPage === 0" (click)="goToPage(currentPage - 1)">← Previous</button>
            <span class="page-info">Page {{ currentPage + 1 }} of {{ totalPages }}</span>
            <button class="btn btn-sm" [disabled]="currentPage >= totalPages - 1" (click)="goToPage(currentPage + 1)">Next →</button>
          </div>
        }
      }

      <!-- Detail Modal -->
      @if (selectedLog) {
        <div class="modal-overlay" (click)="closeDetails()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h2>Audit Log Details</h2>
              <button class="btn-close" (click)="closeDetails()">×</button>
            </div>
            <div class="modal-body">
              <div class="detail-grid">
                <div class="detail-item">
                  <label>ID</label>
                  <span>{{ selectedLog.id }}</span>
                </div>
                <div class="detail-item">
                  <label>Timestamp</label>
                  <span>{{ formatDateFull(selectedLog.timestamp) }}</span>
                </div>
                <div class="detail-item">
                  <label>Actor</label>
                  <span>{{ selectedLog.actorName || selectedLog.actorId }} ({{ selectedLog.actorType }})</span>
                </div>
                <div class="detail-item">
                  <label>IP Address</label>
                  <span>{{ selectedLog.actorIpAddress || 'N/A' }}</span>
                </div>
                <div class="detail-item">
                  <label>Action</label>
                  <span class="badge" [class]="'badge-' + selectedLog.action.toLowerCase()">{{ selectedLog.action }}</span>
                </div>
                <div class="detail-item">
                  <label>Entity</label>
                  <span>{{ selectedLog.entityType }} #{{ selectedLog.entityId }}</span>
                </div>
                <div class="detail-item full-width">
                  <label>Description</label>
                  <span>{{ selectedLog.description || 'No description' }}</span>
                </div>
                @if (selectedLog.previousValue) {
                  <div class="detail-item full-width">
                    <label>Previous Value</label>
                    <pre>{{ formatJson(selectedLog.previousValue) }}</pre>
                  </div>
                }
                @if (selectedLog.newValue) {
                  <div class="detail-item full-width">
                    <label>New Value</label>
                    <pre>{{ formatJson(selectedLog.newValue) }}</pre>
                  </div>
                }
                @if (selectedLog.errorMessage) {
                  <div class="detail-item full-width error-msg">
                    <label>Error</label>
                    <span>{{ selectedLog.errorMessage }}</span>
                  </div>
                }
              </div>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .container {
      padding: 1.5rem;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }

    .header h1 {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 600;
    }

    .filters {
      display: flex;
      gap: 1rem;
      margin-bottom: 1rem;
      flex-wrap: wrap;
      align-items: flex-end;
    }

    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .filter-group label {
      font-size: 0.75rem;
      color: #6b7280;
      font-weight: 500;
    }

    .filter-group select {
      padding: 0.5rem 0.75rem;
      border: 1px solid #e5e7eb;
      border-radius: 6px;
      font-size: 0.875rem;
      min-width: 150px;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1rem;
      border: none;
      border-radius: 8px;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn svg {
      width: 16px;
      height: 16px;
    }

    .btn-secondary {
      background: #f3f4f6;
      color: #374151;
    }

    .btn-secondary:hover:not(:disabled) {
      background: #e5e7eb;
    }

    .btn-text {
      background: none;
      color: #6b7280;
    }

    .btn-text:hover {
      color: #111827;
    }

    .btn-sm {
      padding: 0.375rem 0.75rem;
      font-size: 0.8rem;
    }

    .table-container {
      background: white;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      overflow: hidden;
    }

    :host-context(.dark) .table-container {
      background: #1f2937;
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    th, td {
      padding: 0.75rem 1rem;
      text-align: left;
      border-bottom: 1px solid #eee;
    }

    :host-context(.dark) th, :host-context(.dark) td {
      border-bottom-color: #374151;
    }

    th {
      background: #f9fafb;
      font-weight: 600;
      font-size: 0.75rem;
      text-transform: uppercase;
      color: #6b7280;
    }

    :host-context(.dark) th {
      background: #111827;
    }

    .timestamp {
      font-family: monospace;
      font-size: 0.8rem;
      color: #6b7280;
      white-space: nowrap;
    }

    .actor {
      display: flex;
      flex-direction: column;
    }

    .actor-name {
      font-weight: 500;
    }

    .actor-ip {
      font-size: 0.75rem;
      color: #9ca3af;
      font-family: monospace;
    }

    .entity {
      display: flex;
      flex-direction: column;
    }

    .entity-type {
      font-weight: 500;
      font-size: 0.75rem;
      color: #6b7280;
    }

    .entity-name {
      font-size: 0.875rem;
    }

    .description {
      max-width: 250px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .badge {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .badge-create { background: #d1fae5; color: #065f46; }
    .badge-update { background: #fef3c7; color: #92400e; }
    .badge-delete { background: #fee2e2; color: #dc2626; }
    .badge-system_action { background: #dbeafe; color: #1e40af; }

    .status {
      font-size: 1rem;
    }

    .status.success { color: #059669; }
    .status.failed { color: #dc2626; }

    .btn-icon {
      background: none;
      border: none;
      padding: 0.25rem;
      cursor: pointer;
      font-size: 1rem;
      opacity: 0.7;
      transition: opacity 0.2s;
    }

    .btn-icon:hover {
      opacity: 1;
    }

    .pagination {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 1rem;
      padding: 1rem;
    }

    .page-info {
      color: #6b7280;
      font-size: 0.875rem;
    }

    .loading, .error, .empty {
      text-align: center;
      padding: 2rem;
      color: #6b7280;
    }

    .error {
      color: #dc2626;
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
      justify-content: center;
      align-items: center;
      z-index: 1000;
    }

    .modal {
      background: white;
      border-radius: 12px;
      width: 90%;
      max-width: 700px;
      max-height: 80vh;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }

    :host-context(.dark) .modal {
      background: #1f2937;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.5rem;
      border-bottom: 1px solid #e5e7eb;
    }

    .modal-header h2 {
      margin: 0;
      font-size: 1.25rem;
    }

    .btn-close {
      background: none;
      border: none;
      font-size: 1.5rem;
      cursor: pointer;
      color: #6b7280;
    }

    .modal-body {
      padding: 1.5rem;
      overflow-y: auto;
    }

    .detail-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 1rem;
    }

    .detail-item {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .detail-item.full-width {
      grid-column: 1 / -1;
    }

    .detail-item label {
      font-size: 0.75rem;
      color: #6b7280;
      font-weight: 500;
      text-transform: uppercase;
    }

    .detail-item pre {
      background: #f3f4f6;
      padding: 0.75rem;
      border-radius: 6px;
      font-size: 0.75rem;
      overflow-x: auto;
      max-height: 200px;
    }

    :host-context(.dark) .detail-item pre {
      background: #111827;
    }

    .error-msg {
      color: #dc2626;
    }
  `]
})
export class AuditLogsComponent implements OnInit {
  private http = inject(HttpClient);

  logs: AuditLog[] = [];
  loading = true;
  error = '';
  selectedLog: AuditLog | null = null;

  currentPage = 0;
  totalPages = 0;
  pageSize = 25;

  filters = {
    entityType: '',
    action: '',
    dateRange: '7'
  };

  filterOptions: FilterOptions = {
    entityTypes: [],
    actorIds: [],
    actions: ['CREATE', 'UPDATE', 'DELETE', 'SYSTEM_ACTION']
  };

  ngOnInit(): void {
    this.loadFilterOptions();
    this.loadLogs();
  }

  loadFilterOptions(): void {
    this.http.get<FilterOptions>('http://localhost:8080/api/audit-logs/filter-options').subscribe({
      next: (options) => {
        this.filterOptions = options;
      },
      error: (err) => console.error('Failed to load filter options', err)
    });
  }

  loadLogs(): void {
    this.loading = true;
    this.error = '';

    let params = new HttpParams()
      .set('page', this.currentPage.toString())
      .set('size', this.pageSize.toString())
      .set('sort', 'timestamp,desc');

    if (this.filters.entityType) {
      params = params.set('entityTypes', this.filters.entityType);
    }
    if (this.filters.action) {
      params = params.set('actions', this.filters.action);
    }
    if (this.filters.dateRange && this.filters.dateRange !== 'all') {
      const days = parseInt(this.filters.dateRange);
      const startDate = new Date();
      startDate.setDate(startDate.getDate() - days);
      params = params.set('startDate', startDate.toISOString());
    }

    this.http.get<PageResponse<AuditLog>>('http://localhost:8080/api/audit-logs', { params }).subscribe({
      next: (response) => {
        this.logs = response.content || [];
        this.totalPages = response.totalPages;
        this.currentPage = response.number;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load audit logs. ' + (err.status === 403 ? 'Access denied.' : '');
        this.loading = false;
        console.error(err);
      }
    });
  }

  resetFilters(): void {
    this.filters = {
      entityType: '',
      action: '',
      dateRange: '7'
    };
    this.currentPage = 0;
    this.loadLogs();
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadLogs();
  }

  viewDetails(log: AuditLog): void {
    this.selectedLog = log;
  }

  closeDetails(): void {
    this.selectedLog = null;
  }

  exportCsv(): void {
    let params = new HttpParams();
    if (this.filters.entityType) {
      params = params.set('entityTypes', this.filters.entityType);
    }
    if (this.filters.action) {
      params = params.set('actions', this.filters.action);
    }

    this.http.get('http://localhost:8080/api/audit-logs/export', {
      params,
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `audit_logs_${new Date().toISOString().split('T')[0]}.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        alert('Failed to export audit logs');
        console.error(err);
      }
    });
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  formatDateFull(dateStr: string): string {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString();
  }

  formatJson(json: string): string {
    try {
      return JSON.stringify(JSON.parse(json), null, 2);
    } catch {
      return json;
    }
  }
}
