import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService, StudentGroup } from '../../core/services/api.service';

interface Department {
    id: number;
    name: string;
    size: number;
    childCount: number;
    isParent: boolean;
}

@Component({
    selector: 'app-export',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Export Timetable</h1>
          <p class="text-sm text-secondary-500 mt-1">Download timetables as PDF or Excel files</p>
        </div>
      </div>

      <div class="card p-6">
        <h2 class="text-lg font-semibold mb-4">Export Options</h2>
        
        <form class="space-y-6">
          <!-- Title -->
          <div>
            <label class="label">Export Title</label>
            <input type="text" [(ngModel)]="exportTitle" name="title" class="input" 
                   placeholder="e.g., Computer Science Timetable - Semester 1">
          </div>

          <!-- Scope Selection -->
          <div>
            <label class="label mb-2">Export Scope</label>
            <div class="flex gap-4 mb-4">
              <label class="inline-flex items-center cursor-pointer">
                <input type="radio" name="scope" value="ALL" [(ngModel)]="scope" class="w-4 h-4 text-primary-600">
                <span class="ml-2 text-sm">🏫 Whole School (All Groups)</span>
              </label>
              <label class="inline-flex items-center cursor-pointer">
                <input type="radio" name="scope" value="DEPARTMENTS" [(ngModel)]="scope" class="w-4 h-4 text-primary-600">
                <span class="ml-2 text-sm">🏢 Select Departments</span>
              </label>
              <label class="inline-flex items-center cursor-pointer">
                <input type="radio" name="scope" value="GROUPS" [(ngModel)]="scope" class="w-4 h-4 text-primary-600">
                <span class="ml-2 text-sm">👥 Select Specific Groups</span>
              </label>
            </div>

            <!-- Department Selection -->
            <div *ngIf="scope === 'DEPARTMENTS'" class="border border-secondary-300 dark:border-secondary-600 rounded-lg p-4">
              <p class="text-sm text-secondary-500 mb-3">Select departments (includes all child groups):</p>
              <div class="flex flex-wrap gap-2 max-h-40 overflow-y-auto">
                <label *ngFor="let d of departments" 
                       class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                       [class.bg-primary-100]="isSelected(d.id)"
                       [class.border-primary-500]="isSelected(d.id)"
                       [class.dark:bg-primary-900]="isSelected(d.id)"
                       [class.border-secondary-300]="!isSelected(d.id)"
                       [class.dark:border-secondary-600]="!isSelected(d.id)">
                  <input type="checkbox" [checked]="isSelected(d.id)" (change)="toggleSelection(d.id)" class="mr-2">
                  <span class="text-sm">{{ d.name }} <span class="text-secondary-400">({{ d.childCount }} groups)</span></span>
                </label>
              </div>
            </div>

            <!-- Group Selection -->
            <div *ngIf="scope === 'GROUPS'" class="border border-secondary-300 dark:border-secondary-600 rounded-lg p-4">
              <p class="text-sm text-secondary-500 mb-3">Select specific student groups:</p>
              <div class="flex flex-wrap gap-2 max-h-48 overflow-y-auto">
                <label *ngFor="let g of studentGroups" 
                       class="inline-flex items-center px-3 py-2 rounded-lg border cursor-pointer transition-all"
                       [class.bg-primary-100]="isSelected(g.id)"
                       [class.border-primary-500]="isSelected(g.id)"
                       [class.dark:bg-primary-900]="isSelected(g.id)"
                       [class.border-secondary-300]="!isSelected(g.id)"
                       [class.dark:border-secondary-600]="!isSelected(g.id)">
                  <input type="checkbox" [checked]="isSelected(g.id)" (change)="toggleSelection(g.id)" class="mr-2">
                  <span class="text-sm">{{ g.name }} <span class="text-secondary-400">({{ g.size }})</span></span>
                </label>
              </div>
            </div>
          </div>

          <!-- Selection Summary -->
          <div *ngIf="scope !== 'ALL' && selectedIds.length > 0" 
               class="bg-primary-50 dark:bg-primary-900/30 p-4 rounded-lg">
            <p class="text-sm text-primary-700 dark:text-primary-300">
              <strong>{{ selectedIds.length }}</strong> {{ scope === 'DEPARTMENTS' ? 'departments' : 'groups' }} selected
            </p>
          </div>

          <!-- Format Selection & Export Buttons -->
          <div class="flex flex-col sm:flex-row gap-4 pt-4 border-t border-secondary-200 dark:border-secondary-700">
            <button type="button" (click)="exportExcel()" [disabled]="exporting"
                    class="btn btn-primary flex items-center justify-center gap-2">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                      d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
              </svg>
              {{ exporting ? 'Generating...' : 'Export as Excel (.xlsx)' }}
            </button>
            
            <button type="button" (click)="exportPdf()" [disabled]="exporting"
                    class="btn btn-secondary flex items-center justify-center gap-2">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                      d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"/>
              </svg>
              {{ exporting ? 'Generating...' : 'Export as PDF' }}
            </button>
          </div>
        </form>
      </div>

      <!-- Preview info -->
      <div class="card p-6 bg-secondary-50 dark:bg-secondary-800/50">
        <h3 class="font-semibold mb-2">📋 Export Preview</h3>
        <div class="text-sm text-secondary-600 dark:text-secondary-400 space-y-1">
          <p><strong>Title:</strong> {{ exportTitle || 'Timetable Export' }}</p>
          <p><strong>Scope:</strong> 
            <span *ngIf="scope === 'ALL'">All student groups</span>
            <span *ngIf="scope === 'DEPARTMENTS'">{{ selectedIds.length }} department(s) + children</span>
            <span *ngIf="scope === 'GROUPS'">{{ selectedIds.length }} specific group(s)</span>
          </p>
          <p><strong>Excel Format:</strong> One sheet per group with weekly timetable grid</p>
          <p><strong>PDF Format:</strong> Landscape A4, one page per group</p>
        </div>
      </div>
    </div>
  `
})
export class ExportComponent implements OnInit {
    private http = inject(HttpClient);
    private api = inject(ApiService);

    scope: 'ALL' | 'DEPARTMENTS' | 'GROUPS' = 'ALL';
    exportTitle = '';
    selectedIds: number[] = [];
    departments: Department[] = [];
    studentGroups: StudentGroup[] = [];
    exporting = false;

    ngOnInit() {
        this.loadData();
    }

    loadData() {
        this.http.get<Department[]>('http://localhost:8080/api/v1/export/departments').subscribe({
            next: (deps) => this.departments = deps.filter(d => d.isParent || d.childCount > 0)
        });
        this.api.getStudentGroups().subscribe({
            next: (groups) => this.studentGroups = groups
        });
    }

    isSelected(id: number): boolean {
        return this.selectedIds.includes(id);
    }

    toggleSelection(id: number) {
        const index = this.selectedIds.indexOf(id);
        if (index > -1) {
            this.selectedIds.splice(index, 1);
        } else {
            this.selectedIds.push(id);
        }
    }

    exportExcel() {
        this.export('excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'xlsx');
    }

    exportPdf() {
        this.export('pdf', 'application/pdf', 'pdf');
    }

    private export(format: string, mimeType: string, extension: string) {
        this.exporting = true;

        const payload = {
            groupIds: this.scope === 'ALL' ? [] : this.selectedIds,
            title: this.exportTitle || 'Timetable Export'
        };

        this.http.post(`http://localhost:8080/api/v1/export/${format}`, payload, {
            responseType: 'blob'
        }).subscribe({
            next: (blob) => {
                this.exporting = false;
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `timetable_${new Date().toISOString().slice(0, 10)}.${extension}`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
            },
            error: (err) => {
                this.exporting = false;
                alert('Export failed: ' + (err.message || 'Unknown error'));
            }
        });
    }
}
