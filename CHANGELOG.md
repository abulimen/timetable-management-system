# Changelog

All notable changes to the University Timetable Scheduling Engine.

---

## [1.2.0] - 2025-12-16

### Added

#### Online Classes Support
- Courses can now be marked as **online** (no physical room required)
- Online lessons get timeslots assigned but skip room allocation
- No capacity limits for online classes
- Timetable displays "🌐 Online" instead of room name
- Bulk import supports `is_online` column for courses

#### Bulk Import System
- **Dedicated Import Page** (`/import`) with multi-step guided workflow
- Import order enforced: Zones → Features → Lecturers → Student Groups → Rooms → Courses
- Client-side cross-file validation before upload
- CSV templates downloadable for each entity type
- Progress tracking and detailed error reporting

#### Features Management
- New **Features page** (`/features`) for CRUD operations on room capabilities
- Features can be bulk imported via dedicated step
- Examples: Projector, Lab Equipment, Computers, Wet Lab

#### New Constraints
- **Lecturer Fatigue** (Soft): Penalizes consecutive teaching hours for lecturers
- Setting: `max_lecturer_consecutive_hours` (default: 4)

#### Settings Enhancements
- **Regenerate Timeslots** button: Apply timing changes without restart
- **System Wipe** in Danger Zone: Delete all data with confirmation
- **Friday End Time** setting: Separate end time for Fridays
- **Early Morning Weight**: Configurable penalty for 7am classes

#### Frontend Improvements
- Angular 17 standalone components
- Dark mode support
- Responsive sidebar navigation
- Visual badges for Online/In-Person courses

### Database
- **V17**: `friday_latest_end_time` setting
- **V18**: `weight_early_morning` setting
- **V19**: `max_lecturer_consecutive_hours` setting
- **V20**: `is_online` column on course table

### API Additions
- `POST /api/v1/bulk/{entity}/import` - Bulk import from CSV
- `GET /api/v1/bulk/{entity}/template` - Download CSV template
- `DELETE /api/v1/bulk/{entity}/all` - Bulk delete entity
- `POST /api/v1/settings/regenerate-timeslots` - Regenerate timeslots
- `DELETE /api/v1/bulk/wipe` - System-wide data wipe

---

## [1.1.0] - 2025-12-14

### Added
- **Semester Archive System** - Archive current semester data to prefixed tables
  - `POST /semesters/archive` - Archive and clear for new semester
  - `GET /semesters/archives` - List all archived semesters
  - `GET /semesters/{code}/timetable` - View archived timetable
  - `DELETE /semesters/{code}` - Delete archive and drop tables
- New Flyway migration V9 for `semester_archive` metadata table

### Database
- Added `semester_archive` table for tracking archived semesters
- Archive tables created dynamically with prefix (e.g., `2024_2025_S1_course`)

---

## [1.0.0] - 2025-12-13

### Initial Release

Complete OptaPlanner-based university timetable scheduling engine.

### Features

#### Core Scheduling
- Automated lesson scheduling using OptaPlanner constraint solver
- Support for courses, lecturers, student groups, rooms, and timeslots
- 10 hard constraints and 5 soft constraints
- Configurable solving time and parameters

#### Enterprise Features
- **Pre-solve feasibility check** - Validates data before solving
- **Constraint justification API** - Explains exactly why constraints are violated
- **Combined student groups** - Multiple groups can share a course
- **Runtime-configurable constraints** - Adjust weights and settings via API

#### API Endpoints
- `/api/v1/solver/*` - Solver operations
- `/api/v1/timetable` - Retrieve timetables
- `/api/v1/lessons/*` - Lesson management
- `/api/v1/import/upload` - Excel data import
- `/api/v1/settings/*` - Constraint configuration

#### Data Model
- Hierarchical student groups (parent/child for batch lectures)
- Zone-based room organization
- Room features for capability matching
- Lecturer unavailability periods
- Course → Multiple StudentGroups (Many-to-Many)

#### Documentation
- README with quick start guide
- Complete API reference
- System architecture documentation
- Setup and deployment guide
- Constraint catalog
- Performance optimization guide

### Database
- 8 Flyway migrations (V1-V8)
- MySQL 8.0 support
- Sample data for testing

### Technical Stack
- Java 17
- Spring Boot 3.2.5
- OptaPlanner 9.44.0
- Hibernate 6.4.4
- Flyway for migrations
- Apache POI for Excel import

---

## Migration Notes

### From v1.1.0 to v1.2.0

1. **Database migrations** V17-V20 will run automatically
2. New **Frontend** Angular application at `frontend/` directory
3. Bulk import buttons removed from individual entity pages (use `/import` instead)

### From Earlier Development Versions

If upgrading from an earlier development version:

1. **Database migration V8** adds `course_student_group` table for combined groups
2. **Room capacity** is now a HARD constraint (was soft)
3. New endpoints: `/solver/feasibility` and `/solver/analysis`

### Fresh Installation

For new installations, simply run:
```bash
mysql -u root -e "CREATE DATABASE timetable_db;"
mvn spring-boot:run
```

Flyway automatically applies all migrations.
