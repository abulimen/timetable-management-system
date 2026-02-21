# All Departments Test Import Pack (100-400 Level)

This pack is a large manual dataset for stress-testing full-university imports and solver behavior.

## Current Size Snapshot

- Departments: `23`
- Users rows: `336`
- Lecturer users: `311`
- Other users (admin/coordinator/viewer): `25`
- Zones rows: `11`
- Features rows: `15`
- Rooms rows: `78`
- Student-group rows: `296`
- Child groups (schedulable): `204`
- Course rows: `1259`
- Offline course rows: `1071`
- Online course rows: `188`
- Child-group course load distribution: `7-8` courses per group

## Scope

- Shared admin-managed data (one-time import):
  - `shared/users.csv`
  - `shared/zones.csv`
  - `shared/features.csv`
  - `shared/rooms.csv`
- Department-managed data (import per department):
  - `departments/<department>/student-groups.csv`
  - `departments/<department>/courses.csv`

## Rules Applied

- Lecturer import is retired. Lecturer records are created only from `users.csv` (`role=LECTURER`).
- Popular departments (`CSC`, `SENG`, `ITE`, `EEE`, `BUS`) use `A/B/C` groups.
- Other departments use `A/B` groups.
- Each child group carries `7-8` courses across 100-400 levels.
- Courses include cross-department offerings (`MTH`, `CSC`, `ENT`, `GST`) with mixed lecturer reuse patterns.
- Extra GST courses were added beyond Citizenship Orientation, with both:
  - in-department grouped online classes
  - selected cross-department online classes (max 3 departments per row, one large + smaller departments).
- All CSV files use the exact headers expected by the import validators.

## Departments Included

1. Accounting
2. Agritech
3. Anatomy
4. Architecture
5. Basic Sciences
6. Biochemistry
7. Business Admin
8. Civil Engineering
9. Computer Engineering
10. Computer Science
11. Economics
12. Education
13. Electrical Engineering
14. Estate Management
15. Finance
16. General Studies
17. History
18. Information Technology
19. Law
20. Mass Communication
21. Medical Laboratory Science
22. Microbiology
23. Software Engineering

## Import Order

1. `shared/users.csv`
2. `shared/zones.csv`
3. `shared/features.csv`
4. `shared/rooms.csv`
5. For each department:
   - `student-groups.csv`
   - `courses.csv`

## Notes

- Online courses combine groups with `|` in `student_group_names`.
- Offline courses are one row per group.
- This dataset is intentionally heavy for solver contention and import-validation testing.
