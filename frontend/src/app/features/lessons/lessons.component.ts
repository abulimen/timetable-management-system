import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-lessons',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="space-y-6">
      <h1 class="text-2xl font-bold text-secondary-900 dark:text-white">Lesson Management</h1>
      <div class="card p-6">
        <p class="text-secondary-500">View and manage lessons in the Timetable view. Click on any lesson to pin/unpin it.</p>
        <a routerLink="/timetable" class="btn btn-primary mt-4 inline-block">Go to Timetable</a>
      </div>
    </div>
  `
})
export class LessonsComponent { }
