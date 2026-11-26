# MedOS HMS — Agent Instructions

> This file is read by AI agents (Kiro, Codex, Gemini CLI, etc.) at session start.
> `CLAUDE.md` holds the full context; this file states the working rules.

## Read first

Before writing any code, read **`CLAUDE.md`** at the repo root. It has the
architecture, invariants, module map, open gaps, and common mistakes.

For the compact discovery map (hotspots, commands), read:
`.agents/skills/med-os-project-context/PROJECT_SNAPSHOT.md`

For active design specs, read (task-dependent):
- `docs/superpowers/specs/` — design specs for discharge, partial dispensing, parity
- `docs/adr/0010-product-module-scope.md` — module scope decision
- `docs/requirements/MedOS_Business_Requirements_Document_v4.md` — BRD v4

## Working rules

### Scope
- Work on the specific task only. No unrequested refactors.
- Ask before touching security, encryption, or multi-tenant isolation code.
- When fixing a bug, add or verify a test that would catch a regression.

### Schema changes
- **All** schema changes go into a new `database/migrations/V<n>__name.sql`.
- Never create `backend/src/main/resources/db/migration/`.
- Never edit an applied migration file.
- After adding a migration, run `./tools/verify-migrations.sh`.

### PII / encryption
- New patient or encounter columns that hold personal data **must** use the
  existing AES-256-GCM column converter.
- Never store a plaintext PII value. Never log PII.
- If unsure whether a field is PII, treat it as PII.

### Tenant isolation
- Every new tenant-scoped table must include `tenant_id BIGINT NOT NULL REFERENCES tenants(id)`.
- Add the table name to `EncryptedPiiTables.TENANT_TABLES` (or its write-scoping equivalent).
- Never write a JPQL/SQL query without a tenant predicate on a tenant-scoped table.

### Security
- No new public endpoints without explicit role guards in `SecurityConfig.java`.
- Idempotency endpoints must scope keys to `tenantId + userId`.
- Never trust `X-Forwarded-For` for rate limiting or IP logging.

### Validation
Run the smallest check that covers the change:

| Change type | Command |
|---|---|
| Backend logic | `cd backend && mvn clean test` |
| Backend packaging | `cd backend && mvn -DskipTests package` |
| Frontend logic | `cd frontend && npm test` |
| Frontend style | `cd frontend && npm run lint` |
| Frontend build | `cd frontend && npm run build` |
| Migration added | `./tools/verify-migrations.sh` |
| Compose changes | `docker compose config --quiet` |
| Prod secrets | `./tools/verify-prod-secrets.sh` |

### Output format
When finishing work, state:
1. Files changed and one-line purpose for each.
2. Validation commands run and their outcome.
3. Any production risk (security / reliability / compliance / performance).

## What NOT to do

- Do not edit `database/migrations/V1__initial_schema.sql` or any applied migration.
- Do not add a default value for `JWT_SECRET` or `PII_ENCRYPTION_KEY` anywhere.
- Do not return the JPA entity from `GET /api/v1/users/me` (must be `CurrentUserDTO`).
- Do not publish a port other than Caddy in `docker-compose.prod.yml`.
- Do not seed users or tenants inside a Flyway migration.
- Do not run `git push` to `main`/`master`/`mainline`.
- Do not skip `mvn clean` — stale Surefire XML causes false-green reports.
- Do not write `console.error` and leave UI in a broken zero state (see G40).
- Do not use `X-Tenant-Id` header — it was removed from CORS config (G08).

## Current priorities (as of 2026-11)

**Block prod launch (P0 races):**
- G09 Double-dispense: `SELECT ... FOR UPDATE` on prescription row in `DispenseService`
- G10 Double-discharge: `SELECT ... FOR UPDATE` on admission row in `AdmissionService`
- G11 Double-invoice: atomic claim on `charges` rows in `BillingService`

**Active issue tracker:** https://github.com/gaurav-amritkar/med-os/issues
**November audit tracker:** issue #219
