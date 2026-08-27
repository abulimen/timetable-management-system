# Architecture Decision Records

This document records the foundational architectural decisions made for the University Timetable Management System.

---

## 1. Metaheuristic Search Engine: Timefold Solver over Pure Exact Solvers

### DECISION
Adopt Timefold Solver as the primary optimization engine for university course timetable generation.

### WHY
University course scheduling is an NP hard combinatorial optimization problem with large search spaces. Real academic institutions require scheduling hundreds of lessons across dozens of rooms and timeslots while satisfying complex academic policies. Exact constraint programming solvers can exhibit exponential execution time on large datasets when soft preferences are heavily weighted. Timefold Solver uses metaheuristic local search algorithms including Tabu Search and Late Acceptance to navigate massive search spaces quickly, producing near optimal schedules within predictable time boundaries.

### TRADE OFF
Metaheuristic search does not mathematically prove global optimality, unlike pure integer linear programming. However, in university scheduling contexts, reaching a guaranteed 0 hard conflict schedule with high soft score within 30 seconds is far more valuable than waiting hours for mathematical proof of global soft score optimality.

### CURRENT STATUS
Active and stable. Timefold Solver 1.31.0 serves as the primary solving engine in `SolverService.java`.

---

## 2. Google OR Tools CP SAT Integration: Pre Solve Feasibility and Exact Solver

### DECISION
Integrate Google OR Tools CP SAT alongside Timefold Solver to provide exact pre solve satisfiability checks and alternative exact solving.

### WHY
Large academic datasets may contain logically impossible constraint combinations, such as assigning more teaching hours to a lecturer than available timeslots in a week. Running a metaheuristic search on an infeasible problem wastes computational resources and user time. Google OR Tools CP SAT converts the timetable constraints into a Boolean satisfiability model. It either proves satisfiability within milliseconds or identifies contradictory constraints before full optimization begins.

### TRADE OFF
Maintaining constraint parity across two separate solver definitions requires discipline. OR Tools models constraints via mathematical integer intervals and Boolean clauses, whereas Timefold uses functional streams in Java.

### CURRENT STATUS
Active. Implemented in `CpSatFeasibilityChecker.java`, `CpSatSolverService.java`, and `HybridCpSatSolverService.java`. Pre solve feasibility checks run automatically before long metaheuristic passes, and users can explicitly select the CP SAT engine via the solve request payload.

---

## 3. Forward Checking Heuristic Construction Seed

### DECISION
Implement a custom forward checking greedy heuristic to generate initial solution seeds before handing execution over to Timefold local search.

### WHY
Standard random initialization produces high numbers of initial hard collisions, requiring many local search iterations just to reach initial feasibility. The custom `ForwardCheckingConstructionService` builds a conflict graph across lecturers and student cohorts, sorts lessons by degree of difficulty, and uses forward checking to place lessons into timeslots and rooms that leave maximum open options for future unassigned lessons.

### TRADE OFF
Seed generation adds a small computational overhead of 10 to 50 milliseconds before local search starts. If the seed heuristic assigns a sub optimal slot, the metaheuristic solver must explore moves to undo it.

### CURRENT STATUS
Active and fully integrated into `SolverService.java` and `ForwardCheckingConstructionService.java`.

---

## 4. Adaptive Soft Weight Modulation via Solver Lifecycle Listener

### DECISION
Modulate soft constraint weights dynamically during solver execution using an `AdaptiveSolverListener` hooked into `BestSolutionChangedEvent`.

### WHY
During early optimization phases, soft constraints can pull the solver away from resolving difficult hard constraints. By scaling soft penalties lower when hard violations exist and increasing soft weights as the schedule approaches zero hard violations, the engine guides search trajectories toward strict feasibility first, followed by rapid quality refinement.

### TRADE OFF
Dynamic scoring during search can alter the fitness landscape. To maintain deterministic scoring, `TimetableConstraintProvider` queries atomic weight multipliers adjusted strictly at solution improvement boundaries.

### CURRENT STATUS
Active. Dynamic weight calculation is executed via `AdaptiveSolverListener.java` and referenced in `TimetableConstraintProvider.java`.

---

## 5. Domain Modeling for Hierarchical Student Groups and Combined Classes

### DECISION
Model student groups with self referential parent child hierarchies and many to many course relationships.

### WHY
Academic departments frequently conduct combined lectures for multiple cohorts simultaneously, while splitting those same cohorts for laboratory sessions. A relational schema with parent child group pointers allows automatic conflict detection where parent events block child subgroups, while child lab sessions do not block disjoint sibling groups.

### TRADE OFF
Hierarchical traversal introduces additional checks during score evaluation. The system mitigates this by caching flat conflict group ID sets on `Lesson` domain instances.

### CURRENT STATUS
Active in `StudentGroup.java`, `Course.java`, and `Lesson.java`.

---

## 6. Single Run Concurrency Control and Solver Synchronization

### DECISION
Enforce single active solve execution per tenant or dataset using `AtomicBoolean` locks in memory, backed by database status tracking.

### WHY
Timetable solving is CPU intensive, utilizing multiple hardware threads for move evaluation. Permitting multiple concurrent solver passes on the same database entities causes thread contention, high CPU throttling, and database write conflicts.

### TRADE OFF
Users must wait for an active solve job to finish or explicitly terminate it before starting another run.

### CURRENT STATUS
Active in `SolverService.java` with polling endpoints and cancellation hooks exposed via REST API.
