# Data Page Implementation Backlog

This backlog converts `docs/DATA_PAGE_ENHANCEMENT_SPEC.md` into delivery-ready work items.

## Assumptions
- Frontend: Angular standalone components.
- Current list pages are client-rendered from existing API endpoints.
- We can ship in slices and keep pages functional at all times.

## Sizing model
- `S` = 0.5-1 day
- `M` = 1-3 days
- `L` = 3-5 days
- `XL` = 5+ days

---

## Epic A: Shared Data Exploration Framework

### A1. Shared query state model and utilities
- Size: `M`
- Dependencies: none
- Deliverables:
  - Reusable query state (`search`, `filters`, `sort`, `columns`, `pagination`)
  - Serializer/deserializer for URL query params
  - Filter chip model
- Acceptance criteria:
  - Query state can round-trip to URL and back with no loss.
  - Each page can opt into model incrementally.

### A2. Reusable toolbar component
- Size: `M`
- Dependencies: A1
- Deliverables:
  - Search input, filters button, sort selector, reset action
  - Result count label (`X of Y`)
- Acceptance criteria:
  - Toolbar works in at least one page end-to-end.
  - Emits strongly typed state updates.

### A3. Saved views (local first)
- Size: `M`
- Dependencies: A1
- Deliverables:
  - Save, load, rename, delete view presets (localStorage)
  - Optional “default view”
- Acceptance criteria:
  - Saved view restores filters/sort/search/columns exactly.

### A4. Shared KPI card row component
- Size: `S`
- Dependencies: none
- Deliverables:
  - 3-6 card responsive row with label/value/delta/trend icon slots
- Acceptance criteria:
  - Used by at least two pages.

### A5. Multi-sort and advanced filters drawer
- Size: `L`
- Dependencies: A1, A2
- Deliverables:
  - Sort priority stack UI
  - Filter include/exclude and match-all/match-any modes
- Acceptance criteria:
  - Multi-sort applied deterministically and visibly.

---

## Epic B: Courses Intelligence (highest impact)

### B1. Courses advanced search/filter/sort
- Size: `L`
- Dependencies: A1, A2
- Deliverables:
  - Filters: online/in-person, lecturer assigned, weekly hours range, group level, required feature, allowed zone
  - Sort: code/name/hours/group-count/feature-count
- Acceptance criteria:
  - Any filter combination updates results correctly.
  - Reset returns to original dataset view.

### B2. Courses diagnostics panel
- Size: `L`
- Dependencies: B1
- Deliverables:
  - KPI cards: totals, online split, unassigned lecturer count, high-risk count
  - Row-level badges for risk states
- Acceptance criteria:
  - Risk counts match row-level states.

### B3. Courses “Needs Attention” saved view presets
- Size: `S`
- Dependencies: A3, B1
- Deliverables:
  - Prebuilt views: `No Lecturer`, `Constraint Risk`, `Online Only`, `Heavy Hours`
- Acceptance criteria:
  - Presets apply in one click and are editable.

---

## Epic C: Rooms Capacity & Capability Intelligence

### C1. Rooms advanced filter/sort
- Size: `L`
- Dependencies: A1, A2
- Deliverables:
  - Capacity range, zone, feature has-all/has-any/missing
  - Sort by capacity, feature count, zone/name
- Acceptance criteria:
  - Feature mode toggles (all/any/missing) behave correctly.

### C2. Rooms analytics cards + distributions
- Size: `M`
- Dependencies: C1, A4
- Deliverables:
  - Total rooms, total seat capacity, rooms-without-features, largest/smallest
  - Capacity bucket chart/list
- Acceptance criteria:
  - Metrics update with filtered scope.

### C3. Room quick actions
- Size: `M`
- Dependencies: C1
- Deliverables:
  - Bulk add/remove feature
  - Bulk capacity adjustment (+/-)
- Acceptance criteria:
  - Preview count before apply.
  - Mutation result summary displayed.

---

## Epic D: Student Group Hierarchy Power Tools

### D1. Parent/child tree + flat toggle
- Size: `L`
- Dependencies: A1
- Deliverables:
  - Tree mode with parent expansion
  - Flat mode with existing table
- Acceptance criteria:
  - Expansion state is stable while filtering.

### D2. Student group advanced filters
- Size: `M`
- Dependencies: D1
- Deliverables:
  - Group type, level, base name, parent, size range, child-count range
- Acceptance criteria:
  - Parent-only and child-only filters are exact.

### D3. Group integrity diagnostics
- Size: `M`
- Dependencies: D2, A4
- Deliverables:
  - KPI: parent count, child count, enrolled total, avg size
  - Warnings: orphan child, parent without children, oversized groups
- Acceptance criteria:
  - Warning counts match rows flagged.

---

## Epic E: Lecturer Workload & Availability Insights

### E1. Lecturer advanced filter/sort/search
- Size: `M`
- Dependencies: A1, A2
- Deliverables:
  - Filters: has/missing email, unavailability load, assignment status
  - Sort: name, assigned courses count, unavailable slots
- Acceptance criteria:
  - Missing-email and heavy-unavailability filters are accurate.

### E2. Lecturer analytics cards + heatmap
- Size: `L`
- Dependencies: E1, A4
- Deliverables:
  - KPIs: unassigned lecturers, overloaded lecturers, no-email
  - Day/time unavailability density display
- Acceptance criteria:
  - Heatmap derived from actual unavailability records.

---

## Epic F: Features and Zones Dependency Intelligence

### F1. Features advanced filtering + scarcity ranking
- Size: `M`
- Dependencies: A1, A2
- Deliverables:
  - Filters: used by rooms, required by courses, orphaned
  - Sort: demand count, supply count, scarcity ratio
- Acceptance criteria:
  - Orphan filter returns only unused features.

### F2. Zones usage intelligence
- Size: `M`
- Dependencies: A1, A2
- Deliverables:
  - Filters: used/unused, room-count range
  - Sort by room count and estimated capacity
- Acceptance criteria:
  - Zone usage counts are consistent with rooms list.

---

## Epic G: API and Aggregation Support

### G1. Server-side query support (optional phase gate)
- Size: `XL`
- Dependencies: none
- Deliverables:
  - Query params for list endpoints: `q`, `filters`, `sort`, `page`, `size`
- Acceptance criteria:
  - Large dataset interaction remains responsive.

### G2. Lightweight aggregate endpoints
- Size: `L`
- Dependencies: none
- Deliverables:
  - Per-page summary/distribution endpoints
- Acceptance criteria:
  - KPI cards can render without client-side full recomputation.

### G3. Cross-entity diagnostics endpoints
- Size: `L`
- Dependencies: G2
- Deliverables:
  - Course feasibility snapshot
  - Feature scarcity snapshot
  - Lecturer load summary
- Acceptance criteria:
  - Diagnostics match existing solver/business rules.

---

## Suggested delivery plan

### Sprint 1
- A1, A2, A4
- B1 (core filters/search/sort)
- C1 (core filters/search/sort)

### Sprint 2
- A3, A5
- B2, B3
- C2

### Sprint 3
- D1, D2, D3
- E1

### Sprint 4
- E2
- F1, F2
- C3

### Parallel backend track
- G1/G2/G3 in staged rollout if dataset size/performance requires server-side querying.

---

## Definition of ready (ticket level)
- UI mock or wireframe reference exists.
- Filter fields and operators are explicitly listed.
- Data source and API contract confirmed.
- Edge cases documented (empty state, no matches, invalid values).

## Definition of done (ticket level)
- Unit tests for query/filter/sort behavior.
- Component-level tests for interaction flows.
- Manual QA checklist completed on desktop and mobile widths.
- No regressions in CRUD actions.

---

## Immediate first ticket recommendation
- Start with `B1` (Courses advanced filter/search/sort) and implement it using `A1 + A2` so the pattern is reusable for all other pages.

---

## Current Status

### Completed
- `A1`, `A2`, `A3`, `A4`, `A5`
- `B1`, `B2`, `B3`
- `C1`, `C2`, `C3`
- `D1`, `D2`, `D3`
- `E1`, `E2`
- `F1`, `F2`
- `G2` (initial rollout): added `/api/v1/insights/zones/summary`, `/api/v1/insights/features/summary`, `/api/v1/insights/lecturers/summary`, with frontend bindings and page-level snapshot usage.

### Pending
- `G1` Server-side query support (`q`, `filters`, `sort`, `page`, `size`)
- `G3` Cross-entity diagnostics endpoints (course feasibility snapshot, feature scarcity snapshot, lecturer load summary aligned to solver/business rules)

### Verification Notes
- Frontend build passes after each milestone (`npm run build`).
- Backend compile could not be fully validated in this environment because local JDK does not support project target release 17.
