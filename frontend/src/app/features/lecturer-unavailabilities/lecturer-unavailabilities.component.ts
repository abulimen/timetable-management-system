import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface LecturerUnavailability {
    id: number;
    lecturerId: number;
    lecturerName: string;
    lecturerEmail: string;
    dayOfWeek: string;
    startTime: string;
    endTime: string;
}

@Component({
    selector: 'app-lecturer-unavailabilities',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="page-container">
      <div class="page-header">
        <h1>📅 Lecturer Unavailabilities</h1>
        <p>View all registered lecturer unavailability periods (from lecturer_unavailability table)</p>
        <div class="info-box">
          <strong>⚙️ Solver Info:</strong> The timetable solver respects these entries as a <span class="hard">HARD constraint</span>.
          Lecturers will never be scheduled during their unavailable periods.
        </div>
      </div>

      <div *ngIf="loading" class="loading">Loading unavailabilities...</div>

      <div *ngIf="!loading && unavailabilities.length === 0" class="empty">
        No unavailabilities registered yet.
      </div>

      <div *ngIf="!loading && unavailabilities.length > 0" class="table-container">
        <div class="summary">
          <strong>{{ unavailabilities.length }}</strong> unavailability periods registered across <strong>{{ uniqueLecturers }}</strong> lecturers
        </div>
        <table>
          <thead>
            <tr>
              <th>Lecturer</th>
              <th>Email</th>
              <th>Day</th>
              <th>Time</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let u of unavailabilities">
              <td class="lecturer-name">{{ u.lecturerName }}</td>
              <td class="lecturer-email">{{ u.lecturerEmail }}</td>
              <td>{{ formatDay(u.dayOfWeek) }}</td>
              <td class="time">{{ u.startTime }} - {{ u.endTime }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
    styles: [`
    .page-container {
      padding: 1.5rem;
      max-width: 1200px;
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
      margin: 0 0 1rem;
    }

    .info-box {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 8px;
      padding: 0.75rem 1rem;
      margin-bottom: 1.5rem;
      font-size: 0.875rem;
      color: #166534;
    }

    .hard {
      background: #fee2e2;
      color: #991b1b;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
      font-weight: 600;
    }

    .loading, .empty {
      text-align: center;
      padding: 3rem;
      color: #6b7280;
      background: white;
      border-radius: 12px;
    }

    .summary {
      margin-bottom: 1rem;
      font-size: 0.9rem;
      color: #4b5563;
    }

    .table-container {
      background: white;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
      padding: 1rem;
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    th {
      text-align: left;
      padding: 0.75rem 1rem;
      background: #f8fafc;
      font-weight: 600;
      color: #475569;
      font-size: 0.875rem;
    }

    td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #e2e8f0;
      font-size: 0.875rem;
    }

    .lecturer-name { font-weight: 600; color: #1e293b; }
    .lecturer-email { color: #64748b; font-size: 0.8rem; }
    .time { font-family: monospace; color: #dc2626; font-weight: 500; }

    @media (prefers-color-scheme: dark) {
      .page-header h1 { color: white; }
      .table-container { background: #1f2937; }
      th { background: #374151; color: #d1d5db; }
      td { border-color: #374151; }
      .lecturer-name { color: white; }
    }
  `]
})
export class LecturerUnavailabilitiesComponent implements OnInit {
    unavailabilities: LecturerUnavailability[] = [];
    loading = true;
    uniqueLecturers = 0;

    constructor(private http: HttpClient) { }

    ngOnInit(): void {
        this.loadData();
    }

    loadData(): void {
        this.loading = true;
        this.http.get<LecturerUnavailability[]>('http://localhost:8080/api/v1/lecturers/unavailabilities').subscribe({
            next: (data) => {
                this.unavailabilities = data;
                this.uniqueLecturers = new Set(data.map(u => u.lecturerId)).size;
                this.loading = false;
            },
            error: () => {
                this.unavailabilities = [];
                this.loading = false;
            }
        });
    }

    formatDay(day: string): string {
        return day.charAt(0) + day.slice(1).toLowerCase();
    }
}
