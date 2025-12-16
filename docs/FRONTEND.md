# Frontend Documentation

The Angular 17 frontend application for the University Timetable Scheduling System.

---

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Angular** | 17 | Framework |
| **TypeScript** | 5.x | Language |
| **Tailwind CSS** | 3.x | Styling |
| **RxJS** | 7.x | Reactive programming |

---

## Quick Start

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm start

# Build for production
npm run build
```

**Development server:** http://localhost:4200

---

## Project Structure

```
frontend/
├── src/
│   ├── app/
│   │   ├── core/
│   │   │   └── services/
│   │   │       └── api.service.ts    # All API calls
│   │   ├── features/                  # Page components
│   │   │   ├── dashboard/
│   │   │   ├── courses/
│   │   │   ├── lecturers/
│   │   │   ├── rooms/
│   │   │   ├── features/
│   │   │   ├── zones/
│   │   │   ├── student-groups/
│   │   │   ├── lessons/
│   │   │   ├── solver/
│   │   │   ├── timetable/
│   │   │   ├── semesters/
│   │   │   ├── import/
│   │   │   └── settings/
│   │   ├── layout/
│   │   │   └── sidebar/
│   │   ├── app.component.ts
│   │   ├── app.routes.ts
│   │   └── app.config.ts
│   ├── styles.scss                    # Global styles
│   └── index.html
├── tailwind.config.js
├── angular.json
└── package.json
```

---

## Pages

### Dashboard (`/dashboard`)

Overview page showing:
- Total courses, lessons, rooms
- Scheduled/unscheduled lesson counts
- Quick navigation to solver

### Data Management

| Page | Route | Features |
|------|-------|----------|
| Zones | `/zones` | CRUD, Delete All |
| Rooms | `/rooms` | CRUD, Zone assignment, Features |
| Features | `/features` | CRUD, Delete All |
| Lecturers | `/lecturers` | CRUD, Unavailability periods |
| Student Groups | `/student-groups` | CRUD, Hierarchical groups |
| Courses | `/courses` | CRUD, Online toggle, Lesson generation |
| Lessons | `/lessons` | View, Pin/Unpin |

### Scheduling

| Page | Route | Features |
|------|-------|----------|
| Solver | `/solver` | Feasibility check, Start/Stop solver |
| Timetable | `/timetable` | Grid view, Filters, Pinning |

### System

| Page | Route | Features |
|------|-------|----------|
| Semesters | `/semesters` | Archive, Restore, View history |
| Bulk Import | `/import` | Multi-step CSV import |
| Settings | `/settings` | Configure constraints, Danger Zone |

---

## Components

All components are **standalone** (Angular 17 pattern).

### Example Component Structure

```typescript
@Component({
  selector: 'app-courses',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `...`
})
export class CoursesComponent implements OnInit {
  private api = inject(ApiService);
  
  courses: Course[] = [];
  
  ngOnInit() {
    this.loadCourses();
  }
  
  loadCourses() {
    this.api.getCourses().subscribe({
      next: (courses) => this.courses = courses
    });
  }
}
```

---

## API Service

All backend communication goes through `ApiService`:

```typescript
// Location: src/app/core/services/api.service.ts

@Injectable({ providedIn: 'root' })
export class ApiService {
  private baseUrl = 'http://localhost:8080/api/v1';
  
  // Courses
  getCourses(): Observable<Course[]>
  createCourse(data: any): Observable<Course>
  updateCourse(id: number, data: any): Observable<Course>
  deleteCourse(id: number): Observable<void>
  
  // Solver
  startSolver(mode: string): Observable<SolverStatus>
  getSolverStatus(): Observable<SolverStatus>
  checkFeasibility(): Observable<FeasibilityResult>
  
  // Timetable
  getTimetable(params?: any): Observable<TimetableEntry[]>
  
  // ... and more
}
```

---

## Interfaces

Defined in `api.service.ts`:

```typescript
interface Course {
  id: number;
  code: string;
  name: string;
  totalWeeklyHours: number;
  lecturerId: number | null;
  online: boolean;  // NEW: Online course flag
}

interface TimetableEntry {
  lessonId: number;
  courseCode: string;
  dayOfWeek: string;
  startTime: string;
  roomName: string;
  online: boolean;  // NEW: Shows "🌐 Online" if true
  // ...
}
```

---

## Styling

### Tailwind CSS

The project uses Tailwind CSS for styling.

**Configuration:** `tailwind.config.js`

### Custom Classes

Common patterns used:

```html
<!-- Cards -->
<div class="card p-6">...</div>

<!-- Buttons -->
<button class="btn btn-primary">Save</button>
<button class="btn btn-secondary">Cancel</button>
<button class="btn btn-danger">Delete</button>

<!-- Inputs -->
<input class="input" type="text">
<select class="input">...</select>

<!-- Labels -->
<label class="label">Field Name</label>
```

### Dark Mode

The app supports dark mode:

```html
<div class="bg-white dark:bg-secondary-800">
  <p class="text-secondary-900 dark:text-white">Text</p>
</div>
```

---

## Routes

Defined in `app.routes.ts`:

```typescript
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'courses', component: CoursesComponent },
  { path: 'lecturers', component: LecturersComponent },
  // ... all routes
];
```

---

## Key Features

### Online Course Badge

```html
<span *ngIf="course.online" class="px-2 py-1 bg-blue-100 text-blue-700 text-xs rounded-full">
  🌐 Online
</span>
<span *ngIf="!course.online" class="px-2 py-1 bg-secondary-100 text-secondary-600 text-xs rounded-full">
  🏫 In-Person
</span>
```

### Bulk Import Workflow

The import page (`/import`) implements a multi-step workflow:
1. Shows required import order
2. Validates files before upload
3. Reports success/error counts
4. Tracks progress

### Danger Zone

Settings page includes a "Danger Zone" section:
- System-wide data wipe
- Requires typing "DELETE" to confirm
- Protected by confirmation modal

---

## Development

### Adding a New Page

1. Create component:
   ```bash
   cd frontend/src/app/features
   mkdir new-feature
   # Create new-feature.component.ts
   ```

2. Add to routes in `app.routes.ts`

3. Add to sidebar in `layout/sidebar/sidebar.component.ts`

### Adding API Calls

1. Add method to `ApiService`:
   ```typescript
   getNewData(): Observable<NewType[]> {
     return this.http.get<NewType[]>(`${this.baseUrl}/new-endpoint`);
   }
   ```

2. Add interface for the type

3. Use in component:
   ```typescript
   this.api.getNewData().subscribe({ next: (data) => this.data = data });
   ```

---

## Testing

```bash
# Run unit tests
npm test

# Run tests in watch mode
npm run test:watch
```

---

## Build

```bash
# Development build
npm run build

# Production build
npm run build -- --configuration production
```

Output goes to `dist/frontend/`

---

## Environment

For production, update the API base URL in `api.service.ts` or use environment files.
