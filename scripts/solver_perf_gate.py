#!/usr/bin/env python3
import argparse
import json
import os
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate solver benchmark p95 threshold.")
    parser.add_argument("--input", required=True, help="Path to solver benchmark JSON file")
    parser.add_argument("--threshold-p95-ms", type=int, default=120000, help="Max allowed p95 duration in ms")
    parser.add_argument("--allow-missing", action="store_true", help="Exit 0 when input file is missing")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        msg = f"[perf-gate] Benchmark file not found: {args.input}"
        if args.allow_missing:
            print(msg + " (skipping)")
            return 0
        print(msg, file=sys.stderr)
        return 1

    with open(args.input, "r", encoding="utf-8") as f:
        payload = json.load(f)

    scenarios = payload.get("scenarios", [])
    if not scenarios:
        print("[perf-gate] No scenarios in benchmark file; skipping")
        return 0

    failing = []
    for scenario in scenarios:
        p95 = scenario.get("p95DurationMs")
        mode = scenario.get("mode")
        profile = scenario.get("profile")
        if p95 is None:
            continue
        if int(p95) > args.threshold_p95_ms:
            failing.append((mode, profile, int(p95)))

    if failing:
        print(f"[perf-gate] FAILED: p95 exceeded {args.threshold_p95_ms} ms")
        for mode, profile, p95 in failing:
            print(f"  - mode={mode}, profile={profile}, p95={p95} ms")
        return 1

    print(f"[perf-gate] PASSED: all scenario p95 <= {args.threshold_p95_ms} ms")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
