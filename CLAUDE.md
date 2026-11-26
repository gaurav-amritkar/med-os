# MedOS HMS — LLM Context File

> Read this before writing any code. The code is the source of truth; this file
> summarises structure, invariants, and known gotchas to prevent common mistakes.

## Project at a glance

**MedOS** is a multi-tenant Hospital Management System for Indian hospitals.
- Backend: Java 17 + Spring Boot 3.5.x (Maven)
- Frontend: React 19 + Vite 8
- Database: PostgreSQL 16 (primary) + Redis 7.4 (cache/idempotency/rate-limit)
- Auth: JWT (stateless) + BCrypt
- PII encryption: AES-256-GCM envelope encryption — KEK wraps per-tenant DEK
- Migrations: Flyway (the **only** schema owner — see invariants below)
- CI: GitHub Actions (4 required jobs on every PR)
- Production: Supabase-managed PostgreSQL, Caddy TLS, single host (ADR-0001)

## Repository layout

```
backend/            Java 17 / Spring Boot 3.5.x
  pom.xml           Build; Java version pinned here
  src/main/java/com/medos/
    config/         Spring beans (security, Redis cache, WebSocket, encryption init)
    controller/     REST controllers (one per domain)
    dto/            Request/Response DTOs — API surface lives here
    entity/         JPA entities (map 1:1 to Flyway tables)
    exception/      GlobalExceptionHandler + typed exceptions
    mapper/         Entity ↔ DTO (MapStruct)
    modules/        Feature packages: appointment, billing, clinical, enquiry, payment, pharmacy
    repository/     Spring Data JPA repositories
    security/       JWT, tenant context, KEK/DEK key management, rotation, rate limiter
    service/        Business logic (cross-cutting; module-specific lives in modules/)
    util/           PII cipher, blind index, audit logger
  src/main/resources/
    application.yml, application-prod.yml

frontend/           React 19 + Vite 8
  src/
    api/            Axios client and per-domain API modules
    components/     Layout, Sidebar, Header, Modal, Toast
    pages/          Login, Dashboard, Patients, Encounters,
                    Pharmacy, Admissions, Billing, Appointments, Lab, Staff
    store/          Zustand (auth, toast)
  e2e/              Playwright tests (require E2E_PASSWORD env var)

database/
  migrations/       V1__…sql … V17__…sql — 17 Flyway files, 31 tables total
  Dockerfile        Dev PostgreSQL image
  migrations.Dockerfile  Versioned Flyway migration job (the migrate service)

cache/              Redis image, redis.conf, entrypoint
caddy/              Production reverse proxy, Caddyfile, Dockerfile
docs/
  adr/              10 ADRs (all Accepted)
  requirements/     BRD_v4.md, PRD_v4.md (v3 PDFs retained for provenance only)
  superpowers/specs/ Design specs for active work (discharge, partial dispensing, parity)
  operations.md     Runbooks (§7d KEK rotation, §7e DEK rotation)
  gap-analysis-2026-10-13.md  Full gap inventory with status
tools/              verify-migrations.sh, verify-prod-secrets.sh, seed-dev.sh
.agents/skills/med-os-project-context/  Agent bootstrap skill (PROJECT_SNAPSHOT.md)
docker-compose.yml      Dev stack (includes in-host db)
docker-compose.prod.yml Production stack (no db; Supabase; secrets as files)
```

## Critical invariants — violating these breaks the system

### 1. Schema ownership
- Migrations live in **`database/migrations/V<n>__name.sql`** only.
- There is **no** `backend/src/main/resources/db/migration/`.
- **Never edit an applied migration.** Flyway checksums; editing causes startup failure.
- New changes = new `V<n>__` file. The `migrate` container runs before the backend.

### 2. PII encryption (easy to get wrong)
`PII_ENCRYPTION_KEY` is the **KEK** (key-encryption key), not the data key.

| What | Where |
|---|---|
| KEK wraps per-tenant DEK | `security/TenantKeyHolder.java` |
| AES-256-GCM wrap/unwrap | `security/KeyWrapCipher.java` |
| Ciphertext format `kv1.d<gen>:<b64>` | `util/PiiCiphertextFormat.java` |
| Blind index (independent key) | `util/BlindIndexUtil.java` + `security/BlindIndexKeyService.java` |
| Encrypted columns (5 on Patient, 4 on Encounter) | entity converters |
| KEK rotation runbook | `docs/operations.md` §7d |
| DEK rotation (per-tenant) | `docs/operations.md` §7e, `security/DekRotationService.java` |

New PII columns must use the same envelope converter. Never store a plaintext PII value.

### 3. Tenant isolation
- Every tenant-scoped table carries a `tenant_id` column.
- `TenantStatementInspector` scopes SELECT, UPDATE, DELETE at the SQL boundary
  and rejects INSERTs on tenant tables that omit `tenant_id`.
- Never write a raw query that bypasses `TenantContext.currentTenantId()`.
- The `TENANT_TABLES` set in the inspector is the authoritative list of scoped tables.

### 4. Security
- `JwtAuthenticationFilter` resolves role **from the current membership row**, not the token claim (R02 fix).
- Idempotency keys are scoped to `tenantId + userId + endpoint + clientKey`.
- Login rate limiter uses socket peer, not `X-Forwarded-For`.
- `/api/v1/users/me` must never return a password hash (Playwright asserts this).
- Actuator: only `/manage/health` and `/manage/info` are public. All other `/manage/**` and `/swagger-ui.html` require `admin` role.

### 5. Production compose
- Production uses `docker-compose.prod.yml` only. Never `docker-compose.yml` for prod.
- `medos-private` is NOT `internal: true` (must reach Supabase DNS). `medos-edge` IS internal.
- Secrets are files under `secrets/`, never environment variables in production.
- Only Caddy publishes ports (80/443). Backend, Redis, migrate are not published.

### 6. Frontend
- API proxy: `/api` → backend (nginx at build time).
- Never commit a password or secret into the frontend `.env`.
- E2E tests require `E2E_PASSWORD` env var; specs skip without it.

## Module map (backend `modules/`)

| Package | Domain |
|---|---|
| `appointment` | Scheduling, OPD queue, check-in/out |
| `billing` | Invoices (GST), charges, idempotency-keyed |
| `clinical` | Encounters, admissions, discharge summaries, lab orders |
| `enquiry` | Enquiry desk + status history |
| `payment` | Cash/card/UPI/insurance payments, idempotency-keyed |
| `pharmacy` | Medicine catalog, stock batches, FEFO dispense, prescriptions |

Cross-cutting services (live in `service/`): `AuthService`, `PatientService`, `TenantService`, `UserService`, `NotificationService`, `IdempotencyService`.

## Database — 31 tables, 17 migrations

V1 creates the core schema (23 tables). V2–V17 add:
V2 PII key lifecycle, V3 staff management, V4 super-admin + feature flags,
V5 super_admin role for tenant_users, V6 tenant DEK history, V7 enquiry desk,
V8 appointments deepening, V9 foreign keys, V10 tenant active not-null,
V11 tenant user permissions, V12 consent tenant scope, V13 DEK rotation evidence,
V14 discharge summaries, V15 lab role, V16 prescription quantity, V17 lab result encrypted.

## Open gaps (prioritised)

**P0 (races — fix before launch):**
- G09: Double-dispense — prescription row not locked (`DispenseService`)
- G10: Double-discharge — admission row not locked (`AdmissionService`)
- G11: Double-invoice — unbilled charges not locked (`BillingService`)

**P1:**
- G06: Auth-event auditing (login/failure/lockout) missing from `audit_log`
- G07: No rate limit on `/api/v1/onboarding/register`
- G12: No container memory/CPU limits in either compose file
- G14: Backup/restore drill against Supabase not done
- G17: Name search decrypts up to 5,000 rows in Java — dies past a few thousand patients

**P2 (features):**
- G23: Partial dispensing status (`partially_dispensed`) unreachable
- G24: Room GST hardcoded 0% (`GST_RATE_ROOM = 12%` unused)
- G37: Search pages fire request per keystroke (no debounce / AbortController)
- G38: Modals lack `role="dialog"`, focus trap, Escape key (WCAG AA failure)
- G39: Admissions page has zero error handling
- G40: Dashboard shows fabricated zeros on API failure

**November audit (issue #219) — still open:**
- R08: Discounted invoice balance not recalculated correctly
- R12/R13: Deployment and CI evidence gates for pilot

Full gap inventory: `docs/gap-analysis-2026-10-13.md`

## Validation commands

```bash
# Backend (always use `clean` — stale reports in target/ misreport results)
cd backend && mvn clean test
cd backend && mvn -DskipTests package

# Frontend
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build

# Infrastructure
docker compose config --quiet
./tools/verify-migrations.sh
./tools/verify-prod-secrets.sh

# E2E (requires running stack)
cd frontend
E2E_PASSWORD='<password>' npx playwright test e2e/smoke.spec.js
E2E_PASSWORD='<password>' npx playwright test e2e/full.spec.js
```

## Common mistakes to avoid

1. **Adding a migration file inside `backend/`** — the Flyway container only reads `database/migrations/`.
2. **Editing an applied migration** — creates a checksum failure on startup.
3. **Storing plaintext PII** — all patient demographics and encounter notes are encrypted; use the existing column converters.
4. **Bypassing TenantContext** — always set `TenantContext.setCurrentTenant()` before writing tenant-scoped data; the inspector enforces it on reads but service code must set it for writes too.
5. **Trusting `X-Forwarded-For` for rate limiting** — the rate limiter correctly uses socket peer; don't revert this.
6. **Using `docker-compose.yml` for production** — it has an in-host `db` and no Caddy.
7. **Adding a new PII column without the envelope converter** — plaintext in the DB is a DPDP violation.
8. **Publishing a port other than Caddy in `docker-compose.prod.yml`** — CI asserts only Caddy may publish ports.
9. **Skipping `mvn clean`** — stale Surefire XML in `target/` causes false-green test reports.
10. **Adding a super-admin path that bypasses `tenant_users` membership check** — the JWT filter resolves authority from the membership row.

## ADR summary

| # | Title | Decision |
|---|---|---|
| 0001 | Single-host + managed Postgres | Supabase ap-south-1; no in-host DB in prod |
| 0002 | Row-level tenancy | `tenant_id` on every clinical table; `TenantStatementInspector` |
| 0003 | Gated self-serve onboarding | Platform secret or `super_admin` required; blank = deny |
| 0004 | Secret management | Files under `secrets/`, never env vars in prod |
| 0005 | Migration ownership | `database/migrations/` only; `migrate` container runs first |
| 0006 | v1 non-goals | Infrastructure non-goals (K8s, API GW, Redis cluster, etc.) |
| 0007 | Redis non-authoritative | Redis holds cache/ephemeral state; Postgres is truth |
| 0008 | Supabase managed Postgres | Auth stays in-house; DB delegated to Supabase |
| 0009 | Envelope encryption per-tenant DEKs | KEK wraps DEK; versioned ciphertext; independent blind-index key |
| 0010 | Product module scope (parity) | 27-module MedWorks parity in scope; mobile apps and claim automation out |
