import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService, SolverStatus, SolverAnalysis, FeasibilityCheck } from '../../core/services/api.service';

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
            </div>
            <p class="text-secondary-500 mt-1">Score: {{ status?.score || 'N/A' }}</p>
          </div>
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
          </div>
        </div>

        <div class="flex gap-4">
          <button (click)="checkFeasibility()" class="btn btn-secondary">Check Feasibility</button>
          <button (click)="loadAnalysis()" class="btn btn-secondary">Load Analysis</button>
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
export class SolverComponent implements OnInit {
  private api = inject(ApiService);
  status: SolverStatus | null = null;
  analysis: SolverAnalysis | null = null;
  feasibility: FeasibilityCheck | null = null;
  pollInterval: any;

  get isSolving() { return this.status?.state === 'SOLVING_ACTIVE' || this.status?.state === 'SOLVING'; }

  ngOnInit() { this.loadStatus(); }

  loadStatus() {
    this.api.getSolverStatus().subscribe({ next: (s) => this.status = s });
  }

  startSolver(mode: 'FULL_REPLAN' | 'STABILITY') {
    // Clear any previous feasibility results
    this.feasibility = null;

    this.api.startSolver(mode).subscribe({
      next: (s) => {
        this.status = s;
        this.pollInterval = setInterval(() => {
          this.api.getSolverStatus().subscribe({
            next: (st) => {
              this.status = st;
              if (!this.isSolving) clearInterval(this.pollInterval);
            }
          });
        }, 3000);
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
          this.status = { jobId: '', state: 'ERROR', score: err.error?.message || 'Failed to start' };
        }
      }
    });
  }


  terminateSolver() {
    this.api.terminateSolver().subscribe({
      next: (s) => { this.status = s; clearInterval(this.pollInterval); }
    });
  }

  checkFeasibility() {
    this.api.getFeasibility().subscribe({ next: (f) => this.feasibility = f });
  }

  loadAnalysis() {
    this.api.getSolverAnalysis().subscribe({ next: (a) => this.analysis = a });
  }
}
