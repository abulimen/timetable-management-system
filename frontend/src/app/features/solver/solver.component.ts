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
  LecturerLoadDiagnostics,
  SolverEngine,
  LessonBreakdown
} from '../../core/services/api.service';

interface ParsedSection {
  text: string;
  isHeader?: boolean;
  isAction?: boolean;
  isBullet?: boolean;
  isProblem?: boolean;
}

interface BulletPart {
  text: string;
  isClickable: boolean;
  zoneName?: string;
  featureName?: string;
}

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
                  'bg-gray-400': status?.state === 'NOT_SOLVING' || status?.state === 'IDLE',
                  'bg-green-500 animate-pulse': status?.state === 'SOLVING_ACTIVE' || status?.state === 'SOLVING',
                  'bg-red-500': status?.state === 'ERROR'
                }">
              </span>
              <span class="text-lg font-medium">{{ status?.state || 'Unknown' }}</span>
              <span *ngIf="isSolving" class="text-sm text-secondary-500">(polling every 3s)</span>
            </div>
            
            <!-- CP-SAT Info -->
            <!-- Hybrid Solver Info -->
            <p *ngIf="status?.profile === 'HYBRID' || selectedEngine === 'HYBRID'" class="text-purple-600 font-medium mt-1">
              ⚡ Hybrid Solver: CP-SAT (hard) + Timefold (soft)
            </p>
            <p *ngIf="status?.stage" class="text-purple-600 font-medium mt-1">
              📍 Phase: {{ status?.stage }}
            </p>
            <div *ngIf="status?.stageOneDurationMs != null" class="text-xs text-secondary-500 mt-1">
              ⏱️ Phase 1 (CP-SAT): {{ formatDuration(status?.stageOneDurationMs ?? 0) }}
              <span *ngIf="status?.stageTwoDurationMs != null">| Phase 2 (Timefold): {{ formatDuration(status?.stageTwoDurationMs ?? 0) }}</span>
            </div>
            
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
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700" *ngIf="status?.feasible != null">
                <span class="text-secondary-500">Hard constraints satisfied:</span>
                <span class="font-medium ml-1" [ngClass]="{'text-green-600': status.feasible, 'text-red-600': !status.feasible}">
                  {{ status.feasible ? '✓ Yes' : '✗ No' }}
                </span>
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
                <span class="text-secondary-500">{{ status.adaptiveLimitsEnabled === false ? 'Runtime limits:' : 'Adaptive limits:' }}</span>
                <span class="font-medium ml-1">
                  <ng-container *ngIf="status.adaptiveLimitsEnabled !== false; else manualRuntimeLimits">
                    {{ status.adaptiveDatasetBand || 'N/A' }} band • max {{ status.adaptiveMaxRuntimeMs != null ? formatDuration(status.adaptiveMaxRuntimeMs) : 'N/A' }} • no-improve {{ status.adaptiveUnimprovedMs != null ? formatDuration(status.adaptiveUnimprovedMs) : 'N/A' }}
                  </ng-container>
                  <ng-template #manualRuntimeLimits>
                    Manual • max {{ status.adaptiveMaxRuntimeMs != null ? formatDuration(status.adaptiveMaxRuntimeMs) : 'N/A' }} • no-improve {{ status.adaptiveUnimprovedMs != null ? formatDuration(status.adaptiveUnimprovedMs) : 'N/A' }}
                  </ng-template>
                </span>
              </div>
              <div class="p-2 rounded bg-secondary-100 dark:bg-secondary-700">
                <span class="text-secondary-500">{{ status.adaptiveSearchBreadthEnabled === false ? 'Search breadth (manual):' : 'Adaptive search breadth:' }}</span>
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
              <span class="text-secondary-500">Engine:</span>
              <button
                (click)="selectedEngine='TIMEFOLD'"
                class="px-2 py-1 rounded border text-xs"
                [ngClass]="selectedEngine === 'TIMEFOLD' ? 'bg-purple-600 text-white border-purple-600' : 'bg-white text-secondary-700 border-secondary-300'"
                title="Timefold Solver (OptaPlanner successor) - Local search optimization">
                Timefold
              </button>
              <button
                (click)="selectedEngine='CPSAT'"
                class="px-2 py-1 rounded border text-xs"
                [ngClass]="selectedEngine === 'CPSAT' ? 'bg-purple-600 text-white border-purple-600' : 'bg-white text-secondary-700 border-secondary-300'"
                title="Google OR-Tools CP-SAT - Constraint programming, often faster for large problems">
                CP-SAT
              </button>
              <button
                (click)="selectedEngine='HYBRID'"
                class="px-2 py-1 rounded border text-xs"
                [ngClass]="selectedEngine === 'HYBRID' ? 'bg-purple-600 text-white border-purple-600' : 'bg-white text-secondary-700 border-secondary-300'"
                title="Hybrid: CP-SAT for hard constraints + Timefold for soft optimization - Best of both worlds">
                Hybrid ⚡
              </button>
            </div>
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
                class="btn btn-danger disabled:opacity-50"
                title="Stop solver and save current best solution">
                Stop & Save 💾
              </button>
              <button
                (click)="resumeSolver()"
                [disabled]="isSolving || !status?.resumeAvailable"
                class="btn btn-secondary disabled:opacity-50">
                Resume
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

        <!-- Severity Summary -->
        <div *ngIf="!feasibility.feasible" class="grid grid-cols-4 gap-3 mb-4">
          <div *ngIf="feasibility.criticalCount > 0" class="p-3 bg-red-100 dark:bg-red-900/40 rounded-lg border border-red-300 dark:border-red-700">
            <p class="text-xs text-red-600 dark:text-red-400 font-medium">CRITICAL</p>
            <p class="text-xl font-bold text-red-700 dark:text-red-300">{{ feasibility.criticalCount }}</p>
          </div>
          <div *ngIf="feasibility.highCount > 0" class="p-3 bg-orange-100 dark:bg-orange-900/40 rounded-lg border border-orange-300 dark:border-orange-700">
            <p class="text-xs text-orange-600 dark:text-orange-400 font-medium">HIGH RISK</p>
            <p class="text-xl font-bold text-orange-700 dark:text-orange-300">{{ feasibility.highCount }}</p>
          </div>
          <div *ngIf="feasibility.mediumCount > 0" class="p-3 bg-yellow-100 dark:bg-yellow-900/40 rounded-lg border border-yellow-300 dark:border-yellow-700">
            <p class="text-xs text-yellow-600 dark:text-yellow-400 font-medium">MEDIUM</p>
            <p class="text-xl font-bold text-yellow-700 dark:text-yellow-300">{{ feasibility.mediumCount }}</p>
          </div>
          <div *ngIf="feasibility.lowCount > 0" class="p-3 bg-blue-100 dark:bg-blue-900/40 rounded-lg border border-blue-300 dark:border-blue-700">
            <p class="text-xs text-blue-600 dark:text-blue-400 font-medium">LOW</p>
            <p class="text-xl font-bold text-blue-700 dark:text-blue-300">{{ feasibility.lowCount }}</p>
          </div>
        </div>

        <!-- Analysis Text (detailed breakdown) -->
        <div *ngIf="feasibility.analysisText" class="mb-4 p-4 bg-secondary-50 dark:bg-secondary-800/50 rounded-lg">
          <pre class="text-xs text-secondary-700 dark:text-secondary-300 whitespace-pre-wrap font-mono">{{ feasibility.analysisText }}</pre>
        </div>
        
        <!-- Issues grouped by severity -->
        <div *ngFor="let issue of feasibility.issues" class="mb-4 border rounded-lg overflow-hidden">
          <!-- Issue Header with severity-based coloring -->
          <div class="px-4 py-3 border-b" 
               [ngClass]="{
                 'bg-red-50 dark:bg-red-900/30 border-red-200 dark:border-red-800': issue.severity === 'CRITICAL',
                 'bg-orange-50 dark:bg-orange-900/30 border-orange-200 dark:border-orange-800': issue.severity === 'HIGH',
                 'bg-yellow-50 dark:bg-yellow-900/30 border-yellow-200 dark:border-yellow-800': issue.severity === 'MEDIUM',
                 'bg-blue-50 dark:bg-blue-900/30 border-blue-200 dark:border-blue-800': issue.severity === 'LOW'
               }">
            <div class="flex items-center gap-2">
              <span class="badge" [ngClass]="{
                'badge-error': issue.severity === 'CRITICAL',
                'bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300': issue.severity === 'HIGH',
                'bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300': issue.severity === 'MEDIUM',
                'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300': issue.severity === 'LOW'
              }">{{ issue.severity }}</span>
              <span class="text-xs text-secondary-500">{{ issue.type }}</span>
            </div>
            <p class="text-sm font-medium mt-2 text-secondary-800 dark:text-secondary-200">{{ issue.description }}</p>
          </div>
          
          <!-- Structured Recommendation -->
          <div class="px-4 py-3 bg-secondary-50 dark:bg-secondary-800/50">
            <div class="whitespace-pre-line text-sm">
              <ng-container *ngFor="let section of parseRecommendation(issue.recommendation); let i = index">
                <!-- Section Header -->
                <div *ngIf="section.isHeader" class="font-semibold text-secondary-900 dark:text-white mt-3 mb-2 pb-1 border-b border-secondary-200 dark:border-secondary-600">
                  {{ section.text }}
                </div>
                <!-- Action Item -->
                <div *ngIf="section.isAction" class="flex items-start gap-2 py-1">
                  <span class="text-green-600 dark:text-green-400 font-bold shrink-0">→</span>
                  <span class="text-secondary-700 dark:text-secondary-300">{{ section.text }}</span>
                </div>
                <!-- Bullet Point -->
                <div *ngIf="section.isBullet" class="flex items-start gap-2 py-0.5">
                  <span class="text-secondary-400 dark:text-secondary-500 shrink-0">•</span>
                  <span class="text-secondary-600 dark:text-secondary-400">
                    <ng-container *ngFor="let part of parseBulletParts(section.text); let j = index">
                      <span *ngIf="part.isClickable" 
                            class="text-primary-600 dark:text-primary-400 cursor-pointer hover:underline font-medium"
                            (click)="onLessonClick(part)">
                        {{ part.text }}
                      </span>
                      <span *ngIf="!part.isClickable">{{ part.text }}</span>
                    </ng-container>
                  </span>
                </div>
                <!-- Problem -->
                <div *ngIf="section.isProblem" class="flex items-start gap-2 py-1 bg-red-100 dark:bg-red-900/20 -mx-2 px-2 rounded">
                  <span class="text-red-600 dark:text-red-400 font-bold shrink-0">❌</span>
                  <span class="text-red-700 dark:text-red-300">{{ section.text }}</span>
                </div>
                <!-- Plain text -->
                <p *ngIf="!section.isHeader && !section.isAction && !section.isBullet && !section.isProblem" class="text-secondary-600 dark:text-secondary-400">{{ section.text }}</p>
              </ng-container>
            </div>
          </div>
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

    <!-- Lesson Breakdown Popup Modal -->
    <div *ngIf="showBreakdownPopup" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50" (click)="closeBreakdownPopup()">
      <div class="bg-white dark:bg-secondary-800 rounded-lg shadow-xl max-w-4xl w-full max-h-[80vh] overflow-hidden" (click)="$event.stopPropagation()">
        <!-- Header -->
        <div class="px-6 py-4 border-b border-secondary-200 dark:border-secondary-700 flex items-center justify-between">
          <div>
            <h3 class="text-lg font-semibold">
              {{ (lessonBreakdown?.zoneName ?? lessonBreakdown?.featureName) + (lessonBreakdown?.zoneName ? ' Zone' : ' Feature') }} - Lesson Breakdown
            </h3>
            <p class="text-sm text-secondary-500">
              {{ lessonBreakdown?.totalLessons }} lessons • {{ lessonBreakdown?.totalHours }} hours • {{ lessonBreakdown?.roomsAvailable }} rooms • {{ lessonBreakdown?.utilization?.toFixed(0) }}% utilization
            </p>
          </div>
          <button (click)="closeBreakdownPopup()" class="text-secondary-400 hover:text-secondary-600">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        
        <!-- Content -->
        <div class="p-6 overflow-y-auto max-h-[60vh]">
          <div *ngIf="breakdownLoading" class="flex items-center justify-center py-8">
            <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
          
          <table *ngIf="!breakdownLoading && lessonBreakdown?.lessons" class="min-w-full divide-y divide-secondary-200 dark:divide-secondary-700">
            <thead class="bg-secondary-50 dark:bg-secondary-900">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-secondary-500 uppercase">Course</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-secondary-500 uppercase">Name</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-secondary-500 uppercase">Hours/Week</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-secondary-500 uppercase">Student Groups</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-secondary-500 uppercase">Lecturer</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-secondary-500 uppercase">Students</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-secondary-200 dark:divide-secondary-700">
              <tr *ngFor="let lesson of lessonBreakdown?.lessons" class="hover:bg-secondary-50 dark:hover:bg-secondary-700">
                <td class="px-4 py-3 text-sm font-medium">{{ lesson.courseCode }}</td>
                <td class="px-4 py-3 text-sm">{{ lesson.courseName }}</td>
                <td class="px-4 py-3 text-sm">{{ lesson.weeklyHours }}</td>
                <td class="px-4 py-3 text-sm">
                  <span *ngFor="let group of lesson.studentGroups; let last = last" class="inline-flex items-center">
                    {{ group }}{{ !last ? ', ' : '' }}
                  </span>
                </td>
                <td class="px-4 py-3 text-sm">{{ lesson.lecturerName }}</td>
                <td class="px-4 py-3 text-sm">{{ lesson.studentCount }}</td>
              </tr>
            </tbody>
          </table>
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
  selectedEngine: SolverEngine = 'TIMEFOLD';
  skipFeasibility = false;
  courseDiagnostics: CourseFeasibilityDiagnostics | null = null;
  featureDiagnostics: FeatureScarcityDiagnostics | null = null;
  lecturerDiagnostics: LecturerLoadDiagnostics | null = null;
  pollInterval: any = null;
  
  // Lesson breakdown popup
  showBreakdownPopup = false;
  lessonBreakdown: LessonBreakdown | null = null;
  breakdownLoading = false;

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

    this.api.startSolver({ mode, profile: this.selectedProfile, skipFeasibility: this.skipFeasibility, engine: this.selectedEngine }).subscribe({
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
            criticalCount: 0,
            highCount: err.error.blockingCount || 0,
            mediumCount: 0,
            lowCount: 0,
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

  resumeSolver() {
    this.api.resumeSolver().subscribe({
      next: (s) => {
        this.status = s;
        this.startPolling();
      },
      error: (err) => {
        const message = err?.error?.message || 'No saved solver progress available to resume.';
        alert(message);
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

  /**
   * Parse recommendation text into structured sections for better display
   */
  parseRecommendation(text: string): ParsedSection[] {
    const lines = text.split('\n');
    const sections: ParsedSection[] = [];
    
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      
      // Check for section headers (ALL CAPS or ending with :)
      if (trimmed === trimmed.toUpperCase() && trimmed.includes(':')) {
        sections.push({ text: trimmed, isHeader: true });
      }
      // Check for numbered action items (e.g., "1. ADD", "2. REDUCE")
      else if (/^\d+\.\s+(ADD|REDUCE|REMOVE|CREATE|UPDATE)/i.test(trimmed)) {
        sections.push({ text: trimmed, isAction: true });
      }
      // Check for arrow action items (→)
      else if (trimmed.startsWith('→')) {
        sections.push({ text: trimmed.substring(1).trim(), isAction: true });
      }
      // Check for problem indicators (❌)
      else if (trimmed.includes('❌')) {
        sections.push({ text: trimmed.replace('❌', '').trim(), isProblem: true });
      }
      // Check for bullet points
      else if (trimmed.startsWith('•')) {
        sections.push({ text: trimmed.substring(1).trim(), isBullet: true });
      }
      // Regular text
      else {
        sections.push({ text: trimmed });
      }
    }
    
    return sections;
  }

  // Lesson breakdown popup methods
  showZoneBreakdown(zoneName: string) {
    // Find zone ID from the recommendation text or stored data
    // For now, we'll need to parse zone names from the recommendation
    this.breakdownLoading = true;
    this.showBreakdownPopup = true;
    
    // Extract zone ID from a map we'll need to build
    // For simplicity, we'll call the API with zone name matching
    this.api.getLessonBreakdown(undefined, undefined).subscribe({
      next: () => {
        // We need zone ID - let's fetch all zones first
        this.loadZoneBreakdown(zoneName);
      },
      error: () => {
        this.breakdownLoading = false;
      }
    });
  }

  private loadZoneBreakdown(zoneName: string) {
    // Get all zones and find the ID
    this.api.getZones().subscribe({
      next: (zones) => {
        const zone = zones.find(z => z.name === zoneName);
        if (zone) {
          this.api.getLessonBreakdown(zone.id, undefined).subscribe({
            next: (breakdown) => {
              this.lessonBreakdown = breakdown;
              this.breakdownLoading = false;
            },
            error: () => {
              this.breakdownLoading = false;
            }
          });
        } else {
          this.breakdownLoading = false;
        }
      },
      error: () => {
        this.breakdownLoading = false;
      }
    });
  }

  showFeatureBreakdown(featureName: string) {
    this.breakdownLoading = true;
    this.showBreakdownPopup = true;
    
    this.api.getFeatures().subscribe({
      next: (features) => {
        const feature = features.find(f => f.name === featureName);
        if (feature) {
          this.api.getLessonBreakdown(undefined, feature.id).subscribe({
            next: (breakdown) => {
              this.lessonBreakdown = breakdown;
              this.breakdownLoading = false;
            },
            error: () => {
              this.breakdownLoading = false;
            }
          });
        } else {
          this.breakdownLoading = false;
        }
      },
      error: () => {
        this.breakdownLoading = false;
      }
    });
  }

  closeBreakdownPopup() {
    this.showBreakdownPopup = false;
    this.lessonBreakdown = null;
  }

  // Parse bullet text to identify clickable lesson counts
  parseBulletParts(text: string): BulletPart[] {
    const parts: BulletPart[] = [];
    
    // Pattern: "ZONE_NAME: ~N lessons (X hours) can use this zone"
    // or: "FEATURE_NAME: N lessons need this, X rooms have it"
    const zonePattern = /^([^:]+):\s*[~]?(\d+)\s+lessons?\s*\((\d+)\s+hours?\)/;
    const featurePattern = /^([^:]+):\s*(\d+)\s+lessons?\s+need\s+this/;
    
    let match = text.match(zonePattern);
    if (match) {
      const [, zoneName, lessonCount, hours] = match;
      parts.push({ text: zoneName + ': ', isClickable: false });
      parts.push({ text: `~${lessonCount} lessons`, isClickable: true, zoneName: zoneName.trim() });
      parts.push({ text: ` (${hours} hours)`, isClickable: false });
      // Add remaining text
      const remaining = text.substring(match[0].length);
      if (remaining) parts.push({ text: remaining, isClickable: false });
      return parts;
    }
    
    match = text.match(featurePattern);
    if (match) {
      const [, featureName, lessonCount] = match;
      parts.push({ text: featureName + ': ', isClickable: false });
      parts.push({ text: `${lessonCount} lessons`, isClickable: true, featureName: featureName.trim() });
      // Add remaining text
      const remaining = text.substring(match[0].length);
      if (remaining) parts.push({ text: remaining, isClickable: false });
      return parts;
    }
    
    // No special pattern, return whole text
    parts.push({ text, isClickable: false });
    return parts;
  }

  onLessonClick(part: BulletPart) {
    if (part.zoneName) {
      this.showZoneBreakdown(part.zoneName);
    } else if (part.featureName) {
      this.showFeatureBreakdown(part.featureName);
    }
  }
}
