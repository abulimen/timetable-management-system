# Performance Optimization Guide for University Timetable Engine

A comprehensive guide to optimizing the OptaPlanner-based timetable scheduling engine for handling large-scale university scheduling (1000+ lessons, 100+ rooms).

---

## Current Performance Baseline

| Metric | Current Value |
|--------|---------------|
| Lessons | 21 |
| Timeslots | 45 |
| Rooms | 6 |
| Solve Time | ~30-36 seconds |
| Score Calculation Speed | ~5,500-7,000/sec |

---

## 1. Multi-Threaded Solving

### What It Is
OptaPlanner can run multiple move evaluations in parallel across CPU cores. Instead of evaluating one move at a time, it evaluates 4-16+ moves simultaneously.

### How It Works
```xml
<!-- solver-config.xml -->
<solver>
    <moveThreadCount>AUTO</moveThreadCount>  <!-- Uses all available cores -->
    <!-- OR specify exact count -->
    <moveThreadCount>4</moveThreadCount>
</solver>
```

When enabled, OptaPlanner:
1. Creates a thread pool matching CPU core count
2. Clones the working solution for each thread
3. Evaluates different candidate moves in parallel
4. Merges results and selects the best move

### Pros
| Advantage | Impact |
|-----------|--------|
| **2-4x speedup** on multi-core CPUs | High |
| No code changes required | Easy implementation |
| Scales with hardware | Future-proof |
| Works with all move types | Universal |

### Cons
| Disadvantage | Severity |
|--------------|----------|
| Higher memory usage (cloned solutions) | Medium |
| Doesn't help on single-core systems | Low |
| Slight overhead for small problems | Low |
| Requires thread-safe constraint provider | Medium |

### Recommendation
**Implement immediately** - This is the highest-impact optimization with minimal effort.

---

## 2. Nearby Selection

### What It Is
Instead of randomly selecting candidate values (timeslots/rooms) for moves, "nearby selection" preferentially selects values that are "close" to the current assignment. This dramatically reduces wasted moves.

### How It Works
For a lesson currently scheduled at Monday 9:00:
- **Without nearby**: May try Friday 17:00 (far away, unlikely to improve)
- **With nearby**: Prefers Monday 10:00, Tuesday 9:00 (nearby options)

```xml
<localSearch>
    <changeMoveSelector>
        <valueSelector>
            <nearbySelection>
                <nearbyDistanceMeterClass>
                    com.university.timetable.solver.TimeslotNearbyDistanceMeter
                </nearbyDistanceMeterClass>
                <parabolicDistributionSizeMaximum>15</parabolicDistributionSizeMaximum>
            </nearbySelection>
        </valueSelector>
    </changeMoveSelector>
</localSearch>
```

### Implementation Required
```java
public class TimeslotNearbyDistanceMeter implements NearbyDistanceMeter<Timeslot, Timeslot> {
    @Override
    public double getNearbyDistance(Timeslot origin, Timeslot destination) {
        // Same day = closer
        if (origin.getDayOfWeek() == destination.getDayOfWeek()) {
            // Difference in hours
            return Math.abs(origin.getStartTime().getHour() - destination.getStartTime().getHour());
        }
        // Different day = add penalty
        return Math.abs(origin.getDayOfWeek().getValue() - destination.getDayOfWeek().getValue()) * 24
             + Math.abs(origin.getStartTime().getHour() - destination.getStartTime().getHour());
    }
}
```

### Pros
| Advantage | Impact |
|-----------|--------|
| **30-50% faster convergence** | High |
| Finds better solutions faster | High |
| Works especially well for scheduling | Domain-specific benefit |

### Cons
| Disadvantage | Severity |
|--------------|----------|
| Requires custom distance meter class | Medium |
| May miss global optima if too narrow | Low |
| More configuration complexity | Low |

### Recommendation
**Implement for large problems (100+ lessons)** - Essential for scalability.

---

## 3. Construction Heuristic Optimization

### What It Is
The construction heuristic creates the initial solution before local search begins. A better initial solution means less work for local search.

### Options Available

| Type | Description | Speed | Quality |
|------|-------------|-------|---------|
| FIRST_FIT | Assigns first valid value | ⚡ Fast | ⭐ Basic |
| FIRST_FIT_DECREASING | Hardest entities first | ⚡ Fast | ⭐⭐ Better |
| STRONGEST_FIT | Best value per entity | 🐢 Slower | ⭐⭐⭐ Best |
| WEAKEST_FIT | Spreads out assignments | 🐢 Slower | ⭐⭐ Good |
| ALLOCATE_ENTITY_FROM_QUEUE | Custom ordering | ⚡ Fast | ⭐⭐⭐ Customized |

### Current Setting
```xml
<constructionHeuristicType>FIRST_FIT</constructionHeuristicType>
```

### Recommended for Timetabling
```xml
<constructionHeuristic>
    <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
</constructionHeuristic>
```

This requires a difficulty comparator on Lesson:
```java
@PlanningEntity(difficultyComparatorClass = LessonDifficultyComparator.class)
public class Lesson { ... }

public class LessonDifficultyComparator implements Comparator<Lesson> {
    @Override
    public int compare(Lesson a, Lesson b) {
        // Schedule longest lessons first (they're harder to place)
        return Integer.compare(b.getDurationHours(), a.getDurationHours());
    }
}
```

### Pros
| Advantage | Impact |
|-----------|--------|
| Better initial solutions | Medium |
| Reduces local search iterations | Medium |
| Can reach 0hard faster | High for feasibility |

### Cons
| Disadvantage | Severity |
|--------------|----------|
| STRONGEST_FIT is slow for large problems | Medium |
| Requires difficulty comparator for DECREASING | Low |

### Recommendation
Use **FIRST_FIT_DECREASING** with difficulty comparator for best balance.

---

## 4. Acceptor Combinations

### What It Is
Acceptors decide which moves to accept during local search. Different acceptors explore the solution space differently.

### Available Acceptors

| Acceptor | Description | Exploration | Exploitation |
|----------|-------------|-------------|--------------|
| **Hill Climbing** | Only accept improving moves | Low | High |
| **Tabu Search** | Remember recent moves, avoid repeating | Medium | High |
| **Simulated Annealing** | Accept worse moves with decreasing probability | High | Medium |
| **Late Acceptance** | Compare against solution from N steps ago | High | Medium |
| **Great Deluge** | Accept if above rising "water level" | Medium | High |

### Current Configuration
```xml
<acceptor>
    <entityTabuSize>7</entityTabuSize>
</acceptor>
```

### Recommended Combination
```xml
<acceptor>
    <entityTabuSize>7</entityTabuSize>
    <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
```

**Why combine?**
- Tabu prevents cycling back to recent solutions
- Late Acceptance allows escaping local optima
- Together they balance exploration and exploitation

### Pros
| Advantage | Impact |
|-----------|--------|
| Better exploration of solution space | High |
| Avoids getting stuck in local optima | High |
| Finds better solutions overall | Medium |

### Cons
| Disadvantage | Severity |
|--------------|----------|
| More memory for late acceptance list | Low |
| May need tuning for optimal results | Medium |

### Recommendation
Add **lateAcceptanceSize** to existing tabu configuration.

---

## 5. Constraint Settings Caching

### What It Is
Currently, constraint settings are fetched from the database on each constraint evaluation. Caching these values eliminates redundant lookups.

### Current Pattern (Inefficient)
```java
private LocalTime getLunchBreakStart() {
    ConstraintSettingsService svc = getSettings();  // Lookup each time
    return svc != null ? svc.getLunchBreakStart() : LocalTime.of(12, 0);
}
```

### Optimized Pattern
```java
public class TimetableConstraintProvider implements ConstraintProvider {
    // Cache settings once
    private LocalTime lunchBreakStart;
    private LocalTime lunchBreakEnd;
    private boolean lunchBreakEnforced;
    // ... other cached settings
    
    private void initializeSettings() {
        ConstraintSettingsService svc = SpringContextHolder.getBean(ConstraintSettingsService.class);
        if (svc != null) {
            this.lunchBreakStart = svc.getLunchBreakStart();
            this.lunchBreakEnd = svc.getLunchBreakEnd();
            this.lunchBreakEnforced = svc.isLunchBreakEnforced();
            // ... cache all settings
        }
    }
}
```

### Pros
| Advantage | Impact |
|-----------|--------|
| **10-20% speedup** in constraint evaluation | Medium |
| No configuration changes needed | Easy |
| Reduces Spring context lookups | Medium |

### Cons
| Disadvantage | Severity |
|--------------|----------|
| Settings changes require solver restart | Low |
| Slightly more complex code | Low |

### Recommendation
**Implement immediately** - Simple code change with measurable impact.

---

## 6. Shadow Variables

### What It Is
Shadow variables are automatically calculated values that depend on planning variables. Instead of calculating `endTime` on every constraint check, it's computed once when `timeslot` changes.

### Current Pattern
```java
public LocalTime getEndTime() {
    if (timeslot == null) return null;
    return timeslot.getStartTime().plusHours(durationHours);  // Calculated every call
}
```

### Optimized with Shadow Variable
```java
@PlanningEntity
public class Lesson {
    @PlanningVariable(valueRangeProviderRefs = "timeslotRange")
    private Timeslot timeslot;
    
    @ShadowVariable(
        variableListenerClass = EndTimeListener.class,
        sourceVariableName = "timeslot")
    private LocalTime endTime;  // Cached, updated automatically
}

public class EndTimeListener implements VariableListener<TimeTable, Lesson> {
    @Override
    public void afterVariableChanged(ScoreDirector<TimeTable> scoreDirector, Lesson lesson) {
        Timeslot ts = lesson.getTimeslot();
        LocalTime newEndTime = ts != null ? ts.getStartTime().plusHours(lesson.getDurationHours()) : null;
        scoreDirector.beforeVariableChanged(lesson, "endTime");
        lesson.setEndTime(newEndTime);
        scoreDirector.afterVariableChanged(lesson, "endTime");
    }
}
```

### Pros
| Advantage | Impact |
|-----------|--------|
| Eliminates redundant calculations | Medium |
| Cleaner constraint code | Low |
| OptaPlanner handles update timing | Automatic |

### Cons
| Disadvantage | Severity |
|--------------|----------|
| More complex entity setup | Medium |
| Learning curve for VariableListener | Medium |
| Overkill for simple calculations | Low |

### Recommendation
Implement for complex derived values, but `getEndTime()` is simple enough to skip.

---

## 7. Partitioned Search

### What It Is
For very large problems, split the solving into independent partitions that can be solved separately (potentially in parallel).

### Example: Partition by Department
```java
public class DepartmentPartitioner implements SolutionPartitioner<TimeTable> {
    @Override
    public List<TimeTable> splitWorkingSolution(TimeTable solution) {
        Map<String, List<Lesson>> byDepartment = solution.getLessons().stream()
            .collect(Collectors.groupingBy(l -> l.getCourse().getDepartment()));
        
        return byDepartment.values().stream()
            .map(lessons -> new TimeTable(lessons, solution.getTimeslots(), solution.getRooms()))
            .collect(Collectors.toList());
    }
}
```

```xml
<partitionedSearch>
    <solutionPartitionerClass>
        com.university.timetable.solver.DepartmentPartitioner
    </solutionPartitionerClass>
</partitionedSearch>
```

### Pros
| Advantage | Impact |
|-----------|--------|
| **Essential for 1000+ lessons** | Critical at scale |
| Parallel partition solving | High |
| Linear scaling with partitions | High |

### Cons
| Disadvantage | Severity |
|--------------|----------|
| Cross-partition constraints are complex | High |
| Requires careful partition design | High |
| May miss global optima | Medium |

### Recommendation
**Only for 500+ lessons** - Adds complexity, essential for large universities.

---

## 8. Database & Caching Optimizations

### Second-Level Cache
```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          region.factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
```

### Connection Pool Tuning
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

### Batch Updates for Solution Saving
```java
@Transactional
public void saveSolutionBatch(List<Lesson> lessons) {
    int batchSize = 50;
    for (int i = 0; i < lessons.size(); i++) {
        lessonRepository.save(lessons.get(i));
        if (i % batchSize == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }
}
```

---

## Implementation Priority Matrix

| Optimization | Impact | Effort | Priority |
|--------------|--------|--------|----------|
| Multi-threaded solving | ⚡⚡⚡ High | 🟢 Easy | **1st** |
| Cache constraint settings | ⚡⚡ Medium | 🟢 Easy | **2nd** |
| Late acceptance acceptor | ⚡⚡ Medium | 🟢 Easy | **3rd** |
| FIRST_FIT_DECREASING | ⚡ Medium | 🟡 Medium | **4th** |
| Nearby selection | ⚡⚡⚡ High | 🟡 Medium | **5th** |
| Partitioned search | ⚡⚡⚡ Critical | 🔴 Hard | *When needed* |
| Shadow variables | ⚡ Low | 🟡 Medium | *Optional* |

---

## Quick Start: Implement Top 3 Optimizations

### 1. Enable Multi-Threading
```xml
<!-- solver-config.xml -->
<solver>
    <moveThreadCount>AUTO</moveThreadCount>
    ...
</solver>
```

### 2. Add Late Acceptance
```xml
<acceptor>
    <entityTabuSize>7</entityTabuSize>
    <lateAcceptanceSize>400</lateAcceptanceSize>
</acceptor>
```

### 3. Cache Settings in ConstraintProvider
Modify `TimetableConstraintProvider.java` to cache settings on first access rather than looking them up repeatedly.

---

## Expected Results After Optimization

| Metric | Before | After (Expected) |
|--------|--------|------------------|
| Solve Time (21 lessons) | 30-36 sec | 10-15 sec |
| Solve Time (100 lessons) | N/A | 60-90 sec |
| Solve Time (500 lessons) | N/A | 3-5 min |
| Score Calc Speed | 5,500/sec | 15,000-20,000/sec |
