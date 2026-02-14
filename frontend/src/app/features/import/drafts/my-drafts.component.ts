import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService, ImportBatch } from '../../../core/services/api.service';
import { FormsModule } from '@angular/forms';
import { EntityType, getCSVHeaders, getColumnDefinitions } from '../inline-editor/column-definitions';
import { InlineEditorComponent } from '../inline-editor/inline-editor.component';
import { ReferenceDataService } from '../services/reference-data.service';

interface DraftWithValidation extends ImportBatch {
    errorCount: number;
    mismatchCount: number;
    rowCount: number;
    validationStatus: 'loading' | 'valid' | 'errors' | 'unknown';
}

@Component({
    selector: 'app-my-drafts',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule, InlineEditorComponent],
    template: `
    <div class="container mx-auto px-4 py-8">
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-3xl font-bold text-gray-800 dark:text-gray-100">Data Imports</h1>
        <button (click)="showCreateSection = !showCreateSection" 
                class="bg-primary-600 hover:bg-primary-700 text-white font-bold py-2 px-4 rounded transition-colors duration-200">
            {{ showCreateSection ? '✕ Cancel' : '+ Create New Draft' }}
        </button>
      </div>

      <!-- Create New Draft Section -->
      <div *ngIf="showCreateSection" class="bg-white dark:bg-secondary-800 rounded-lg shadow-lg p-6 mb-6 border border-primary-200 dark:border-primary-800">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-100 mb-4">Create New Draft</h2>
        
        <!-- Entity Type Selector -->
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Entity Type</label>
          <select [(ngModel)]="selectedEntityType" 
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-secondary-700 text-gray-900 dark:text-gray-100">
            <option value="student-groups">Student Groups</option>
            <option value="users">Users</option>
            <option value="courses">Courses</option>
            <option value="rooms">Rooms</option>
            <option value="zones">Zones</option>
            <option value="features">Features</option>
          </select>
        </div>

        <!-- Upload or Editor Choice -->
        <div class="grid grid-cols-2 gap-4">
          <button (click)="openFileUpload()" 
                  class="flex flex-col items-center justify-center p-6 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg hover:border-primary-500 dark:hover:border-primary-400 transition-colors">
            <svg class="w-12 h-12 text-gray-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"/>
            </svg>
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">Upload CSV File</span>
          </button>
          
          <button (click)="openInlineEditor()" 
                  class="flex flex-col items-center justify-center p-6 border-2 border-dashed border-gray-300 dark:border-gray-600 rounded-lg hover:border-primary-500 dark:hover:border-primary-400 transition-colors">
            <svg class="w-12 h-12 text-gray-400 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
            </svg>
            <span class="text-sm font-medium text-gray-700 dark:text-gray-300">Use Inline Editor</span>
          </button>
        </div>

        <!-- Hidden File Input -->
        <input #fileInput type="file" accept=".csv" (change)="handleFileUpload($event)" class="hidden">
      </div>

      <!-- Inline Editor Modal -->
      <app-inline-editor
        *ngIf="showEditor"
        [entityType]="selectedEntityType"
        [initialData]="[]"
        (closeModal)="closeEditor()"
        (saveData)="saveNewDraft($event)"
      ></app-inline-editor>

      <div *ngIf="loading" class="flex justify-center py-12">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>

      <div *ngIf="!loading && drafts.length === 0 && !showCreateSection" class="bg-white dark:bg-secondary-800 rounded-lg shadow p-12 text-center">
        <svg class="w-16 h-16 text-gray-400 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
        </svg>
        <h3 class="text-xl font-medium text-gray-900 dark:text-gray-100 mb-2">No drafts found</h3>
        <p class="text-gray-500 dark:text-gray-400">Click "Create New Draft" to get started.</p>
      </div>

      <div *ngIf="!loading && drafts.length > 0" class="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <div *ngFor="let draft of drafts" class="bg-white dark:bg-secondary-800 rounded-lg shadow hover:shadow-lg transition-shadow duration-200 overflow-hidden border border-gray-100 dark:border-secondary-700">
           <div class="p-6">
                <div class="flex justify-between items-start mb-3">
                    <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-300">
                        {{ draft.entityType }}
                    </span>
                    <span class="text-xs text-gray-500 dark:text-gray-400">
                        {{ draft.createdAt | date:'medium' }}
                    </span>
                </div>
                
                <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100 mb-2 truncate" title="{{ draft.originalFilename }}">
                    {{ draft.originalFilename }}
                </h3>
                
                <!-- Validation Status -->
                <div class="mb-4">
                    <div *ngIf="draft.validationStatus === 'loading'" class="flex items-center gap-2 text-sm text-gray-500">
                        <div class="animate-spin rounded-full h-4 w-4 border-2 border-gray-300 border-t-primary-600"></div>
                        <span>Checking...</span>
                    </div>
                    
                    <div *ngIf="draft.validationStatus === 'valid'" class="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                        </svg>
                        <span>{{ draft.rowCount }} rows • Ready to submit</span>
                    </div>
                    
                    <div *ngIf="draft.validationStatus === 'errors'" class="text-sm">
                        <div class="flex items-center gap-2 text-red-600 dark:text-red-400 mb-1">
                            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                            </svg>
                            <span>{{ draft.rowCount }} rows</span>
                        </div>
                        <div class="flex flex-wrap gap-2 text-xs">
                            <span *ngIf="draft.mismatchCount > 0" class="px-2 py-0.5 bg-orange-100 text-orange-700 dark:bg-orange-900/50 dark:text-orange-300 rounded-full">
                                {{ draft.mismatchCount }} mismatches
                            </span>
                            <span *ngIf="draft.errorCount > 0" class="px-2 py-0.5 bg-red-100 text-red-700 dark:bg-red-900/50 dark:text-red-300 rounded-full">
                                {{ draft.errorCount }} errors
                            </span>
                        </div>
                    </div>
                    
                    <div *ngIf="draft.validationStatus === 'unknown'" class="text-sm text-gray-500">
                        <span>{{ draft.rowCount || '?' }} rows • Click Edit to validate</span>
                    </div>
                </div>

                <div class="flex flex-col gap-2">
                    <!-- Primary Actions Row -->
                    <div class="flex gap-2">
                        <a [routerLink]="['/import/draft', draft.id]" class="flex-1 px-3 py-2 bg-primary-50 text-primary-700 rounded hover:bg-primary-100 dark:bg-primary-900/30 dark:text-primary-300 dark:hover:bg-primary-900/50 transition-colors text-center text-sm font-medium">
                            ✏️ Edit
                        </a>
                        <button 
                            (click)="submitForApproval(draft)" 
                            [disabled]="draft.validationStatus !== 'valid' || submitting"
                            class="flex-1 px-3 py-2 rounded text-sm font-medium transition-colors"
                            [ngClass]="{
                                'bg-green-600 text-white hover:bg-green-700': draft.validationStatus === 'valid',
                                'bg-gray-200 text-gray-400 cursor-not-allowed dark:bg-gray-700 dark:text-gray-500': draft.validationStatus !== 'valid'
                            }">
                            {{ submitting && submittingId === draft.id ? 'Submitting...' : '📤 Submit' }}
                        </button>
                    </div>
                    <!-- Delete Button -->
                    <button (click)="deleteDraft(draft)" class="w-full px-3 py-2 bg-red-50 text-red-700 rounded hover:bg-red-100 dark:bg-red-900/30 dark:text-red-300 dark:hover:bg-red-900/50 transition-colors text-sm font-medium">
                        🗑️ Delete
                    </button>
                </div>
           </div>
        </div>
      </div>
    </div>
  `
})
export class MyDraftsComponent implements OnInit {
    drafts: DraftWithValidation[] = [];
    loading = true;
    submitting = false;
    submittingId: number | null = null;

    // Create Draft functionality
    showCreateSection = false;
    showEditor = false;
    selectedEntityType: EntityType = 'student-groups';

    @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

    constructor(
        private apiService: ApiService,
        private refDataService: ReferenceDataService
    ) { }

    ngOnInit() {
        this.loadDrafts();
    }

    openFileUpload() {
        this.fileInput.nativeElement.click();
    }

    handleFileUpload(event: Event) {
        const input = event.target as HTMLInputElement;
        if (input.files && input.files.length > 0) {
            const file = input.files[0];

            // Create draft from file
            this.loading = true;
            this.apiService.createDraft(this.selectedEntityType, file).subscribe({
                next: (response: any) => {
                    this.showCreateSection = false;
                    this.loadDrafts(); // Reload to see new draft
                    alert('Draft created successfully!');
                },
                error: (err) => {
                    console.error('Failed to create draft:', err);
                    alert('Failed to create draft: ' + (err.error?.message || err.message));
                    this.loading = false;
                }
            });

            // Reset input
            input.value = '';
        }
    }

    openInlineEditor() {
        this.showEditor = true;
    }

    closeEditor() {
        this.showEditor = false;
    }

    saveNewDraft(data: Record<string, string>[]) {
        if (data.length === 0) {
            this.closeEditor();
            return;
        }

        // Convert data to CSV
        const csvBlob = this.editorDataToCSVBlob(data, this.selectedEntityType);
        const file = new File([csvBlob], `new_${this.selectedEntityType}_draft.csv`, { type: 'text/csv' });

        this.loading = true;
        this.apiService.createDraft(this.selectedEntityType, file).subscribe({
            next: (response: any) => {
                this.showEditor = false;
                this.showCreateSection = false;
                this.loadDrafts();
                alert('Draft created successfully!');
            },
            error: (err) => {
                console.error('Failed to create draft:', err);
                alert('Failed to create draft: ' + (err.error?.message || err.message));
                this.loading = false;
            }
        });
    }

    private editorDataToCSVBlob(data: Record<string, string>[], entityType: string): Blob {
        if (data.length === 0) {
            return new Blob([''], { type: 'text/csv' });
        }

        // Use consistent headers
        const headers = getCSVHeaders(entityType as EntityType);

        // Build CSV content
        const csvRows: string[] = [
            headers.join(','), // Header row
            ...data.map(row =>
                headers.map(h => {
                    const val = row[h] || '';
                    if (val.includes(',') || val.includes('"') || val.includes('\n')) {
                        return `"${val.replace(/"/g, '""')}"`;
                    }
                    return val;
                }).join(',')
            )
        ];

        const csvContent = csvRows.join('\n');
        return new Blob([csvContent], { type: 'text/csv' });
    }

    async loadDrafts() {
        this.loading = true;

        // Load reference data first
        try {
            await this.refDataService.loadAll();
        } catch (e) {
            console.error('Failed to load reference data:', e);
        }

        this.apiService.getMyDrafts().subscribe({
            next: (data) => {
                this.drafts = data.map(d => ({
                    ...d,
                    errorCount: 0,
                    mismatchCount: 0,
                    rowCount: 0,
                    validationStatus: 'loading' as const
                }));
                this.loading = false;

                // Validate each draft
                this.drafts.forEach(draft => this.validateDraft(draft));
            },
            error: (err) => {
                console.error("Failed to load drafts", err);
                this.loading = false;
            }
        });
    }

    validateDraft(draft: DraftWithValidation) {
        // Load the draft content and validate it
        this.apiService.getDraft(draft.id).subscribe({
            next: (fullDraft) => {
                const content = fullDraft.content || '';
                const entityType = (draft.entityType?.toLowerCase() || 'courses') as EntityType;

                // Parse CSV and validate
                const { rowCount, errorCount, mismatchCount } = this.validateCsvContent(content, entityType);

                draft.rowCount = rowCount;
                draft.errorCount = errorCount;
                draft.mismatchCount = mismatchCount;
                draft.validationStatus = (errorCount === 0 && mismatchCount === 0) ? 'valid' : 'errors';
            },
            error: () => {
                draft.validationStatus = 'unknown';
            }
        });
    }

    validateCsvContent(content: string, entityType: EntityType): { rowCount: number; errorCount: number; mismatchCount: number } {
        const lines = content.split(/\r?\n/).filter(line => line.trim().length > 0);
        if (lines.length <= 1) {
            return { rowCount: 0, errorCount: 0, mismatchCount: 0 };
        }

        const rowCount = lines.length - 1; // Exclude header
        const columnDefs = getColumnDefinitions(entityType, this.refDataService);
        const expectedHeaders = getCSVHeaders(entityType);

        // Parse header
        const csvHeaders = this.parseCSVLine(lines[0]);

        // Map CSV column index to Field Name
        const colIndexToField = new Map<number, string>();
        csvHeaders.forEach((header, idx) => {
            const normalized = header.toLowerCase().replace(/[\s_-]/g, '');
            const matched = expectedHeaders.find(eh =>
                eh.toLowerCase().replace(/[\s_-]/g, '') === normalized
            );
            if (matched) colIndexToField.set(idx, matched);
        });

        // 1. Parse all rows into objects to support cross-row validation
        const allRows: any[] = [];
        for (let i = 1; i < lines.length; i++) {
            const values = this.parseCSVLine(lines[i]);
            const rowData: any = {};
            colIndexToField.forEach((field, idx) => {
                rowData[field] = values[idx] || '';
            });
            allRows.push(rowData);
        }

        let errorCount = 0;
        let mismatchCount = 0;

        // 2. Validate using full logic
        allRows.forEach(rowData => {
            colIndexToField.forEach((field, _) => {
                const colDef = columnDefs.find(cd => cd.field === field);
                if (!colDef) return;

                const value = rowData[field];

                // Run Validator
                // This now supports complex logic (e.g. is_parent checks, cross-row checks)
                if (colDef.validator) {
                    const result = colDef.validator(value, rowData, allRows);
                    if (!result.valid) {
                        errorCount++;
                    }
                }

                // Reference Check (for mismatch counts)
                if (colDef.referenceCheck && colDef.autocompleteValues && value && String(value).trim()) {
                    const sourceValues = colDef.autocompleteValues();
                    const valuesToCheck = String(value).includes('|')
                        ? String(value).split('|').map(v => v.trim()).filter(v => v)
                        : [String(value).trim()];

                    valuesToCheck.forEach(v => {
                        const matched = sourceValues.some(sv =>
                            sv.toLowerCase() === v.toLowerCase()
                        );
                        if (!matched) {
                            mismatchCount++;
                        }
                    });
                }
            });
        });

        return { rowCount, errorCount, mismatchCount };
    }

    parseCSVLine(line: string): string[] {
        const result: string[] = [];
        let current = '';
        let inQuotes = false;

        for (let i = 0; i < line.length; i++) {
            const char = line[i];

            if (char === '"') {
                if (inQuotes && line[i + 1] === '"') {
                    current += '"';
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (char === ',' && !inQuotes) {
                result.push(current.trim());
                current = '';
            } else {
                current += char;
            }
        }
        result.push(current.trim());

        return result;
    }

    submitForApproval(draft: DraftWithValidation) {
        if (draft.validationStatus !== 'valid') {
            alert('Cannot submit: Please fix all errors first by clicking Edit.');
            return;
        }

        if (!confirm(`Submit "${draft.originalFilename}" for approval?`)) {
            return;
        }

        this.submitting = true;
        this.submittingId = draft.id;

        this.apiService.submitDraft(draft.id).subscribe({
            next: () => {
                // Remove from list after successful submission
                this.drafts = this.drafts.filter(d => d.id !== draft.id);
                this.submitting = false;
                this.submittingId = null;
                alert('Draft submitted for approval!');
            },
            error: (err) => {
                alert('Failed to submit: ' + (err.error?.message || err.message));
                this.submitting = false;
                this.submittingId = null;
            }
        });
    }

    deleteDraft(draft: DraftWithValidation) {
        if (confirm(`Are you sure you want to delete draft "${draft.originalFilename}"?`)) {
            this.apiService.deleteDraft(draft.id).subscribe({
                next: () => {
                    this.drafts = this.drafts.filter(d => d.id !== draft.id);
                },
                error: (err) => alert("Failed to delete draft: " + err.message)
            });
        }
    }
}
