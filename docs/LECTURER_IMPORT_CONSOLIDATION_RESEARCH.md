# Lecturer Import Consolidation Research

## Scope
Investigate and compare the two lecturer-related import entry points:

1. `Data Imports` flow (entity-based drafts/staging/approval) with `Lecturers` entity.
2. `User Management` bulk import flow where `role=LECTURER` can be imported as users.

Goal: reduce admin confusion and avoid incorrect import sequencing while preserving data integrity.

## Current Architecture (What Exists Today)

### Flow A: Data Imports -> Lecturers (Staged import workflow)
- UI entry: `frontend/src/app/features/import/drafts/my-drafts.component.ts:40`
  - Entity selector includes `lecturers` (and not `users`).
- APIs used via draft/staging:
  - `POST /api/v1/bulk/staging/draft/{entityType}` `src/main/java/com/university/timetable/controller/BulkOperationsController.java:620`
  - `POST /api/v1/bulk/staging/draft/{id}/submit` `src/main/java/com/university/timetable/controller/BulkOperationsController.java:757`
  - `POST /api/v1/bulk/staging/{batchId}/approve` `src/main/java/com/university/timetable/controller/BulkOperationsController.java:473`
- Backend import handler:
  - `importLecturers(...)` `src/main/java/com/university/timetable/service/BulkImportService.java:126`
- Lecturer CSV schema:
  - `name,email` `frontend/src/app/features/import/inline-editor/column-definitions.ts:513`
  - Template also confirms `name,email` `src/main/java/com/university/timetable/service/BulkImportService.java:61`
- Core rule:
  - Lecturer import requires **existing user account** with allowed role (`LECTURER/COORDINATOR/ADMIN/SUPER_ADMIN`) and active status `src/main/java/com/university/timetable/service/BulkImportService.java:269`.

### Flow B: User Management -> Bulk Import Users (Direct live import)
- UI entry:
  - `frontend/src/app/features/users/users.component.ts:68`
  - Upload posts directly to `POST /api/v1/bulk/users/import` `frontend/src/app/features/users/users.component.ts:563`
- Backend route:
  - Generic bulk endpoint routes `users` to `importUsers(...)` `src/main/java/com/university/timetable/controller/BulkOperationsController.java:177`
- User CSV schema:
  - `email,first_name,last_name,role,department,phone` `frontend/src/app/features/users/users.component.ts:77`
  - Same in template `src/main/java/com/university/timetable/service/BulkImportService.java:70`
- Core behavior:
  - Creates user accounts.
  - For rows with `role=LECTURER`, auto-creates/links `Lecturer` entity `src/main/java/com/university/timetable/service/BulkImportService.java:533`.
  - Sends welcome emails `src/main/java/com/university/timetable/service/BulkImportService.java:539`.

## Data Model Reality
- `Lecturer` and `User` are linked entities.
- User lifecycle service also auto-links/unlinks lecturer depending on role changes `src/main/java/com/university/timetable/service/UserService.java:366`.

This means there are effectively two ways to end up with a lecturer:
- Import lecturer records (requires user first).
- Import users with `LECTURER` role (auto creates lecturer).

## Key Differences (Why Admins Get Confused)

### 1) Prerequisite Direction Is Opposite
- Flow A says: "You need users first."
- Flow B says: "I can create lecturer-capable users now and auto-create lecturers."

### 2) Governance/Approval Mismatch
- Flow A has draft/edit/validation/staging/approval workflow.
- Flow B bypasses staging and writes live immediately.

### 3) Import History / Rollback Inconsistency
- Most entity imports record import history (e.g., lecturers, rooms) `src/main/java/com/university/timetable/service/BulkImportService.java:336`.
- `importUsers(...)` currently does **not** set `importHistoryId` (no `recordHistory("USERS", ...)` call in that method).
- Result: user bulk imports are less traceable in import history workflows.

### 4) Permission Surface Inconsistency
- Data Imports pages are for `SUPER_ADMIN/ADMIN/COORDINATOR` routes/guards.
- User management page bulk import is effectively admin-facing.
- Combined effect: CSV operations that influence lecturer registry are spread across different menus and role assumptions.

### 5) UX Semantics Conflict
- "Data Imports" implies all master data imports are centralized.
- But "Users" has separate CSV import for lecturer-capable principals.
- Admin may unknowingly run both, potentially duplicating effort or causing naming mismatch surprises.

## Risks Observed

1. Incorrect sequence by admins:
- Import lecturers in Data Imports before importing users -> expected hard failures.

2. Partial governance:
- User CSV bulk path can create lecturer records without approval gate.

3. Audit blind spot:
- User CSV activity is not aligned with import-history lifecycle used elsewhere.

4. Identity/profile divergence:
- Lecturer `name` from lecturer CSV may diverge from user first/last naming style if both flows are used loosely.

## Merge Feasibility
Merge is feasible because both paths already converge in `BulkImportService`.
Main effort is product/UX consolidation and policy consistency, not core parsing complexity.

## Merge Options

### Option A: Keep both paths, but enforce strict boundaries
- Keep User Management import for identity provisioning only.
- Keep Data Imports lecturer import for roster curation only.
- Add hard UI/API guardrails:
  - User import screen banner: "Creates users and (for LECTURER role) linked lecturer records."
  - Data Imports lecturer draft pre-check helper: "Missing user emails detected."
  - Cross-links between pages.

Pros:
- Smaller change.
Cons:
- Still two import sources; confusion reduced but not eliminated.

### Option B (Recommended): Centralize all CSV imports under Data Imports
- Add `users` to Data Imports entity selector and inline-editor schemas.
- Route user CSV through draft/staging/approval like other entities.
- Deprecate direct CSV import section in Users page (replace with link to Data Imports preset `users`).

Pros:
- Single import mental model.
- Uniform validation, approval, and audit lifecycle.
- Lower operational confusion.
Cons:
- Requires moderate UI/API harmonization.

### Option C: Centralize only in Users page
- Move lecturer data import there too and retire Data Imports lecturer entity.

Pros:
- One page for identity + lecturer.
Cons:
- Loses consistency with other entity import workflows (draft/staging/approval).
- Weak fit versus existing staging architecture.

## Recommended Direction
Adopt **Option B**.

Reason:
- Existing architecture already treats staging/draft as the system-of-record import workflow for operational data.
- Extending that same path to users removes the only major "live bypass" import behavior.
- It creates one obvious rule for admins: "All CSV imports happen in Data Imports."

## Suggested Future Design (No Implementation Yet)

### UX model
- Data Imports entity selector includes:
  - `users` (new)
  - `lecturers` (retained for explicit roster corrections if needed)
- Users page:
  - Keep manual add/edit.
  - Replace bulk CSV card with "Go to Data Imports -> Users".

### Guardrails
1. If importing `lecturers`, show preflight:
- list missing emails (no matching user).
- optional CTA: "Create missing users draft".

2. If importing `users` with role `LECTURER`:
- explicit note that lecturer entities will be auto-linked/created.

3. Add a consistency validator:
- warn if lecturer name differs significantly from linked user full name (optional, non-blocking).

### Audit/History alignment
- Ensure `importUsers(...)` records import history same as other entities.
- Keep staged approval metadata for user imports.

## Minimal Change Plan (When You Decide to Implement)

1. Add `users` entity to Data Imports UI selector and draft editor support.
2. Ensure staging flow supports `USERS` end-to-end in UI (service already supports backend execution path).
3. Deprecate/remove direct users CSV upload panel from Users page.
4. Add/import-history recording for user import operation.
5. Add copy and cross-links to explain lecturer-user coupling.
6. Regression tests:
  - user import via staging path
  - lecturer import fails if user missing
  - user import with lecturer role creates linked lecturer
  - import history contains USERS entries

## Discussion Decisions Needed
1. Do you want `lecturers` import retained after centralization, or eventually replaced by `users`-only + manual lecturer edits?
2. Should lecturer name always mirror user full name, or remain independently editable?
3. Should coordinator role be allowed to submit `users` imports, or admin-only?

