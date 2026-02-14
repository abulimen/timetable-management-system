# Deferred Production Actions

These are intentionally deferred and must be completed before production go-live.

## 1) Rotate/Revoke Exposed Credentials

- Rotate JWT signing secret in your secrets manager.
- Revoke and regenerate Brevo API key.
- Update deployment secrets with new values.

## 2) Set Real Production Environment Variables

- Configure production/staging environment variables in deployment platform.
- Use `docs/PRODUCTION_ENV_TEMPLATE.md` as the source template.

## 3) Enable Remote VCS Branch Protection + Required CI Checks

- Protect `main` (or release branch) against direct pushes.
- Require pull requests.
- Require CI checks to pass before merge (backend + frontend jobs).

