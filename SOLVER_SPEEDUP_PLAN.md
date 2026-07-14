# Solver Speedup Plan: 50% Faster with Clash-Free Timetable

## Problem Analysis

Current state (from logs):
- CP-SAT Phase 1a: 23s — assigns timeslots ✓
- Room matching Phase 1b: Assigns ~400 rooms, fails for ~1400 lessons ✗
- Timefold Phase 2: 85+ minutes, still at -133hard/-11073soft ✗

**Root cause**: Room matching algorithm is too simple (greedy, no backtracking). Timefold spends most of its time assigning rooms instead of optimizing.

Performance killers:
1. 1400 lessons need room assignment during local search
2. Each step evaluates ~500 moves (125:1 rejection rate)
3. entityTabuSize=45 too small for 1832 lessons
4. No early termination when stuck

## Solution: Smart Room Matching + Tuning

### Phase 1: Improve Room Matching Algorithm

Current: Greedy assignment (pick first compatible room)
New: Constraint-based assignment with backtracking

```
1. Sort lessons by difficulty (most constrained first)
2. For each lesson:
   a. Find all compatible rooms
   b. Check if room is already used in this timeslot
   c. If yes, try to reassign conflicting lesson to different room (backtrack)
   d. If backtrack fails, try next room
   e. If all rooms fail, mark lesson as unassigned (Timefold will handle it)
```

Expected outcome: Room matching succeeds for ~1200 lessons instead of ~400.

### Phase 2: Tune Timefold Parameters

Current solver-config.xml:
- entityTabuSize=45 (too small)
- lateAcceptanceSize=800 (conservative)
- acceptedCountLimit=4 (125:1 rejection rate)
- unimprovedSecondsSpentLimit=60s (no early termination)

New values:
- entityTabuSize=120 (6.5% of 1832 lessons)
- lateAcceptanceSize=1200 (50% more history)
- acceptedCountLimit=8 (accept more moves, less wasted evaluations)
- unimprovedSecondsSpentLimit=120s (allow more time for hard violations)
- minutesSpentLimit=45 (3x current limit for large datasets)

### Phase 3: Optimize Move Evaluation

Current: NearbyMoveFactory evaluates ~500 moves per step
Problem: Most moves are rejected (125:1 ratio)

Fixes:
1. Increase acceptedCountLimit to 8 (accept more moves)
2. Reduce MAX_MOVE_CREATION_MS from 200ms to 150ms (generate fewer but better moves)
3. Filter out obviously bad moves before evaluation

## Implementation Plan

### Step 1: Rewrite RoomMatchingService
- Add backtracking algorithm
- Sort lessons by difficulty (fewer compatible rooms = harder)
- Try to reassign conflicting lessons when stuck
- Track assignment statistics

### Step 2: Update solver-config.xml
- Increase entityTabuSize to 120
- Increase lateAcceptanceSize to 1200
- Increase acceptedCountLimit to 8
- Increase unimprovedSecondsSpentLimit to 120
- Increase minutesSpentLimit to 45

### Step 3: Tune NearbyMoveFactory
- Reduce MAX_MOVE_CREATION_MS to 150ms
- Add move quality filter (skip moves that increase hard score)

### Step 4: Test and Validate
- Run on test dataset
- Verify room0hard violations
- Measure speed improvement (target: 50% faster)

## Expected Results

Before:
- Total time: 85+ minutes
- Final score: -133hard/-11073soft (many violations)

After:
- Room matching: ~1200 lessons assigned (vs ~400)
- Timefold: Focuses on optimization, not assignment
- Total time: ~40 minutes (50% faster)
- Final score: 0hard/-8000soft (clash-free)

## Risk Mitigation

If backtracking is too slow:
- Limit backtrack depth to 3 levels
- Time limit: 30 seconds for room matching
- Fall back to greedy if time exceeded

If Timefold still slow:
- Further increase acceptedCountLimit to 12
- Reduce lateAcceptanceSize to 1000
- Add more aggressive ruin-recreate

## Success Criteria

✓ Total solve time < 45 minutes (50% faster than 85+ min)
✓ Hard score = 0 (clash-free timetable)
✓ Soft score < -8000 (reasonable quality)
