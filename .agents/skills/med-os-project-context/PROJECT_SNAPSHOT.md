# MedOS Project Snapshot

Optimised for quick agent orientation. Treat as a starting point; verify targeted
files before edits. **The code is the source of truth** — this snapshot and
`docs/roadmap.md` have both been stale in the past.

## Top-level layout

```
backend/            Java 17 / Spring Boot 3.5.x (Maven)
frontend/           React 19 + Vite 8
database/
  migrations/       V1–V17 Flyway SQL files — the ONLY schema owner (17 files, 31 tables)
  Dockerfile        Dev PostgreSQL 16 image
  migrations.Dockerfile  Versioned Flyway migration job
cache/              Redis 7.4 image + redis.conf + entrypoint
caddy/              Production TLS reverse proxy (Caddyfile + Dockerfile)
docs/
  adr/              10 ADRs (all Accepted; 0001–0010)
  requirements/     BRD_v4.md, PRD_v4.md (v3 PDFs retained for provenance only — do NOT read v3)
  superpowers/specs/ Design specs (discharge summary, partial dispensing, MedWorks parity)
  gap-analysis-2026-10-13.md  Full gap inventory with current status
  operations.md     Runbooks incl. §7d KEK rotation, §7e DEK rotation
tools/              verify-migrations.sh, verify-prod-secrets.sh, seed-dev.sh, Trivy baseline
.agents/skills/med-os-project-context/  This skill
.kiro/steering/     Kiro-native always-on context (project.md, security.md, database.md, frontend.md)
CLAUDE.md           Full LLM context — read this first
AGENTS.md           Agent working rules — read this second
docker-compose.yml      Dev stack (in-host db, no TLS)
docker-compose.prod.yml Production stack (Supabase, Caddy, secrets as files)
.github/workflows/ci.yml  4 required CI jobs: backend, frontend, containers, security
```

## Backend quick map

```
backend/src/main/java/com/medos/
  config/           Spring beans (SecurityConfig, EncryptionConfig, RedisConfig, WebSocketConfig)
  controller/       REST controllers
  dto/              Request / Response DTOs — the API surface
  entity/           JPA entities (1:1 with Flyway tables)
  exception/        GlobalExceptionHandler + typed exceptions
  mapper/           MapStruct entity ↔ DTO
  modules/          Feature packages (see below)
  repository/       Spring Data JPA repos
  security/         JWT, TenantContext, KeyHolder, KeyWrapCipher, LoginRateLimiter, DekRotationService
  service/          Cross-cutting: AuthService, PatientService, TenantService, UserService,
                    NotificationService, IdempotencyService
  util/             PiiCiphertextFormat, BlindIndexUtil, AuditLogger
backend/src/main/resources/
  application.yml, application-prod.yml
backend/src/test/java/com/medos/   ~41 test classes
```

### Modules (backend/src/main/java/com/medos/modules/)

| Package | Domain |
|---|---|
| `appointment` | Scheduling, OPD queue, check-in/out |
| `billing` | GST invoices, charges (idempotency-keyed) |
| `clinical` | Encounters, admissions, discharge summaries, lab orders |
| `enquiry` | Enquiry desk + status history |
| `payment` | Payments: cash/card/UPI/insurance (idempotency-keyed) |
| `pharmacy` | Medicine catalog, stock batches, FEFO dispense, prescriptions |

### PII encryption (high error rate — read carefully)

`PII_ENCRYPTION_KEY` = **KEK** (key-encryption key), not the data key.

| Concern | File |
|---|---|
| KEK wraps per-tenant DEK; 5-min cache | `security/TenantKeyHolder.java` |
| AES-256-GCM wrap/unwrap | `security/KeyWrapCipher.java` |
| Ciphertext `kv1.d<gen>:<b64>` + legacy read | `util/PiiCiphertextFormat.java` |
| Blind index (independent key) | `util/BlindIndexUtil.java`, `security/BlindIndexKeyService.java` |
| KEK rotation (dry-run/apply/verify) | `security/KeyRewrapService.java`, `security/PiiKeyRotationRunner.java` |
| DEK rotation (per-tenant) | `security/DekRotationService.java` |
| Acting-tenant ThreadLocal | `security/TenantContext.java` |

Encrypted columns: 5 on `Patient`, 4 on `Encounter`, 1 on `LabOrder` (result — added V17).

**Implemented:** per-tenant DEK, versioned ciphertext, independent blind-index key,
KEK re-wrap + runbook, per-tenant DEK rotation, blind-index key rotation.

## Frontend quick map

```
frontend/src/
  api/          Axios client + domain modules
  components/   Layout, Sidebar, Header, Modal (note: 7+ hand-rolled copies — G38 open)
  pages/        Login, Dashboard, Patients, Encounters, Pharmacy, Admissions,
                Billing, Appointments, Lab, Staff
  store/        Zustand: authStore, toastStore
  test/         Vitest unit tests
frontend/e2e/   Playwright (smoke, full, login, staff, appointments) — need E2E_PASSWORD
```

## Database (31 tables, 17 migrations)

V1 (23 tables) + V2 PII key lifecycle + V3 staff + V4 super-admin/feature-flags +
V5 super_admin role + V6 DEK history + V7 enquiry desk + V8 appointments deepening +
V9 foreign keys + V10 tenant active not-null + V11 tenant user permissions +
V12 consent tenant scope + V13 DEK rotation evidence + V14 discharge summaries +
V15 lab role + V16 prescription quantity + **V17 lab result encrypted**

**Next migration = V18.**

## Open blockers worth knowing before you start

**P0 races (block prod launch):**
- G09 Double-dispense: prescription row unlocked in `DispenseService`
- G10 Double-discharge: admission row unlocked in `AdmissionService`
- G11 Double-invoice: charges not atomically claimed in `BillingService`

**P1:**
- G06 No auth-event auditing (login/failure/lockout in `audit_log`)
- G07 No rate limit on `/api/v1/onboarding/register`
- G12 No container memory/CPU limits in either compose file
- G17 Name search decrypts up to 5,000 rows in Java (dies at scale)

**November audit #219 — still open:** R08 (discounted invoice balance), R12/R13 (pilot deployment evidence)

Full list: `docs/gap-analysis-2026-10-13.md`

## Validation commands (use these, not others)

```bash
# Backend — always use `clean`; stale Surefire XML misreports results
cd backend && mvn clean test
cd backend && mvn -DskipTests package

# Frontend
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
cd frontend && npm run check:contrast

# Infrastructure
docker compose config --quiet
./tools/verify-migrations.sh           # run after any migration change
./tools/verify-prod-secrets.sh

# E2E (requires running stack + E2E_PASSWORD)
cd frontend && E2E_PASSWORD='<pw>' npx playwright test e2e/smoke.spec.js
cd frontend && E2E_PASSWORD='<pw>' npx playwright test e2e/full.spec.js
```

## Token-saving playbook

1. Read `CLAUDE.md` (architecture + invariants) then `docs/roadmap.md` (priorities).
2. Locate the target with `grep`/`glob` scoped to `backend/**` or `frontend/**`.
3. Read only the needed file sections.
4. Run the smallest relevant validation command, then broaden only if needed.
5. Never scan `node_modules/`, `dist/`, or `target/`.

## Common mistakes agents make in this repo

1. Creating `backend/src/main/resources/db/migration/` — wrong path.
2. Editing an applied migration — causes Flyway checksum failure.
3. Storing plaintext PII — use the column converter.
4. Querying a tenant table without `tenant_id` predicate.
5. Skipping `mvn clean` — stale reports give false green.
6. Reading v3 BRD (Node/SQLite) as current — it is not.
7. Trusting `X-Forwarded-For` for rate limiting.
8. Publishing a port other than Caddy in `docker-compose.prod.yml`.
