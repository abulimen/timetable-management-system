# System Architecture

Technical architecture documentation for the University Timetable Scheduling Engine.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│   Web UI / Admin Dashboard / Mobile App / External Systems                  │
│                              ▼ REST API                                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            API LAYER (Spring Boot)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌────────────┐         │
│  │   Solver    │  │  Timetable   │  │   Lesson   │  │  Settings  │         │
│  │ Controller  │  │  Controller  │  │ Controller │  │ Controller │         │
│  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘  └─────┬──────┘         │
│         │                │                │                │                │
│         ▼                ▼                ▼                ▼                │
│  ┌─────────────────────────────────────────────────────────────────┐       │
│  │                      SERVICE LAYER                               │       │
│  ├─────────────────────────────────────────────────────────────────┤       │
│  │  SolverService │ TimetableService │ LessonService │ Settings... │       │
│  │  InfeasibilityChecker │ ConstraintJustificationService          │       │
│  └─────────────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          ▼                        ▼                        ▼
┌─────────────────┐    ┌─────────────────────┐    ┌─────────────────┐
│  SOLVER LAYER   │    │   PERSISTENCE LAYER │    │   MIGRATION     │
│  (OptaPlanner)  │    │   (JPA/Hibernate)   │    │   (Flyway)      │
├─────────────────┤    ├─────────────────────┤    ├─────────────────┤
│ SolverManager   │    │ LessonRepository    │    │ V1__base_tables │
│ SolutionManager │    │ RoomRepository      │    │ V2__lecturers   │
│ ConstraintProv. │    │ CourseRepository    │    │ ...             │
│ ScoreManager    │    │ TimeslotRepository  │    │ V8__combined    │
└────────┬────────┘    └─────────┬───────────┘    └────────┬────────┘
         │                       │                         │
         └───────────────────────┼─────────────────────────┘
                                 ▼
                    ┌─────────────────────────┐
                    │    DATABASE (MySQL)     │
                    ├─────────────────────────┤
                    │  lesson, course, room,  │
                    │  timeslot, lecturer,    │
                    │  student_group, zone,   │
                    │  feature, constraint... │
                    └─────────────────────────┘
```

---

## Component Details

### 1. Controller Layer

REST API controllers handling HTTP requests.

| Controller | Responsibility |
|------------|----------------|
| `SolverController` | Solver operations (start, stop, status, analysis) |
| `TimetableController` | Retrieve generated timetables |
| `LessonController` | Update individual lessons |
| `ImportController` | Excel file data import |
| `SettingsController` | Constraint settings CRUD |
| `SemesterArchiveController` | Semester archiving operations |

### 2. Service Layer

Business logic and orchestration.

| Service | Responsibility |
|---------|----------------|
| `SolverService` | Manages async OptaPlanner solving |
| `TimetableService` | Queries and filters timetable data |
| `LessonService` | Lesson generation from courses |
| `InfeasibilityChecker` | Pre-solve validation (5 checks) |
| `ConstraintJustificationService` | Score explanation |
| `ConstraintSettingsService` | Settings cache and retrieval |
| `IngestionService` | Excel parsing and data import |
| `TimeslotService` | Generates timeslots from settings |
| `SolutionSaver` | Persists solver results to DB |
| `SemesterArchiveService` | Archives semesters to prefixed tables |

### 3. Solver Layer (OptaPlanner)

Constraint-based scheduling engine.

| Component | Responsibility |
|-----------|----------------|
| `SolverFactory` | Creates solver instances |
| `SolverManager` | Async solver lifecycle |
| `SolutionManager` | Score calculation and explanation |
| `TimetableConstraintProvider` | Defines hard/soft constraints |
| `TimeTable` | Planning solution (lessons + timeslots + rooms) |
| `Lesson` | Planning entity with `@PlanningVariable` |

### 4. Domain Layer

JPA entities and OptaPlanner annotations.

| Entity | Role |
|--------|------|
| `Lesson` | **Planning Entity** - gets assigned timeslot + room |
| `Timeslot` | **Planning Value** - day/time slots |
| `Room` | **Planning Value** - physical rooms |
| `Course` | Parent of lessons, links to student groups |
| `Lecturer` | Teacher with unavailabilities |
| `StudentGroup` | Class cohort with parent/child hierarchy |
| `Zone` | Building/area grouping |
| `Feature` | Room capability (projector, lab, etc.) |
| `ConstraintSetting` | Runtime-configurable parameters |
| `SemesterArchive` | Archived semester metadata |

---

## Data Model

### Entity Relationships

```
Zone (1) ◀───── (N) Room (N) ─────▶ (M) Feature
  │                   │
  │                   └──────────────────────────┐
  ▼                                              ▼
StudentGroup (N) ─────▶ (M) Course (1) ◀──── Lecturer
     │                       │
     │                       ▼
     │               Lesson (N) ◀───── Timeslot
     │                   │
     └───────────────────┘
           (through Course)
```

### Key Relationships

| Relationship | Type | Description |
|-------------|------|-------------|
| Course → StudentGroups | Many-to-Many | Combined classes support |
| Course → Lessons | One-to-Many | Auto-generated from weeklyHours |
| Room → Features | Many-to-Many | Room capabilities |
| Room → Zone | Many-to-One | Building grouping |
| Lecturer → Unavailabilities | One-to-Many | Blocked time periods |
| StudentGroup → Parent | Many-to-One | Hierarchical groups |

---

## Solving Process

```
┌─────────────────────────────────────────────────────────────────┐
│                      SOLVING WORKFLOW                            │
└─────────────────────────────────────────────────────────────────┘

1. LOAD PROBLEM
   ├── Load lessons from DB (unassigned or partially assigned)
   ├── Load timeslots (generated from settings)
   ├── Load rooms with features
   └── Create TimeTable planning solution

2. PRE-SOLVE VALIDATION (Optional)
   ├── Check room capacity for all groups
   ├── Check feature availability
   ├── Check zone compatibility
   ├── Check lecturer availability
   └── Return InfeasibilityReport

3. CONSTRUCTION HEURISTIC
   ├── Assign initial timeslot + room to each lesson
   └── Use FIRST_FIT strategy (fast initial solution)

4. LOCAL SEARCH
   ├── Evaluate moves (swap timeslot, change room)
   ├── Apply Tabu Search to avoid cycling
   ├── Score each solution with ConstraintProvider
   └── Save best solution on improvement

5. TERMINATION
   ├── Time limit reached (30 seconds default)
   ├── Score limit reached (0hard)
   └── Manual termination requested

6. PERSIST SOLUTION
   └── Update lesson timeslot/room in database
```

---

## Constraint Architecture

### Hard Constraints (Must Satisfy)

| Constraint | Description |
|------------|-------------|
| `roomConflict` | No double-booking rooms |
| `lecturerConflict` | No overlapping lecturer assignments |
| `studentGroupConflict` | No overlapping student group lessons |
| `roomCapacityOverflow` | Room must fit all students |
| `roomFeatureRequired` | Room must have required features |
| `zoneRestriction` | Course must be in allowed zone |
| `lecturerUnavailability` | Respect lecturer blocked times |
| `lunchBreakOverlap` | No lessons during lunch |
| `lessonExceedsEndTime` | Lessons must end by limit |
| `sameCourseOnSameDay` | (Configurable) Same course parts on different days |

### Soft Constraints (Optimize)

| Constraint | Weight | Description |
|------------|--------|-------------|
| `roomCapacityEfficiency` | 1 | Prefer rooms matching group size |
| `studentFatigue` | 1 | Avoid long consecutive lessons |
| `lecturerRoomTransition` | 5 | Minimize room changes |
| `dayBalanceForStudentGroup` | 2 | Balance lessons across days |
| `earlyMorningPenalty` | 3 | Avoid 7am classes |

---

## Configuration

### Application Properties

Located in `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/timetable_db
    username: root
    password: ""
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway handles schema
  flyway:
    enabled: true
```

### Solver Configuration

Located in `src/main/resources/solver-config.xml`:

```xml
<solver>
  <solutionClass>...TimeTable</solutionClass>
  <entityClass>...Lesson</entityClass>
  
  <scoreDirectorFactory>
    <constraintProviderClass>...TimetableConstraintProvider</constraintProviderClass>
  </scoreDirectorFactory>
  
  <termination>
    <secondsSpentLimit>30</secondsSpentLimit>
  </termination>
  
  <constructionHeuristic>
    <constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
  </constructionHeuristic>
  
  <localSearch>
    <acceptor>
      <entityTabuSize>7</entityTabuSize>
    </acceptor>
  </localSearch>
</solver>
```

---

## Database Schema

### Flyway Migrations (V1-V9)

| Migration | Description |
|-----------|-------------|
| V1 | Base tables (zone, feature, room) |
| V2 | Lecturer tables with unavailabilities |
| V3 | Student group with hierarchy |
| V4 | Course tables with features/zones |
| V5 | Timetable tables (timeslot, lesson) |
| V6 | Sample data for testing |
| V7 | Constraint settings table |
| V8 | Course-StudentGroup many-to-many |
| V9 | Semester archive metadata |

---

## Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Framework | Spring Boot | 3.2.5 |
| Solver | OptaPlanner | 9.44.0 |
| ORM | Hibernate | 6.4.4 |
| Database | MySQL | 8.0 |
| Migrations | Flyway | (Spring Boot default) |
| Build | Maven | 3.8+ |
| Java | OpenJDK | 17 |
| Excel Parsing | Apache POI | 5.2.5 |
