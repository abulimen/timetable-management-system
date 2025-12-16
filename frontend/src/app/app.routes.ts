import { Routes } from '@angular/router';

export const routes: Routes = [
    {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
    },
    {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
    },
    {
        path: 'timetable',
        loadComponent: () => import('./features/timetable/timetable.component').then(m => m.TimetableComponent)
    },
    {
        path: 'zones',
        loadComponent: () => import('./features/zones/zones.component').then(m => m.ZonesComponent)
    },
    {
        path: 'rooms',
        loadComponent: () => import('./features/rooms/rooms.component').then(m => m.RoomsComponent)
    },
    {
        path: 'features',
        loadComponent: () => import('./features/features/features.component').then(m => m.FeaturesComponent)
    },
    {
        path: 'lecturers',
        loadComponent: () => import('./features/lecturers/lecturers.component').then(m => m.LecturersComponent)
    },
    {
        path: 'student-groups',
        loadComponent: () => import('./features/student-groups/student-groups.component').then(m => m.StudentGroupsComponent)
    },
    {
        path: 'courses',
        loadComponent: () => import('./features/courses/courses.component').then(m => m.CoursesComponent)
    },
    {
        path: 'solver',
        loadComponent: () => import('./features/solver/solver.component').then(m => m.SolverComponent)
    },
    {
        path: 'semesters',
        loadComponent: () => import('./features/semesters/semesters.component').then(m => m.SemestersComponent)
    },
    {
        path: 'import',
        loadComponent: () => import('./features/import/import.component').then(m => m.ImportComponent)
    },
    {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
    }
];

