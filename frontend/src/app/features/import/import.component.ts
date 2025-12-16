import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface ImportStep {
  id: number;
  entity: string;
  label: string;
  description: string;
  format: string;
  example: string;
  file: File | null;
  status: 'pending' | 'ready' | 'importing' | 'success' | 'error' | 'skipped';
  result: any;
  required: boolean;
  dependencies: string[];
}

@Component({
  selector: 'app-import',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Bulk Data Import</h1>
          <p class="text-secondary-500 mt-1">Import data in the correct order to maintain referential integrity</p>
        </div>
        <div class="flex gap-2">
          <button (click)="downloadAllTemplates()" class="btn btn-secondary">📥 Download All Templates</button>
          <button (click)="resetAll()" class="btn btn-ghost" [disabled]="isImporting">Reset</button>
        </div>
      </div>

      <!-- Progress Overview -->
      <div class="card p-4">
        <div class="flex items-center gap-4">
          <div class="flex-1">
            <div class="flex gap-1">
              <div *ngFor="let step of steps" 
                   class="flex-1 h-2 rounded-full transition-all"
                   [ngClass]="{
                     'bg-secondary-200 dark:bg-secondary-700': step.status === 'pending',
                     'bg-blue-400': step.status === 'ready',
                     'bg-yellow-400 animate-pulse': step.status === 'importing',
                     'bg-green-500': step.status === 'success',
                     'bg-red-500': step.status === 'error',
                     'bg-secondary-300': step.status === 'skipped'
                   }">
              </div>
            </div>
          </div>
          <span class="text-sm text-secondary-500">{{ getProgressText() }}</span>
        </div>
      </div>

      <!-- Cross-Validation Results -->
      <div *ngIf="validationResult" class="card p-4" 
           [ngClass]="{'bg-green-500/10 border-green-500': validationResult.valid, 'bg-red-500/10 border-red-500': !validationResult.valid}">
        <div class="flex items-start gap-3">
          <span class="text-2xl">{{ validationResult.valid ? '✓' : '⚠' }}</span>
          <div class="flex-1">
            <h3 class="font-semibold">{{ validationResult.valid ? 'Cross-File Validation Passed' : 'Validation Issues Found' }}</h3>
            <div *ngIf="validationResult.warnings?.length" class="mt-2 space-y-1">
              <p *ngFor="let warn of validationResult.warnings" class="text-sm text-yellow-600">⚠ {{ warn }}</p>
            </div>
            <div *ngIf="validationResult.errors?.length" class="mt-2 space-y-1">
              <p *ngFor="let err of validationResult.errors" class="text-sm text-red-600">✗ {{ err }}</p>
            </div>
          </div>
          <button (click)="validationResult = null" class="text-secondary-400 hover:text-secondary-600">✕</button>
        </div>
      </div>

      <!-- Import Steps -->
      <div class="space-y-4">
        <div *ngFor="let step of steps; let i = index" 
             class="card p-6 transition-all"
             [ngClass]="{
               'opacity-50': !canUpload(step),
               'ring-2 ring-primary-500': step.status === 'ready',
               'ring-2 ring-green-500': step.status === 'success',
               'ring-2 ring-red-500': step.status === 'error'
             }">
          
          <div class="flex items-start gap-4">
            <!-- Step Number -->
            <div class="w-10 h-10 rounded-full flex items-center justify-center font-bold text-lg"
                 [ngClass]="{
                   'bg-secondary-200 text-secondary-600': step.status === 'pending',
                   'bg-blue-500 text-white': step.status === 'ready',
                   'bg-yellow-500 text-white': step.status === 'importing',
                   'bg-green-500 text-white': step.status === 'success',
                   'bg-red-500 text-white': step.status === 'error',
                   'bg-secondary-300 text-secondary-500': step.status === 'skipped'
                 }">
              {{ step.status === 'success' ? '✓' : step.status === 'error' ? '✗' : step.id }}
            </div>

            <!-- Step Content -->
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <h3 class="font-semibold text-lg">{{ step.label }}</h3>
                <span *ngIf="step.required" class="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded">Required</span>
                <span *ngIf="!step.required" class="text-xs bg-secondary-100 text-secondary-600 px-2 py-0.5 rounded">Optional</span>
              </div>
              <p class="text-secondary-500 text-sm mt-1">{{ step.description }}</p>
              
              <!-- Dependencies -->
              <div *ngIf="step.dependencies.length > 0" class="text-xs text-secondary-400 mt-1">
                Depends on: {{ step.dependencies.join(', ') }}
              </div>

              <!-- Format Info -->
              <div class="mt-3 bg-secondary-50 dark:bg-secondary-800 rounded p-3 text-sm">
                <div class="font-mono text-xs">
                  <span class="text-secondary-400">Header: </span>
                  <span class="text-primary-600">{{ step.format }}</span>
                </div>
                <div class="font-mono text-xs mt-1">
                  <span class="text-secondary-400">Example: </span>
                  <span>{{ step.example }}</span>
                </div>
              </div>

              <!-- File Selection -->
              <div class="mt-4 flex items-center gap-3">
                <input type="file" 
                       [id]="'file-' + step.entity" 
                       accept=".csv" 
                       class="hidden"
                       (change)="onFileSelected($event, step)"
                       [disabled]="!canUpload(step)">
                <label [for]="'file-' + step.entity"
                       class="btn btn-secondary cursor-pointer"
                       [class.opacity-50]="!canUpload(step)"
                       [class.cursor-not-allowed]="!canUpload(step)">
                  {{ step.file ? '📄 ' + step.file.name : '📁 Select CSV File' }}
                </label>
                <a [href]="'http://localhost:8080/api/v1/bulk/' + step.entity + '/template'" 
                   download 
                   class="text-sm text-primary-600 hover:underline">
                  Download template
                </a>
                <button *ngIf="step.file && step.status !== 'success'" 
                        (click)="clearFile(step)"
                        class="text-secondary-400 hover:text-red-500">
                  ✕ Clear
                </button>
                <button *ngIf="!step.required && step.status === 'pending'"
                        (click)="skipStep(step)"
                        class="text-sm text-secondary-400 hover:text-secondary-600">
                  Skip this step →
                </button>
              </div>

              <!-- Import Result -->
              <div *ngIf="step.result" class="mt-3 p-3 rounded text-sm"
                   [ngClass]="{'bg-green-100 dark:bg-green-900/30': step.status === 'success', 'bg-red-100 dark:bg-red-900/30': step.status === 'error'}">
                <div *ngIf="step.result.created !== undefined">
                  ✓ Created: {{ step.result.created }} | Skipped: {{ step.result.skipped }}
                </div>
                <div *ngIf="step.result.errors?.length > 0" class="mt-1 text-red-600">
                  <div *ngFor="let err of step.result.errors.slice(0, 5)">{{ err }}</div>
                  <div *ngIf="step.result.errors.length > 5">...and {{ step.result.errors.length - 5 }} more errors</div>
                </div>
                <div *ngIf="step.result.message" class="text-red-600">{{ step.result.message }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="card p-6 bg-primary-50 dark:bg-primary-900/20">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold">Ready to Import</h3>
            <p class="text-sm text-secondary-500">{{ getReadyCount() }} file(s) selected for import</p>
          </div>
          <div class="flex gap-3">
            <button (click)="validateFiles()" 
                    class="btn btn-secondary"
                    [disabled]="getReadyCount() === 0 || isImporting">
              🔍 Validate Files
            </button>
            <button (click)="startImport()" 
                    class="btn btn-primary"
                    [disabled]="getReadyCount() === 0 || isImporting">
              {{ isImporting ? 'Importing...' : '🚀 Start Import' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ImportComponent {
  private http = inject(HttpClient);

  steps: ImportStep[] = [
    {
      id: 1, entity: 'zones', label: 'Zones', required: false,
      description: 'Buildings, wings, or campus areas. Rooms will be assigned to zones.',
      format: 'name', example: 'Building A',
      file: null, status: 'pending', result: null, dependencies: []
    },
    {
      id: 2, entity: 'features', label: 'Features', required: false,
      description: 'Room capabilities like Projector, Lab Equipment, Computers, etc.',
      format: 'name', example: 'Projector',
      file: null, status: 'pending', result: null, dependencies: []
    },
    {
      id: 3, entity: 'lecturers', label: 'Lecturers', required: true,
      description: 'Teaching staff who will be assigned to courses.',
      format: 'name,email', example: 'John Smith,john.smith@university.edu',
      file: null, status: 'pending', result: null, dependencies: []
    },
    {
      id: 4, entity: 'student-groups', label: 'Student Groups', required: true,
      description: 'Classes or cohorts of students. Can be nested (parent-child hierarchy).',
      format: 'name,size,parent_group_name', example: 'COSC_1A,40,COSC_Year1',
      file: null, status: 'pending', result: null, dependencies: []
    },
    {
      id: 5, entity: 'rooms', label: 'Rooms', required: true,
      description: 'Physical spaces where lessons can be scheduled.',
      format: 'name,capacity,zone_name', example: 'Room A101,50,Building A',
      file: null, status: 'pending', result: null, dependencies: ['zones']
    },
    {
      id: 6, entity: 'courses', label: 'Courses', required: true,
      description: 'Subjects taught by lecturers to student groups. Set is_online=true for online courses.',
      format: 'code,name,weekly_hours,lecturer_email,student_group_name,is_online',
      example: 'COSC101,Intro to Programming,3,john.smith@university.edu,COSC_1A,false',
      file: null, status: 'pending', result: null, dependencies: ['lecturers', 'student-groups']
    }
  ];

  isImporting = false;
  validationResult: { valid: boolean; warnings: string[]; errors: string[] } | null = null;

  canUpload(step: ImportStep): boolean {
    if (this.isImporting) return false;
    // Check if all dependencies are satisfied
    for (const dep of step.dependencies) {
      const depStep = this.steps.find(s => s.entity === dep || s.label.toLowerCase() === dep.toLowerCase());
      if (depStep) {
        // Dependency is satisfied if it's success, skipped, or has a file ready
        const isSatisfied = depStep.status === 'success' ||
          depStep.status === 'skipped' ||
          depStep.file !== null;
        if (!isSatisfied) {
          return false;
        }
      }
    }
    return true;
  }

  onFileSelected(event: Event, step: ImportStep) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      step.file = input.files[0];
      step.status = 'ready';
      step.result = null;
      this.validationResult = null;
    }
  }

  clearFile(step: ImportStep) {
    step.file = null;
    step.status = 'pending';
    step.result = null;
  }

  skipStep(step: ImportStep) {
    step.status = 'skipped';
    step.file = null;
    step.result = null;
  }

  getProgressText(): string {
    const done = this.steps.filter(s => s.status === 'success' || s.status === 'skipped').length;
    return `${done}/${this.steps.length} complete`;
  }

  getReadyCount(): number {
    return this.steps.filter(s => s.file && s.status === 'ready').length;
  }

  async validateFiles() {
    this.validationResult = { valid: true, warnings: [], errors: [] };

    // Read and parse all files
    const fileContents: Map<string, string[][]> = new Map();

    for (const step of this.steps) {
      if (step.file) {
        try {
          const content = await this.readFileContent(step.file);
          const rows = this.parseCSV(content);
          fileContents.set(step.entity, rows);
        } catch (e) {
          this.validationResult.errors.push(`${step.label}: Failed to read file`);
          this.validationResult.valid = false;
        }
      }
    }

    // Cross-validation: Check lecturer emails in courses exist in lecturers file
    const lecturerRows = fileContents.get('lecturers');
    const courseRows = fileContents.get('courses');

    if (lecturerRows && courseRows) {
      const lecturerEmails = new Set(lecturerRows.map(r => r[1]?.toLowerCase().trim()).filter(Boolean));
      for (let i = 0; i < courseRows.length; i++) {
        const email = courseRows[i][3]?.toLowerCase().trim();
        if (email && !lecturerEmails.has(email)) {
          this.validationResult.warnings.push(`Courses row ${i + 2}: Lecturer email '${email}' not found in lecturers file`);
        }
      }
    }

    // Cross-validation: Check student groups in courses exist
    const groupRows = fileContents.get('student-groups');
    if (groupRows && courseRows) {
      const groupNames = new Set(groupRows.map(r => r[0]?.toLowerCase().trim()).filter(Boolean));
      for (let i = 0; i < courseRows.length; i++) {
        const groupName = courseRows[i][4]?.toLowerCase().trim();
        if (groupName && !groupNames.has(groupName)) {
          this.validationResult.warnings.push(`Courses row ${i + 2}: Student group '${groupName}' not found in student-groups file`);
        }
      }
    }

    // Cross-validation: Check zones in rooms exist
    const zoneRows = fileContents.get('zones');
    const roomRows = fileContents.get('rooms');
    if (zoneRows && roomRows) {
      const zoneNames = new Set(zoneRows.map(r => r[0]?.toLowerCase().trim()).filter(Boolean));
      for (let i = 0; i < roomRows.length; i++) {
        const zoneName = roomRows[i][2]?.toLowerCase().trim();
        if (zoneName && !zoneNames.has(zoneName)) {
          this.validationResult.warnings.push(`Rooms row ${i + 2}: Zone '${zoneName}' not found in zones file`);
        }
      }
    }

    // Check parent groups exist within same file
    if (groupRows) {
      const groupNames = new Set(groupRows.map(r => r[0]?.toLowerCase().trim()).filter(Boolean));
      for (let i = 0; i < groupRows.length; i++) {
        const parentName = groupRows[i][2]?.toLowerCase().trim();
        if (parentName && !groupNames.has(parentName)) {
          this.validationResult.warnings.push(`Student Groups row ${i + 2}: Parent group '${parentName}' not found`);
        }
      }
    }

    if (this.validationResult.warnings.length > 0) {
      this.validationResult.valid = false;
    }
  }

  async startImport() {
    this.isImporting = true;
    this.validationResult = null;

    for (const step of this.steps) {
      if (step.file && step.status === 'ready') {
        step.status = 'importing';

        try {
          const formData = new FormData();
          formData.append('file', step.file);

          const result = await this.http.post<any>(
            `http://localhost:8080/api/v1/bulk/${step.entity}/import`,
            formData
          ).toPromise();

          step.result = result;
          step.status = result?.created > 0 || result?.skipped >= 0 ? 'success' : 'error';
        } catch (e: any) {
          step.result = { message: e.error?.message || 'Import failed' };
          step.status = 'error';
        }
      }
    }

    this.isImporting = false;
  }

  resetAll() {
    for (const step of this.steps) {
      step.file = null;
      step.status = 'pending';
      step.result = null;
    }
    this.validationResult = null;
  }

  downloadAllTemplates() {
    for (const step of this.steps) {
      const link = document.createElement('a');
      link.href = `http://localhost:8080/api/v1/bulk/${step.entity}/template`;
      link.download = `${step.entity}_template.csv`;
      link.click();
    }
  }

  private readFileContent(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsText(file);
    });
  }

  private parseCSV(content: string): string[][] {
    const lines = content.split('\n').slice(1); // Skip header
    return lines
      .filter(line => line.trim())
      .map(line => line.split(',').map(cell => cell.replace(/^"|"$/g, '').trim()));
  }
}
