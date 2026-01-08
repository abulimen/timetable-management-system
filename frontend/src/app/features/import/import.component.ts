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
            <p *ngIf="getReadyCount() > 0 && !validationResult" class="text-sm text-yellow-600 mt-1">
              ⚠ Please validate files before importing
            </p>
            <p *ngIf="validationResult && !validationResult.valid" class="text-sm text-red-600 mt-1">
              ✗ Fix validation errors before importing
            </p>
          </div>
          <div class="flex gap-3">
            <button (click)="validateFiles()" 
                    class="btn btn-secondary"
                    [disabled]="getReadyCount() === 0 || isImporting">
              🔍 Validate Files
            </button>
            <button (click)="startImport()" 
                    class="btn btn-primary"
                    [disabled]="getReadyCount() === 0 || isImporting || !validationResult?.valid"
                    [title]="!validationResult ? 'Validate files first' : (!validationResult.valid ? 'Fix validation errors first' : 'Start importing')">
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
      description: 'Classes or cohorts. Parent groups have EMPTY size (calculated from children). Child groups have actual student count.',
      format: 'name,size,parent_group_name', example: 'Computer Science Year 1,,\nCSC 1A,40,Computer Science Year 1',
      file: null, status: 'pending', result: null, dependencies: []
    },
    {
      id: 5, entity: 'rooms', label: 'Rooms', required: true,
      description: 'Physical spaces where lessons can be scheduled. Features are pipe-separated.',
      format: 'name,capacity,zone_name,features', example: 'Room A101,50,Building A,Projector|Whiteboard',
      file: null, status: 'pending', result: null, dependencies: ['zones', 'features']
    },
    {
      id: 6, entity: 'courses', label: 'Courses', required: true,
      description: 'Subjects taught by lecturers to student groups. Use pipe (|) for combined lectures (e.g., CS_200|IT_200). Set is_online=true for online courses.',
      format: 'code,name,weekly_hours,lecturer_email,student_group_names,is_online',
      example: 'SEM200,Interdisciplinary Seminar,2,john@uni.edu,CS_200|IT_200|SE_200,false',
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

    // Duplicate detection within each CSV file (strict mode - errors not warnings)
    // Zones: check name uniqueness
    const zonesData = fileContents.get('zones');
    if (zonesData) {
      const seenNames = new Map<string, number>();
      for (let i = 0; i < zonesData.length; i++) {
        const name = zonesData[i][0]?.toLowerCase().trim();
        if (name) {
          if (seenNames.has(name)) {
            this.validationResult.errors.push(`Zones row ${i + 2}: Duplicate zone name '${zonesData[i][0].trim()}' (first seen in row ${seenNames.get(name)})`);
            this.validationResult.valid = false;
          } else {
            seenNames.set(name, i + 2);
          }
        }
      }
    }

    // Features: check name uniqueness
    const featuresData = fileContents.get('features');
    if (featuresData) {
      const seenNames = new Map<string, number>();
      for (let i = 0; i < featuresData.length; i++) {
        const name = featuresData[i][0]?.toLowerCase().trim();
        if (name) {
          if (seenNames.has(name)) {
            this.validationResult.errors.push(`Features row ${i + 2}: Duplicate feature name '${featuresData[i][0].trim()}' (first seen in row ${seenNames.get(name)})`);
            this.validationResult.valid = false;
          } else {
            seenNames.set(name, i + 2);
          }
        }
      }
    }

    // Rooms: check name uniqueness
    const roomsData = fileContents.get('rooms');
    if (roomsData) {
      const seenNames = new Map<string, number>();
      for (let i = 0; i < roomsData.length; i++) {
        const name = roomsData[i][0]?.toLowerCase().trim();
        if (name) {
          if (seenNames.has(name)) {
            this.validationResult.errors.push(`Rooms row ${i + 2}: Duplicate room name '${roomsData[i][0].trim()}' (first seen in row ${seenNames.get(name)})`);
            this.validationResult.valid = false;
          } else {
            seenNames.set(name, i + 2);
          }
        }
      }
    }

    // Lecturers: check name and email uniqueness
    const lecturersData = fileContents.get('lecturers');
    if (lecturersData) {
      const seenNames = new Map<string, number>();
      const seenEmails = new Map<string, number>();
      for (let i = 0; i < lecturersData.length; i++) {
        const name = lecturersData[i][0]?.toLowerCase().trim();
        const email = lecturersData[i][1]?.toLowerCase().trim();
        if (name) {
          if (seenNames.has(name)) {
            this.validationResult.errors.push(`Lecturers row ${i + 2}: Duplicate lecturer name '${lecturersData[i][0].trim()}' (first seen in row ${seenNames.get(name)})`);
            this.validationResult.valid = false;
          } else {
            seenNames.set(name, i + 2);
          }
        }
        if (email) {
          if (seenEmails.has(email)) {
            this.validationResult.errors.push(`Lecturers row ${i + 2}: Duplicate email '${lecturersData[i][1].trim()}' (first seen in row ${seenEmails.get(email)})`);
            this.validationResult.valid = false;
          } else {
            seenEmails.set(email, i + 2);
          }
        }
      }
    }

    // Student groups: check name uniqueness
    const studentGroupsData = fileContents.get('student-groups');
    if (studentGroupsData) {
      const seenNames = new Map<string, number>();
      for (let i = 0; i < studentGroupsData.length; i++) {
        const name = studentGroupsData[i][0]?.toLowerCase().trim();
        if (name) {
          if (seenNames.has(name)) {
            this.validationResult.errors.push(`Student Groups row ${i + 2}: Duplicate group name '${studentGroupsData[i][0].trim()}' (first seen in row ${seenNames.get(name)})`);
            this.validationResult.valid = false;
          } else {
            seenNames.set(name, i + 2);
          }
        }
      }
    }

    // Courses: check code uniqueness
    const coursesData = fileContents.get('courses');
    if (coursesData) {
      const seenCodes = new Map<string, number>();
      for (let i = 0; i < coursesData.length; i++) {
        const code = coursesData[i][0]?.toLowerCase().trim();
        if (code) {
          if (seenCodes.has(code)) {
            this.validationResult.errors.push(`Courses row ${i + 2}: Duplicate course code '${coursesData[i][0].trim()}' (first seen in row ${seenCodes.get(code)})`);
            this.validationResult.valid = false;
          } else {
            seenCodes.set(code, i + 2);
          }
        }
      }
    }

    // Cross-validation: Check lecturer emails in courses exist in lecturers file
    const lecturerRows = fileContents.get('lecturers');
    const courseRows = fileContents.get('courses');

    if (courseRows) {
      // Build set of valid lecturer emails
      const lecturerEmails = new Set<string>();
      if (lecturerRows) {
        for (const row of lecturerRows) {
          const email = row[1]?.toLowerCase().trim();
          if (email) lecturerEmails.add(email);
        }
      }

      // Check that every course has a valid lecturer email
      const missingEmails: { email: string; row: number }[] = [];
      for (let i = 0; i < courseRows.length; i++) {
        const email = courseRows[i][3]?.toLowerCase().trim();
        if (email && !lecturerEmails.has(email)) {
          missingEmails.push({ email: courseRows[i][3]?.trim() || email, row: i + 2 });
        }
      }

      if (missingEmails.length > 0) {
        this.validationResult.valid = false;

        if (!lecturerRows || lecturerRows.length === 0) {
          this.validationResult.errors.push(
            `Courses file contains lecturer emails but no lecturers file was selected. ` +
            `Please upload a lecturers.csv file containing all lecturer emails used in courses.`
          );
        }

        // Group by unique email for cleaner output
        const uniqueMissing = [...new Set(missingEmails.map(m => m.email))];
        if (uniqueMissing.length <= 5) {
          for (const emailInfo of missingEmails) {
            this.validationResult.errors.push(
              `Courses row ${emailInfo.row}: Lecturer email '${emailInfo.email}' not found in lecturers file. ` +
              `Ensure this email exists in lecturers.csv with exact same spelling (check for typos).`
            );
          }
        } else {
          this.validationResult.errors.push(
            `${missingEmails.length} courses reference lecturer emails not found in lecturers file: ` +
            `${uniqueMissing.slice(0, 3).join(', ')}... and ${uniqueMissing.length - 3} more. ` +
            `Please check for typos or add missing lecturers to lecturers.csv.`
          );
        }
      }
    }

    // Cross-validation: Check student groups in courses exist (supports pipe-separated multiple groups)
    const groupRows = fileContents.get('student-groups');
    if (groupRows && courseRows) {
      const groupNames = new Set(groupRows.map(r => r[0]?.toLowerCase().trim()).filter(Boolean));
      for (let i = 0; i < courseRows.length; i++) {
        const groupNamesStr = courseRows[i][4]?.trim();
        if (groupNamesStr) {
          // Split by pipe separator for multiple groups
          const groupList = groupNamesStr.split('|');
          for (const groupName of groupList) {
            const trimmedName = groupName.toLowerCase().trim();
            if (trimmedName && !groupNames.has(trimmedName)) {
              this.validationResult.warnings.push(`Courses row ${i + 2}: Student group '${groupName.trim()}' not found in student-groups file`);
            }
          }
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
