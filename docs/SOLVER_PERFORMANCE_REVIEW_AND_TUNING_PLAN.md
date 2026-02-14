# Solver Performance Review And Tuning Plan

## Scope
- Reviewed solver runtime path from API entry (`/api/v1/solver/solve`) through feasibility checks, problem loading, OptaPlanner solving, and solution persistence.
- Reviewed constraints implementation and solver config to identify high-impact latency and throughput bottlenecks.

## Current Runtime Flow
1. `SolverController.startSolving(...)` runs feasibility check first.
2. `InfeasibilityChecker.checkFeasibility()` currently regenerates all timeslots before every run.
3. `SolverService.startSolving(...)` loads lessons/timeslots/rooms/lecturers/groups and starts `solveAndListen`.
4. Every best solution callback saves assignments to DB via `SolutionSaver.saveSolution(...)`.

## Key Findings

### F1. Solve start includes a heavy pre-step
- Feasibility check currently does a full timeslot regeneration (`TimeslotService.regenerateTimeslots()`), which:
  - nulls lesson timeslot assignments,
  - flushes all lesson updates,
  - deletes/recreates all timeslots.
- This is expensive on large datasets and can dominate "time-to-start-solving".

### F2. Constraint hot path still has dynamic bean lookup
- `TimetableConstraintProvider.lecturerUnavailability(...)` calls `SpringContextHolder.getBean(...)` via `isUnavailabilitySystemEnabled()` in constraint evaluation path.
- Any dynamic lookup in score calculation can add avoidable overhead at scale.

### F3. Best-solution save path can be expensive
- On every best solution, all lessons are iterated and each lesson is individually read+saved.
- This guarantees correctness but adds significant DB traffic when solver finds many improving solutions early.

### F4. Constraint complexity is high in pair-based constraints
- Multiple `forEachUniquePair(Lesson.class, ...)` constraints are active (room conflict, lecturer conflict, student-group conflict, fatigue, transition, day-balance, etc.).
- This is expected for timetable complexity, but it means data-size growth increases score calculation cost quickly.

### F5. Limited observability
- Current logs are not detailed enough to separate:
  - feasibility time,
  - problem load time,
  - first best solution latency,
  - per-improvement solve speed,
  - solution persistence time.

## Preset Guidance (Current)
- `FULL_REPLAN` is best for initial schedule generation or major data changes.
- `STABILITY` is best for incremental adjustments when prior assignments should be preserved.
- For this application long-term:
  - keep both modes;
  - add a third "fast diagnostic" preset (short time limit, hard-constraint focus) for quick operational checks.

## Recommended Optimizations

## Tier 1 (Low risk, high value)
- Add full phase timing instrumentation (feasibility, load, solve progress, persistence).
- Cache all constraint settings used in score calculation (remove runtime bean lookups from hot path).
- Throttle solution persistence:
  - save only on improved hard score or at a configurable interval (e.g., every N seconds).

## Tier 2 (Moderate effort, high value)
- Avoid full timeslot regeneration unless settings actually changed.
- Add targeted candidate-room precomputation for each course/lesson to cut impossible room attempts.
- Add solver run metrics endpoint/history for comparing runs over time.

## Tier 3 (Advanced algorithmic)
- Tune local search by dataset size profile (small/medium/large configs).
- Add nearby/filtered move selectors to reduce search neighborhood.
- Consider partitioned search for very large datasets (if data distribution supports it).
- Introduce benchmark-driven parameter tuning using reproducible datasets.

## Measurement Plan
- Capture these metrics per run:
  - feasibility duration,
  - problem load duration,
  - solver startup latency,
  - first-best-solution latency,
  - total solve duration,
  - improvement count,
  - best score progression timeline,
  - persistence duration per save.
- Compare baseline vs post-change on the same dataset.

## Immediate Next Steps
1. Add detailed debug/perf logs to solver, feasibility, timeslot regeneration, and solution saver.
2. Eliminate dynamic lookup in constraint hot path.
3. Baseline current data with three repeated runs per mode and compare p50/p95 timings.

## Implemented In This Iteration
- Added conditional timeslot synchronization (`ensureTimeslotsMatchSettings`) to avoid full regeneration when unchanged.
- Added persistence throttling in solver callback:
  - configurable minimum save interval,
  - configurable every-N-improvements save,
  - immediate save on hard-score improvement.
- Added solver run-history metrics persistence and API:
  - stores mode/status/duration/first-best/improvements/persistence stats/final score,
  - exposes p50/p95 summary for duration and first-best latency.
