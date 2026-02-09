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

interface UserForm {
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  department: string;
  role: string;
  password: string;
  active: boolean;
}

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <div class="header">
        <h1>User Management</h1>
        <div class="header-actions">
          <button class="btn btn-danger" (click)="confirmDeleteAll()" [disabled]="users.length <= 1">
            🗑️ Delete All
          </button>
          <button class="btn btn-primary" (click)="openCreateModal()">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Add User
          </button>
        </div>
      </div>

      <!-- Delete All Confirmation -->
      <div *ngIf="showDeleteAllConfirm" class="alert alert-danger">
        <p>⚠️ Delete all users except yourself? This cannot be undone!</p>
        <div class="alert-actions">
          <button class="btn btn-secondary btn-sm" (click)="showDeleteAllConfirm = false">Cancel</button>
          <button class="btn btn-danger btn-sm" (click)="deleteAllUsers()" [disabled]="processing">
            {{ processing ? 'Deleting...' : 'Yes, Delete All Others' }}
          </button>
        </div>
      </div>

      <!-- Bulk Import Section -->
      <div class="import-section">
        <div class="import-header" (click)="showImportSection = !showImportSection">
          <h3>📥 Bulk Import Users</h3>
          <span class="toggle-icon">{{ showImportSection ? '▼' : '▶' }}</span>
        </div>
        <div *ngIf="showImportSection" class="import-content">
          <p class="import-desc">Import multiple users from a CSV file. Passwords will be auto-generated and emailed to each user.</p>
          <div class="import-format">
            <strong>Format:</strong> email, first_name, last_name, role, department, phone<br>
            <strong>Roles:</strong> ADMIN, COORDINATOR, LECTURER, VIEWER<br>
            <small>⚠️ SUPER_ADMIN cannot be created via import</small>
          </div>
          <div class="import-actions">
            <button class="btn btn-secondary" (click)="downloadTemplate()">📄 Download Template</button>
            <label class="btn btn-primary file-input-label">
              📤 Select CSV File
              <input type="file" accept=".csv" (change)="onFileSelected($event)" hidden>
            </label>
          </div>
          <div *ngIf="importFile" class="import-file-info">
            <span>📎 {{ importFile.name }}</span>
            <button class="btn btn-primary btn-sm" (click)="importUsers()" [disabled]="importing">
              {{ importing ? 'Importing...' : 'Import Users' }}
            </button>
          </div>
          <div *ngIf="importResult" class="import-result" [class.success]="importResult.success" [class.error]="!importResult.success">
            {{ importResult.message }}
          </div>
        </div>
      </div>

      <!-- User Modal -->
      <div *ngIf="showModal" class="modal-overlay" (click)="closeModal()">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h2>{{ editingUser ? 'Edit User' : 'Add New User' }}</h2>
            <button class="modal-close" (click)="closeModal()">×</button>
          </div>
          <form (ngSubmit)="saveUser()" class="modal-body">
            <div class="form-grid">
              <div class="form-group">
                <label>Email *</label>
                <input type="email" [(ngModel)]="formData.email" name="email" class="input" required [disabled]="!!editingUser">
              </div>
              <div class="form-group">
                <label>Role *</label>
                <select [(ngModel)]="formData.role" name="role" class="input" required>
                  <option value="VIEWER">Viewer</option>
                  <option value="LECTURER">Lecturer</option>
                  <option value="COORDINATOR">Coordinator</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </div>
              <div class="form-group">
                <label>First Name *</label>
                <input type="text" [(ngModel)]="formData.firstName" name="firstName" class="input" required>
              </div>
              <div class="form-group">
                <label>Last Name *</label>
                <input type="text" [(ngModel)]="formData.lastName" name="lastName" class="input" required>
              </div>
              <div class="form-group">
                <label>Phone</label>
                <input type="tel" [(ngModel)]="formData.phone" name="phone" class="input">
              </div>
              <div class="form-group">
                <label>Department</label>
                <input type="text" [(ngModel)]="formData.department" name="department" class="input">
              </div>
              <div class="form-group email-notice" *ngIf="!editingUser">
                <span class="notice-icon">📧</span>
                <span>A secure password will be auto-generated and emailed to the user.</span>
              </div>
              <div class="form-group" *ngIf="editingUser">
                <label class="checkbox-label">
                  <input type="checkbox" [(ngModel)]="formData.active" name="active">
                  Account Active
                </label>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="closeModal()">Cancel</button>
              <button type="submit" class="btn btn-primary" [disabled]="processing">
                {{ processing ? 'Saving...' : (editingUser ? 'Update User' : 'Create User') }}
              </button>
            </div>
          </form>
        </div>
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
                <tr [class.inactive]="!user.active">
                  <td>{{ user.firstName }} {{ user.lastName }}</td>
                  <td>{{ user.email }}</td>
                  <td>
                    <span class="badge" [class]="'badge-' + user.role.toLowerCase()">
                      {{ formatRole(user.role) }}
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
                    <button class="btn-icon" title="Edit" (click)="editUser(user)">✏️</button>
                    <button class="btn-icon" title="Reset Password" (click)="resetPassword(user)">🔑</button>
                    @if (!isSuperAdmin(user)) {
                      @if (user.active) {
                        <button class="btn-icon" title="Lock Account" (click)="lockUser(user)">🔒</button>
                      } @else {
                        <button class="btn-icon" title="Unlock Account" (click)="unlockUser(user)">🔓</button>
                      }
                      <button class="btn-icon btn-delete" title="Delete" (click)="deleteUser(user)">🗑️</button>
                    } @else {
                      <span class="protected-badge" title="SUPER_ADMIN cannot be locked or deleted">🛡️</span>
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
    .container { padding: 1.5rem; }
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .header h1 { margin: 0; font-size: 1.5rem; font-weight: 600; }
    .header-actions { display: flex; gap: 0.5rem; }
    
    .btn { display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border: none; border-radius: 8px; font-size: 0.875rem; font-weight: 500; cursor: pointer; transition: all 0.2s; }
    .btn svg { width: 16px; height: 16px; }
    .btn-primary { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; }
    .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3); }
    .btn-secondary { background: #6b7280; color: white; }
    .btn-danger { background: #dc2626; color: white; }
    .btn-sm { padding: 0.375rem 0.75rem; font-size: 0.75rem; }
    .btn:disabled { opacity: 0.5; cursor: not-allowed; }

    .alert { padding: 1rem; border-radius: 8px; margin-bottom: 1rem; display: flex; justify-content: space-between; align-items: center; }
    .alert-danger { background: #fee2e2; border: 1px solid #fecaca; color: #991b1b; }
    .alert-actions { display: flex; gap: 0.5rem; }

    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .modal { background: white; border-radius: 12px; width: 100%; max-width: 500px; max-height: 90vh; overflow-y: auto; }
    .modal-header { display: flex; justify-content: space-between; align-items: center; padding: 1rem 1.5rem; border-bottom: 1px solid #e5e7eb; }
    .modal-header h2 { margin: 0; font-size: 1.25rem; }
    .modal-close { background: none; border: none; font-size: 1.5rem; cursor: pointer; color: #6b7280; }
    .modal-body { padding: 1.5rem; }
    .modal-footer { display: flex; justify-content: flex-end; gap: 0.5rem; padding: 1rem 1.5rem; border-top: 1px solid #e5e7eb; }

    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .form-group { display: flex; flex-direction: column; gap: 0.25rem; }
    .form-group label { font-size: 0.875rem; font-weight: 500; color: #374151; }
    .form-group small { font-size: 0.75rem; color: #6b7280; }
    .input { padding: 0.5rem 0.75rem; border: 1px solid #d1d5db; border-radius: 6px; font-size: 0.875rem; }
    .input:focus { outline: none; border-color: #667eea; box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1); }
    .input:disabled { background: #f3f4f6; cursor: not-allowed; }
    .checkbox-label { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; }

    .table-container { background: white; border-radius: 12px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); overflow: hidden; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid #eee; }
    th { background: #f9fafb; font-weight: 600; font-size: 0.75rem; text-transform: uppercase; color: #6b7280; }
    tr.inactive { opacity: 0.6; }

    .badge { display: inline-block; padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600; }
    .badge-super_admin { background: #fef3c7; color: #92400e; }
    .badge-admin { background: #dbeafe; color: #1e40af; }
    .badge-coordinator { background: #d1fae5; color: #065f46; }
    .badge-lecturer { background: #e0e7ff; color: #3730a3; }
    .badge-viewer { background: #f3f4f6; color: #374151; }

    .status { display: inline-block; padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.75rem; background: #fee2e2; color: #dc2626; }
    .status.active { background: #d1fae5; color: #059669; }

    .actions { display: flex; gap: 0.25rem; }
    .btn-icon { background: none; border: none; padding: 0.25rem; cursor: pointer; font-size: 1rem; opacity: 0.7; transition: opacity 0.2s; }
    .btn-icon:hover { opacity: 1; }
    .btn-icon:disabled { opacity: 0.3; cursor: not-allowed; }
    .btn-delete:hover:not(:disabled) { filter: brightness(0.8); }
    .protected-badge { font-size: 1.25rem; color: #059669; cursor: help; }
    .email-notice { display: flex; align-items: center; gap: 0.5rem; padding: 0.75rem; background: #dbeafe; border-radius: 6px; color: #1e40af; font-size: 0.875rem; grid-column: 1 / -1; }
    .notice-icon { font-size: 1.25rem; }

    /* Import Section */
    .import-section { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 1rem; }
    .import-header { display: flex; justify-content: space-between; align-items: center; padding: 0.75rem 1rem; cursor: pointer; }
    .import-header h3 { margin: 0; font-size: 1rem; color: #1e293b; }
    .toggle-icon { color: #64748b; }
    .import-content { padding: 1rem; border-top: 1px solid #e2e8f0; }
    .import-desc { margin: 0 0 0.75rem; color: #475569; font-size: 0.875rem; }
    .import-format { background: #e0e7ff; padding: 0.75rem; border-radius: 6px; font-size: 0.8rem; color: #3730a3; margin-bottom: 1rem; }
    .import-format small { display: block; margin-top: 0.5rem; color: #dc2626; }
    .import-actions { display: flex; gap: 0.5rem; margin-bottom: 0.75rem; }
    .file-input-label { cursor: pointer; }
    .import-file-info { display: flex; align-items: center; gap: 1rem; padding: 0.75rem; background: #f0fdf4; border-radius: 6px; }
    .import-result { padding: 0.75rem; border-radius: 6px; margin-top: 0.75rem; }
    .import-result.success { background: #d1fae5; color: #059669; }
    .import-result.error { background: #fee2e2; color: #dc2626; }

    .loading, .error, .empty { text-align: center; padding: 2rem; color: #6b7280; }
    .error { color: #dc2626; }

    @media (prefers-color-scheme: dark) {
      .modal { background: #1f2937; color: white; }
      .modal-header { border-color: #374151; }
      .modal-footer { border-color: #374151; }
      .form-group label { color: #d1d5db; }
      .input { background: #374151; border-color: #4b5563; color: white; }
      .table-container { background: #1f2937; }
      th { background: #111827; color: #9ca3af; }
      td { border-color: #374151; }
    }
  `]
})
export class UsersComponent implements OnInit {
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  users: User[] = [];
  loading = true;
  error = '';
  processing = false;
  showModal = false;
  showDeleteAllConfirm = false;
  showImportSection = false;
  importFile: File | null = null;
  importing = false;
  importResult: { success: boolean; message: string } | null = null;
  editingUser: User | null = null;
  currentUser = this.authService.getCurrentUser();

  formData: UserForm = {
    email: '',
    firstName: '',
    lastName: '',
    phone: '',
    department: '',
    role: 'VIEWER',
    password: '',
    active: true
  };

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

  formatRole(role: string): string {
    return role.replace('_', ' ');
  }

  isCurrentUser(user: User): boolean {
    return this.currentUser?.id === user.id;
  }

  isSuperAdmin(user: User): boolean {
    return user.role === 'SUPER_ADMIN';
  }

  // Modal operations
  openCreateModal(): void {
    this.editingUser = null;
    this.formData = { email: '', firstName: '', lastName: '', phone: '', department: '', role: 'VIEWER', password: '', active: true };
    this.showModal = true;
  }

  editUser(user: User): void {
    this.editingUser = user;
    this.formData = {
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      phone: user.phone || '',
      department: user.department || '',
      role: user.role,
      password: '',
      active: user.active
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.editingUser = null;
  }

  saveUser(): void {
    this.processing = true;

    if (this.editingUser) {
      // Update existing user
      const updateData = {
        firstName: this.formData.firstName,
        lastName: this.formData.lastName,
        phone: this.formData.phone || null,
        department: this.formData.department || null,
        role: this.formData.role,
        active: this.formData.active
      };
      this.http.put(`http://localhost:8080/api/users/${this.editingUser.id}`, updateData).subscribe({
        next: () => {
          this.processing = false;
          this.closeModal();
          this.loadUsers();
        },
        error: (err) => {
          this.processing = false;
          alert('Failed to update user: ' + (err.error?.error || 'Unknown error'));
        }
      });
    } else {
      // Create new user
      const createData = {
        email: this.formData.email,
        firstName: this.formData.firstName,
        lastName: this.formData.lastName,
        phone: this.formData.phone || null,
        department: this.formData.department || null,
        role: this.formData.role,
        password: this.formData.password
      };
      this.http.post('http://localhost:8080/api/users', createData).subscribe({
        next: () => {
          this.processing = false;
          this.closeModal();
          this.loadUsers();
        },
        error: (err) => {
          this.processing = false;
          alert('Failed to create user: ' + (err.error?.error || 'Unknown error'));
        }
      });
    }
  }

  deleteUser(user: User): void {
    if (this.isCurrentUser(user)) {
      alert('You cannot delete your own account!');
      return;
    }
    if (confirm(`Delete user ${user.firstName} ${user.lastName}? This will deactivate their account.`)) {
      this.http.delete(`http://localhost:8080/api/users/${user.id}`).subscribe({
        next: () => this.loadUsers(),
        error: (err) => alert('Failed to delete user: ' + (err.error?.error || 'Unknown error'))
      });
    }
  }

  // Bulk operations
  confirmDeleteAll(): void {
    this.showDeleteAllConfirm = true;
  }

  deleteAllUsers(): void {
    this.processing = true;
    // Delete all users except current user
    const usersToDelete = this.users.filter(u => !this.isCurrentUser(u));
    let completed = 0;
    let failed = 0;

    if (usersToDelete.length === 0) {
      this.processing = false;
      this.showDeleteAllConfirm = false;
      return;
    }

    usersToDelete.forEach(user => {
      this.http.delete(`http://localhost:8080/api/users/${user.id}`).subscribe({
        next: () => {
          completed++;
          if (completed + failed === usersToDelete.length) {
            this.processing = false;
            this.showDeleteAllConfirm = false;
            this.loadUsers();
            if (failed > 0) alert(`Deleted ${completed} users. ${failed} failed.`);
          }
        },
        error: () => {
          failed++;
          if (completed + failed === usersToDelete.length) {
            this.processing = false;
            this.showDeleteAllConfirm = false;
            this.loadUsers();
            alert(`Deleted ${completed} users. ${failed} failed.`);
          }
        }
      });
    });
  }

  // User actions
  resetPassword(user: User): void {
    if (confirm(`Reset password for ${user.firstName} ${user.lastName}?`)) {
      this.http.post<{ temporaryPassword: string }>(`http://localhost:8080/api/users/${user.id}/reset-password`, {}).subscribe({
        next: () => {
          alert(`✅ Password reset successful!\n\nA new password has been emailed to ${user.email}`);
        },
        error: (err) => alert('Failed to reset password: ' + (err.error?.error || 'Unknown error'))
      });
    }
  }

  lockUser(user: User): void {
    if (confirm(`Lock account for ${user.firstName} ${user.lastName}?`)) {
      this.http.post(`http://localhost:8080/api/users/${user.id}/lock?minutes=1440`, {}).subscribe({
        next: () => this.loadUsers(),
        error: (err) => alert('Failed to lock user: ' + (err.error?.error || 'Unknown error'))
      });
    }
  }

  unlockUser(user: User): void {
    this.http.post(`http://localhost:8080/api/users/${user.id}/unlock`, {}).subscribe({
      next: () => this.loadUsers(),
      error: (err) => alert('Failed to unlock user: ' + (err.error?.error || 'Unknown error'))
    });
  }

  // Bulk Import Methods
  downloadTemplate(): void {
    const template = 'email,first_name,last_name,role,department,phone\njohn@example.com,John,Doe,LECTURER,Computer Science,+1234567890';
    const blob = new Blob([template], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'users_template.csv';
    a.click();
    window.URL.revokeObjectURL(url);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.importFile = input.files[0];
      this.importResult = null;
    }
  }

  importUsers(): void {
    if (!this.importFile) return;

    this.importing = true;
    this.importResult = null;

    const formData = new FormData();
    formData.append('file', this.importFile);

    this.http.post<any>('http://localhost:8080/api/v1/bulk/users/import', formData).subscribe({
      next: (response) => {
        this.importing = false;
        this.importFile = null;
        this.importResult = {
          success: true,
          message: `✅ Successfully imported ${response.created} users. Welcome emails have been sent.`
        };
        this.loadUsers();
      },
      error: (err) => {
        this.importing = false;
        const errors = err.error?.errors || [];
        const message = errors.length > 0
          ? `❌ Import failed:\n${errors.slice(0, 5).join('\n')}${errors.length > 5 ? `\n...and ${errors.length - 5} more errors` : ''}`
          : `❌ Import failed: ${err.error?.message || 'Unknown error'}`;
        this.importResult = { success: false, message };
      }
    });
  }
}
