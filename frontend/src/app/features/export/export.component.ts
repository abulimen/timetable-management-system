import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ApiService, StudentGroup } from '../../core/services/api.service';

@Component({
    selector: 'app-export',
    standalone: true,
    imports: [CommonModule, FormsModule],
    template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Export Timetable</h1>
          <p class="text-sm text-secondary-500 mt-1">
            {{ archiveCode ? ('Download archived timetable: ' + archiveCode) : 'Download timetables as PDF or Excel files' }}
          </p>
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
                <input type="radio" name="scope" value="GROUPS" [(ngModel)]="scope" class="w-4 h-4 text-primary-600">
                <span class="ml-2 text-sm">👥 Select Specific Groups</span>
              </label>
            </div>

            <!-- Group Selection -->
            <div *ngIf="scope === 'GROUPS'" class="border border-secondary-300 dark:border-secondary-600 rounded-lg p-4">
              <p class="text-sm text-secondary-500 mb-3">Select child student groups:</p>
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
              <strong>{{ selectedIds.length }}</strong> groups selected
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
            <span *ngIf="scope === 'ALL'">All child student groups</span>
            <span *ngIf="scope === 'GROUPS'">{{ selectedIds.length }} specific child group(s)</span>
          </p>
          <p><strong>Excel Format:</strong> One sheet per group with weekly timetable grid</p>
          <p><strong>PDF Format:</strong> Landscape A4, one page per group</p>
        </div>
      </div>
    </div>
  `
})
export class ExportComponent implements OnInit {
    private api = inject(ApiService);
    private route = inject(ActivatedRoute);

    scope: 'ALL' | 'GROUPS' = 'ALL';
    exportTitle = '';
    selectedIds: number[] = [];
    studentGroups: StudentGroup[] = [];
    exporting = false;
    archiveCode: string | null = null;

    ngOnInit() {
        this.archiveCode = this.route.snapshot.queryParamMap.get('archiveCode');
        if (this.archiveCode) {
            this.exportTitle = `Archived Timetable - ${this.archiveCode}`;
        }
        this.loadData();
    }

    loadData() {
        const request$ = this.archiveCode
            ? this.api.getArchivedSemesterGroups(this.archiveCode)
            : this.api.getStudentGroups();
        request$.subscribe({
            next: (groups) => this.studentGroups = groups
                .filter(group => group.parentGroupId !== null)
                .sort((a, b) => a.name.localeCompare(b.name))
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

    private export(format: 'excel' | 'pdf', mimeType: string, extension: string) {
        this.exporting = true;

        const payload = {
            groupIds: this.scope === 'ALL' ? [] : this.selectedIds,
            title: this.exportTitle || (this.archiveCode ? 'Archived Timetable Export' : 'Timetable Export')
        };

        const request$ = this.archiveCode
            ? this.api.exportArchivedTimetable(format, this.archiveCode, payload)
            : this.api.exportTimetable(format, payload);
        request$.subscribe({
            next: (blob) => {
                this.exporting = false;
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                const prefix = this.archiveCode ? `archived_timetable_${this.archiveCode}` : `timetable_${new Date().toISOString().slice(0, 10)}`;
                a.download = `${prefix}.${extension}`;
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
