# Solver Architectural Analysis & Recommendations

**Date:** 2026-02-25  
**Author:** Cascade AI Assistant  
**Status:** Request for Expert Review

---

## Executive Summary

After extensive debugging and multiple failed attempts, I have identified fundamental issues with ALL CP-SAT solver implementations in this codebase. The problems range from semantic bugs in constraint modeling to fundamental scalability limitations. This report documents all findings and requests expert review for a viable path forward.

---

## Problem Scale

| Metric | Value |
|--------|-------|
| Lessons | 1,832 |
| Timeslots | 41 |
| Rooms | 122 |
| Lecturers | 253 |
| Student Groups | 296 |
| Search Space (monolithic) | ~9.2 million combinations |

---

## Detailed Analysis of Each Solver Implementation

### 1. Single-Phase CP-SAT Solver (`SinglePhaseCpSatSolver.java`)

#### Bug #1: One-Way Implication in Optional Intervals (FIXED but...)

**Original Code (Lines ~200-227):**
```java
BoolVar isRoom = model.newBoolVar("lesson_" + i + "_room_" + r);
model.addEquality(roomVars[i], r).onlyEnforceIf(isRoom);
// Missing: backward implication!
IntervalVar optInterval = model.newOptionalIntervalVar(
        startVars[i], model.newConstant(duration), endVar, isRoom, ...);
```

**Problem:** `onlyEnforceIf()` creates one-way implication:
- `isRoom = true` → `roomVars[i] == r` ✓
- `roomVars[i] == r` → `isRoom = true` ✗ NOT ENFORCED

**Result:** Solver could assign rooms while keeping optional intervals inactive, completely bypassing NoOverlap constraints. This produced solutions with 99 lessons in the same room at the same time.

**Fix Applied:** Added `addExactlyOne()` to ensure exactly one room boolean is true per lesson, providing backward implication.

#### Bug #2: Scalability (FUNDAMENTAL, NOT FIXABLE)

Even with correct constraints, the model creates:
- **~223,704 BoolVars** (1832 lessons × 122 rooms for room assignment)
- **~223,704 Optional Intervals**
- **Plus interval variables, end variables, channeling constraints**

**Result:** Solver times out with `UNKNOWN` status after 10 minutes. CP-SAT cannot handle this model size efficiently.

**Evidence:**
```
2026-02-25T00:58:00.554+01:00 ERROR - Single-Phase CP-SAT: Solver failed with status UNKNOWN
```

---

### 2. Two-Phase CP-SAT Solver (`TwoPhaseCpSatSolver.java`)

#### Phase 1: Timeslot Assignment

**Status:** WORKS (as far as I can tell)

**Why it works:**
- Only deals with timeslots (41 values per lesson)
- Uses simple NoOverlap constraints per lecturer/student group
- Creates ~75K combinations (1832 × 41)
- No reified constraints needed

**Expected solve time:** 30 seconds to 2 minutes

#### Phase 2: Room Assignment

**Status:** HAS THE SAME BUG AS SINGLE-PHASE

**Code (Lines 411-413):**
```java
BoolVar isInRoom = model.newBoolVar("lesson" + lessonIdx + "_room" + r);
model.addEquality(roomVars[lessonIdx], r).onlyEnforceIf(isInRoom);
model.addDifferent(roomVars[lessonIdx], r).onlyEnforceIf(isInRoom.not());
```

**Problems:**
1. **Same one-way implication bug** - `roomVars[lessonIdx] = r` doesn't force `isInRoom = true`
2. **No `addExactlyOne` constraint** - No guarantee that exactly one room boolean is true
3. **Creates BoolVars per (lesson, room, timeslot)** - Still potentially large

**Why Phase 2 would fail even if fixed:**
- Phase 1 assigns timeslots without considering room availability
- If Phase 1 puts too many lessons in the same timeslot, Phase 2 becomes infeasible
- The "room capacity per timeslot" constraint in Phase 1 (line 275) attempts to address this, but it's a weak approximation

---

### 3. Timefold Solver (Currently Active)

**Status:** WORKS but SLOW

**Configuration:**
- Construction Heuristic: FIRST_FIT_DECREASING
- Local Search: Tabu + Late Acceptance
- Custom moves: NearbyMoveFactory, RuinAndRecreateMoveFactory
- Termination: 30 minutes max, 60 seconds unimproved

**Why it's slow:**
- Explores ~9.2M combination search space incrementally
- Local search can get stuck in local optima
- Metaheuristics require many iterations to find good solutions
- Hard constraints create a "rugged" fitness landscape

**Why it works:**
- Constraint Provider uses direct comparisons (no reification bugs)
- Incremental score calculation is efficient
- Move selectors focus search on promising areas

---

## Root Cause Analysis

### The Fundamental Problem: CP-SAT + Room Assignment = Hard

Room assignment in timetabling is fundamentally difficult for CP-SAT because:

1. **Disjunctive constraints require reification**
   - "If lesson A is in room R, then lesson B cannot be in room R at same time"
   - This requires `BoolVar` per (lesson, room) pair
   - With 1832 lessons × 122 rooms = 223K variables

2. **NoOverlap with optional intervals is tricky**
   - Optional intervals need a "presence" boolean
   - The presence boolean must be equivalent to the room assignment
   - CP-SAT's `onlyEnforceIf` is one-way, not two-way

3. **Two-phase decomposition loses global optimality**
   - Phase 1 doesn't know about room constraints
   - Phase 2 is constrained by Phase 1's decisions
   - Can lead to infeasibility even when a global solution exists

---

## All Attempted Solutions

| Attempt | Description | Result |
|---------|-------------|--------|
| Original Single-Phase | Optional intervals with `onlyEnforceIf` | Room conflicts (semantic bug) |
| Fixed Single-Phase | Added `addExactlyOne` for two-way implication | Timeout (scalability) |
| Pairwise Room Constraints | O(n²) constraints per room | OutOfMemoryError |
| Slot-Room Constraints | O(timeslots × rooms × lessons) booleans | Too many variables |
| Two-Phase Phase 1 | Simple NoOverlap | Works |
| Two-Phase Phase 2 | Same pattern as single-phase | Same bug, same scalability |

---

## Options for Expert Consideration

### Option A: Fix Two-Phase Phase 2 with Smaller Model

**Approach:**
1. Fix the one-way implication bug in Phase 2
2. Use bipartite matching per timeslot instead of global CP-SAT
3. Each timeslot is an independent room assignment problem

**Pros:**
- Phase 2 becomes O(max_lessons_per_slot × rooms) instead of O(lessons × rooms)
- Bipartite matching is polynomial time
- Decomposes naturally by timeslot

**Cons:**
- Still depends on Phase 1 making good decisions
- Cross-timeslot soft constraints (lecturer room transitions) become harder

### Option B: Hybrid CP-SAT + Timefold

**Approach:**
1. Use CP-SAT Phase 1 for timeslot assignment (fast, optimal for hard constraints)
2. Use Timefold for room assignment with fixed timeslots
3. Timefold handles soft constraints better than CP-SAT

**Pros:**
- Leverages strengths of both solvers
- CP-SAT gives optimal timeslot assignments quickly
- Timefold's local search handles room soft constraints

**Cons:**
- Two different solver technologies to maintain
- Still has two-phase dependency issue

### Option C: Optimize Timefold Heavily

**Approach:**
1. Implement custom `SelectionSorter` to prioritize difficult lessons
2. Add more aggressive ruin-and-recreate moves
3. Use `PillarChangeMove` for simultaneous multi-lesson changes
4. Consider `PartitionedSearchPhaseConfig` to solve faculties independently

**Pros:**
- Timefold is proven to work correctly
- Many optimization opportunities unexplored
- No fundamental algorithm changes needed

**Cons:**
- Still won't be as fast as CP-SAT for hard constraints
- Requires deep Timefold expertise

### Option D: Problem Reduction / Preprocessing

**Approach:**
1. Identify and pin "easy" lessons (lessons with unique valid rooms)
2. Partition by faculty/zone and solve independently
3. Merge solutions with conflict resolution
4. Reduce timeslots if possible (currently 41 seems high)

**Pros:**
- Reduces problem size dramatically
- Can parallelize solving
- Works with any solver

**Cons:**
- Requires careful partitioning to avoid cross-faculty conflicts
- May miss globally optimal solutions

### Option E: Different CP-SAT Formulation

**Approach:**
1. Use `Cumulative` constraint instead of NoOverlap for rooms
2. Model rooms as "resources" with capacity 1
3. Use interval variables without optional intervals

**Pros:**
- Might avoid the BoolVar explosion
- Cumulative is well-suited for resource scheduling

**Cons:**
- Requires complete reformulation
- May have other scalability issues
- I'm not confident this would work

---

## My Recommendation

I recommend **Option B (Hybrid)** or **Option D (Problem Reduction)**:

**Option B** because:
- CP-SAT Phase 1 is already working
- Timefold is already working (just slow)
- Combining them leverages each solver's strengths
- Minimal risk compared to new formulations

**Option D** because:
- 1832 lessons across multiple faculties suggests natural partitioning
- Solving smaller problems is always easier
- Can be combined with any solver approach

---

## Questions for Expert Review

1. Is there a CP-SAT formulation I'm missing that avoids the BoolVar explosion?
2. Would a constraint programming approach with different variable encoding work?
3. Is the two-phase decomposition fundamentally flawed, or just poorly implemented?
4. Are there commercial solvers (Gurobi, CPLEX) that would handle this better?
5. Would a custom algorithm (e.g., graph coloring, SAT encoding) be more appropriate?
6. Is the problem size (1832 lessons) actually tractable for exact methods, or is heuristic the only option?

---

## Files Involved

| File | Purpose | Status |
|------|---------|--------|
| `SinglePhaseCpSatSolver.java` | Single-phase CP-SAT | Buggy + Too slow |
| `TwoPhaseCpSatSolver.java` | Two-phase CP-SAT | Phase 1 works, Phase 2 buggy |
| `SolverController.java` | Routes to solvers | Not wired to TwoPhase |
| `TimetableConstraintProvider.java` | Timefold constraints | Works |
| `solver-config.xml` | Timefold configuration | Works |
| `application.yml` | Solver selection | Single-phase disabled |

---

## Conclusion

I have reached the limits of my ability to solve this problem. The core issue is that CP-SAT constraint modeling for room assignment requires patterns that either:
1. Create too many variables (scalability)
2. Use incorrect semantics (semantic bugs)

I request expert review to determine if there's a formulation I've missed, or if the project should commit to Timefold with heavy optimization, or if a completely different approach is needed.

---

*Report generated by Cascade AI Assistant - Requesting expert review*
