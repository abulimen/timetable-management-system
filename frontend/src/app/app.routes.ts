import { Routes } from '@angular/router';
import { authGuard, coordinatorGuard, adminGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
    {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
    },
    {
        path: 'login',
        loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
        canActivate: [guestGuard]
    },
    {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
        canActivate: [authGuard]
    },
    {
        path: 'timetable',
        loadComponent: () => import('./features/timetable/timetable.component').then(m => m.TimetableComponent),
        canActivate: [authGuard]
    },
    {
        path: 'zones',
        loadComponent: () => import('./features/zones/zones.component').then(m => m.ZonesComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'rooms',
        loadComponent: () => import('./features/rooms/rooms.component').then(m => m.RoomsComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'features',
        loadComponent: () => import('./features/features/features.component').then(m => m.FeaturesComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'lecturers',
        loadComponent: () => import('./features/lecturers/lecturers.component').then(m => m.LecturersComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'student-groups',
        loadComponent: () => import('./features/student-groups/student-groups.component').then(m => m.StudentGroupsComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'courses',
        loadComponent: () => import('./features/courses/courses.component').then(m => m.CoursesComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'solver',
        loadComponent: () => import('./features/solver/solver.component').then(m => m.SolverComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'semesters',
        loadComponent: () => import('./features/semesters/semesters.component').then(m => m.SemestersComponent),
        canActivate: [authGuard, adminGuard]
    },
    {
        path: 'import',
        loadComponent: () => import('./features/import/import.component').then(m => m.ImportComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'special-events',
        loadComponent: () => import('./features/special-events/special-events.component').then(m => m.SpecialEventsComponent),
        canActivate: [authGuard, coordinatorGuard]
    },
    {
        path: 'export',
        loadComponent: () => import('./features/export/export.component').then(m => m.ExportComponent),
        canActivate: [authGuard]
    },
    {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent),
        canActivate: [authGuard, adminGuard]
    },
    {
        path: 'users',
        loadComponent: () => import('./features/users/users.component').then(m => m.UsersComponent),
        canActivate: [authGuard, adminGuard]
    },
    {
        path: '**',
        redirectTo: 'dashboard'
    }
];
