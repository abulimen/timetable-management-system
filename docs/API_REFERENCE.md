# API Reference

Complete REST API documentation for the University Timetable Scheduling Engine.

**Base URL:** `http://localhost:8080/api/v1`

---

## Table of Contents
- [Solver API](#solver-api)
- [Timetable API](#timetable-api)
- [Lesson API](#lesson-api)
- [Import API](#import-api)
- [Semester Archive API](#semester-archive-api)
- [Settings API](#settings-api)

---

## Solver API

Base path: `/api/v1/solver`

### POST /solver/solve

Start the OptaPlanner solver to generate/update the timetable.

**Request Body:**
```json
{
  "mode": "FULL_REPLAN"   // or "STABILITY"
}
```

| Mode | Description |
|------|-------------|
| `FULL_REPLAN` | Re-schedule all lessons from scratch |
| `STABILITY` | Keep pinned lessons fixed, only schedule unpinned ones |

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "SOLVING",
  "score": "N/A"
}
```

**States:**
- `SOLVING` - Solver is running
- `NOT_SOLVING` - Solver finished or not started
- `ERROR` - Solver failed to start

---

### GET /solver/status

Get the current solver status.

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "NOT_SOLVING",
  "score": "0hard/-50soft"
}
```

**Score Interpretation:**
- `0hard` = All hard constraints satisfied (valid solution)
- `-3hard` = 3 hard constraint violations (invalid solution)
- `-50soft` = Soft constraint penalty (lower is better)

---

### POST /solver/terminate

Stop the solver early and save the current best solution.

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "state": "TERMINATED",
  "score": "Final solution saved"
}
```

---

### GET /solver/feasibility

**Pre-solve validation.** Run BEFORE solving to catch impossible constraints.

**Response:**
```json
{
  "feasible": false,
  "blockingCount": 2,
  "warningCount": 1,
  "lessonCount": 100,
  "timeslotCount": 45,
  "roomCount": 10,
  "availableRoomSlots": 450,
  "issues": [
    {
      "type": "CAPACITY_EXCEEDED",
      "severity": "BLOCKING",
      "description": "Course 'ENG101' has 300 students but largest room has 200 capacity",
      "recommendation": "Split into smaller groups or add larger room"
    },
    {
      "type": "HIGH_UTILIZATION",
      "severity": "WARNING",
      "description": "High utilization: 95 lessons using 95% of 100 available slots",
      "recommendation": "Solution may be tight. Consider adding capacity"
    }
  ]
}
```

**Issue Types:**
| Type | Severity | Description |
|------|----------|-------------|
| `INSUFFICIENT_SLOTS` | BLOCKING | More lessons than room-slots |
| `CAPACITY_EXCEEDED` | BLOCKING | Group too large for any room |
| `FEATURE_MISMATCH` | BLOCKING | No room has required features |
| `ZONE_MISMATCH` | BLOCKING | No room in allowed zone |
| `LECTURER_OVERLOAD` | BLOCKING | Lecturer hours exceed availability |
| `HIGH_UTILIZATION` | WARNING | Near capacity limits |

---

### GET /solver/analysis

**Constraint violation analysis.** Run AFTER solving to understand why score is not 0hard.

**Response:**
```json
{
  "score": "-2hard/-35soft",
  "feasible": false,
  "hardViolationCount": 2,
  "softPenalty": 35,
  "hardViolations": [
    {
      "constraintName": "Room capacity overflow",
      "matchCount": 2,
      "scoreImpact": "-2hard/0soft",
      "weight": 0,
      "details": [
        {
          "entity": "CS101-Part1",
          "description": "120 students in ENG-LAB1 (capacity: 30)",
          "recommendation": "Assign to larger room or split group"
        }
      ]
    }
  ],
  "softViolations": [
    {
      "constraintName": "Room capacity efficiency",
      "matchCount": 5,
      "scoreImpact": "0hard/-15soft",
      "details": [...]
    }
  ]
}
```

---

## Timetable API

Base path: `/api/v1/timetable`

### GET /timetable

Get the scheduled timetable with optional filters.

**Query Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `student_group_id` | Long | Filter by student group |
| `lecturer_id` | Long | Filter by lecturer |
| `room_id` | Long | Filter by room |

**Response:**
```json
[
  {
    "lessonId": 1,
    "courseCode": "CS101",
    "courseName": "Introduction to Programming",
    "partNumber": 1,
    "durationHours": 2,
    "dayOfWeek": "MONDAY",
    "startTime": "09:00",
    "endTime": "11:00",
    "roomId": 3,
    "roomName": "MAIN-HALL",
    "roomCapacity": 200,
    "lecturerId": 2,
    "lecturerName": "Dr. Smith",
    "studentGroupId": 1,
    "studentGroupName": "CS Year 1",
    "studentGroupSize": 120,
    "pinned": false,
    "scheduled": true
  }
]
```

**Examples:**
```bash
# Get all lessons
curl http://localhost:8080/api/v1/timetable

# Get lessons for student group 1
curl "http://localhost:8080/api/v1/timetable?student_group_id=1"

# Get lessons for lecturer 2
curl "http://localhost:8080/api/v1/timetable?lecturer_id=2"

# Get lessons in room 3
curl "http://localhost:8080/api/v1/timetable?room_id=3"
```

---

## Lesson API

Base path: `/api/v1/lessons`

### GET /lessons/{id}

Get a single lesson by ID.

**Response:** Same as timetable entry.

---

### PATCH /lessons/{id}

Update a lesson's timeslot, room, or pinned status.

**Request Body:**
```json
{
  "assignedTimeslotId": 15,
  "assignedRoomId": 3,
  "pinned": true
}
```

All fields are optional. Include only what you want to update.

**Response:** Updated lesson DTO.

**Use Cases:**
- Manually fix a lesson to a specific timeslot/room
- Pin a lesson so solver doesn't move it
- Override solver's assignment

---

## Import API

Base path: `/api/v1/import`

### POST /import/upload

Upload an Excel file to import data (zones, rooms, lecturers, courses, etc.).

**Request:**
- Content-Type: `multipart/form-data`
- Body: Excel file (.xlsx)

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/v1/import/upload \
  -F "file=@university_data.xlsx"
```

**Response:**
```json
{
  "success": true,
  "zonesImported": 3,
  "roomsImported": 10,
  "lecturersImported": 15,
  "coursesImported": 25,
  "lessonsGenerated": 50,
  "errors": []
}
```

**Excel Sheet Format:**
See [SYSTEM_INPUTS.md](SYSTEM_INPUTS.md) for expected sheet structure.

---

## Bulk Operations API

Base path: `/api/v1/bulk`

Bulk import, export, and delete operations.

### GET /bulk/{entity}/template

Download a CSV template for the specified entity.

**Supported Entities:**
- `lecturers`
- `rooms`
- `student-groups`
- `zones`
- `features`
- `courses`

**Response:** Plain text CSV template

**Example:**
```bash
curl http://localhost:8080/api/v1/bulk/courses/template
```

**Response:**
```csv
code,name,weekly_hours,lecturer_email,student_group_name,is_online
COSC101,Introduction to Programming,3,john.smith@university.edu,COSC_1A,false
```

---

### POST /bulk/{entity}/import

Import data from a CSV file.

**Request:**
- Content-Type: `multipart/form-data`
- Body: CSV file

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/bulk/courses/import \
  -F "file=@courses.csv"
```

**Response:**
```json
{
  "created": 15,
  "skipped": 2,
  "errors": [
    "Row 5: Lecturer with email 'unknown@uni.edu' not found"
  ]
}
```

---

### DELETE /bulk/{entity}/all

Delete all records of the specified entity.

**Request Body:**
```json
{
  "confirm": true
}
```

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/v1/bulk/courses/all \
  -H "Content-Type: application/json" \
  -d '{"confirm": true}'
```

**Response:**
```json
{
  "message": "Deleted all courses successfully",
  "deletedCount": 25
}
```

---

### DELETE /bulk/wipe

System-wide data wipe (all entities).

**Request Body:**
```json
{
  "confirm": true
}
```

**What gets deleted:**
- Lessons
- Courses
- Lecturers
- Student Groups
- Rooms
- Zones
- Features

**Response:**
```json
{
  "message": "System wipe completed",
  "deletedLessons": 100,
  "deletedCourses": 25,
  "deletedRooms": 10
}
```

> ⚠️ **Warning:** This is irreversible! Always archive semester data first.

---

## Semester Archive API

Base path: `/api/v1/semesters`

Archive current semester data to prefixed tables for historical preservation.

### GET /semesters/archives

List all archived semesters.

**Response:**
```json
[
  {
    "id": 1,
    "code": "2024_2025_S1",
    "name": "2024/2025 1st Semester",
    "academicYear": "2024/2025",
    "semesterNumber": 1,
    "archivedAt": "2025-01-15T14:30:00",
    "courseCount": 25,
    "lessonCount": 50,
    "studentGroupCount": 10,
    "lecturerCount": 15
  }
]
```

---

### POST /semesters/archive

Archive current semester data to prefixed tables, then clear main tables.

**Request Body:**
```json
{
  "code": "2024_2025_S1",
  "name": "2024/2025 1st Semester",
  "academicYear": "2024/2025",
  "semesterNumber": 1
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `code` | Yes | Unique identifier (alphanumeric + underscores) |
| `name` | No | Human-readable name |
| `academicYear` | No | Academic year (e.g., "2024/2025") |
| `semesterNumber` | No | 1 or 2 |

**Response:**
```json
{
  "message": "Semester archived successfully",
  "archive": {
    "id": 1,
    "code": "2024_2025_S1",
    "courseCount": 25,
    "lessonCount": 50
  }
}
```

**What Happens:**
1. Creates archive tables with prefix (e.g., `2024_2025_S1_course`)
2. Copies all data to archive tables
3. Clears main tables (course, lesson, student_group, lecturer)
4. Rooms, zones, features remain (shared across semesters)

---

### GET /semesters/{code}

Get archive metadata by code.

**Response:** Same as single item in archives list.

---

### GET /semesters/{code}/timetable

View timetable from an archived semester.

**Response:** Same format as `/api/v1/timetable`

**Example:**
```bash
curl http://localhost:8080/api/v1/semesters/2024_2025_S1/timetable
```

---

### DELETE /semesters/{code}

Delete an archive and drop all its tables.

**Response:**
```json
{
  "message": "Archive deleted successfully",
  "code": "2024_2025_S1"
}
```

> **Warning:** This permanently deletes all archived data for the semester.

---

## Settings API

Base path: `/api/v1/settings`

### GET /settings

Get all constraint settings.

**Response:**
```json
[
  {
    "key": "lunch_break_start",
    "value": "12:00",
    "category": "TIMING",
    "description": "Start of lunch break period"
  },
  {
    "key": "weight_room_capacity",
    "value": "1",
    "category": "WEIGHT",
    "description": "Penalty for oversized rooms"
  }
]
```

---

### GET /settings/category/{category}

Get settings by category.

**Categories:**
- `TIMING` - Time-related settings
- `LIMIT` - Maximum values
- `WEIGHT` - Soft constraint weights
- `FEATURE` - Feature flags

**Example:**
```bash
curl http://localhost:8080/api/v1/settings/category/timing
```

---

### GET /settings/{key}

Get a single setting by key.

---

### PUT /settings/{key}

Update a setting's value.

**Request Body:**
```json
{
  "value": "13:00"
}
```

**Response:** Updated setting DTO.

---

### POST /settings/refresh

Refresh the settings cache after direct database updates.

**Response:**
```json
{
  "status": "Cache refreshed"
}
```

---

### GET /settings/summary

Get current effective settings summary grouped by type.

**Response:**
```json
{
  "timing": {
    "lunchBreakStart": "12:00",
    "lunchBreakEnd": "13:00",
    "earliestStartTime": "07:00",
    "latestEndTime": "18:00"
  },
  "limits": {
    "maxLecturerHoursPerDay": 6,
    "maxStudentConsecutiveHours": 3,
    "minBreakBetweenLessons": 10
  },
  "weights": {
    "roomCapacity": 1,
    "dayBalance": 2,
    "lecturerTransition": 5,
    "studentFatigue": 1
  },
  "features": {
    "lunchBreakEnforced": true,
    "dayBalanceEnforced": true,
    "sameCourseSameDayAllowed": false
  }
}
```

---

## Error Handling

All endpoints return consistent error responses:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Timeslot not found",
  "path": "/api/v1/lessons/1"
}
```

**HTTP Status Codes:**
| Code | Meaning |
|------|---------|
| 200 | Success |
| 400 | Bad Request (invalid input) |
| 404 | Resource not found |
| 500 | Internal server error |
