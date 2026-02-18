# Solver Speed And Quality Optimization Research

## Purpose
This document is a deep, practical research guide for improving timetable solve speed while preserving or improving timetable quality (especially soft-constraint optimization quality) in this project.

The goal is to answer:
1. Which optimization options will give the biggest speed gains?
2. How each option affects quality and operational behavior.
3. What technical work is required in this codebase.
4. What tradeoffs and risks to watch.

---

## Executive Summary
There is no single "magic switch" that makes this solver fast and high-quality at full-university scale. The highest impact comes from a combination of:
1. reducing expensive constraint-stream work,
2. using better search strategy profiles (two-stage solve),
3. reducing the size of each solve (scoped/partitioned solving),
4. and correctly tuning parallelism.

If implemented well, these changes can move from "minutes with weak soft-score progress" to "faster feasibility + significantly better soft optimization progress."

---

## Ranking Method
Each option is ranked on:
- **Speed Impact**: expected solve-time gain at medium/large data scale.
- **Quality Impact**: expected improvement in soft-constraint outcome quality.
- **Complexity**: engineering effort and delivery risk.

Ratings use:
- `Very High`
- `High`
- `Medium`
- `Low`

---

## Ranked Options (Highest Practical Impact First)

| Rank | Option | Speed Impact | Quality Impact | Complexity |
|---|---|---|---|---|
| 1 | Constraint model refactor (reduce pair explosions) | Very High | High | High |
| 2 | Two-stage solve strategy (hard-feasible then soft optimization) | High | Very High | Medium |
| 3 | Partitioned solving (domain-aware splits + reconcile) | High to Very High | Medium to High | High |
| 4 | Move selector and neighborhood pruning | High | Medium to High | Medium |
| 5 | Adaptive runtime profiles and termination tuning | Medium to High | Medium to High | Medium |
| 6 | Parallelism tuning (move threads and solver jobs) | Medium | Low to Medium | Low |
| 7 | Scoped/incremental replan over full replan | Very High (for change scenarios) | Medium | Medium |
| 8 | Native image/runtime optimization | Low to Medium | Low | Medium |

Notes:
- Speed gains are contextual: real gains depend on dataset shape, not just row count.
- Quality gains depend on whether hard constraints are already satisfied early.

---

## Option 1: Constraint Model Refactor (Reduce Pair Explosions)
**Rank:** #1  
**Speed Impact:** Very High  
**Quality Impact:** High  
**Why this matters most:** constraint-stream cost dominates runtime in large search spaces.

### What it is
The current constraint model includes several `forEachUniquePair(...)` constraints. Pair-based joins are expensive because candidate comparisons grow roughly quadratically as lesson counts grow. As lesson count increases, score calculation becomes the bottleneck, and search explores fewer meaningful moves per second.

### Why it affects speed so much
OptaPlanner local search repeatedly recalculates score deltas. If each move triggers many expensive joins, throughput drops. Lower score calculation throughput means:
- fewer accepted candidate moves,
- slower convergence,
- weaker soft-score improvement before termination.

### Why it affects quality
When score calculation is expensive, solver spends budget "thinking slowly" instead of exploring useful alternatives. Even if theoretical constraints are correct, practical quality is lower because search depth and breadth are cut short.

### Where this project currently feels the pain
In `TimetableConstraintProvider`, multiple constraints still use pairwise scans:
- student-group conflict,
- same-course same-day,
- student fatigue,
- lecturer transition,
- day balance,
- lecturer fatigue.

Some are unavoidable as pair concepts, but many can be reduced with:
- precomputed overlap facts,
- indexed joins,
- lower-cardinality aggregations,
- or alternative formulations.

### Refactor directions
1. Replace broad pair checks with indexed existence checks where semantics allow.
2. Use precomputed lesson facts (group overlap ids are already partially present via `getConflictGroupIds()`; extend that approach further).
3. Convert some penalties to aggregated constraints (for example, per day per group load deviation instead of all pairwise interactions).
4. Avoid recomputing heavy object traversals in filter lambdas.

### Risks
- Incorrect reformulation can change solver behavior silently.
- Must validate all hard constraints remain exact.

### Validation plan
1. Snapshot baseline hard/soft score and solve times.
2. Refactor one constraint at a time.
3. Ensure hard-constraint regression tests pass.
4. Compare benchmark p50/p95 + soft-score deltas.

---

## Option 2: Two-Stage Solve Strategy
**Rank:** #2  
**Speed Impact:** High  
**Quality Impact:** Very High  
**Why it is critical:** separates "feasibility search" from "quality optimization."

### What it is
Run solver in two intentional phases:
1. **Stage A (Feasibility Phase):** aggressively find a hard-feasible timetable.
2. **Stage B (Quality Phase):** continue from Stage A result with settings focused on soft-score improvement.

### Why this works
Hard constraints create narrow feasible regions. Searching for feasibility and searching for soft optimality are different tasks. Mixing both under one static configuration often causes premature stopping with many soft penalties.

### Quality advantage
This is one of the biggest quality boosters because Stage B can:
- relax feasibility-first urgency,
- run longer unimproved windows,
- use broader accepted move counts,
- and focus on soft score descent.

### Speed impact explanation
Counterintuitively, quality can improve **without increasing wall time too much**, because Stage A reaches feasibility quickly and Stage B starts from a valid seed instead of repeatedly failing hard constraints.

### How to apply in this codebase
1. Make `SolverProfile.QUALITY` materially different from `BALANCED`.
2. Use profile-based overrides for:
   - `unimprovedSecondsSpentLimit`,
   - `acceptedCountLimit`,
   - termination ceilings.
3. Optionally run two consecutive solves under one user action.

### Risks
- If Stage B is misconfigured, it can churn without meaningful gain.
- Requires strong run metrics to verify gains.

### Validation plan
Compare:
- hard-feasible time,
- final soft score,
- total duration  
between one-stage and two-stage runs.

---

## Option 3: Partitioned Solving
**Rank:** #3  
**Speed Impact:** High to Very High  
**Quality Impact:** Medium to High  
**Why:** largest scale lever for full-university datasets.

### What it is
Split the global problem into subproblems (partitions), solve partitions in parallel, then reconcile cross-partition conflicts.

### Why it can be huge for speed
Search space grows combinatorially. Partitioning reduces each solve space drastically and enables parallel execution across CPU cores.

### Common partition keys
- Department/college,
- level bands,
- day windows,
- campus/zone.

### Quality considerations
Partitioning can reduce global optimality because cross-partition interactions are weakened. Quality remains strong when partitions align with real-world weakly-coupled domains.

### Required caution
Do not partition blindly. If lecturers, rooms, or groups frequently cross partitions, reconciliation complexity grows and can erase speed gains.

### Fit for this project
Likely strong fit if departments mostly use department-specific groups/rooms and limited cross-department lecturer sharing.

### Risks
- Reconciliation logic complexity.
- Potential boundary conflicts.

### Validation plan
1. Start with a pilot partition strategy.
2. Measure total wall time vs baseline.
3. Track cross-partition conflict count and repair overhead.

---

## Option 4: Move Selector / Neighborhood Pruning
**Rank:** #4  
**Speed Impact:** High  
**Quality Impact:** Medium to High  

### What it is
Reduce candidate moves per step to more relevant neighborhoods:
- nearby selection,
- filtered move selectors,
- weighted move focus on violated areas.

### Why it helps
Random global move exploration wastes cycles on low-value candidates. Pruning raises useful move density.

### Quality effect
Often improves quality per unit time because search effort is spent where penalties actually exist.

### Project-specific note
You already have nearby distance meter artifacts; fully wiring them into solver config can produce immediate gains.

### Risks
- Over-pruning can trap search in local minima.

### Validation plan
Benchmark with and without neighborhood pruning across identical seeds/configs.

### Implementation status (completed)
Implemented in `src/main/resources/solver-config.xml`:
1. `unionMoveSelector` with two pruned `changeMoveSelector` branches:
   - timeslot nearby moves using `TimeslotNearbyDistanceMeter`
   - room nearby moves using `RoomNearbyDistanceMeter`
2. `LINEAR_DISTRIBUTION` nearby selection:
   - timeslot neighborhood max size: `16`
   - room neighborhood max size: `12`
3. `selectedCountLimit` aligned to neighborhood size to prevent broad random scans.

### Quick benchmark result (same dataset, BALANCED, FULL_REPLAN, skipFeasibility=true)
- **Before Option 4** (`docs/benchmarks/option4_before_quick.json`):
  - samples: `120782 ms`, `109740 ms`
  - avg: `115261 ms` (1m 55s)
  - soft score: `-1460`
- **After Option 4** (`docs/benchmarks/option4_after_quick.json`):
  - samples: `93125 ms`, `85524 ms`
  - avg: `89324.5 ms` (1m 29s)
  - soft score: `-1520`

### Measured effect
- Speed improvement: `~22.5%` faster on this dataset.
- Quality tradeoff observed: soft score worsened by `60` points in this quick run.

### Next tuning to recover soft quality while keeping speed
1. Increase Stage-B (QUALITY) neighborhood sizes while leaving BALANCED conservative.
2. Add a small swap move branch only in QUALITY stage for escape from local minima.
3. Raise Stage-B `acceptedCountLimit` to improve exploitation depth.

---

## Option 5: Adaptive Runtime Profiles And Termination Tuning
**Rank:** #5  
**Speed Impact:** Medium to High  
**Quality Impact:** Medium to High  

### What it is
Dynamic parameter selection based on data size and solve progress:
- adjust `acceptedCountLimit`,
- adjust `unimprovedSecondsSpentLimit`,
- adjust solve caps and profile defaults.

### Why it matters
Static settings that are okay for small departments can be wrong for university-wide workloads.

### Quality relation
Adaptive settings prevent stopping too early on large instances while avoiding over-solving tiny instances.

### Risks
- More operational complexity.
- Requires robust diagnostics.

### Validation plan
Use benchmark matrix by dataset size and validate p50/p95 gains.

### Implementation status (completed)
Implemented adaptive runtime policy inside `SolverService` with stage-aware watchdog termination:
1. **Dataset bands** (small/medium/large) are selected from lesson count.
   - configurable thresholds via settings keys (optional):  
     - `solver_adaptive_small_dataset_threshold` (default `200`)  
     - `solver_adaptive_large_dataset_threshold` (default `800`)
2. **Profile-aware policy resolution** per run/stage:
   - `BALANCED`: moderate limits (also used for Stage-A feasibility handoff)
   - `QUALITY`: larger no-improvement window and broader search target
3. **Two-stage QUALITY integration**:
   - Stage A uses BALANCED adaptive policy tuned for quicker feasibility handoff
   - Stage B uses QUALITY adaptive policy
4. **Watchdog enforcement**:
   - terminate stage when max stage runtime is reached
   - terminate stage when no improvement exceeds adaptive unimproved limit
   - still supports first-feasible handoff for Stage A
5. **Visibility in solver status/UI**:
   - effective dataset band
   - effective max runtime
   - effective unimproved limit
   - effective accepted-count target
   - termination reason (if policy-triggered)

---

## Option 6: Parallelism Tuning (Threads/Jobs)
**Rank:** #6  
**Speed Impact:** Medium  
**Quality Impact:** Low to Medium  

### What it is
Tune:
- `moveThreadCount` (parallel move evaluation in one solve),
- `parallelSolverCount` (concurrent independent solve jobs).

### Important practical rule
For a single active university timetable solve, `parallelSolverCount` should usually stay `1`.  
Most gains come from `moveThreadCount`, typically moderate values (for example 4 or 8 depending on CPU and memory bandwidth).

### Why gains are not always large
Thread coordination, memory pressure, and nondeterminism overhead can reduce gains at high thread counts.

### Quality effect
Mostly neutral to mild; can vary with reproducibility mode and stochastic path differences.

### Risks
- Over-threading can slow solves.
- Cross-machine result variance increases under non-reproducible mode.

### Validation plan
Run benchmark matrix at 2/4/8 threads and compare both duration and soft score.

---

## Option 7: Scoped/Incremental Replan
**Rank:** #7 (for post-change operations this can be #1)  
**Speed Impact:** Very High for localized edits  
**Quality Impact:** Medium  

### What it is
Re-solve only impacted lessons after localized changes instead of full global replan.

### Why it is strong
When only 2-10% of lessons are impacted, solve cost drops sharply and user feedback is much faster.

### Quality impact
Global optimum may be lower than full replan, but practical quality remains high for operational changes.

### Risks
- Scope misidentification can lock in bad local minima.
- Needs robust impact preview logic.

### Validation plan
Compare full vs scoped for the same change scenario and track disturbance + quality.

---

## Option 8: Native Runtime Optimizations (e.g., GraalVM Native)
**Rank:** #8  
**Speed Impact:** Low to Medium  
**Quality Impact:** Low  

### What it is
Optimize runtime startup/footprint rather than core search logic.

### Why lower priority
Your bottleneck is solver search and scoring, not API boot time. Native image may help startup and memory profile but rarely transforms deep solve runtime as much as model/search changes.

### When to use
Useful for operational deployment characteristics, not primary solve-speed strategy.

---

## Cross-Cutting Observations For This Project

### 1) Hard constraints are respected first by design
`HardSoftScore` is lexicographic. If hard score is negative, soft score is secondary. This is correct and should remain.

### 2) "Thousands of soft penalties" can still be normal
Large datasets naturally produce many soft violations; the key metric is whether soft score improves steadily and sufficiently before termination.

### 3) Profile behavior must be truly differentiated
If `BALANCED` and `QUALITY` runtime behavior is too similar, quality gains will be minimal.

### 4) Diagnostics-driven tuning is mandatory
Without p50/p95 and per-phase timings, tuning becomes guesswork.

---

## Recommended Implementation Roadmap

### Phase A (Immediate, 1-2 sprints)
1. Make profile policies truly distinct (especially QUALITY).
2. Expand benchmark runs and compare profiles on same dataset.
3. Tune termination windows to reduce premature stops.

### Phase B (High impact, 2-4 sprints)
1. Refactor most expensive pairwise soft constraints.
2. Introduce move selector pruning and nearby selection.
3. Re-benchmark and lock better defaults.

### Phase C (Scale readiness)
1. Add partitioned solve pilot for full-university data.
2. Add reconciliation and conflict repair pass.
3. Compare end-to-end time and quality against single global solve.

---

## Practical Configuration Guidance (Initial Defaults)

For quality-focused full solves:
- `environmentMode`: `REPRODUCIBLE` (for stable benchmark comparisons)
- `moveThreadCount`: `4` or `8` (benchmark both)
- `parallelSolverCount`: `1`
- `unimprovedSecondsSpentLimit`: increase substantially for QUALITY
- `acceptedCountLimit`: increase for QUALITY only

For fast operational solves:
- BALANCED profile with moderate time limits.

For large data pre-production:
- run benchmark matrix and lock profile defaults from measured results, not estimates.

---

## Risk Register
1. **Constraint semantics drift** during refactor.
   - Mitigation: hard-constraint regression tests and known conflict fixtures.
2. **False confidence from one dataset benchmark.**
   - Mitigation: benchmark small/medium/large suites.
3. **Over-threading instability.**
   - Mitigation: keep move threads conservative and benchmark.
4. **Partition boundary conflicts.**
   - Mitigation: reconcile pass + conflict audit metrics.

---

## Recommended KPIs
Track these for each run:
1. Time to first feasible (hard=0).
2. Total solve duration.
3. Final soft score.
4. Soft-score improvement rate over time.
5. Score calculation throughput if available.
6. CPU utilization and GC pressure.

---

## Final Recommendation
If the objective is "significantly faster solves with much better soft optimization" at full-university scale, prioritize this sequence:
1. **Constraint stream optimization**,
2. **True two-stage solve profiles**,
3. **Neighborhood pruning**,
4. **partitioned solving for full scale**.

This path gives the highest realistic gains without abandoning the current solver platform.
