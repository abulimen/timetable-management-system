# Production Readiness Assessment

Date: 2026-02-11  
Scope: Full-stack BUTMS readiness for production deployment (backend, frontend, security, data operations, auditability, testing, operability)

## Executive Verdict

Current state: **Not production-ready yet**.

The system is feature-rich and actively evolving, and both backend compile and frontend production build are currently passing in this workspace.  
However, there are critical security/configuration and release-governance gaps that should be resolved before any public or institutional production launch.

Recommended release posture:
- **Internal pilot / controlled beta**: possible after P0 items are closed.
- **General production release**: only after P0 + key P1 items below are closed.

## Evidence Used

Checks run in this assessment:
- `./mvnw -DskipTests compile` -> **BUILD SUCCESS**
- `npm run build` (frontend) -> **success**, with CommonJS optimization warnings from Handsontable/moment modules
- `./mvnw test` -> **failed in this shell environment** due to Java runtime mismatch (`release version 17 not supported`)

Code and config inspection highlights:
- Hardcoded secrets in `src/main/resources/application.yml:75` and `src/main/resources/application.yml:81`
- Security debug logging enabled in `src/main/resources/application.yml:70`
- CORS origin list hardcoded to localhost in `src/main/java/com/university/timetable/security/SecurityConfig.java:83`
- Global security fallback is present (`anyRequest().authenticated()`) in `src/main/java/com/university/timetable/security/SecurityConfig.java:68`
- Significant hardcoded backend URLs in frontend (`http://localhost:8080`) across multiple components/services, e.g. `frontend/src/app/core/services/api.service.ts:9`, `frontend/src/app/core/services/auth.service.ts:34`
- Test suite is currently small (4 backend test classes under `src/test/java/com/university/timetable`)

## Readiness Scorecard

Scoring: 0-100 (higher is better)

- Security: **42/100**
- Reliability/Correctness: **63/100**
- Testing/Quality Gates: **35/100**
- Operations/Deployability: **48/100**
- Data Integrity/Migrations: **74/100**
- Auditability/Traceability: **72/100**
- UX/Workflow Completeness: **70/100**

Overall weighted readiness: **57/100**

## What Is Already Strong

- Mature domain coverage (timetabling, imports, solver, archive, approvals, audit logs, user management).
- Backend compiles cleanly in current workspace.
- Frontend production build succeeds and emits deployable assets.
- Audit foundation exists and is integrated in many high-value mutating endpoints.
- DB migration discipline exists via Flyway with many versioned scripts.
- Global API auth guard exists (`anyRequest().authenticated()`).

## Production Blockers (P0)

1. Secret exposure in repository config
- Problem: JWT secret and Brevo API key are directly in `application.yml`.
- Risk: immediate credential compromise and account abuse if code is shared or leaked.
- Needed: move to environment/secret manager, rotate keys, purge leaked credentials.

2. Environment coupling to localhost
- Problem: frontend and backend configs are heavily tied to localhost URLs/origins.
- Risk: brittle deployments, wrong endpoint targeting, poor multi-environment support.
- Needed: strict environment-based API base URLs and CORS profile strategy.

3. Missing production test gate
- Problem: tests did not run in this environment and suite depth is limited.
- Risk: regressions can ship undetected.
- Needed: deterministic CI pipeline with Java 17, mandatory backend+frontend checks on merge.

4. Security logging verbosity
- Problem: security package logging set to DEBUG in default config.
- Risk: token/auth context leakage in logs and noisy observability.
- Needed: production logging profile with hardened levels and PII-safe policy.

## High-Priority Non-Blockers (P1)

1. Audit log immutability hardening
- Current audit logging is good functionally, but DB-level immutability/retention controls should be formalized for compliance-grade operation.

2. Formal NFR guardrails
- Define and enforce SLO/SLA targets: API latency, error rates, solver duration budget, import throughput, and audit write reliability.

3. Build and dependency hardening
- Frontend build warnings indicate CommonJS optimization bailouts (Handsontable/moment/core-js modules). Not a blocker, but should be optimized for performance and long-term maintainability.

4. Incident/ops runbooks
- Add documented runbooks for rollback, data repair, solver failure recovery, and email-provider outage handling.

## Important Features and User Stories Still Needed

These are the most valuable remaining stories before a confident production release.

1. Platform/Admin Stories
- As a platform admin, I can promote builds across `dev/staging/prod` with environment-specific secrets and CORS settings.
- As an operator, I can view system health dashboards (API health, queue depth, solver job status, email failures) and receive alerts.
- As a compliance admin, I can enforce retention/archival policy on audit logs and verify immutability guarantees.

2. Data Governance Stories
- As an admin, I can run preflight validation before bulk import and get a full conflict report (including duplicate course-group assignments) before commit.
- As a data steward, I can rollback/undo destructive bulk changes with clear impact previews.
- As an auditor, I can search historical changes by entity, actor, request ID, and time window with exportable evidence.

3. Scheduling and Operations Stories
- As a scheduler, I can run solver with safe guards (dry run, what-if simulation, cancel/retry, diagnostics) and compare results before applying.
- As an academic officer, I can detect and resolve timetable risk clusters (low room diversity, high constraint density) from a single triage view.

4. Lecturer/Availability Workflow Stories
- As an admin, I can manage unavailability requests and direct slot overrides from one unified workflow with full traceability.
- As a lecturer, I receive reliable email notifications for request state changes and direct unavailability updates/deletions.

5. Release Safety Stories
- As a maintainer, I have mandatory CI gates: unit/integration tests, frontend build, static checks, migration validation, and smoke tests.
- As a release manager, I have staged rollout + rollback checklist with database backup verification.

## Recommended Plan to Production

Phase 1 (Immediate, 1-3 days)
- Remove hardcoded secrets and rotate compromised keys.
- Move API URLs and CORS to environment profiles.
- Create production config profile (`application-prod.yml`) with safe logging defaults.

Phase 2 (Short-term, 3-7 days)
- Stand up CI pipeline with Java 17 and required quality gates.
- Expand automated tests around critical workflows (auth, bulk import, course duplicate prevention, availability approvals/revokes).
- Add smoke tests for core user paths.

Phase 3 (Short-term+, 1-2 weeks)
- Complete operational runbooks and alerting hooks.
- Harden audit governance (retention + immutability policy).
- Optimize frontend dependency warnings and bundle hot spots.

## Ship Readiness Decision

- **Today**: Do not ship as full production.
- **After P0 closure**: eligible for controlled pilot.
- **After P0 + key P1 + CI enforcement**: eligible for production go-live.

## Progress Update (2026-02-11)

Completed after this assessment:
- Secrets externalized from default config (`JWT_SECRET`, `BREVO_API_KEY`) and production profile added.
- CORS moved to env-driven config (`APP_CORS_ALLOWED_ORIGINS`).
- Frontend API base URL made runtime-configurable via interceptor + `runtime-config.js`.
- CI workflow added for backend compile/tests and frontend build (`.github/workflows/ci.yml`).
- Java 17 enforced at Maven level (`maven-enforcer-plugin`) to prevent unsupported toolchains.

Still open:
- Rotate previously exposed secrets (JWT/Brevo) and confirm revocation.
- Configure real production environment values in deployment platform.
- Ensure CI is active in remote VCS and required as a merge gate.
- Further expand automated test depth for critical workflows (integration-level auth/import/approval/rollback paths).

Operational closure checklist:
- `docs/PRODUCTION_CUTOFF_CHECKLIST.md`
