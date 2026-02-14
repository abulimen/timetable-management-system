# Software Engineering Test Import Pack (100-400 Level)

This pack is a manual, coherent test dataset for all currently importable entities in BUTMS.

## Covered entities

- `users`
- `zones`
- `features`
- `rooms`
- `student-groups`
- `courses`

## Import order (important)

1. `users.csv`
2. `zones.csv`
3. `features.csv`
4. `rooms.csv`
5. `student-groups.csv`
6. `courses.csv`

## Notes

- Lecturer CSV import is retired in this project. Lecturer records are created by importing users with role `LECTURER`.
- Courses include cross-department offerings (e.g. `MTH`, `GST`, `ENT`, `CSC`) assigned to Software Engineering groups.
- Student groups are provided up to 400 level (`SENG`) with parent + child groups A/B.
- All files use the exact headers expected by the system import validators.
