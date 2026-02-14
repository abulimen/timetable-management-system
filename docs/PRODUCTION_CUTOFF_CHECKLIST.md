# Production Cutoff Checklist

This checklist closes the remaining operational items from `docs/PRODUCTION_READINESS_ASSESSMENT.md`.

## 1) Credential Rotation (Required)

- Rotate JWT signing secret used by production.
- Revoke and reissue Brevo API key.
- Update deployment secrets store with new values:
  - `JWT_SECRET`
  - `BREVO_API_KEY`
- Restart backend with new secrets loaded.
- Verify:
  - login works
  - refresh token flow works
  - outbound emails still send

## 2) Deployment Environment Variables (Required)

Set all required variables from:
- `docs/PRODUCTION_ENV_TEMPLATE.md`

Minimum required:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `BREVO_API_KEY`
- `BREVO_SENDER_EMAIL`
- `APP_LOGIN_URL`
- `APP_CORS_ALLOWED_ORIGINS`

## 3) CI Enforcement in Remote Repo (Required)

In GitHub/GitLab branch protection for `main`:
- Require status checks to pass before merge.
- Mark CI workflow checks as required:
  - backend compile/tests
  - frontend build
- Disallow direct pushes to `main` (PR-only flow).

## 4) Frontend CommonJS Warnings (Accepted + Mitigated)

Current policy:
- CommonJS dependencies from `core-js` and `moment` are explicitly accepted in Angular config.
- Revisit optimization later if bundle size or startup performance becomes an issue.

Tracking:
- `frontend/angular.json` has `allowedCommonJsDependencies` configured.

## 5) Test Coverage Expansion (Next Iteration)

Current baseline:
- Full backend suite passes (`./mvnw test`) with zero failures.
- Added tests for:
  - duplicate course-group assignment prevention
  - auth login success/failure
  - availability request guard checks

Next recommended additions:
- Integration (`MockMvc`) tests for course create/update duplicate rejections.
- Availability workflow transition tests (`approve`, `reject`, `revoke`, `resubmit`) at service level.
- Import rollback and conflict-resolution integration tests.

