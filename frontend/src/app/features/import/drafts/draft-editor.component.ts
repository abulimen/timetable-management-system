import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { InlineEditorComponent } from '../inline-editor/inline-editor.component';
import { EntityType, getCSVHeaders } from '../inline-editor/column-definitions';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'app-draft-editor',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule, InlineEditorComponent],
    template: `
    <div *ngIf="loading" class="flex justify-center items-center min-h-screen bg-gray-50">
      <div class="flex flex-col items-center gap-4">
        <div class="animate-spin rounded-full h-16 w-16 border-b-2 border-primary-600"></div>
        <span class="text-gray-600">Loading draft...</span>
      </div>
    </div>

    <div *ngIf="!loading && error" class="flex justify-center items-center min-h-screen bg-gray-50">
      <div class="bg-red-50 border border-red-200 p-6 rounded-lg max-w-md text-center">
        <svg class="w-12 h-12 text-red-500 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
        </svg>
        <h2 class="text-lg font-semibold text-red-700 mb-2">Error Loading Draft</h2>
        <p class="text-red-600">{{ error }}</p>
        <button (click)="goBack()" class="mt-4 btn btn-secondary">Go Back</button>
      </div>
    </div>

    <!-- Use the InlineEditorComponent when draft is loaded -->
    <app-inline-editor
      *ngIf="!loading && !error && draft"
      [entityType]="entityType"
      [initialData]="parsedData"
      (closeModal)="goBack()"
      (saveData)="handleSave($event)"
      (validationChange)="onValidationChange($event)"
    ></app-inline-editor>
  `
})
export class DraftEditorComponent implements OnInit {
    draft: any = null;
    loading = true;
    error: string | null = null;
    entityType: EntityType = 'courses';
    parsedData: Record<string, string>[] = [];
    isValid = false; // Track validation status

    constructor(
        private apiService: ApiService,
        private route: ActivatedRoute,
        private router: Router
    ) { }

    ngOnInit() {
        const id = this.route.snapshot.paramMap.get('id');
        if (id) {
            this.loadDraft(Number(id));
        } else {
            this.error = "No Draft ID provided";
            this.loading = false;
        }
    }

    /**
     * Handle validation changes from InlineEditor
     */
    onValidationChange(status: { valid: boolean; errorCount: number }) {
        this.isValid = status.valid;
    }

    loadDraft(id: number) {
        this.loading = true;
        this.apiService.getDraft(id).subscribe({
            next: (data) => {
                this.draft = data;
                // Determine entity type from draft
                this.entityType = (data.entityType?.toLowerCase() as EntityType) || 'courses';
                // Parse CSV content into structured data
                this.parsedData = this.parseCsvToRecords(data.content, this.entityType);
                this.loading = false;
            },
            error: (err) => {
                this.error = "Failed to load draft: " + (err.error?.message || err.message);
                this.loading = false;
            }
        });
    }

    /**
     * Parse CSV string into array of record objects
     */
    parseCsvToRecords(content: string, entityType: EntityType): Record<string, string>[] {
        const lines = content.split(/\r?\n/).filter(line => line.trim().length > 0);
        if (lines.length === 0) return [];

        // Get expected headers for this entity type
        const expectedHeaders = getCSVHeaders(entityType);

        // Parse the CSV header line
        const csvHeaders = this.parseCSVLine(lines[0]);

        // Parse data rows
        const records: Record<string, string>[] = [];
        for (let i = 1; i < lines.length; i++) {
            const values = this.parseCSVLine(lines[i]);
            const record: Record<string, string> = {};

            // Map CSV headers to expected headers
            csvHeaders.forEach((csvHeader, idx) => {
                // Try to find matching expected header (case-insensitive, underscore/space normalization)
                const normalizedCsvHeader = csvHeader.toLowerCase().replace(/[\s_-]/g, '');
                const matchedHeader = expectedHeaders.find(eh =>
                    eh.toLowerCase().replace(/[\s_-]/g, '') === normalizedCsvHeader
                );

                if (matchedHeader) {
                    record[matchedHeader] = values[idx] || '';
                } else {
                    // Use original CSV header if no match found
                    record[csvHeader] = values[idx] || '';
                }
            });

            // Ensure all expected headers have at least empty values
            expectedHeaders.forEach(h => {
                if (!(h in record)) {
                    record[h] = '';
                }
            });

            records.push(record);
        }

        return records;
    }

    /**
     * Parse a single CSV line handling quoted fields
     */
    parseCSVLine(line: string): string[] {
        const result: string[] = [];
        let current = '';
        let inQuotes = false;

        for (let i = 0; i < line.length; i++) {
            const char = line[i];

            if (char === '"') {
                if (inQuotes && line[i + 1] === '"') {
                    // Escaped quote
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

    /**
     * Convert records back to CSV string
     */
    recordsToCsv(records: Record<string, string>[], entityType: EntityType): string {
        if (records.length === 0) return '';

        const headers = getCSVHeaders(entityType);
        const headerLine = headers.join(',');

        const dataLines = records.map(record => {
            return headers.map(h => {
                const value = record[h] || '';
                // Quote if contains comma, newline, or quote
                if (value.includes(',') || value.includes('\n') || value.includes('"')) {
                    return '"' + value.replace(/"/g, '""') + '"';
                }
                return value;
            }).join(',');
        });

        return [headerLine, ...dataLines].join('\n');
    }

    /**
     * Handle save from InlineEditor - save draft and optionally submit
     */
    handleSave(data: Record<string, string>[]) {
        if (!this.draft) return;

        const csv = this.recordsToCsv(data, this.entityType);

        this.apiService.updateDraft(this.draft.id, csv).subscribe({
            next: () => {
                // If valid, ask to submit. If invalid, just save and close.
                if (this.isValid) {
                    if (confirm('Draft saved! Do you want to submit it for approval?')) {
                        this.submitDraft();
                    } else {
                        this.goBack();
                    }
                } else {
                    // Notify user about errors
                    alert('Draft saved. Note: It contains errors so it cannot be submitted yet.');
                    this.goBack();
                }
            },
            error: (err) => {
                alert("Failed to save draft: " + (err.error?.message || err.message));
            }
        });
    }

    submitDraft() {
        if (!this.draft) return;

        this.apiService.submitDraft(this.draft.id).subscribe({
            next: () => {
                this.router.navigate(['/import']);
            },
            error: (err) => {
                alert("Submission failed: " + (err.error?.message || err.message));
            }
        });
    }

    goBack() {
        this.router.navigate(['/import/drafts']);
    }
}
