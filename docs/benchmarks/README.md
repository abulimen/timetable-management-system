# Solver Benchmarks

This folder stores benchmark outputs for solver performance tracking.

## Generate Benchmark Results

Run the app, then call:

`POST /api/v1/solver/benchmark`

Example request body:

```json
{
  "warmupRuns": 1,
  "measuredRuns": 10,
  "pollIntervalMs": 1000,
  "perRunTimeoutSeconds": 600,
  "modes": ["FULL_REPLAN"],
  "profiles": ["FAST_FEASIBLE", "BALANCED", "QUALITY"],
  "skipFeasibility": true,
  "clearAssignmentsBeforeEachRun": true
}
```

Save response JSON to:

`docs/benchmarks/latest_solver_benchmark.json`

## CI Gate

CI runs:

`python3 scripts/solver_perf_gate.py --input docs/benchmarks/latest_solver_benchmark.json --threshold-p95-ms 120000 --allow-missing`

If `latest_solver_benchmark.json` exists, scenarios with `p95DurationMs > 120000` fail CI.
