# Data Page Enhancement Spec

## Scope
Pages covered:
- Zones
- Rooms
- Student Groups
- Courses
- Lecturers
- Features

Goal:
- Add advanced filtering, sorting, searching, and analysis displays.
- Make day-to-day operations faster and safer for admins/coordinators.
- Keep behavior consistent across pages while adding page-specific power features.

---

## Shared UX Framework (apply to all pages)

### 1) Unified table/grid controls
- Sticky toolbar with:
  - Global search input
  - `Filters` button with count badge
  - `Sort` dropdown
  - `Columns` selector
  - `Saved Views` dropdown
  - `Reset` action
- Result summary: `X of Y records`.

### 2) Search behavior standard
- Debounced type-ahead (250-350ms).
- Case-insensitive partial match.
- Token search support:
  - Example: `online no-lecturer 300-level`.
- Optional exact prefix `=`:
  - Example: `=LAW504`.

### 3) Filter model standard
- Filter chips visible under toolbar.
- Multi-select and range filters.
- Include/exclude mode per filter.
- Logical mode toggle: `Match All` vs `Match Any`.

### 4) Sorting standard
- Single-sort quick mode.
- Multi-sort advanced mode (priority stack).
- Persist sort with saved view.

### 5) Analysis panel standard
- Top row KPI cards.
- Optional right-side “Insights” panel:
  - Distribution charts
  - Outliers
  - Data quality warnings

### 6) Saved views
- `My views`: save filter/sort/columns/search state.
- `Shared views`: admin can share team presets.
- Optional default view per page.

### 7) Bulk operations safety
- Scoped action text:
  - `Apply to all filtered (N)` vs `Apply to selected (M)`.
- Dry-run preview for destructive actions.

---

## 1) Zones Page

### Current opportunity
- Flat list (ID, name) with basic CRUD.
- No usage visibility, no filtering/search/sorting.

### Advanced filtering
- `Name contains`
- `Usage status`:
  - Used by rooms
  - Unused
- `Room count` range (0, 1-5, 6+)
- `Referenced by courses`:
  - Indirectly via allowed zones

### Sorting
- Name A-Z/Z-A
- Room count
- Courses using zone
- Recently edited (if audit timestamp is available)

### Searching
- Search by zone name.
- Search aliases/synonyms (future optional metadata).

### Analysis/display features (unique to Zones)
- KPI cards:
  - Total zones
  - Unused zones
  - Most used zone
- Heatmap/list:
  - Zone -> room count -> total seat capacity
- Consistency check:
  - Zones with zero rooms but referenced in course constraints.

### Efficiency actions
- Merge zones (repoint rooms/courses from A -> B).
- “Create rooms from zone” quick action.

---

## 2) Rooms Page

### Current opportunity
- Includes capacity, zone, features; no query power.
- High-value page for scheduling feasibility and bottlenecks.

### Advanced filtering
- Capacity range slider/input
- Zone multi-select
- Feature filters:
  - Has all selected features
  - Has any selected features
  - Missing selected features
- Utilization class (requires schedule stats):
  - Under-utilized / balanced / over-utilized
- Compatibility filters:
  - Can host selected course(s)
  - Can host selected group size

### Sorting
- Capacity ascending/descending
- Zone then name
- Feature count
- Estimated utilization %
- “Best fit score” for selected criteria

### Searching
- Room name/code
- Zone name
- Feature names

### Analysis/display features (unique to Rooms)
- KPI cards:
  - Total rooms
  - Total seat capacity
  - Rooms without features
  - Largest/smallest room
- Distribution charts:
  - Capacity buckets (0-49, 50-99, 100-199, 200+)
  - Rooms per zone
- Constraint coverage:
  - Top 10 required feature sets by courses and how many rooms satisfy each.
- Bottleneck detector:
  - “Courses likely unschedulable due to feature/capacity limits.”

### Efficiency actions
- Duplicate room template (zone + feature preset).
- Bulk assign/remove feature.
- Bulk capacity adjust by % or fixed delta.

---

## 3) Student Groups Page

### Current opportunity
- Hierarchy-aware data exists, but list is flat and hard to audit.

### Advanced filtering
- Group type:
  - Parent only
  - Child only
- Level filter (100-600)
- Department/base name filter
- Parent group filter
- Size range
- Child count range
- Integrity status:
  - Parent with no children
  - Child without valid parent

### Sorting
- Level, then name
- Size
- Parent name
- Child count
- “Impact size” (parent aggregate)

### Searching
- Name, base name, group notation, parent name.
- Smart patterns:
  - `300 grp a`
  - `cosc 200`

### Analysis/display features (unique to Student Groups)
- Dual views:
  - Tree view (hierarchy)
  - Flat analytical table
- KPI cards:
  - Parent count
  - Child count
  - Total enrolled students
  - Avg group size
- Quality checks:
  - Oversized child groups threshold warnings
  - Parent size mismatch vs sum(children)

### Efficiency actions
- Split group wizard (A/B/C generation + size distribution).
- Merge child groups into parent.
- Re-parent child groups in bulk.

---

## 4) Courses Page

### Current opportunity
- Already has bulk actions and modal editing.
- Needs strong query and readiness analysis for scheduling.

### Advanced filtering
- By code/name text
- Online vs in-person
- Lecturer assigned/unassigned
- Weekly hours range
- Student group level/department
- Required features includes/excludes
- Allowed zones includes/excludes
- “Constraint risk” class:
  - High (few eligible rooms)
  - Medium
  - Low

### Sorting
- Code, name
- Weekly hours
- Number of student groups
- Number of required features
- Eligibility score (rooms that can host it)

### Searching
- Full search across:
  - Code, name
  - Lecturer
  - Group names
  - Features and zones
- Fast exact code match mode for registrar workflows.

### Analysis/display features (unique to Courses)
- KPI cards:
  - Total courses
  - Online/in-person split
  - Courses without lecturer
  - High-risk constraint courses
- Coverage diagnostics:
  - For each course: candidate room count meeting capacity + feature + zone.
- Conflict indicators:
  - Required feature set with zero room matches.
  - Group population larger than any room.

### Efficiency actions
- Smart bulk edit:
  - Set lecturer for filtered subset
  - Add/remove required feature
  - Add/remove allowed zone
- “Needs attention” view preset:
  - No lecturer OR no feasible room pool OR invalid group mapping.

---

## 5) Lecturers Page

### Current opportunity
- Stores unavailability; currently not leveraged analytically.

### Advanced filtering
- Name/email text
- Has email / missing email
- Unavailability status:
  - None
  - Light
  - Heavy (configurable threshold)
- Teaching load class (requires course links):
  - Unassigned
  - Balanced
  - Overloaded
- Approval workflow status (if tied to request pages)

### Sorting
- Name
- Number of courses assigned
- Total weekly teaching hours
- Unavailability blocks count

### Searching
- Name and email
- Optional alias/nickname support

### Analysis/display features (unique to Lecturers)
- KPI cards:
  - Total lecturers
  - Without email
  - Unassigned to courses
  - Overloaded lecturers
- Availability profile:
  - Day-of-week heatmap of unavailable slots.
- Risk detector:
  - Lecturer with high load + heavy unavailability.

### Efficiency actions
- Bulk assign courses by department or level.
- “Find replacement candidates” action:
  - same department + available in timeslot + low load.

---

## 6) Features Page

### Current opportunity
- Card list is simple but lacks dependency visibility.

### Advanced filtering
- Name contains
- Usage:
  - Used by rooms
  - Required by courses
  - Orphaned (unused)
- Criticality class:
  - Required by many courses but present in few rooms

### Sorting
- Name
- Rooms using feature count
- Courses requiring feature count
- Scarcity ratio:
  - `courses requiring / rooms providing`

### Searching
- Feature name and synonyms/tags.

### Analysis/display features (unique to Features)
- KPI cards:
  - Total features
  - Orphan features
  - Most demanded feature
  - Most scarce feature
- Matrix view:
  - Features vs zones (where capacity exists for each feature).
- Risk list:
  - “Courses requiring feature X but only Y eligible rooms.”

### Efficiency actions
- Merge duplicate/similar features.
- Bulk assign feature to filtered rooms.
- Rename with migration preview (impact summary before commit).

---

## Recommended delivery order

### Phase 1 (highest ROI)
- Shared framework (search/filter/sort/saved views).
- Courses diagnostics (feasibility/risk).
- Rooms analytics (capacity + feature coverage).

### Phase 2
- Student group hierarchy analytics.
- Lecturers load/availability analytics.

### Phase 3
- Zones and features dependency intelligence.
- Advanced merge/migration tools.

---

## Data and API needs (minimal)

- Add optional list endpoints with server-side query params:
  - `q`, `filters`, `sort`, `page`, `size`.
- Add lightweight aggregate endpoints per page:
  - counts, distributions, risk summaries.
- Add cross-entity helper endpoints:
  - course feasibility snapshot
  - feature scarcity snapshot
  - lecturer load summary

If API changes are deferred, Phase 1 can start client-side for small datasets, then migrate to server-side filtering for scale.

---

## Definition of done (per page)

- Search + filter + sort combine correctly.
- Saved views persist and restore accurately.
- KPI cards reflect current filtered scope.
- Bulk actions clearly scope to selected vs filtered rows.
- Empty-state and “no match” states are explicit and actionable.

