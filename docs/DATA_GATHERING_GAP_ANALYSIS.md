# Data Gathering & Approval Workflow - Gap Analysis

## 1. Context & User Requirement

**Context:**  

- A typical university has a "School Registration Portal" (Source of Truth) where Lecturers, Departments, Courses, and Groups already exist.
- **BUTMS (This System)** is the downstream consumer of this data for the purpose of scheduling.
- **Goal:** Import data from the Portal -> Generate Timetable.
- **Requirement:** Department Coordinators should bring data into BUTMS, but a **Super Admin should approve** these actions before they affect the live system.

## 2. Typical Detailed Workflow (Ideal State)

To support the requirement of "Coordinator proposes -> Admin approves", the workflow should be:

### Phase 1: External Extraction (Source of Truth)

1. **Source:** Registration Portal (Oracle/SQL/etc).
2. **Action:** Coordinators export data (likely CSVs per department) or an API syncs data.
3. **Data Scope:**
    - Active Courses for Semester X
    - Allocated Lecturers
    - Student Group sizes

### Phase 2: Staging Import (The "Pending" State)

**Actor:** `COORDINATOR`

1. **Log in** to BUTMS.
2. **Upload CSV** (e.g., "Computer Science Courses").
3. **System Action:**
    - Validate data format & internal conflicts.
    - **CRITICAL:** Save data to a **"Staging Area"** (or mark as `PENDING_APPROVAL`).
    - **Do NOT** update the live main tables yet.
4. **UI Feedback:** "Import successful. Submitted for Admin Approval."

### Phase 3: Review & Approval

**Actor:** `SUPER_ADMIN` / `ADMIN`

1. **Notification:** Admin sees "3 Pending Imports" on dashboard.
2. **Review:**
    - Admin views the "Draft Course List" from Computer Science.
    - Checks for obvious errors (e.g., duplicated codes, massive global changes).
3. **Action:** Click **"Approve & Merge"**.
4. **System Action:**
    - The Staged data is merged into the Live tables.
    - Live Conflict Resolution happens here (if Admin merging causes conflicts).

### Phase 4: Active Scheduling

**Actor:** `COORDINATOR` / `ADMIN`
- Once data is live, the Solver can run.

---

## 3. Current System Analysis

### How it currently works

1. **Direct Import:** Coordinators (or Admins) upload CSV.
2. **Immediate Effect:** Data is validated and **immediately saved** to the live `Course`, `Lecturer`, etc. tables.
3. **No "Draft" State:** There is no column for `status` (Draft/Approved) in the database entities.
4. **No Staging Table:** There are no separate tables to hold imported-but-not-active data.

### Does the current system support the requirement?

**NO.** The current system is "Trust-Based". If a Coordinator has the permission to import, their changes go live immediately.

---

## 4. Gap Analysis & Recommendations

| Feature | Current State | Required State | Severity |
| :--- | :--- | :--- | :--- |
| **Approval Queue** | Non-existent. Import = Commit. | **Staging Area**: Coordinators upload to a holding area. Admins review and commit. | **High** (If strict governance is needed) |
| **Data Segregation** | All active users see all data. | **Draft Mode**: Imported data should be invisible to Solver/Public until approved. | **High** |
| **Conflict Scope** | Conflicts handled at import time by uploader. | **Merge Conflicts**: Admin handles conflicts when merging staging -> live. | **Medium** |
| **Role Permissions** | Coordinators have `WRITE` access to live tables via Import. | Coordinators have `WRITE` access to **Staging** only. `READ` access to Live. | **High** |

### Recommended Solution: "The Staging Area"

To implement this without rewriting the entire core, we recommend:

1. **New Entity:** `ImportBatch`
    - `id`, `uploader`, `status` (PENDING, APPROVED, REJECTED), `timestamp`, `entityType`
2. **New Entity:** `StagedData`
    - Stores the raw JSON/CSV content of the rows.
3. **UI Changes:**
    - Coordinator View: "Upload" button changes to "Submit for Approval".
    - Admin View: New "Pending Approvals" page.
    - Preview Diff: Admin sees "This batch adds 5 courses and modifies 2".

This approach keeps the core domain (`Course`, `Lecturer`) clean and simple, acting only as the "Approved Production Data".
