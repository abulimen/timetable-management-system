# University Timetable Generation Process

This document outlines the standard operating procedure for generating a university-wide timetable using the Centralized Timetable Management System (BUTMS). It details the roles of various university officials and the step-by-step workflow from data gathering to publication.

## 1. Key Officials & System Roles

The system maps real-world university hierarchies to specific permissions roles.

| University Official | System Role | Responsibilities |
| :--- | :--- | :--- |
| **University Registrar / Central Committee** | **SUPER_ADMIN** or **ADMIN** | **Global Orchestration**<br>• Define global rules (semester dates, operating hours)<br>• Manage shared resources (auditoriums, labs)<br>• Resolve inter-faculty conflicts |
| **Dean of Faculty** | **ADMIN** | **Faculty Oversight**<br>• Ensure departmental schedules harmonize<br>• Manage cross-departmental lecturer assignments |
| **Head of Department (HOD) / Coordinator** | **COORDINATOR** | **Data Ownership**<br>• Define active Courses for the semester<br>• Assign Lecturers to Courses<br>• Manage Student Groups (cohorts) |
| **Lecturers** | **LECTURER** | **Constraints Provider**<br>• Submit availability/unavailability requests<br>• View personal schedules |
| **Students** | **VIEWER** | **End User**<br>• View published timetables |

---

## 2. The Generation Workflow

The generation process is cyclical, typically occurring before the start of each new semester.

### Phase 1: Data Gathering (The "Call for Data")

**Actors:** `ADMIN`, `COORDINATOR`

* **Action:** The central office opens the system for the new semester.
* **Task:** Departmental Coordinators upload or update their lists using the **Bulk Import** feature.
  * **Courses:** Which subjects are running?
  * **Rooms:** Are there new classrooms or labs?
  * **Student Groups:** Update cohort sizes (e.g., "Computer Science Year 1" size may change).

### Phase 2: Preference & Constraint Collection

**Actors:** `LECTURER`

* **Action:** Lecturers log in to the **Unavailability Requests** portal.
* **Task:** Mark specific times they cannot teach (e.g., research blocks, part-time constraints).
* **Outcome:** These become "Hard" or "Soft" constraints for the Solver.

### Phase 3: The Generation (Solver Execution)

**Actors:** `ADMIN`, `COORDINATOR`

* **Action:** A Coordinator triggers the AI Solver for their faculty or the whole university.
* **Process:** The algorithms (Timefold/OptaPlanner) attempt to fit thousands of lessons into available slots while respecting:
  * Room capacities
  * Lecturer availability
  * Student group non-overlap (students can't be in two classes at once)

### Phase 4: Review & Conflict Resolution

**Actors:** `COORDINATOR`

* **Action:** Review the "Draft" timetable.
* **Task:** Manual adjustments.
  * *Scenario:* The Solver satisfied all rules, but placed a 70-year-old professor in a 4th-floor room without an elevator.
  * *Resolution:* The Coordinator manually swaps the room or time slot.
  * *Conflict Detection:* The system immediately flags if a manual move creates a new conflict.

### Phase 5: Publication

**Actors:** `SUPER_ADMIN`, `ADMIN`

* **Action:** "Publish" the final timetable.
* **Outcome:**
  * Dashboards update for all users.
  * Alerts/Emails (optional) actived.
  * The timetable becomes "Read-Only" for the general public.

---

## 3. The "Cross-Departmental" Advantage

In a manual (Excel-based) system, a lecturer teaching for both **Dept A** and **Dept B** is a high risk for clashes. Dept A schedules them at 10 AM, Dept B schedules them at 10 AM, and nobody notices until the first week.

**In BUTMS:**

* **Single Identity:** "Lecturer Smith" is one entity in the database.
* **Global Blocking:** If the Biology Dept schedules Smith at 10 AM, that slot becomes legally "Occupied" for Smith globally.
* **Auto-Resolution:** If the Chemistry Dept tries to use Smith at 10 AM, the Solver (or manual validator) immediately rejects it.
