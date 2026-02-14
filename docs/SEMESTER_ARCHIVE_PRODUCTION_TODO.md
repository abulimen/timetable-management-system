# Semester Archive Production Hardening TODO

This checklist is updated as implementation progresses.

## Scope
- Secure semester archive endpoints.
- Fix archived timetable data correctness.
- Prepare sem/archives feature for reliable production use.

## Tasks
- [x] Create tracking TODO document.
- [x] Add backend authorization (`@PreAuthorize`) to semester archive endpoints.
- [x] Fix archived timetable query to read archived room/group mappings correctly.
- [x] Populate combined-group fields (`combined`, `combinedGroupNames`, `totalStudentCount`) for archived timetable.
- [x] Add/verify audit logs for all archive actions.
- [x] Run backend compile checks.
- [x] Document current status and remaining gaps.
- [x] Archive full DB snapshot (all active entity tables, including rooms/users/lecturers).
- [x] Change archive rollover clear policy to semester-only tables (users/lecturers persist).
- [x] Add restore endpoint with automatic backup snapshot.
- [x] Add UI actions: view archived timetable, restore, export, delete.
- [x] Run frontend build checks.

## Progress Log
- 2026-02-12: Created TODO tracker and started backend hardening implementation.
- 2026-02-12: Added admin-only method guards on archive endpoints in `SemesterArchiveController`.
- 2026-02-12: Reworked archived timetable SQL in `SemesterArchiveService` to use archived room + course_group joins and include combined-class metadata.
- 2026-02-12: Backend compile check passed (`mvn -DskipTests compile`).
- 2026-02-12: Added audit logging for archive list/detail/timetable read operations, including failure paths.
- 2026-02-12: Fixed async audit context snapshot to prevent recycled-request errors in `AuditLogService`.
- 2026-02-12: Refactored semester archive to snapshot all active DB tables (excluding archive metadata/system table only), including `room`, `users`, and `lecturer`.
- 2026-02-12: Updated archive rollover clearing to semester-scoped tables only; lecturer/user master data now persists.
- 2026-02-12: Added `POST /api/v1/semesters/{code}/restore` with automatic backup archive creation before restore.
- 2026-02-12: Added `GET /api/v1/semesters/{code}/export` for archived timetable CSV export.
- 2026-02-12: Added Semesters UI actions for `View Timetable`, `Export`, `Restore`, and `Delete`.
- 2026-02-12: Frontend build check passed (`npm --prefix frontend run build`).

## Remaining Gaps
- Runtime verification: exercise archive create/view/export/restore/delete flow end-to-end from UI with realistic data.
- Test coverage: add focused automated tests for full snapshot restore behavior and archived timetable mapping edge cases.
