import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService, Stats, SolverStatus } from '../../core/services/api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Dashboard</h1>

      <!-- Stats Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="card p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-secondary-500 dark:text-secondary-400">Courses</p>
              <p class="text-3xl font-bold text-secondary-900 dark:text-white">{{ stats?.courseCount || 0 }}</p>
            </div>
            <div class="w-12 h-12 bg-blue-100 dark:bg-blue-900 rounded-lg flex items-center justify-center">
              <svg class="w-6 h-6 text-blue-600 dark:text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
              </svg>
            </div>
          </div>
        </div>

        <div class="card p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-secondary-500 dark:text-secondary-400">Lessons</p>
              <p class="text-3xl font-bold text-secondary-900 dark:text-white">{{ stats?.lessonCount || 0 }}</p>
              <p class="text-xs text-green-600 dark:text-green-400">{{ stats?.scheduledLessonCount || 0 }} scheduled</p>
            </div>
            <div class="w-12 h-12 bg-green-100 dark:bg-green-900 rounded-lg flex items-center justify-center">
              <svg class="w-6 h-6 text-green-600 dark:text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
              </svg>
            </div>
          </div>
        </div>

        <div class="card p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-secondary-500 dark:text-secondary-400">Rooms</p>
              <p class="text-3xl font-bold text-secondary-900 dark:text-white">{{ stats?.roomCount || 0 }}</p>
            </div>
            <div class="w-12 h-12 bg-purple-100 dark:bg-purple-900 rounded-lg flex items-center justify-center">
              <svg class="w-6 h-6 text-purple-600 dark:text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"/>
              </svg>
            </div>
          </div>
        </div>

        <div class="card p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-secondary-500 dark:text-secondary-400">Lecturers</p>
              <p class="text-3xl font-bold text-secondary-900 dark:text-white">{{ stats?.lecturerCount || 0 }}</p>
            </div>
            <div class="w-12 h-12 bg-orange-100 dark:bg-orange-900 rounded-lg flex items-center justify-center">
              <svg class="w-6 h-6 text-orange-600 dark:text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"/>
              </svg>
            </div>
          </div>
        </div>
      </div>

      <!-- Solver Status & Quick Actions -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Solver Status -->
        <div class="card p-6">
          <h2 class="text-lg font-semibold text-secondary-900 dark:text-white mb-4">Solver Status</h2>
          <div class="flex items-center gap-4">
            <div class="flex-1">
              <div class="flex items-center gap-2 mb-2">
                <span 
                  class="w-3 h-3 rounded-full"
                  [ngClass]="{
                    'bg-gray-400': solverStatus?.state === 'NOT_SOLVING',
                    'bg-green-500 animate-pulse': solverStatus?.state === 'SOLVING_ACTIVE' || solverStatus?.state === 'SOLVING',
                    'bg-red-500': solverStatus?.state === 'ERROR'
                  }">
                </span>
                <span class="font-medium text-secondary-900 dark:text-white">
                  {{ solverStatus?.state || 'Not Running' }}
                </span>
                <span *ngIf="isSolving" class="text-xs text-secondary-500">(polling)</span>
              </div>
              <p class="text-sm text-secondary-500 dark:text-secondary-400">
                Score: {{ solverStatus?.score || 'N/A' }}
              </p>
            </div>
            <button 
              (click)="startSolver()"
              [disabled]="isSolving"
              class="btn btn-primary disabled:opacity-50 disabled:cursor-not-allowed">
              Start Solver
            </button>
          </div>
        </div>

        <!-- Quick Actions -->
        <div class="card p-6">
          <h2 class="text-lg font-semibold text-secondary-900 dark:text-white mb-4">Quick Actions</h2>
          <div class="grid grid-cols-2 gap-3">
            <a routerLink="/timetable" class="btn btn-secondary text-center">View Timetable</a>
            <a routerLink="/courses" class="btn btn-secondary text-center">Manage Courses</a>
            <a routerLink="/solver" class="btn btn-secondary text-center">Solver Controls</a>
            <a routerLink="/semesters" class="btn btn-secondary text-center">Archives</a>
          </div>
        </div>
      </div>

      <!-- Additional Stats -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="card p-4">
          <p class="text-sm text-secondary-500 dark:text-secondary-400">Student Groups</p>
          <p class="text-2xl font-bold text-secondary-900 dark:text-white">{{ stats?.studentGroupCount || 0 }}</p>
        </div>
        <div class="card p-4">
          <p class="text-sm text-secondary-500 dark:text-secondary-400">Zones</p>
          <p class="text-2xl font-bold text-secondary-900 dark:text-white">{{ stats?.zoneCount || 0 }}</p>
        </div>
        <div class="card p-4">
          <p class="text-sm text-secondary-500 dark:text-secondary-400">Timeslots</p>
          <p class="text-2xl font-bold text-secondary-900 dark:text-white">{{ stats?.timeslotCount || 0 }}</p>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class DashboardComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);

  stats: Stats | null = null;
  solverStatus: SolverStatus | null = null;
  private pollInterval: any = null;

  get isSolving() {
    return this.solverStatus?.state === 'SOLVING_ACTIVE' || this.solverStatus?.state === 'SOLVING';
  }

  ngOnInit() {
    this.loadStats();
    this.loadSolverStatus();
  }

  ngOnDestroy() {
    this.stopPolling();
  }

  loadStats() {
    this.api.getStats().subscribe({
      next: (stats) => this.stats = stats,
      error: (err) => console.error('Failed to load stats', err)
    });
  }

  loadSolverStatus() {
    this.api.getSolverStatus().subscribe({
      next: (status) => {
        this.solverStatus = status;
        // Start polling if solver is already active on page load
        if (this.isSolving && !this.pollInterval) {
          this.startPolling();
        }
      },
      error: (err) => console.error('Failed to load solver status', err)
    });
  }

  private startPolling() {
    this.stopPolling(); // Clear any existing interval
    this.pollInterval = setInterval(() => {
      this.api.getSolverStatus().subscribe({
        next: (s) => {
          this.solverStatus = s;
          if (!this.isSolving) {
            this.stopPolling();
            // Refresh stats when solver completes
            this.loadStats();
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

  startSolver() {
    this.api.startSolver('FULL_REPLAN').subscribe({
      next: (status) => {
        this.solverStatus = status;
        this.startPolling();
      },
      error: (err) => console.error('Failed to start solver', err)
    });
  }
}

