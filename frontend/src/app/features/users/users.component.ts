import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';

interface User {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    phone?: string;
    department?: string;
    role: string;
    lecturerId?: number;
    lecturerName?: string;
    active: boolean;
    emailVerified: boolean;
    createdAt: string;
    lastLoginAt?: string;
    mustChangePassword: boolean;
}

@Component({
    selector: 'app-users',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="container">
      <div class="header">
        <h1>User Management</h1>
        <button class="btn btn-primary" (click)="showCreateModal = true">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          Add User
        </button>
      </div>

      @if (loading) {
        <div class="loading">Loading users...</div>
      } @else if (error) {
        <div class="error">{{ error }}</div>
      } @else {
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Department</th>
                <th>Status</th>
                <th>Last Login</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (user of users; track user.id) {
                <tr>
                  <td>{{ user.firstName }} {{ user.lastName }}</td>
                  <td>{{ user.email }}</td>
                  <td>
                    <span class="badge" [class]="'badge-' + user.role.toLowerCase()">
                      {{ user.role }}
                    </span>
                  </td>
                  <td>{{ user.department || '-' }}</td>
                  <td>
                    <span class="status" [class.active]="user.active">
                      {{ user.active ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td>{{ user.lastLoginAt ? formatDate(user.lastLoginAt) : 'Never' }}</td>
                  <td class="actions">
                    <button class="btn-icon" title="Reset Password" (click)="resetPassword(user)">
                      🔑
                    </button>
                    @if (user.active) {
                      <button class="btn-icon" title="Lock Account" (click)="lockUser(user)">
                        🔒
                      </button>
                    } @else {
                      <button class="btn-icon" title="Unlock Account" (click)="unlockUser(user)">
                        🔓
                      </button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="7" class="empty">No users found</td>
                </tr>
              }
            </tbody>
          </table>
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

    .btn-primary {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
    }

    .btn-primary:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
    }

    .table-container {
      background: white;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      overflow: hidden;
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

    th {
      background: #f9fafb;
      font-weight: 600;
      font-size: 0.75rem;
      text-transform: uppercase;
      color: #6b7280;
    }

    .badge {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .badge-super_admin { background: #fef3c7; color: #92400e; }
    .badge-admin { background: #dbeafe; color: #1e40af; }
    .badge-coordinator { background: #d1fae5; color: #065f46; }
    .badge-lecturer { background: #e0e7ff; color: #3730a3; }
    .badge-viewer { background: #f3f4f6; color: #374151; }

    .status {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      background: #fee2e2;
      color: #dc2626;
    }

    .status.active {
      background: #d1fae5;
      color: #059669;
    }

    .actions {
      display: flex;
      gap: 0.25rem;
    }

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

    .loading, .error, .empty {
      text-align: center;
      padding: 2rem;
      color: #6b7280;
    }

    .error {
      color: #dc2626;
    }
  `]
})
export class UsersComponent implements OnInit {
    private http = inject(HttpClient);
    private authService = inject(AuthService);

    users: User[] = [];
    loading = true;
    error = '';
    showCreateModal = false;

    ngOnInit(): void {
        this.loadUsers();
    }

    loadUsers(): void {
        this.loading = true;
        this.http.get<{ content: User[] }>('http://localhost:8080/api/users?size=100').subscribe({
            next: (response) => {
                this.users = response.content || [];
                this.loading = false;
            },
            error: (err) => {
                this.error = 'Failed to load users';
                this.loading = false;
                console.error(err);
            }
        });
    }

    formatDate(dateStr: string): string {
        return new Date(dateStr).toLocaleDateString();
    }

    resetPassword(user: User): void {
        if (confirm(`Reset password for ${user.firstName} ${user.lastName}?`)) {
            this.http.post<{ temporaryPassword: string }>(`http://localhost:8080/api/users/${user.id}/reset-password`, {}).subscribe({
                next: (response) => {
                    alert(`New temporary password: ${response.temporaryPassword}\n\nPlease share this securely with the user.`);
                },
                error: (err) => {
                    alert('Failed to reset password: ' + (err.error?.error || 'Unknown error'));
                }
            });
        }
    }

    lockUser(user: User): void {
        if (confirm(`Lock account for ${user.firstName} ${user.lastName}?`)) {
            this.http.post(`http://localhost:8080/api/users/${user.id}/lock?minutes=1440`, {}).subscribe({
                next: () => {
                    this.loadUsers();
                },
                error: (err) => {
                    alert('Failed to lock user: ' + (err.error?.error || 'Unknown error'));
                }
            });
        }
    }

    unlockUser(user: User): void {
        this.http.post(`http://localhost:8080/api/users/${user.id}/unlock`, {}).subscribe({
            next: () => {
                this.loadUsers();
            },
            error: (err) => {
                alert('Failed to unlock user: ' + (err.error?.error || 'Unknown error'));
            }
        });
    }
}
