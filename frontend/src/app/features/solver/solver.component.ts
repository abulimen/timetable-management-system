import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ApiService,
  SolverStatus,
  TimetableChangeStatus,
  SolverRuntimeDiagnostics,
  SolverAnalysis,
  FeasibilityCheck,
  CourseFeasibilityDiagnostics,
  FeatureScarcityDiagnostics,
  LecturerLoadDiagnostics
} from '../../core/services/api.service';

@Component({
  selector: 'app-solver',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Solver Control</h1>

      <!-- Status & Controls -->
      <div class="card p-6">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h2 class="text-lg font-semibold">Solver Status</h2>
            <div class="flex items-center gap-2 mt-2">
              <span 
                class="w-4 h-4 rounded-full"
                [ngClass]="{
                  'bg-gray-400': status?.state === 'NOT_SOLVING',
                  'bg-green-500 animate-pulse': status?.state === 'SOLVING_ACTIVE' || status?.state === 'SOLVING',
                  'bg-red-500': status?.state === 'ERROR'
                }">
              </span>
              <span class="text-lg font-medium">{{ status?.state || 'Unknown' }}</span>
              <span *ngIf="isSolving" class="text-sm text-secondary-500">(polling every 3s)</span>
            </div>
            
            <!-- Standard Score Display -->
            <p *ngIf="status?.state !== 'ERROR'" class="text-secondary-500 mt-1">Score: {{ status?.score || 'N/A' }}</p>
            <p *ngIf="status?.profile" class="text-secondary-500 mt-1">Profile: {{ status?.profile }}</p>
            <p *ngIf="status?.durationMs != null" class="text-secondary-500 mt-1">
              Solve time: {{ formatDuration(status?.durationMs ?? 0) }}
            </p>
            <p *ngIf="runtime" class="text-secondary-500 mt-1">
              Runtime: {{ runtime.moveThreadCount }} move threads • {{ runtime.environmentMode }} • {{ runtime.availableProcessors }} CPU cores
            </p>
            <div class="mt-3 grid grid-cols-1 md:grid-cols-2 gap-2 text-sm" *ngIf="status">
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Time to first best:</span>
                <span class="font-medium ml-1">{{ status.timeToFirstBestMs != null ? formatDuration(status.timeToFirstBestMs) : 'N/A' }}</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Time to hard-feasible:</span>
                <span class="font-medium ml-1">{{ status.timeToFirstFeasibleMs != null ? formatDuration(status.timeToFirstFeasibleMs) : 'N/A' }}</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Improvements found:</span>
                <span class="font-medium ml-1">{{ status.improvementCount ?? 0 }}</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Checkpoint saves:</span>
                <span class="font-medium ml-1">{{ status.persistenceCount ?? 0 }}</span>
                <span class="text-secondary-500 ml-1" *ngIf="status.avgPersistenceMs != null">(avg {{ formatDuration(status.avgPersistenceMs) }})</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Current hard score:</span>
                <span class="font-medium ml-1">{{ status.bestHardScore ?? 'N/A' }}</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Current soft score:</span>
                <span class="font-medium ml-1">{{ status.bestSoftScore ?? 'N/A' }}</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Loaded dataset:</span>
                <span class="font-medium ml-1">{{ status.lessonsCount ?? 0 }} lessons • {{ status.timeslotsCount ?? 0 }} slots • {{ status.roomsCount ?? 0 }} rooms</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Live runtime:</span>
                <span class="font-medium ml-1">{{ status.moveThreadCount || runtime?.moveThreadCount || 'N/A' }} threads • {{ status.parallelSolverCount || runtime?.parallelSolverCount || 'N/A' }} jobs • {{ status.availableProcessors ?? runtime?.availableProcessors ?? 'N/A' }} cores</span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Adaptive limits:</span>
                <span class="font-medium ml-1">
                  {{ status.adaptiveDatasetBand || 'N/A' }} band • max {{ status.adaptiveMaxRuntimeMs != null ? formatDuration(status.adaptiveMaxRuntimeMs) : 'N/A' }} • no-improve {{ status.adaptiveUnimprovedMs != null ? formatDuration(status.adaptiveUnimprovedMs) : 'N/A' }}
                </span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">Adaptive search breadth:</span>
                <span class="font-medium ml-1">{{ status.adaptiveAcceptedCountLimit ?? 'N/A' }}</span>
                <span *ngIf="status.adaptiveTerminationReason" class="text-amber-700 ml-2">({{ status.adaptiveTerminationReason }})</span>
              </div>
            </div>
            <div *ngIf="status?.pendingChanges" class="mt-2 text-xs text-amber-700 bg-amber-100 rounded px-2 py-1 inline-block">
              Pending timetable changes not yet replanned: {{ status?.pendingChangeReason || 'Unknown reason' }}
            </div>
            <div class="mt-2 text-xs inline-block px-2 py-1 rounded"
              [ngClass]="editingStatus?.editingEnabled ? 'bg-emerald-100 text-emerald-800' : 'bg-secondary-200 text-secondary-700'">
              Editing mode: {{ editingStatus?.editingEnabled ? 'ENABLED' : 'LOCKED' }}
            </div>
            
            <!-- Error Alert -->
            <div *ngIf="status?.state === 'ERROR'" class="mt-4 p-4 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-200 rounded-lg flex items-start gap-3 max-w-xl">
              <svg class="w-6 h-6 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <div>
                <h3 class="font-bold">Solver Error</h3>
                <p class="text-sm mt-1">{{ status?.score }}</p>
              </div>
            </div>
          </div>
          <div class="flex flex-col gap-3 items-end">
            <div class="flex items-center gap-2 text-sm">
              <span class="text-secondary-500">Profile:</span>
              <button
                (click)="selectedProfile='BALANCED'"
                class="px-2 py-1 rounded border text-xs"
                [ngClass]="selectedProfile === 'BALANCED' ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-secondary-700 border-secondary-300'">
                Balanced
              </button>
              <button
                (click)="selectedProfile='QUALITY'"
                class="px-2 py-1 rounded border text-xs"
                [ngClass]="selectedProfile === 'QUALITY' ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-secondary-700 border-secondary-300'">
                Quality
              </button>
            </div>
            <label class="flex items-center gap-2 text-xs text-secondary-600">
              <input type="checkbox" [checked]="skipFeasibility" (change)="skipFeasibility = !skipFeasibility" />
              Skip feasibility pre-check
            </label>
            <div class="flex gap-3">
              <button 
                (click)="startSolver('FULL_REPLAN')"
                [disabled]="isSolving"
                class="btn btn-primary disabled:opacity-50">
                Full Replan
              </button>
              <button 
                (click)="startSolver('STABILITY')"
                [disabled]="isSolving"
                class="btn btn-secondary disabled:opacity-50">
                Stability Mode
              </button>
              <button 
                (click)="terminateSolver()"
                [disabled]="!isSolving"
                class="btn btn-danger disabled:opacity-50">
                Stop
              </button>
              <button
                (click)="clearCurrentTimetable()"
                [disabled]="isSolving"
                class="btn btn-danger disabled:opacity-50">
                Clear Timetable
              </button>
              <button
                *ngIf="!editingStatus?.editingEnabled"
                (click)="enableEditingMode()"
                class="btn btn-warning">
                Enable Editing
              </button>
              <button
                *ngIf="editingStatus?.editingEnabled"
                (click)="disableEditingMode()"
                class="btn btn-secondary">
                Lock Editing
              </button>
            </div>
          </div>
        </div>

        <div class="flex gap-4">
          <button (click)="checkFeasibility()" class="btn btn-secondary">Check Feasibility</button>
          <button (click)="loadAnalysis()" class="btn btn-secondary">Load Analysis</button>
          <button (click)="loadDiagnostics()" class="btn btn-secondary">Load Diagnostics</button>
        </div>
      </div>

      <!-- Cross-Entity Diagnostics -->
      <div *ngIf="courseDiagnostics || featureDiagnostics || lecturerDiagnostics" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">Cross-Entity Diagnostics</h2>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div class="p-4 bg-secondary-100 dark:bg-secondary-700 rounded-lg">
            <p class="text-sm text-secondary-500">Course Feasibility</p>
            <p class="text-xl font-bold" [class.text-red-600]="courseDiagnostics && !courseDiagnostics.feasible">
              {{ courseDiagnostics ? (courseDiagnostics.feasible ? 'Feasible' : 'Blocked') : 'N/A' }}
            </p>
            <p *ngIf="courseDiagnostics" class="text-xs text-secondary-500 mt-1">
              {{ courseDiagnostics.blockingCount }} blocking / {{ courseDiagnostics.warningCount }} warnings
            </p>
          </div>
          <div class="p-4 bg-secondary-100 dark:bg-secondary-700 rounded-lg">
            <p class="text-sm text-secondary-500">Feature Scarcity</p>
            <p class="text-xl font-bold">{{ featureDiagnostics?.criticalCount || 0 }} critical</p>
            <p *ngIf="featureDiagnostics" class="text-xs text-secondary-500 mt-1">
              {{ featureDiagnostics.highCount }} high risk
            </p>
          </div>
          <div class="p-4 bg-secondary-100 dark:bg-secondary-700 rounded-lg">
            <p class="text-sm text-secondary-500">Lecturer Load</p>
            <p class="text-xl font-bold">{{ lecturerDiagnostics?.criticalCount || 0 }} critical</p>
            <p *ngIf="lecturerDiagnostics" class="text-xs text-secondary-500 mt-1">
              {{ lecturerDiagnostics.highCount }} high risk
            </p>
          </div>
        </div>

        <div *ngIf="featureDiagnostics?.items?.length" class="mb-5">
          <h3 class="font-medium mb-2">Top Feature Risks</h3>
          <div *ngFor="let item of (featureDiagnostics?.items || []).slice(0, 5)" class="p-3 mb-2 rounded-lg bg-secondary-100 dark:bg-secondary-700">
            <p class="font-medium">
              {{ item.name }}
              <span class="ml-2 px-2 py-0.5 rounded text-xs font-semibold border"
                [ngClass]="riskBadgeClass(item.risk)">
                {{ normalizeRisk(item.risk) }}
              </span>
            </p>
            <p class="text-xs text-secondary-500">Demand {{ item.demandCount }} / Supply {{ item.supplyCount }} / Scarcity {{ item.scarcityRatio ?? 'INF' }}</p>
          </div>
        </div>

        <div *ngIf="lecturerDiagnostics?.items?.length">
          <h3 class="font-medium mb-2">Top Lecturer Load Risks</h3>
          <div *ngFor="let item of (lecturerDiagnostics?.items || []).slice(0, 5)" class="p-3 mb-2 rounded-lg bg-secondary-100 dark:bg-secondary-700">
            <p class="font-medium">
              {{ item.name }}
              <span class="ml-2 px-2 py-0.5 rounded text-xs font-semibold border"
                [ngClass]="riskBadgeClass(item.risk)">
                {{ normalizeRisk(item.risk) }}
              </span>
            </p>
            <p class="text-xs text-secondary-500">Assigned {{ item.assignedHours }}h / Available {{ item.availableHours }}h / Ratio {{ (item.loadRatio * 100).toFixed(0) }}%</p>
          </div>
        </div>
      </div>

      <!-- Feasibility -->
      <div *ngIf="feasibility" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">Feasibility Check</h2>
        <div class="flex items-center gap-2 mb-4">
          <span class="badge" [ngClass]="feasibility.feasible ? 'badge-success' : 'badge-error'">
            {{ feasibility.feasible ? 'Feasible' : 'Not Feasible' }}
          </span>
          <span class="text-sm text-secondary-500">
            {{ feasibility.lessonCount }} lessons, {{ feasibility.roomCount }} rooms, {{ feasibility.timeslotCount }} slots
          </span>
        </div>
        <div *ngFor="let issue of feasibility.issues" class="p-3 mb-2 rounded-lg bg-secondary-100 dark:bg-secondary-700">
          <div class="flex items-center gap-2">
            <span class="badge" [ngClass]="issue.severity === 'BLOCKING' ? 'badge-error' : 'badge-warning'">{{ issue.type }}</span>
          </div>
          <p class="text-sm mt-1">{{ issue.description }}</p>
          <p class="text-xs text-secondary-500 mt-1">{{ issue.recommendation }}</p>
        </div>
      </div>

      <!-- Analysis -->
      <div *ngIf="analysis" class="card p-6">
        <h2 class="text-lg font-semibold mb-4">Constraint Analysis</h2>
        <div class="grid grid-cols-3 gap-4 mb-6">
          <div class="p-4 bg-secondary-100 dark:bg-secondary-700 rounded-lg">
            <p class="text-sm text-secondary-500">Score</p>
            <p class="text-xl font-bold">{{ analysis.score }}</p>
          </div>
          <div class="p-4 bg-secondary-100 dark:bg-secondary-700 rounded-lg">
            <p class="text-sm text-secondary-500">Hard Violations</p>
            <p class="text-xl font-bold" [class.text-red-600]="analysis.hardViolationCount > 0">{{ analysis.hardViolationCount }}</p>
          </div>
          <div class="p-4 bg-secondary-100 dark:bg-secondary-700 rounded-lg">
            <p class="text-sm text-secondary-500">Soft Penalty</p>
            <p class="text-xl font-bold">{{ analysis.softPenalty }}</p>
          </div>
        </div>

        <div *ngIf="analysis.hardViolations.length">
          <h3 class="font-medium text-red-600 mb-2">Hard Violations</h3>
          <div *ngFor="let v of analysis.hardViolations" class="p-3 mb-2 bg-red-50 dark:bg-red-900/20 rounded-lg">
            <p class="font-medium">{{ v.constraintName }} ({{ v.matchCount }})</p>
            <p class="text-sm text-secondary-500">{{ v.scoreImpact }}</p>
          </div>
        </div>
      </div>
    </div>
  `
})
export class SolverComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  status: SolverStatus | null = null;
  editingStatus: TimetableChangeStatus | null = null;
  analysis: SolverAnalysis | null = null;
  feasibility: FeasibilityCheck | null = null;
  runtime: SolverRuntimeDiagnostics | null = null;
  selectedProfile: 'BALANCED' | 'QUALITY' = 'BALANCED';
  skipFeasibility = false;
  courseDiagnostics: CourseFeasibilityDiagnostics | null = null;
  featureDiagnostics: FeatureScarcityDiagnostics | null = null;
  lecturerDiagnostics: LecturerLoadDiagnostics | null = null;
  pollInterval: any = null;

  get isSolving() { return this.status?.state === 'SOLVING_ACTIVE' || this.status?.state === 'SOLVING'; }

  ngOnInit() {
    this.loadStatus();
    this.loadRuntimeDiagnostics();
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  loadStatus() {
    this.api.getSolverStatus().subscribe({
      next: (s) => {
        this.status = s;
        this.refreshEditingStatus();
        // Start polling if solver is already active on page load
        if (this.isSolving && !this.pollInterval) {
          this.startPolling();
        }
      }
    });
  }

  private startPolling() {
    this.stopPolling(); // Clear any existing interval
    this.pollInterval = setInterval(() => {
      this.api.getSolverStatus().subscribe({
        next: (st) => {
          this.status = st;
          this.refreshEditingStatus();
          if (!this.isSolving) {
            this.stopPolling();
          }
        }
      });
    }, 3000);
  }

  private stopPolling() {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }


  startSolver(mode: 'FULL_REPLAN' | 'STABILITY') {
    // Clear any previous feasibility results
    this.feasibility = null;

    this.api.startSolver({ mode, profile: this.selectedProfile, skipFeasibility: this.skipFeasibility }).subscribe({
      next: (s) => {
        this.status = s;
        this.startPolling();
      },
      error: (err) => {
        // Handle feasibility failure response
        if (err.error?.error === 'FEASIBILITY_FAILED') {
          // Show feasibility report with blocking issues
          this.feasibility = {
            feasible: false,
            lessonCount: 0,
            roomCount: 0,
            timeslotCount: 0,
            blockingCount: err.error.blockingCount,
            warningCount: 0,
            availableRoomSlots: 0,
            issues: err.error.issues || []
          };
          this.status = { jobId: '', state: 'ERROR', score: 'Feasibility check failed' };
          console.error('Solver blocked by feasibility issues:', err.error.issues);
        } else {
          console.error('Failed to start solver:', err);
          // Backend returns error message in the 'score' field for SolverStatusDTO
          const errorMsg = err.error?.score || err.error?.message || 'Failed to start';
          this.status = { jobId: '', state: 'ERROR', score: errorMsg };
        }
      }
    });
  }

  private loadRuntimeDiagnostics() {
    this.api.getSolverRuntimeDiagnostics().subscribe({
      next: (runtime) => {
        this.runtime = runtime;
      }
    });
  }


  terminateSolver() {
    this.api.terminateSolver().subscribe({
      next: (s) => {
        this.status = s;
        this.stopPolling();
      }
    });
  }

  clearCurrentTimetable() {
    const first = window.confirm(
      'This will clear ALL current lesson assignments (timeslot/room) and unpin lessons. Imported data remains. Continue?'
    );
    if (!first) {
      return;
    }

    const second = window.confirm(
      'Final confirmation: clear the current timetable now? This action cannot be undone.'
    );
    if (!second) {
      return;
    }

    this.api.clearCurrentTimetable().subscribe({
      next: (res) => {
        alert(`Timetable cleared. Lessons updated: ${res.lessonsCleared}`);
        this.loadStatus();
      },
      error: (err) => {
        const message = err?.error?.message || 'Failed to clear timetable.';
        alert(message);
      }
    });
  }

  enableEditingMode() {
    const confirmed = window.confirm(
      'Enable editing mode? Any changes you make will require running FULL REPLAN to generate a new timetable.'
    );
    if (!confirmed) {
      return;
    }
    this.api.enableEditingMode().subscribe({
      next: (status) => {
        this.editingStatus = status;
      }
    });
  }

  disableEditingMode() {
    this.api.disableEditingMode().subscribe({
      next: (status) => {
        this.editingStatus = status;
      }
    });
  }

  private refreshEditingStatus() {
    this.api.getTimetableChangeStatus().subscribe({
      next: (status) => {
        this.editingStatus = status;
      }
    });
  }


  checkFeasibility() {
    this.api.getFeasibility().subscribe({ next: (f) => this.feasibility = f });
  }

  loadAnalysis() {
    this.api.getSolverAnalysis().subscribe({ next: (a) => this.analysis = a });
  }

  loadDiagnostics() {
    this.api.getCourseFeasibilityDiagnostics().subscribe({ next: (d) => this.courseDiagnostics = d });
    this.api.getFeatureScarcityDiagnostics().subscribe({ next: (d) => this.featureDiagnostics = d });
    this.api.getLecturerLoadDiagnostics().subscribe({ next: (d) => this.lecturerDiagnostics = d });
  }

  normalizeRisk(risk: string | null | undefined): string {
    return (risk || 'LOW').toUpperCase();
  }

  riskBadgeClass(risk: string | null | undefined): string {
    const level = this.normalizeRisk(risk);
    if (level === 'CRITICAL') return 'bg-red-100 text-red-800 border-red-300 dark:bg-red-900/30 dark:text-red-200 dark:border-red-700';
    if (level === 'HIGH') return 'bg-orange-100 text-orange-800 border-orange-300 dark:bg-orange-900/30 dark:text-orange-200 dark:border-orange-700';
    if (level === 'MEDIUM') return 'bg-yellow-100 text-yellow-800 border-yellow-300 dark:bg-yellow-900/30 dark:text-yellow-200 dark:border-yellow-700';
    return 'bg-green-100 text-green-800 border-green-300 dark:bg-green-900/30 dark:text-green-200 dark:border-green-700';
  }

  formatDuration(durationMs: number): string {
    if (!Number.isFinite(durationMs) || durationMs < 0) {
      return 'N/A';
    }
    const totalSeconds = Math.floor(durationMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    const millis = durationMs % 1000;
    if (minutes > 0) {
      return `${minutes}m ${seconds}s`;
    }
    if (seconds > 0) {
      return `${seconds}.${Math.floor(millis / 100)}s`;
    }
    return `${durationMs}ms`;
  }
}
