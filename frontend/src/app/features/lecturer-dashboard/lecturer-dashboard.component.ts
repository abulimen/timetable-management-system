import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface Course {
  id: number;
  code: string;
  name: string;
  weeklyHours: number;
  studentGroups: string[];
}

interface Lesson {
  id: number;
  courseCode: string;
  courseName: string;
  day: string;
  startTime: string;
  endTime: string;
  room: string;
  duration: number;
}

interface LecturerProfile {
  id: number;
  name: string;
  email: string;
}

interface AvailabilityRequest {
  id: number;
  lecturerId: number;
  lecturerName: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  newStatus: string;
  reason: string;
  status: string;
  createdAt: string;
  reviewedByEmail?: string;
  reviewNotes?: string;
}

@Component({
  selector: 'app-lecturer-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="dashboard-container">
      <div class="dashboard-header">
        <h1>📚 My Dashboard</h1>
        <p *ngIf="profile" class="welcome">Welcome, {{ profile.name }}</p>
      </div>

      <div *ngIf="loading" class="loading">Loading your dashboard...</div>
      <div *ngIf="error" class="error">{{ error }}</div>

      <div *ngIf="!loading && !error" class="dashboard-content">
        <!-- My Courses Section -->
        <section class="section">
          <h2>📖 My Courses</h2>
          <div *ngIf="courses.length === 0" class="empty">No courses assigned yet.</div>
          <div class="courses-grid">
            <div *ngFor="let course of courses" class="course-card">
              <div class="course-code">{{ course.code }}</div>
              <div class="course-name">{{ course.name }}</div>
              <div class="course-hours">{{ course.weeklyHours }} hrs/week</div>
              <div class="course-groups">
                <span *ngFor="let group of course.studentGroups" class="group-tag">{{ group }}</span>
              </div>
            </div>
          </div>
        </section>

        <!-- My Schedule Section -->
        <section class="section">
          <h2>📅 My Schedule</h2>
          <div *ngIf="lessons.length === 0" class="empty">No scheduled lessons yet.</div>
          <div class="schedule-grid">
            <div *ngFor="let day of days" class="day-column">
              <div class="day-header">{{ day }}</div>
              <div class="day-lessons">
                <div *ngFor="let lesson of getLessonsForDay(day)" class="lesson-card" [style.background]="getColor(lesson.courseCode)">
                  <div class="lesson-time">{{ lesson.startTime }} - {{ lesson.endTime }}</div>
                  <div class="lesson-course">{{ lesson.courseCode }}</div>
                  <div class="lesson-room">📍 {{ lesson.room }}</div>
                </div>
                <div *ngIf="getLessonsForDay(day).length === 0" class="no-lessons">-</div>
              </div>
            </div>
          </div>
        </section>

        <!-- Unavailability Requests Section -->
        <section class="section" *ngIf="profile && unavailabilitySettings.systemEnabled" id="availability">
          <h2>🚫 Unavailability Requests</h2>
          <p class="section-desc">Request time slots when you're unavailable for teaching.</p>

          <!-- Request Form -->
          <div class="request-form">
            <h3>{{ isResubmitting ? 'Resubmit Request' : 'Submit New Request' }}</h3>
            <div class="form-row">
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
              <textarea [(ngModel)]="newRequest.reason" rows="2" placeholder="Explain why you'll be unavailable..."></textarea>
            </div>
            <div class="form-actions">
              <button class="btn-submit" (click)="submitRequest()" [disabled]="submitting || newRequest.reason.length < 20">
                {{ submitting ? 'Submitting...' : (isResubmitting ? 'Resubmit Request' : 'Submit Request') }}
              </button>
              <button *ngIf="isResubmitting" class="btn-cancel" (click)="cancelResubmit()">Cancel</button>
            </div>
            <span *ngIf="submitMessage" class="submit-message" [class.success]="submitSuccess">{{ submitMessage }}</span>
          </div>

          <!-- My Requests List -->
          <div class="requests-list">
            <h3>My Requests</h3>
            <div *ngIf="myRequests.length === 0" class="empty">No requests submitted yet.</div>
            <table *ngIf="myRequests.length > 0">
              <thead>
                <tr>
                  <th>Day</th>
                  <th>Time</th>
                  <th>Reason</th>
                  <th>Status</th>
                  <th>Submitted</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let req of myRequests" [class.returned-row]="req.status === 'RETURNED'">
                  <td>{{ req.dayOfWeek }}</td>
                  <td>{{ req.startTime }} - {{ req.endTime }}</td>
                  <td class="reason-cell">{{ req.reason }}</td>
                  <td>
                    <span class="status-badge" [class]="req.status.toLowerCase()">{{ req.status }}</span>
                  </td>
                  <td>{{ formatDate(req.createdAt) }}</td>
                  <td class="actions-cell">
                    <ng-container *ngIf="req.status === 'RETURNED'">
                      <button class="btn-view-feedback" (click)="showFeedback(req)" title="View admin feedback">
                        💬
                      </button>
                      <button class="btn-resubmit" (click)="resubmitRequest(req)" title="Edit and resubmit">
                        ✏️ Resubmit
                      </button>
                    </ng-container>
                    <span *ngIf="req.status !== 'RETURNED'" class="no-action">-</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Feedback Modal -->
          <div *ngIf="showFeedbackModal" class="modal-overlay" (click)="closeFeedbackModal()">
            <div class="modal-content" (click)="$event.stopPropagation()">
              <h3>📝 Admin Feedback</h3>
              <div class="feedback-details">
                <p><strong>Request:</strong> {{ selectedRequest?.dayOfWeek }} {{ selectedRequest?.startTime }} - {{ selectedRequest?.endTime }}</p>
                <p><strong>Your Reason:</strong> {{ selectedRequest?.reason }}</p>
                <div class="admin-notes">
                  <strong>Admin Notes:</strong>
                  <p>{{ selectedRequest?.reviewNotes || 'No notes provided' }}</p>
                </div>
                <p *ngIf="selectedRequest?.reviewedByEmail"><strong>Reviewed by:</strong> {{ selectedRequest?.reviewedByEmail }}</p>
              </div>
              <button class="btn-close" (click)="closeFeedbackModal()">Close</button>
            </div>
          </div>
        </section>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-container {
      padding: 1.5rem;
      max-width: 1400px;
      margin: 0 auto;
    }

    .dashboard-header {
      margin-bottom: 2rem;
    }

    .dashboard-header h1 {
      font-size: 1.75rem;
      font-weight: 700;
      margin: 0 0 0.5rem;
      color: #1e293b;
    }

    .welcome {
      color: #64748b;
      margin: 0;
    }

    .section {
      background: white;
      border-radius: 12px;
      padding: 1.5rem;
      margin-bottom: 1.5rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }

    .section h2 {
      font-size: 1.25rem;
      font-weight: 600;
      margin: 0 0 0.5rem;
      color: #374151;
    }

    .section-desc {
      color: #6b7280;
      font-size: 0.875rem;
      margin: 0 0 1rem;
    }

    /* Courses Grid */
    .courses-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 1rem;
    }

    .course-card {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border-radius: 10px;
      padding: 1.25rem;
    }

    .course-code { font-size: 0.875rem; opacity: 0.9; }
    .course-name { font-size: 1.125rem; font-weight: 600; margin: 0.25rem 0; }
    .course-hours { font-size: 0.875rem; opacity: 0.8; margin-bottom: 0.75rem; }
    .course-groups { display: flex; flex-wrap: wrap; gap: 0.25rem; }
    .group-tag { background: rgba(255,255,255,0.2); padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.75rem; }

    /* Schedule Grid */
    .schedule-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 0.5rem;
    }

    .day-column { background: #f8fafc; border-radius: 8px; overflow: hidden; }
    .day-header { background: #1e293b; color: white; text-align: center; padding: 0.75rem; font-weight: 600; font-size: 0.875rem; }
    .day-lessons { padding: 0.5rem; min-height: 200px; }
    .lesson-card { background: #10b981; color: white; border-radius: 6px; padding: 0.75rem; margin-bottom: 0.5rem; }
    .lesson-time { font-size: 0.75rem; opacity: 0.9; }
    .lesson-course { font-weight: 600; font-size: 0.875rem; margin: 0.25rem 0; }
    .lesson-room { font-size: 0.75rem; opacity: 0.9; }
    .no-lessons { text-align: center; color: #9ca3af; padding: 2rem; }

    .loading, .error, .empty { text-align: center; padding: 2rem; color: #6b7280; }
    .error { color: #dc2626; background: #fee2e2; border-radius: 8px; }

    /* Request Form */
    .request-form {
      background: #f8fafc;
      border-radius: 8px;
      padding: 1rem;
      margin-bottom: 1.5rem;
    }

    .request-form h3 {
      font-size: 1rem;
      font-weight: 600;
      margin: 0 0 1rem;
      color: #374151;
    }

    .form-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 1rem;
      margin-bottom: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
    }

    .form-group.full-width {
      grid-column: 1 / -1;
    }

    .form-group label {
      font-size: 0.8rem;
      font-weight: 500;
      color: #4b5563;
      margin-bottom: 0.25rem;
    }

    .form-group input, .form-group select, .form-group textarea {
      padding: 0.5rem;
      border: 1px solid #d1d5db;
      border-radius: 6px;
      font-size: 0.9rem;
    }

    .form-group textarea {
      resize: vertical;
    }

    .btn-submit {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      padding: 0.6rem 1.5rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      transition: opacity 0.2s;
    }

    .btn-submit:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .form-actions {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }

    .btn-cancel {
      background: #64748b;
      color: white;
      border: none;
      padding: 0.6rem 1rem;
      border-radius: 6px;
      cursor: pointer;
    }

    .submit-message {
      margin-left: 1rem;
      font-size: 0.875rem;
      color: #dc2626;
    }

    .submit-message.success {
      color: #059669;
    }

    /* Requests List */
    .requests-list h3 {
      font-size: 1rem;
      font-weight: 600;
      margin: 0 0 1rem;
      color: #374151;
    }

    .requests-list table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.875rem;
    }

    .requests-list th {
      text-align: left;
      padding: 0.75rem;
      background: #f1f5f9;
      font-weight: 600;
      color: #475569;
    }

    .requests-list td {
      padding: 0.75rem;
      border-bottom: 1px solid #e2e8f0;
    }

    .reason-cell {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .status-badge {
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .status-badge.pending { background: #fef3c7; color: #92400e; }
    .status-badge.approved { background: #d1fae5; color: #065f46; }
    .status-badge.rejected { background: #fee2e2; color: #991b1b; }
    .status-badge.returned { background: #e0e7ff; color: #3730a3; }

    /* Returned row highlight */
    .returned-row { background: #fef3c7; }

    /* Actions column */
    .actions-cell { white-space: nowrap; }
    .no-action { color: #9ca3af; }

    .btn-view-feedback, .btn-resubmit {
      border: none;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      cursor: pointer;
      font-size: 0.8rem;
      margin-right: 0.25rem;
    }

    .btn-view-feedback {
      background: #e0e7ff;
      color: #3730a3;
    }

    .btn-resubmit {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
    }

    /* Modal styles */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0,0,0,0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-content {
      background: white;
      padding: 1.5rem;
      border-radius: 12px;
      max-width: 500px;
      width: 90%;
      box-shadow: 0 10px 25px rgba(0,0,0,0.3);
    }

    .modal-content h3 {
      margin: 0 0 1rem;
      font-size: 1.25rem;
      color: #1e293b;
    }

    .feedback-details p {
      margin: 0.5rem 0;
      color: #475569;
    }

    .admin-notes {
      background: #fef3c7;
      padding: 1rem;
      border-radius: 8px;
      margin: 1rem 0;
      border-left: 4px solid #f59e0b;
    }

    .admin-notes p { margin: 0.5rem 0 0; color: #92400e; }

    .btn-close {
      background: #64748b;
      color: white;
      border: none;
      padding: 0.5rem 1rem;
      border-radius: 6px;
      cursor: pointer;
      margin-top: 1rem;
    }

    @media (prefers-color-scheme: dark) {
      .dashboard-header h1 { color: white; }
      .section { background: #1f2937; }
      .section h2 { color: #e5e7eb; }
      .day-column { background: #374151; }
      .request-form { background: #374151; }
      .form-group label { color: #d1d5db; }
      .form-group input, .form-group select, .form-group textarea { background: #1f2937; color: white; border-color: #4b5563; }
      .requests-list th { background: #374151; color: #d1d5db; }
      .requests-list td { border-color: #4b5563; }
    }

    @media (max-width: 768px) {
      .schedule-grid { grid-template-columns: 1fr; }
      .form-row { grid-template-columns: 1fr; }
    }
  `]
})
export class LecturerDashboardComponent implements OnInit {
  profile: LecturerProfile | null = null;
  courses: Course[] = [];
  lessons: Lesson[] = [];
  myRequests: AvailabilityRequest[] = [];
  loading = true;
  error: string | null = null;

  days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
  colorMap = new Map<string, string>();
  colors = ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6'];

  newRequest = {
    dayOfWeek: 'MONDAY',
    startTime: '08:00',
    endTime: '10:00',
    newStatus: 'UNAVAILABLE',
    reason: ''
  };
  submitting = false;
  submitMessage = '';
  submitSuccess = false;

  // Modal state
  showFeedbackModal = false;
  selectedRequest: AvailabilityRequest | null = null;
  isResubmitting = false;
  resubmittingRequestId: number | null = null;

  // Unavailability system settings
  unavailabilitySettings = { systemEnabled: false, requestsOpen: false };

  constructor(private http: HttpClient) { }

  ngOnInit(): void {
    this.loadUnavailabilitySettings();
    this.loadDashboard();
  }

  loadUnavailabilitySettings(): void {
    this.http.get<any>('http://localhost:8080/api/v1/availability-requests/settings').subscribe({
      next: (data) => this.unavailabilitySettings = data,
      error: () => { }
    });
  }

  loadDashboard(): void {
    this.loading = true;
    this.http.get<LecturerProfile>('http://localhost:8080/api/v1/lecturers/me').subscribe({
      next: (profile) => {
        this.profile = profile;
        this.loadCourses();
        this.loadLessons();
        this.loadMyRequests();
      },
      error: () => {
        this.profile = null;
        this.courses = [];
        this.lessons = [];
        this.loading = false;
      }
    });
  }

  loadCourses(): void {
    this.http.get<Course[]>('http://localhost:8080/api/v1/lecturers/me/courses').subscribe({
      next: (courses) => this.courses = courses,
      error: () => this.courses = []
    });
  }

  loadLessons(): void {
    this.http.get<Lesson[]>('http://localhost:8080/api/v1/lecturers/me/lessons').subscribe({
      next: (lessons) => {
        this.lessons = lessons;
        this.loading = false;
      },
      error: () => {
        this.lessons = [];
        this.loading = false;
      }
    });
  }

  loadMyRequests(): void {
    this.http.get<AvailabilityRequest[]>('http://localhost:8080/api/v1/availability-requests/my').subscribe({
      next: (requests) => this.myRequests = requests,
      error: () => this.myRequests = []
    });
  }

  submitRequest(): void {
    if (this.submitting || !this.profile) return;
    this.submitting = true;
    this.submitMessage = '';

    const payload = {
      lecturerId: this.profile.id,
      dayOfWeek: this.newRequest.dayOfWeek,
      startTime: this.newRequest.startTime,
      endTime: this.newRequest.endTime,
      newStatus: this.newRequest.newStatus,
      reason: this.newRequest.reason
    };

    // Use PUT for resubmitting, POST for new requests
    const request$ = this.isResubmitting && this.resubmittingRequestId
      ? this.http.put(`http://localhost:8080/api/v1/availability-requests/${this.resubmittingRequestId}/resubmit`, payload)
      : this.http.post('http://localhost:8080/api/v1/availability-requests', payload);

    request$.subscribe({
      next: () => {
        this.submitting = false;
        this.submitSuccess = true;
        this.submitMessage = this.isResubmitting ? 'Request resubmitted successfully!' : 'Request submitted successfully!';
        this.newRequest.reason = '';
        this.isResubmitting = false;
        this.resubmittingRequestId = null;
        this.loadMyRequests();
        setTimeout(() => this.submitMessage = '', 3000);
      },
      error: (err) => {
        this.submitting = false;
        this.submitSuccess = false;
        this.submitMessage = err.error?.message || 'Failed to submit request';
      }
    });
  }

  getLessonsForDay(day: string): Lesson[] {
    return this.lessons.filter(l => l.day === day).sort((a, b) => a.startTime.localeCompare(b.startTime));
  }

  getColor(courseCode: string): string {
    if (!this.colorMap.has(courseCode)) {
      this.colorMap.set(courseCode, this.colors[this.colorMap.size % this.colors.length]);
    }
    return this.colorMap.get(courseCode)!;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString();
  }

  showFeedback(request: AvailabilityRequest): void {
    this.selectedRequest = request;
    this.showFeedbackModal = true;
  }

  closeFeedbackModal(): void {
    this.showFeedbackModal = false;
    this.selectedRequest = null;
  }

  resubmitRequest(request: AvailabilityRequest): void {
    // Pre-fill the form with the returned request's data
    this.newRequest = {
      dayOfWeek: request.dayOfWeek,
      startTime: request.startTime,
      endTime: request.endTime,
      newStatus: request.newStatus,
      reason: request.reason
    };
    this.isResubmitting = true;
    this.resubmittingRequestId = request.id;

    // Show feedback if available
    if (request.reviewNotes) {
      this.submitMessage = `📝 Admin feedback: "${request.reviewNotes}" - Please update and resubmit.`;
      this.submitSuccess = false;
    }

    // Scroll to the form
    const formSection = document.getElementById('availability');
    if (formSection) {
      formSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  cancelResubmit(): void {
    this.isResubmitting = false;
    this.resubmittingRequestId = null;
    this.submitMessage = '';
    this.newRequest = {
      dayOfWeek: 'MONDAY',
      startTime: '08:00',
      endTime: '10:00',
      newStatus: 'UNAVAILABLE',
      reason: ''
    };
  }
}
