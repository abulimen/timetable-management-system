# Known System Limitations and Engineering Constraints

This document outlines the current technical boundaries, architectural trade offs, and acknowledged engineering constraints of the University Timetable Management System.

---

## 1. Single Node Solver Concurrency

### Context
Optimization jobs run as asynchronous tasks managed by the in process `SolverManager`. An in memory `AtomicBoolean` lock ensures that only one solve job executes at any given time.

### Current Limitation
In a multi replica or clustered deployment, separate server nodes do not share memory space. Running multiple backend instances against a shared database without distributed locking (such as Redis locks or PostgreSQL advisory locks) could allow two nodes to start competing solver jobs on the same academic term simultaneously.

### Mitigation and Roadmap
For single node university deployments, the `AtomicBoolean` lock and database status flags prevent local race conditions. For distributed multi cluster scaling, jobs should be queued through a distributed task orchestrator such as RabbitMQ, Kafka, or Redis with distributed mutex locks.

---

## 2. Dynamic Soft Weight Convergence Dynamics

### Context
The solver employs `AdaptiveSolverListener` to scale soft constraint penalties dynamically based on hard score feasibility.

### Current Limitation
Dynamic weight modulation introduces minor non determinism in intermediate trajectory scoring across different CPU speeds. When soft weights change dynamically during move evaluation, two runs with identical random seeds on different hardware might evaluate slightly different numbers of local search steps within fixed time limits.

### Mitigation and Roadmap
Termination is configured with fixed time budgets and step limits. The final solution is evaluated against static, standard weights to guarantee score integrity and reproducible ranking.

---

## 3. Scale Boundaries for Simultaneous Departmental Solves

### Context
The solver loads an entire term planning problem into Java heap memory as a unified `@PlanningSolution` instance (`TimeTable`).

### Current Limitation
For massive university institutions with more than 5000 lessons, 500 rooms, and 2000 lecturers across 10 independent faculties, solving the entire university in a single unified planning solution requires significant heap memory (4GB to 8GB) and extended solving time.

### Mitigation and Roadmap
Large institutions should partition the scheduling problem by academic faculty or campus zones where facilities and lecturers are disjoint. Independent department schedules can be generated in parallel and merged.

---

## 4. Frontend Build Time Dependencies

### Context
The frontend is built using Angular 17 with Node.js and TypeScript.

### Current Limitation
Running security vulnerability audits (`npm audit`) on the frontend reports low to moderate advisory warnings in development tooling packages (such as Webpack, Terser, and Vite bundled transitively inside the Angular CLI toolchain).

### Mitigation and Roadmap
These dependencies exist strictly in build time devDependencies and are not bundled or served in the production web client artifacts. Production assets are compiled to static, optimized JavaScript files served via Spring Boot or Nginx.

---

## 5. Constraint Parity across Timefold and OR Tools CP SAT

### Context
The application supports both Timefold Solver (metaheuristic) and Google OR Tools CP SAT (exact constraint satisfaction).

### Current Limitation
Timefold Solver includes advanced custom soft preference heuristics (such as graduated fatigue decay and room stability clustering) that are expressed as functional Java streams. Google OR Tools CP SAT models strict hard constraints and standard soft penalties through mathematical linear equations.

### Mitigation and Roadmap
CP SAT is recommended primarily for pre solve satisfiability verification and strict hard constraint feasibility testing, while Timefold Solver is the designated default engine for nuanced institutional soft preference optimization.
