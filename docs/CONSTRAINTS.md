# Constraint Catalog

Complete documentation of all scheduling constraints in the University Timetable Engine.

---

## Overview

The OptaPlanner-based solver uses two types of constraints:

| Type | Description | Score Impact |
|------|-------------|--------------|
| **Hard** | Must be satisfied for valid solution | `-Xhard` (violation = infeasible) |
| **Soft** | Optimized, but solution valid without | `-Xsoft` (penalty) |

**Score Format:** `0hard/-50soft`
- `0hard` = All hard constraints satisfied ✅
- `-3hard` = 3 hard constraint violations ❌
- `-50soft` = Soft penalty (lower is better)

---

## Hard Constraints

These MUST be satisfied. Any violation makes the solution infeasible.

### 1. Room Conflict

**Constraint:** No two lessons can be scheduled in the same room at overlapping times.

```java
// Triggers when:
lesson1.room == lesson2.room && 
lesson1.timeslot overlaps lesson2.timeslot
```

**Impact:** `-1hard` per conflict

---

### 2. Lecturer Conflict

**Constraint:** A lecturer cannot teach two lessons at the same time.

```java
// Triggers when:
lesson1.lecturer == lesson2.lecturer && 
lesson1.timeslot overlaps lesson2.timeslot
```

**Impact:** `-1hard` per conflict

---

### 3. Student Group Conflict

**Constraint:** A student group cannot attend two lessons at the same time.

Supports **combined groups** (Course with Groups A+D+E):
- Group A cannot attend two lessons simultaneously
- If Course 1 has Groups A+B and Course 2 has Groups A+C, they conflict on Group A

```java
// Triggers when:
hasStudentGroupOverlap(lesson1, lesson2) && 
lesson1.timeslot overlaps lesson2.timeslot
```

**Impact:** `-1hard` per conflict

---

### 4. Room Capacity Overflow

**Constraint:** Room capacity must be ≥ total students in the lesson.

For combined groups, total = sum of all group sizes.

```java
// Triggers when:
lesson.totalStudentCount > lesson.room.capacity
```

**Example:**
- Course "English ADE" has Groups A(30) + D(30) + E(30) = 90 students
- Assigned to Room-101 (capacity: 50)
- **VIOLATION**: 90 > 50

**Impact:** `-1hard` per violation

---

### 5. Room Feature Required

**Constraint:** Room must have ALL features required by the course.

```java
// Triggers when:
!lesson.room.features.containsAll(lesson.course.requiredFeatures)
```

**Example:**
- Course "CS101 Lab" requires: [Computers, Projector]
- Assigned to Room-101 with: [Projector]
- **VIOLATION**: Missing "Computers"

**Impact:** `-1hard` per missing feature

---

### 6. Zone Restriction

**Constraint:** Lessons must be in rooms within the course's allowed zones.

```java
// Triggers when:
course.allowedZones.isNotEmpty() && 
!course.allowedZones.contains(lesson.room.zone)
```

**Example:**
- Course "CS201" allowed zones: [Engineering Building]
- Assigned to room in Science Building
- **VIOLATION**: Wrong zone

**Impact:** `-1hard` per violation

---

### 7. Lecturer Unavailability

**Constraint:** Lessons cannot be scheduled during lecturer's blocked time periods.

```java
// Triggers when:
lesson.timeslot overlaps lecturer.unavailabilities
```

**Example:**
- Dr. Smith unavailable: Tuesday 14:00-16:00 (Meeting)
- CS101 scheduled: Tuesday 15:00
- **VIOLATION**: Overlaps unavailability

**Impact:** `-1hard` per violation

---

### 8. Lunch Break Overlap

**Constraint:** No lessons during the lunch break period (configurable).

**Settings:**
- `lunch_break_start`: 12:00
- `lunch_break_end`: 13:00
- `enforce_lunch_break`: true/false

```java
// Triggers when (if enforced):
lesson.timeslot.startTime >= lunchStart && 
lesson.timeslot.startTime < lunchEnd
```

**Impact:** `-1hard` per violation

---

### 9. Lesson Exceeds End Time

**Constraint:** Lessons must end by the configured latest end time.

**Settings:**
- `latest_end_time`: 18:00 (Mon-Thu)
- `friday_latest_end_time`: 12:00 (Friday)

```java
// Triggers when:
lesson.endTime > latestEndTime
```

**Example:**
- Friday lesson starts at 11:00, duration 2 hours → ends at 13:00
- Friday limit: 12:00
- **VIOLATION**: 13:00 > 12:00

**Impact:** `-1hard` per violation

---

### 10. Same Course on Same Day

**Constraint:** (Configurable) Multiple parts of the same course should not be on the same day.

**Setting:** `allow_same_course_same_day`: false

```java
// Triggers when (if not allowed):
lesson1.course == lesson2.course && 
lesson1.timeslot.dayOfWeek == lesson2.timeslot.dayOfWeek
```

**Impact:** `-1hard` per pair

---

## Soft Constraints

These are OPTIMIZED. The solver tries to minimize total soft penalty but won't sacrifice hard constraints.

### 1. Room Capacity Efficiency

**Goal:** Prefer rooms that closely match student count (avoid wasted space).

**Weight:** `weight_room_capacity` (default: 1)

```java
// Penalty:
penalty = (room.capacity - totalStudents) / 10 * weight
```

**Example:**
- 30 students in 200-capacity hall
- Penalty = (200-30)/10 * 1 = 17 soft

**Impact:** `-Xsoft` based on formula

---

### 2. Student Fatigue

**Goal:** Avoid long consecutive lesson blocks for students.

**Weight:** `weight_student_fatigue` (default: 1)

```java
// Triggers when:
Two lessons for same group are back-to-back with no break
```

**Impact:** `-1soft` per consecutive pair

---

### 3. Lecturer Room Transition

**Goal:** Minimize room changes between consecutive lessons for the same lecturer.

**Weight:** `weight_lecturer_transition` (default: 5)

```java
// Triggers when:
lesson1.lecturer == lesson2.lecturer &&
lesson1 immediately followed by lesson2 &&
lesson1.room != lesson2.room
```

**Impact:** `-5soft` per transition (default)

---

### 4. Day Balance for Student Group

**Goal:** Balance lessons evenly across days of the week.

**Weight:** `weight_day_balance` (default: 2)

```java
// Penalty based on:
Variance of lesson count per day for each group
```

**Impact:** Penalty increases with imbalance

---

### 5. Early Morning Penalty

**Goal:** Avoid 7:00 AM classes when possible.

**Weight:** `weight_early_morning` (default: 3)

```java
// Triggers when:
lesson.timeslot.startTime == 07:00
```

**Impact:** `-3soft` per early lesson

---

### 6. Lecturer Fatigue

**Goal:** Avoid long consecutive teaching sessions for lecturers.

**Weight:** Uses `weight_student_fatigue` (default: 1)

**Setting:** `max_lecturer_consecutive_hours` (default: 4)

```java
// Triggers when:
lesson1.lecturer == lesson2.lecturer &&
lesson1 immediately followed by lesson2 &&
no break between them
```

**Impact:** `-1soft` per consecutive pair

**Why it matters:** Lecturers need breaks between classes for preparation and rest.

---

## Online Classes

Online courses receive special handling:

| Constraint | Behavior for Online Lessons |
|------------|------------------------------|
| Room Conflict | **Skipped** - no room needed |
| Room Capacity | **Skipped** - unlimited students |
| Room Features | **Skipped** - no physical requirements |
| Zone Restriction | **Skipped** - no location |
| Lecturer Conflict | **Applied** - lecturer still needed |
| Student Group Conflict | **Applied** - students still attend |
| Timeslot Assignment | **Applied** - live session time |

**Setting a course as online:**
- In UI: Check "🌐 Online Course" when creating/editing
- In CSV import: Set `is_online` column to `true`

---

## Constraint Configuration

All weights and settings are stored in the `constraint_setting` database table.

### View Current Settings

```bash
curl http://localhost:8080/api/v1/settings/summary
```

### Modify Weight

```bash
# Increase lecturer transition penalty
curl -X PUT http://localhost:8080/api/v1/settings/weight_lecturer_transition \
  -H "Content-Type: application/json" \
  -d '{"value": "10"}'
```

### Disable Constraint

```bash
# Disable lunch break enforcement
curl -X PUT http://localhost:8080/api/v1/settings/enforce_lunch_break \
  -H "Content-Type: application/json" \
  -d '{"value": "false"}'
```

---

## Constraint Tuning Guide

### For Tighter Scheduling

Increase time constraint strictness:
```bash
# Earlier latest end time
curl -X PUT http://localhost:8080/api/v1/settings/latest_end_time \
  -d '{"value": "16:00"}'
```

### For Lecturer Wellness

Reduce consecutive teaching:
```bash
# Increase fatigue penalty
curl -X PUT http://localhost:8080/api/v1/settings/weight_student_fatigue \
  -d '{"value": "5"}'
```

### For Room Efficiency

Penalize oversized rooms more:
```bash
curl -X PUT http://localhost:8080/api/v1/settings/weight_room_capacity \
  -d '{"value": "10"}'
```

---

## Adding Custom Constraints

To add a new constraint, modify `TimetableConstraintProvider.java`:

```java
// 1. Add to defineConstraints()
return new Constraint[] {
    // ... existing constraints
    myNewConstraint(factory)  // Add here
};

// 2. Implement constraint method
private Constraint myNewConstraint(ConstraintFactory factory) {
    return factory.forEach(Lesson.class)
        .filter(lesson -> /* violation condition */)
        .penalize(HardSoftScore.ONE_SOFT)  // or ONE_HARD
        .asConstraint("My new constraint");
}
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for full constraint provider documentation.
