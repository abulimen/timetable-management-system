# Audit Logging Gap Analysis and Remediation Plan

Date: 2026-02-10  
Scope: Backend + audit UI auditability coverage across the BUTMS application

## 1) Current State Summary

The project has a real audit foundation (`audit_log` table, `AuditLogService`, UI page), but coverage is partial and inconsistent.

What exists today:
- Audit entity/model and storage: `src/main/java/com/university/timetable/domain/AuditLog.java`, `src/main/resources/db/migration/V26__create_audit_log.sql`
- Audit service and queries/export: `src/main/java/com/university/timetable/service/AuditLogService.java`, `src/main/java/com/university/timetable/repository/AuditLogRepository.java`
- Audit API + UI: `src/main/java/com/university/timetable/controller/AuditLogController.java`, `frontend/src/app/features/audit-logs/audit-logs.component.ts`
- Some CRUD controllers already log create/update/delete (Course, Lecturer, StudentGroup, Room, Zone, Feature)
- Solver start/terminate and system wipe have partial system-action logging (`SolverController`, `DataWipeService`)

Core issue:
- Logging is currently manual and sparse. Many mutating operations are not audited at all.

## 2) Coverage Findings (What is audited vs not)

## 2.1 Controllers with mutating endpoints and no audit logging

These controllers have `POST/PUT/PATCH/DELETE` operations but no `AuditLogService` calls:
- `src/main/java/com/university/timetable/controller/AuthController.java` (`/login`, `/refresh`, `/logout`, `/logout-all`)
- `src/main/java/com/university/timetable/controller/UserController.java` (create/update/deactivate/reset password/lock/unlock/profile updates)
- `src/main/java/com/university/timetable/controller/AvailabilityChangeRequestController.java` (submit/approve/reject/return/resubmit/revoke/settings update)
- `src/main/java/com/university/timetable/controller/BulkOperationsController.java` (bulk import, rollback, staging submit/approve/reject/draft lifecycle, delete-all)
- `src/main/java/com/university/timetable/controller/ImportController.java` (Excel ingest)
- `src/main/java/com/university/timetable/controller/ExportController.java` (PDF/Excel export actions)
- `src/main/java/com/university/timetable/controller/SettingsController.java` (setting updates, cache refresh, timeslot regeneration)
- `src/main/java/com/university/timetable/controller/SpecialEventController.java` (create/update/delete/toggle active)
- `src/main/java/com/university/timetable/controller/LessonController.java` (`PATCH` lesson edits)
- `src/main/java/com/university/timetable/controller/SemesterArchiveController.java` (archive/delete archive)

Impact:
- High-value administrative and security-sensitive actions leave no immutable operational trail.

## 2.2 Controllers with partial audit coverage

- `src/main/java/com/university/timetable/controller/LecturerController.java`
  - Base lecturer CRUD is audited.
  - Unavailability add/remove endpoints are not audited (`/lecturers/{id}/unavailability`, `/lecturers/{id}/unavailability/{unavailabilityId}`).

- `src/main/java/com/university/timetable/controller/CourseController.java`
  - CRUD and batch update/delete are audited.
  - `POST /{id}/generate-lessons` is not audited.

- `src/main/java/com/university/timetable/service/DataWipeService.java`
  - `wipeAllData()` logs system action.
  - `deleteAllOfType()` does not log.

## 2.3 Data quality issues in logs that are written

- Incorrect `previousValue` snapshots in some update flows:
  - `src/main/java/com/university/timetable/controller/CourseController.java:115` onward mutates entity first, then captures `previousState` at `:159`.
  - `src/main/java/com/university/timetable/controller/RoomController.java:79` onward mutates first, then captures `previousState` at `:95`.
- Result: before/after values for these updates are unreliable.

- `changedFields` is never computed:
  - `src/main/java/com/university/timetable/service/AuditLogService.java` (`getChangedFields(...)` always returns `null`).
- Result: UPDATE diff context is missing despite schema support.

## 2.4 Action taxonomy is too coarse

- `AuditAction` only has `CREATE`, `UPDATE`, `DELETE`, `SYSTEM_ACTION`:
  - `src/main/java/com/university/timetable/domain/AuditAction.java`
- Result: impossible to distinguish important operational events cleanly (e.g., login success/failure, password reset, solver start vs terminate vs failed start, import approve vs reject, archive vs restore).

## 2.5 Failure-path auditing gaps

- Most audit writes are only on success paths.
- Failure conditions are generally returned/logged to app logs only, not persisted as audit records with `success=false` + `errorMessage`.
- Example partial exception: solver start failure has system audit in `SolverController`, but this pattern is not consistent elsewhere.

## 2.6 Query and UI capability gaps vs requirements

Backend/API (`AuditLogController` + `AuditLogRepository`) currently supports:
- Date range, entity type(s), action(s), actorId exact, success.

Missing or weak:
- Entity ID filter in main list endpoint (only separate entity-history route)
- Actor name search / actor partial search
- Free-text search across description/entity name/error message
- JSON export (CSV only)
- Better request-level correlation filtering

Frontend (`audit-logs.component.ts`) also limits filtering to single-value dropdowns (`entityType`, `action`, simple date range), not advanced faceted/multi-filter workflows.

## 2.7 Immutability and governance gaps

- Requirement says audit logs are immutable; current implementation relies on convention only.
- `AuditLogRepository` extends `JpaRepository`, so update/delete methods still technically exist.
- No DB-level guard (trigger/policy/procedure separation) preventing mutation/deletion of `audit_log`.
- No retention/archival policy implementation found (despite requirement target).

## 2.8 Context/correlation gaps

- `requestId` is randomly generated per log entry in `AuditLogService`, not a request-scoped correlation ID reused across all logs in the same HTTP request.
- No servlet filter/interceptor populating a stable request correlation ID in MDC/request context.
- This weakens incident reconstruction for multi-step operations.

## 3) Risk-Based Priority Gaps

Critical:
- Missing audit on auth and user-management security operations
- Missing audit on import/staging/approval/rollback flows
- Missing audit on settings changes and semester archive/delete operations

High:
- Broken before-state capture in some updates
- No failed-operation audit entries
- Coarse action enum limits operational visibility

Medium:
- Query/UI filter limitations and no text search
- No `changedFields` calculation
- No retention strategy

Low:
- CSV-only export format
- No live stream/auto-refresh monitoring mode

## 4) Recommended Remediation Plan

## Phase 1: Establish a reliable audit contract (Foundation)
1. Define an event taxonomy:
   - Add granular action types (or `SYSTEM_ACTION` + `eventType` field) for login/logout, password ops, solver lifecycle, import workflow, settings updates, archive operations, export operations.
2. Introduce a centralized `AuditEventPublisher` wrapper:
   - Standard payload structure (`entityType`, `entityId`, `entityName`, `before`, `after`, `metadata`, `success`, `error`).
3. Add request correlation:
   - Implement servlet filter/interceptor to inject stable request ID into MDC/context and reuse for all logs in that request.

Deliverable:
- Unified audit API used by controllers/services, with standards for success/failure logging.

## Phase 2: Close coverage gaps on all mutating operations
1. Add audit calls to all currently unaudited mutating endpoints listed in section 2.1.
2. Add missing partial areas:
   - Lecturer unavailability add/remove
   - Course lesson-generation endpoint
   - DataWipe `deleteAllOfType`
3. Ensure both success and failure outcomes are captured where business-critical.

Deliverable:
- 100% mutation coverage matrix with endpoint-by-endpoint completion checklist.

## Phase 3: Improve data correctness and usefulness
1. Fix `previousValue` capture ordering (snapshot before mutation) in Course and Room update flows.
2. Implement `changedFields` computation in `AuditLogService`.
3. Add structured metadata for bulk operations:
   - counts (created/updated/skipped/errors), batch IDs, entity type, dry-run vs live, approval actor.

Deliverable:
- Accurate before/after audit records and actionable diffs.

## Phase 4: Audit query UX and API enhancements
1. Extend API filters:
   - `entityId`, actor text search, free-text query over description/entity/error.
2. Add JSON export alongside CSV.
3. Upgrade frontend audit page:
   - multi-select filters
   - richer detail view (changed fields + highlighted diff)
   - saved views for common investigations

Deliverable:
- Faster incident/compliance investigations with less manual scanning.

## Phase 5: Governance, integrity, and operations
1. Enforce immutability at DB level:
   - deny updates/deletes on `audit_log` via DB policy/trigger or restricted DB principal.
2. Add retention strategy:
   - periodic archival/partitioning and retention windows.
3. Add monitoring/alerts:
   - alert on audit write failures and unusual spikes in critical events.

Deliverable:
- Compliance-grade audit trail with operational safeguards.

## 5) Suggested Implementation Order (High ROI)

1. Phase 1 + Phase 2 for `AuthController`, `UserController`, `BulkOperationsController`, `SettingsController`, `SemesterArchiveController`
2. Fix before/after correctness bugs (Course/Room) and changedFields
3. Then expand filters/UI/reporting and retention controls

This sequence closes the largest accountability gaps first, then improves investigative power.

