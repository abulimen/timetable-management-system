# Timetable Engine - System Inputs

This document describes all the data inputs required for the university timetable engine to function properly.

---

## Overview

The timetable engine uses OptaPlanner to automatically schedule lessons into timeslots and rooms while respecting various hard and soft constraints. For the solver to work, it requires properly configured input data.

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        MASTER DATA (Admin Setup)                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌──────────┐     ┌──────────┐     ┌──────────────────┐               │
│   │  Zones   │────▶│  Rooms   │◀────│    Features      │               │
│   └──────────┘     └──────────┘     └──────────────────┘               │
│        │                                      │                         │
│        ▼                                      ▼                         │
│   ┌──────────────┐              ┌─────────────────────┐                │
│   │Student Groups│─────────────▶│      Courses        │                │
│   └──────────────┘              └─────────────────────┘                │
│                                          │                              │
│   ┌──────────────┐                       │                              │
│   │  Lecturers   │───────────────────────┼──────────────────┐          │
│   └──────────────┘                       │                  │          │
│        │                                 ▼                  │          │
│        │                          ┌──────────┐              │          │
│        └─────────────────────────▶│ Lessons  │◀─────────────┘          │
│                                   └──────────┘                          │
└─────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           OPTAPLANNER SOLVER                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   Inputs:                          Outputs:                             │
│   • Lessons (unassigned)           • Lessons with assigned:             │
│   • Timeslots                        - Timeslot                         │
│   • Rooms                            - Room                             │
│   • Constraint Settings                                                 │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         SCHEDULED TIMETABLE                             │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Core Entities

### 1. Zone
Physical locations or building areas on campus.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `name` | String | Yes | Zone name (e.g., "Engineering Block") |
| `code` | String | Yes | Short code (e.g., "ENG") |

**Used By:** Rooms, Student Groups, Courses (zone restrictions)

---

### 2. Feature
Room capabilities or equipment requirements.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `name` | String | Yes | Feature name (e.g., "Projector", "Lab Equipment", "Whiteboard") |

**Used By:** Rooms (available features), Courses (required features)

---

### 3. Room
Physical classrooms, labs, or lecture halls.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `name` | String | Yes | Room name/number (e.g., "Room 101", "Lab A") |
| `capacity` | Integer | Yes | Maximum student capacity |
| `zone` | Zone | Yes | Which zone this room belongs to |
| `features` | Set\<Feature\> | No | Available features in this room |

**Constraints:**
- Lessons can only be assigned to rooms with **all** required features
- **Room must fit ALL students (HARD CONSTRAINT)** - No lesson can be assigned to a room smaller than the total student count

---

### 4. Lecturer
Faculty members who teach courses.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `name` | String | Yes | Full name |
| `email` | String | No | Contact email |
| `unavailabilities` | Set\<LecturerUnavailability\> | No | Times when lecturer cannot teach |

**Sub-entity: LecturerUnavailability**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `dayOfWeek` | DayOfWeek | Yes | MONDAY, TUESDAY, etc. |
| `startTime` | LocalTime | Yes | Start of unavailable period |
| `endTime` | LocalTime | Yes | End of unavailable period |
| `reason` | String | No | Optional reason |

---

### 5. Student Group
Classes or cohorts of students who take courses together.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `name` | String | Yes | Group name (e.g., "CS Year 1", "EE Semester 3") |
| `size` | Integer | Yes | Number of students in the group |
| `allowedZones` | Set\<Zone\> | No | Zones where this group can have classes |

**Constraints:**
- Student group cannot have overlapping lessons (hard constraint)
- Days should be balanced for each group (soft constraint)

---

### 6. Course
Subjects or modules taught during the semester.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `code` | String | Yes | Course code (e.g., "CS101") |
| `name` | String | Yes | Course name (e.g., "Introduction to Programming") |
| `weeklyHours` | Integer | Yes | Total hours per week |
| `studentGroups` | Set\<StudentGroup\> | Yes | Groups taking this course (supports combined classes) |
| `requiredFeatures` | Set\<Feature\> | No | Required room features |
| `allowedZones` | Set\<Zone\> | No | Zones where course can be scheduled |

**Combined Classes Example:**
- Course "English 101 - Combined ADE" → studentGroups: [Group A, Group D, Group E]
- Total student count = sum of all group sizes → used for room capacity constraint

**Note:** Lessons are auto-generated from courses based on `weeklyHours` configuration.

---

### 7. Timeslot
Available time periods for scheduling.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `dayOfWeek` | DayOfWeek | Yes | MONDAY through FRIDAY |
| `startTime` | LocalTime | Yes | Slot start time |

**Generation:** Timeslots are auto-generated based on:
- Earliest start time (default: 07:00)
- Latest end time (default: 18:00, 12:00 on Fridays)
- Lunch break period (default: 12:00-13:00)

---

### 8. Lesson (Planning Entity)
Individual class sessions to be scheduled.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | Long | Auto | Primary key |
| `course` | Course | Yes | Parent course |
| `durationHours` | Integer | Yes | Length of lesson (1 or 2 hours) |
| `partNumber` | Integer | Yes | Which part of weekly hours (1, 2, etc.) |
| `lecturer` | Lecturer | Yes | Assigned lecturer |
| `timeslot` | Timeslot | **Solver** | Assigned by OptaPlanner |
| `room` | Room | **Solver** | Assigned by OptaPlanner |
| `pinned` | Boolean | No | If true, assignment is locked |

---

## Configuration Settings

Stored in `constraint_setting` table, editable at runtime.

### Timing Settings
| Key | Default | Description |
|-----|---------|-------------|
| `lunch_break_start` | 12:00 | Start of lunch break |
| `lunch_break_end` | 13:00 | End of lunch break |
| `latest_end_time` | 18:00 | Latest lesson end time (Mon-Thu) |
| `friday_latest_end_time` | 12:00 | Latest lesson end time (Friday) |
| `earliest_start_time` | 07:00 | Earliest lesson start time |

### Feature Flags
| Key | Default | Description |
|-----|---------|-------------|
| `enforce_lunch_break` | true | Enforce no lessons during lunch |
| `allow_same_course_same_day` | false | Allow multiple lessons of same course on one day |
| `enforce_day_balance` | true | Balance lessons across days |

### Constraint Weights (Soft)
| Key | Default | Description |
|-----|---------|-------------|
| `weight_room_capacity` | 1 | Penalty for oversized rooms |
| `weight_day_balance` | 2 | Penalty for unbalanced days |
| `weight_lecturer_transition` | 5 | Penalty for room changes between consecutive lessons |
| `weight_student_fatigue` | 1 | Penalty for long consecutive lessons |
| `weight_early_morning` | 3 | Penalty for 7am lessons |

---

## Data Entry Order

For proper setup, enter data in this order:

1. **Zones** - Define campus areas
2. **Features** - Define room capabilities  
3. **Rooms** - Create rooms with zones and features
4. **Lecturers** - Add faculty with unavailabilities
5. **Student Groups** - Define classes with sizes and allowed zones
6. **Courses** - Create courses linking to student groups
7. **Configuration** - Adjust constraint settings as needed
8. **Run Solver** - Generate timetable

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/solver/solve` | Start solving with `{"mode":"FULL_REPLAN"}` |
| GET | `/api/v1/solver/status` | Check solver status |
| GET | `/api/v1/timetable` | Get generated timetable |
| GET | `/api/v1/courses` | List all courses |
| GET | `/api/v1/rooms` | List all rooms |
| GET | `/api/v1/lecturers` | List all lecturers |

---

## Sample Data Requirements

For a minimal working timetable:

- At least **1 Zone**
- At least **1 Room** with capacity ≥ largest student group
- At least **1 Lecturer**
- At least **1 Student Group**
- At least **1 Course** with weekly hours defined
- **Timeslots** (auto-generated on app startup)
