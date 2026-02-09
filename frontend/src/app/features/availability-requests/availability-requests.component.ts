import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface AvailabilityRequest {
  id: number;
  lecturerId: number;
  lecturerName: string;
  requestedByEmail: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  newStatus: string;
  reason: string;
  status: string;
  affectedLessonsCount: number;
  createdAt: string;
  reviewedByEmail?: string;
  reviewedAt?: string;
  reviewNotes?: string;
}

interface Lecturer {
  id: number;
  name: string;
  email: string;
}

@Component({
  selector: 'app-availability-requests',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1>🚫 Unavailability Requests</h1>
        <p>Review and manage lecturer unavailability requests</p>
      </div>

      <div class="tabs">
        <button [class.active]="activeTab === 'pending'" (click)="activeTab = 'pending'; loadPending()">
          Pending <span class="badge" *ngIf="pendingCount > 0">{{ pendingCount }}</span>
        </button>
        <button [class.active]="activeTab === 'all'" (click)="activeTab = 'all'; loadAll()">All Requests</button>
        <button [class.active]="activeTab === 'create'" (click)="activeTab = 'create'; loadLecturers()">+ Create Request</button>
      </div>

      <!-- Create Request Form -->
      <div *ngIf="activeTab === 'create'" class="create-form-container">
        <div class="create-form">
          <h3>Create Unavailability Request for Lecturer</h3>
          <div class="form-row">
            <div class="form-group">
              <label>Lecturer</label>
              <select [(ngModel)]="newRequest.lecturerId">
                <option [ngValue]="null">-- Select Lecturer --</option>
                <option *ngFor="let lec of lecturers" [ngValue]="lec.id">{{ lec.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>Day</label>
              <select [(ngModel)]="newRequest.dayOfWeek">
                <option value="MONDAY">Monday</option>
                <option value="TUESDAY">Tuesday</option>
                <option value="WEDNESDAY">Wednesday</option>
                <option value="THURSDAY">Thursday</option>
                <option value="FRIDAY">Friday</option>
              </select>
            </div>
            <div class="form-group">
              <label>Start Time</label>
              <input type="time" [(ngModel)]="newRequest.startTime">
            </div>
            <div class="form-group">
              <label>End Time</label>
              <input type="time" [(ngModel)]="newRequest.endTime">
            </div>
          </div>
          <div class="form-group full-width">
            <label>Reason (min 20 characters)</label>
            <textarea [(ngModel)]="newRequest.reason" rows="2" placeholder="Reason for unavailability..."></textarea>
          </div>
          <button class="btn-create" (click)="createRequest()" [disabled]="creating || !newRequest.lecturerId || newRequest.reason.length < 20">
            {{ creating ? 'Creating...' : 'Create Request' }}
          </button>
          <span *ngIf="createMessage" class="create-message" [class.success]="createSuccess">{{ createMessage }}</span>
        </div>
      </div>

      <div *ngIf="activeTab !== 'create' && loading" class="loading">Loading requests...</div>

      <div *ngIf="activeTab !== 'create' && !loading && requests.length === 0" class="empty">
        No {{ activeTab === 'pending' ? 'pending' : '' }} requests found.
      </div>

      <div *ngIf="activeTab !== 'create' && !loading && requests.length > 0" class="requests-table">
        <table>
          <thead>
            <tr>
              <th>Lecturer</th>
              <th>Day</th>
              <th>Time</th>
              <th>Reason</th>
              <th>Impact</th>
              <th>Status</th>
              <th>Submitted</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let req of requests">
              <td>
                <div class="lecturer-name">{{ req.lecturerName }}</div>
                <div class="lecturer-email">{{ req.requestedByEmail }}</div>
              </td>
              <td>{{ req.dayOfWeek }}</td>
              <td>{{ req.startTime }} - {{ req.endTime }}</td>
              <td class="reason-cell" [title]="req.reason">{{ req.reason }}</td>
              <td>
                <span class="impact" [class.high]="req.affectedLessonsCount > 0">
                  {{ req.affectedLessonsCount }} lessons
                </span>
              </td>
              <td>
                <span class="status-badge" [class]="req.status.toLowerCase()">{{ req.status }}</span>
              </td>
              <td>{{ formatDate(req.createdAt) }}</td>
              <td class="actions">
                <ng-container *ngIf="req.status === 'PENDING'">
                  <button class="btn-approve" (click)="openModal(req, 'approve')">✓ Approve</button>
                  <button class="btn-reject" (click)="openModal(req, 'reject')">✗ Reject</button>
                  <button class="btn-return" (click)="openModal(req, 'return')">↩ Return</button>
                </ng-container>
                <button *ngIf="req.status === 'APPROVED'" class="btn-revoke" (click)="openModal(req, 'revoke')">⚠️ Revoke</button>
                <span *ngIf="req.status !== 'PENDING' && req.status !== 'APPROVED'" class="no-action">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Review Modal -->
      <div class="modal-overlay" *ngIf="showModal" (click)="closeModal()">
        <div class="modal" (click)="$event.stopPropagation()">
          <h2>{{ modalAction | titlecase }} Request</h2>
          <div class="modal-content">
            <p><strong>Lecturer:</strong> {{ selectedRequest?.lecturerName }}</p>
            <p><strong>Request:</strong> {{ selectedRequest?.dayOfWeek }} {{ selectedRequest?.startTime }} - {{ selectedRequest?.endTime }} ({{ selectedRequest?.newStatus }})</p>
            <p><strong>Reason:</strong> {{ selectedRequest?.reason }}</p>
            
            <!-- Lecturer History Stats -->
            <div *ngIf="lecturerStats" class="lecturer-stats">
              <strong>Lecturer History:</strong>
              <span class="stat approved">{{ lecturerStats.approved }} approved</span>
              <span class="stat pending">{{ lecturerStats.pending }} pending</span>
              <span class="stat rejected">{{ lecturerStats.rejected }} rejected</span>
              <span class="stat total">({{ lecturerStats.total }} total)</span>
            </div>
            
            <div class="form-group">
              <label>Notes {{ modalAction === 'reject' || modalAction === 'return' || modalAction === 'revoke' ? '(required)' : '(optional)' }}</label>
              <textarea [(ngModel)]="reviewNotes" rows="3" placeholder="Add notes for the lecturer..."></textarea>
            </div>
          </div>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="closeModal()">Cancel</button>
            <button 
              class="btn-confirm" 
              [class]="'btn-' + modalAction"
              (click)="performAction()"
              [disabled]="processing || ((modalAction === 'reject' || modalAction === 'return') && reviewNotes.length < 5)">
              {{ processing ? 'Processing...' : (modalAction | titlecase) }}
            </button>
          </div>
        </div>
      </div>

      <div *ngIf="message" class="message" [class.success]="messageSuccess">{{ message }}</div>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 1.5rem;
      max-width: 1400px;
      margin: 0 auto;
    }

    .page-header h1 {
      font-size: 1.75rem;
      font-weight: 700;
      margin: 0 0 0.5rem;
      color: #1e293b;
    }

    .page-header p {
      color: #64748b;
      margin: 0 0 1.5rem;
    }

    .tabs {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1.5rem;
    }

    .tabs button {
      padding: 0.75rem 1.5rem;
      background: white;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 500;
      color: #64748b;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .tabs button.active {
      background: #667eea;
      color: white;
      border-color: #667eea;
    }

    .badge {
      background: #ef4444;
      color: white;
      padding: 0.125rem 0.5rem;
      border-radius: 10px;
      font-size: 0.75rem;
    }

    .tabs button.active .badge {
      background: white;
      color: #667eea;
    }

    .requests-table {
      background: white;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    th {
      text-align: left;
      padding: 1rem;
      background: #f8fafc;
      font-weight: 600;
      color: #475569;
      font-size: 0.875rem;
    }

    td {
      padding: 1rem;
      border-bottom: 1px solid #e2e8f0;
      font-size: 0.875rem;
    }

    .lecturer-name { font-weight: 600; color: #1e293b; }
    .lecturer-email { font-size: 0.75rem; color: #64748b; }

    .reason-cell {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .type-badge, .status-badge {
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .type-badge.unavailable { background: #fee2e2; color: #991b1b; }
    .type-badge.preferred { background: #d1fae5; color: #065f46; }

    .status-badge.pending { background: #fef3c7; color: #92400e; }
    .status-badge.approved { background: #d1fae5; color: #065f46; }
    .status-badge.rejected { background: #fee2e2; color: #991b1b; }
    .status-badge.returned { background: #e0e7ff; color: #3730a3; }

    .impact { font-size: 0.75rem; color: #64748b; }
    .impact.high { color: #dc2626; font-weight: 600; }

    .actions {
      display: flex;
      gap: 0.5rem;
    }

    .btn-approve, .btn-reject, .btn-return {
      padding: 0.375rem 0.75rem;
      border: none;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-approve { background: #d1fae5; color: #065f46; }
    .btn-reject { background: #fee2e2; color: #991b1b; }
    .btn-return { background: #e0e7ff; color: #3730a3; }
    .btn-revoke { background: #fef3c7; color: #92400e; padding: 0.375rem 0.75rem; border: none; border-radius: 4px; font-size: 0.75rem; font-weight: 600; cursor: pointer; }
    .no-action { color: #9ca3af; }

    /* Create Form */
    .create-form-container {
      background: white;
      border-radius: 12px;
      padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }

    .create-form h3 {
      margin: 0 0 1rem;
      font-size: 1.125rem;
      color: #1e293b;
    }

    .form-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 1rem;
      margin-bottom: 1rem;
    }

    .form-group { display: flex; flex-direction: column; }
    .form-group.full-width { grid-column: 1 / -1; }

    .form-group label {
      font-size: 0.8rem;
      font-weight: 500;
      color: #4b5563;
      margin-bottom: 0.25rem;
    }

    .create-form select, .create-form input, .create-form textarea {
      padding: 0.5rem;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      font-size: 0.9rem;
    }

    .btn-create {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      padding: 0.6rem 1.5rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-create:disabled { opacity: 0.6; cursor: not-allowed; }

    .create-message {
      margin-left: 1rem;
      font-size: 0.875rem;
      color: #dc2626;
    }

    .create-message.success { color: #059669; }

    .loading, .empty {
      text-align: center;
      padding: 3rem;
      color: #6b7280;
      background: white;
      border-radius: 12px;
    }

    /* Modal */
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal {
      background: white;
      border-radius: 12px;
      padding: 1.5rem;
      width: 100%;
      max-width: 500px;
      box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);
    }

    .modal h2 {
      margin: 0 0 1rem;
      font-size: 1.25rem;
      color: #1e293b;
    }

    .modal-content p {
      margin: 0.5rem 0;
      font-size: 0.875rem;
    }

    .lecturer-stats {
      background: #f1f5f9;
      padding: 0.75rem;
      border-radius: 8px;
      margin: 1rem 0;
      font-size: 0.8rem;
    }

    .lecturer-stats .stat {
      margin-left: 0.5rem;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
    }

    .lecturer-stats .stat.approved { background: #d1fae5; color: #065f46; }
    .lecturer-stats .stat.pending { background: #fef3c7; color: #92400e; }
    .lecturer-stats .stat.rejected { background: #fee2e2; color: #991b1b; }
    .lecturer-stats .stat.total { color: #6b7280; }

    .modal .form-group {
      margin-top: 1rem;
    }

    .modal label {
      display: block;
      font-size: 0.875rem;
      font-weight: 500;
      margin-bottom: 0.25rem;
    }

    .modal textarea {
      width: 100%;
      padding: 0.5rem;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      font-size: 0.875rem;
    }

    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1.5rem;
    }

    .btn-cancel, .btn-confirm {
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-cancel {
      background: #f1f5f9;
      border: 1px solid #e2e8f0;
      color: #475569;
    }

    .btn-confirm {
      border: none;
      color: white;
    }

    .btn-confirm.btn-approve { background: #10b981; }
    .btn-confirm.btn-reject { background: #ef4444; }
    .btn-confirm.btn-return { background: #6366f1; }
    .btn-confirm.btn-revoke { background: #f59e0b; }
    .btn-confirm:disabled { opacity: 0.5; cursor: not-allowed; }

    .message {
      position: fixed;
      bottom: 1rem;
      right: 1rem;
      padding: 1rem 1.5rem;
      background: #fee2e2;
      color: #991b1b;
      border-radius: 8px;
      box-shadow: 0 4px 6px rgba(0,0,0,0.1);
    }

    .message.success {
      background: #d1fae5;
      color: #065f46;
    }

    @media (prefers-color-scheme: dark) {
      .page-header h1 { color: white; }
      .requests-table, .loading, .empty { background: #1f2937; }
      th { background: #374151; color: #d1d5db; }
      td { border-color: #374151; }
      .lecturer-name { color: white; }
      .tabs button { background: #1f2937; border-color: #374151; color: #9ca3af; }
      .modal { background: #1f2937; }
      .modal h2 { color: white; }
    }
  `]
})
export class AvailabilityRequestsComponent implements OnInit {
  requests: AvailabilityRequest[] = [];
  loading = true;
  activeTab = 'pending';
  pendingCount = 0;

  showModal = false;
  selectedRequest: AvailabilityRequest | null = null;
  modalAction = '';
  reviewNotes = '';
  processing = false;
  message = '';
  messageSuccess = false;

  // Create form state
  lecturers: Lecturer[] = [];
  newRequest = {
    lecturerId: null as number | null,
    dayOfWeek: 'MONDAY',
    startTime: '08:00',
    endTime: '10:00',
    newStatus: 'UNAVAILABLE',
    reason: ''
  };
  creating = false;
  createMessage = '';
  createSuccess = false;

  // Stats for modal
  lecturerStats: { approved: number; pending: number; rejected: number; total: number } | null = null;

  constructor(private http: HttpClient) { }

  ngOnInit(): void {
    this.loadPending();
    this.loadPendingCount();
  }

  loadPending(): void {
    this.loading = true;
    this.http.get<AvailabilityRequest[]>('http://localhost:8080/api/v1/availability-requests/pending').subscribe({
      next: (data) => { this.requests = data; this.loading = false; },
      error: () => { this.requests = []; this.loading = false; }
    });
  }

  loadAll(): void {
    this.loading = true;
    this.http.get<AvailabilityRequest[]>('http://localhost:8080/api/v1/availability-requests').subscribe({
      next: (data) => { this.requests = data; this.loading = false; },
      error: () => { this.requests = []; this.loading = false; }
    });
  }

  loadPendingCount(): void {
    this.http.get<{ count: number }>('http://localhost:8080/api/v1/availability-requests/pending/count').subscribe({
      next: (data) => this.pendingCount = data.count,
      error: () => this.pendingCount = 0
    });
  }

  openModal(req: AvailabilityRequest, action: string): void {
    this.selectedRequest = req;
    this.modalAction = action;
    this.reviewNotes = '';
    this.lecturerStats = null;
    this.showModal = true;

    // Load lecturer stats
    this.http.get<any>(`http://localhost:8080/api/v1/availability-requests/lecturer/${req.lecturerId}/stats`).subscribe({
      next: (stats) => this.lecturerStats = stats,
      error: () => this.lecturerStats = null
    });
  }

  closeModal(): void {
    this.showModal = false;
    this.selectedRequest = null;
    this.reviewNotes = '';
  }

  performAction(): void {
    if (!this.selectedRequest) return;
    this.processing = true;

    const url = `http://localhost:8080/api/v1/availability-requests/${this.selectedRequest.id}/${this.modalAction}`;
    const body = { notes: this.reviewNotes };

    this.http.post(url, body).subscribe({
      next: () => {
        this.processing = false;
        this.showMessage(`Request ${this.modalAction}d successfully!`, true);
        this.closeModal();
        this.loadPending();
        this.loadPendingCount();
      },
      error: (err) => {
        this.processing = false;
        this.showMessage(err.error?.message || `Failed to ${this.modalAction} request`, false);
      }
    });
  }

  showMessage(msg: string, success: boolean): void {
    this.message = msg;
    this.messageSuccess = success;
    setTimeout(() => this.message = '', 3000);
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString();
  }

  loadLecturers(): void {
    if (this.lecturers.length > 0) return; // Already loaded
    this.http.get<Lecturer[]>('http://localhost:8080/api/v1/lecturers').subscribe({
      next: (data) => this.lecturers = data,
      error: () => this.lecturers = []
    });
  }

  createRequest(): void {
    if (this.creating || !this.newRequest.lecturerId) return;
    this.creating = true;
    this.createMessage = '';

    this.http.post('http://localhost:8080/api/v1/availability-requests', this.newRequest).subscribe({
      next: () => {
        this.creating = false;
        this.createSuccess = true;
        this.createMessage = 'Request created successfully!';
        this.newRequest.reason = '';
        this.loadPendingCount();
        setTimeout(() => this.createMessage = '', 3000);
      },
      error: (err) => {
        this.creating = false;
        this.createSuccess = false;
        this.createMessage = err.error?.message || 'Failed to create request';
      }
    });
  }
}
