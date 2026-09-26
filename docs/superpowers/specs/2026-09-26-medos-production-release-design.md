# MedOS Production Release — Design

- **Date:** 2026-09-26
- **Status:** Draft for review
- **Scope:** Architecture assessment, production-readiness assessment, and a staged release plan for MedOS HMS v3.0
- **Target:** Multi-tenant SaaS for many hospitals, on a single cloud host with managed PostgreSQL

---

## 1. Purpose and success criteria

MedOS is a Hospital Management System: Spring Boot 3.2 + React 19, PostgreSQL, Redis, JWT auth, multi-tenant by `tenant_id`, serving clinical, pharmacy, admission, and billing workflows for hospitals that handle protected health information.

This document exists to answer three questions and produce one artifact:

1. What is the actual architecture of the system as built (not as documented)?
2. What stands between it and a defensible production release?
3. In what order should that gap be closed, with what verification gate at each step?

**Success criteria for the release:**

- Every release gate in §7 passes on the release-candidate commit, with evidence committed.
- A hospital's IT/security reviewer can be handed `defect-baseline.md`, `db-integrity-report.md`, the ADRs in §9, and `docs/operations.md`, and find no unanswered question about how PHI is stored, transmitted, retained, or recovered.
- A single operator can rebuild and restart the stack from documentation alone.
- A new hospital can be onboarded without engineering intervention.

**Explicit non-goals for v1:** Kubernetes/Helm, API gateway, Redis cluster, PostgreSQL read replicas, distributed tracing, schema-per-tenant, Postgres row-level security, blue/green or canary releases. Rationale in §4 and §9 (ADR-0006).

---

## 2. Architecture as built

### 2.1 Components

| Component | Technology | Authority |
|---|---|---|
| Frontend | React 19 + Vite 8, Zustand, Recharts; served by `nginxinc/nginx-unprivileged:1.27` on `:8080` | `frontend/` |
| Backend | Spring Boot 3.2 monolith, Java 17 target, Temurin 21 runtime; 9 controllers, 17 services, 24 entity files | `backend/` |
| Database | PostgreSQL (15 in compose, 16.9 in `database/Dockerfile` — unreconciled) | `database/` |
| Cache | Redis 7 — cache and login-lockout counters only | `cache/` |
| Migrations | Flyway, run in-band by the backend on boot | `database/migrations/`, mirrored to `backend/src/main/resources/db/migration/` |
| Auth | JWT (stateless) + BCrypt, 6 roles, `TenantUser` membership | `backend/src/main/java/com/medos/security/` |
| Encryption | AES-256-GCM for patient PII via `util/EncryptionUtil` | `backend/src/main/java/com/medos/util/` |
| Proxy/TLS | Caddy — **present but unreferenced by any compose file** | `caddy/Caddyfile` |

### 2.2 Module layout

`backend/src/main/java/com/medos/` holds `config/ controller/ dto/ entity/ exception/ repository/ security/ service/ util/` plus a partially-adopted `modules/` tree (`billing clinical payment pharmacy`). The god-service problem flagged in `PRODUCTION_READINESS.md` has been substantially fixed: the largest service is 150 lines (`EncounterService`), and pharmacy is split into `DispenseService` / `InventoryService` / `MedicineCatalogService`. That document is stale.

### 2.3 Tenancy

Row-level multi-tenancy, implemented at two layers:

- `entity/TenantEntityListener` — stamps `tenant_id` on persist.
- `config/TenantStatementInspector` — a Hibernate statement inspector that injects tenant predicates.
- `security/TenantContext` — per-request tenant from the JWT.

Enforced by `TenantIsolationTest` (3) and `RbacMatrixTest` (22).

### 2.4 Request/response and error contract

All controllers are mounted under `/api/v1`. `GlobalExceptionHandler` normalizes errors; unauthenticated requests return 401. `POST /api/pharmacy/dispense`, `/api/billing/invoices`, `/api/billing/payments` require an `Idempotency-Key` header and replay the cached result with `Idempotency-Key-Replayed: true`.

### 2.5 Data model

23 tables created by `V2__initial_schema.sql` (generated from JPA entities via `SchemaExportTest`, then normalized with hot-path indexes): `tenants`, `users`, `tenant_users`, `tenant_configs`, `tenant_settings`, `patients`, `appointments`, `opd_queue`, `encounters`, `prescriptions`, `lab_orders`, `rooms`, `admissions`, `medicine_catalog`, `medicine_batches`, `stock_transactions`, `disease_medicine_map`, `charges`, `invoices`, `payments`, `consents`, `notifications`, `audit_log`. `V1__init.sql` owns three sequences: `uhid_seq`, `invoice_number_seq`, `payment_number_seq`.

Optimistic locking is present (`version BIGINT NOT NULL` on hot entities). Money math is centralized in `util/MoneyUtil`.

### 2.6 Deployment as built

`docker-compose.yml` (56 lines) defines `db`, `redis`, `backend`, `frontend`. It runs the backend with `SPRING_PROFILES_ACTIVE=prod` while supplying development credentials inline, publishes 5432/6379/8080 to the host, and declares no volumes. There is no `migrate` service, no network segmentation, and no secret indirection.

The hardened version described in `README.md` and required by CI exists only as `docker-compose.yml.bak`.

### 2.7 CI as built

`.github/workflows/ci.yml` has three jobs: `Backend (build + tests)` (JDK 21, `mvn test` + package), `Frontend (lint + tests + build)` (Node 22, oxlint + vitest + vite build), and `Container definitions` (compose config validation, image build, then `docker compose run --rm migrate migrate` and `... migrate validate`).

**The third job cannot pass**, because the `migrate` service does not exist in the compose file.

---

## 3. Verified baseline

Commands run on 2026-09-26 against the working tree, with results as found:

| Check | Command | Result |
|---|---|---|
| Backend compile | `mvn -B -DskipTests compile` (JDK 25, target 17) | pass |
| Backend tests | `mvn -B test` | **85 pass, 0 fail, 1 skipped** |
| Frontend lint | `npm run lint` (oxlint, 29 files, 91 rules) | 0 warnings, 0 errors |
| Frontend tests | `npx vitest run` | 15 pass across 3 files |
| Compose model | `docker compose config --quiet` | valid |
| Compose services | `docker compose config --services` | `db redis backend frontend` — no `migrate` |
| CI status | `gh run list --limit 5` | **5 of 5 runs on `main`: failure** |
| Failing job | `gh run view 35636316031` | `Container definitions`: failure — `no such service: migrate` |
| Branch protection | `gh api .../branches/main/protection` | 404 — **not protected** |
| Working tree | `git status --porcelain` | **50 changed files**, `.DS_Store` tracked |

The `prod` profile already sets `spring.jpa.hibernate.ddl-auto: validate` with Flyway owning the schema. The "critical" `ddl-auto: update` finding in `PRODUCTION_READINESS.md` is stale and must not be actioned.

---

## 4. Findings

Ordered by severity. "Verified" means reproduced by command in §3 or by direct code/config inspection.

### Blocker — CI cannot pass

`docker compose run --rm migrate` fails with `no such service: migrate`. Commit `7850493` removed the `migrate` service, the `medos-private` / `medos-edge` networks, the `medos-db-data` / `medos-redis-data` volumes, and the `${VAR:?}` secret guards from `docker-compose.yml`. Since then every push to `main` has failed the `containers` job while `Backend` and `Frontend` stayed green — a partially-green pipeline that reads as success.

### Blocker — production credentials are committed to git

`secrets/` is tracked: `jwt-secret.txt` (64 bytes), `pii-encryption-key.txt`, `db-password.txt`, `redis-password.txt`, `bootstrap-admin-password.txt`, `cors-origins.txt`. `docker-compose.yml` additionally hardcodes `JWT_SECRET`, `PII_ENCRYPTION_KEY`, `DB_PASSWORD`, and `BOOTSTRAP_ADMIN_PASSWORD: Admin@123` under the `prod` profile, and `application.yml` carries the same development JWT secret and PII key as a fallback default.

All of these values must be treated as compromised. The PII key is the serious one: disclosure means existing ciphertext must be re-encrypted or the data discarded. `.gitignore` does not cover `secrets/`.

### Blocker — main is unprotected

`main` has no branch protection, so nothing blocked five consecutive red runs, and 50 files of in-flight multi-tenancy work sit uncommitted on a tree that cannot be released.

### Blocker — unauthenticated tenant and administrator creation

`POST /api/v1/onboarding/register` is `permitAll` (`SecurityConfig.java:52`) and `OnboardingController.onboardTenant` creates a tenant, an active user, and an `admin` `TenantUser` membership from the request body. There is no invite, no email verification, no captcha, no rate limit, and no approval. On a public deployment any anonymous caller can provision a hospital tenant with full admin rights, unbounded, and can collide on `users.username` (unique) to trigger 500s.

### High — seed tooling cannot work against the current schema

`tools/seed-dev.sql` inserts plaintext PII into `patients.name`, `phone`, `email`, `address`, `blood_group`, which hold AES-256-GCM ciphertext (`V2__initial_schema.sql:78-84`, `Patient.java:35-58`). Seeded rows are therefore unreadable through the application. The same insert omits `patients.version`, which is `NOT NULL` with no default, so it fails on the not-null constraint before that. `README.md` documents this script as the dev-data path and `tests/e2e/api-test.sh` depends on the demo logins it creates.

### High — no browser-level test coverage

No Playwright, no Cypress, no browser automation of any kind. The only end-to-end asset is `tests/e2e/api-test.sh` (1188 lines, curl + jq, 6 roles) with its last recorded report dated 2026-09-13. Nothing verifies that the React application renders, that nginx proxies correctly, that the tenant-isolation guarantee holds through the browser session, or that a login works in the built bundle.

### High — no observability

`pom.xml` contains no micrometer registry, no Prometheus exporter, no tracing, and there is no logback configuration, so logs are unstructured plain text with no correlation ID. There is no alerting. `NEXT_STEPS.md` items 9 and T7 have never been started. For an incident in a clinical system, mean time to detect is currently unbounded.

### High — backups are documented but never rehearsed

`docs/operations.md` contains a runbook. No restore has been executed and no RTO/RPO is stated. Managed PostgreSQL with PITR makes this cheap to fix and expensive to skip.

### Medium — migration ownership is split three ways

`database/migrations/` (`V1`, `V2`) and `backend/src/main/resources/db/migration/` (byte-identical copies) coexist with `caddy/migrations/` (`V1`–`V8`), which is a different and superseded lineage — its `V1__initial_schema.sql` is 335 lines against the current 396-line `V2`, and it carries seed data, PII-encryption DDL, and index work that no longer exists in the consolidated baseline. Two copies of the authoritative migrations can silently diverge; a third dead lineage invites someone to apply the wrong file.

### Medium — security headers absent at the edge

`frontend/nginx.conf` sets no `Strict-Transport-Security`, `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`, or `Referrer-Policy`. TLS is not terminated in the compose stack at all; `caddy/` is orphaned.

### Medium — documentation contradicts the code

`README.md` documents endpoints without the `/api/v1` prefix (every documented URL 404s), states that a dedicated `migrate` container and docker secrets are in place, lists default credentials as a supported path, and names PostgreSQL 15 while `database/Dockerfile` pins 16.9. `PRODUCTION_READINESS.md` reports resolved problems as open (god services, `ddl-auto: update`) and lists Kubernetes, an API gateway, Redis clustering, read replicas, and ELK as gaps — none of which are v1 goals. `docs/PRODUCTION_READINESS_VERDICT.md` reports race conditions in UHID and invoice generation that the sequence-based implementation has fixed.

### Medium — staff PII is stored in plaintext

`users.email` and `users.full_name` are plaintext `VARCHAR` while patient PII is encrypted. Staff email is personal data under the DPDP Act. Either encrypt it or record a decision not to, with reasons.

### Low — observability-adjacent inconsistencies

`backend/Dockerfile` builds with `-Dmaven.test.skip=true`, so the shipped artifact is not the tested artifact; CI tests separately, which is acceptable but should be stated. `NEXT_STEPS.md` contains a duplicated "Phase 3" section. `.DS_Store` is tracked. Local JDK is 25 while the build targets 17 and CI uses 21.

---

## 5. Target architecture

```
Internet
   │  TLS 1.3, HSTS, security headers
Caddy (host :80/:443)                     ← caddy/Caddyfile, plus a headers block
   │
   ├── /                → frontend :8080   nginx-unprivileged, non-root, read-only fs
   │                       ├── /api/v1/*        → backend:8080
   │                       ├── /ws/*            → backend:8080
   │                       ├── /swagger-ui/*    → backend:8080 (admin)
   │                       └── /manage/health   → backend:8080 (public, no details)
   └── medos-edge

   backend   temurin 21 JRE · appuser · -Xmx512m · graceful shutdown 30s
   migrate   flyway/flyway:10.22-alpine · run-once · gate before backend
   redis     redis:7-alpine · AOF everysec · cache + lockout counters only
   ─────────────── medos-private (internal: true, no egress, no ports) ───────────────
   PostgreSQL  MANAGED (RDS / Supabase): private endpoint, PITR + 30-day snapshots,
               no public IP, TLS-required connections
```

Compose services publish exactly one port: Caddy on 443/80. `db` and `redis` are not reachable from the host. Managed PostgreSQL is reached over its private endpoint by the backend and the `migrate` job.

**Redis is demoted to non-authoritative.** Losing it costs cache warmth and in-flight login-lockout counters (an in-memory fallback already exists), never patient data. This removes "Redis cluster" from the release's critical path.

**Tenancy stays row-level.** Application-layer enforcement is implemented and tested; Postgres RLS is a genuine defense-in-depth improvement for PHI but requires per-connection tenant binding that conflicts with Hibernate pooling and complicates migrations and incident response. Deferred to v2 with rationale recorded (ADR-0006).

---

## 6. Release stages

Five stages. Each ends at a gate; each is independently mergeable; work stops cleanly after any one of them.

Each stage is planned and executed separately; this document is the program-level design, not a single implementation plan. The first plan covers Stage 0 only, because Stage 1's shape depends on what Stage 0 finds.

### Stage 0 — Baseline validation (immediate)

Establish observed reality on the current stack before changing it, so the remainder of this plan is driven by evidence.

1. `docker compose up -d --build`; gate on `/manage/health` returning healthy. Postgres has no declared volume, so its data lives in an anonymous volume: it survives `down` but not `down -v`. Recorded as a finding, not relied upon.
2. Provision data correctly rather than via `tools/seed-dev.sql`: tenant, six users, and `tenant_users` rows by SQL (these columns are not PII-encrypted), then **create patients through the API** so the encryption path is exercised. A green baseline then proves the crypto path rather than merely the presence of rows. `tools/seed-dev.sql` is fixed as part of Stage 1.
3. Re-run `tests/e2e/api-test.sh`; diff the result against the report committed on 2026-09-13.
4. Add Playwright to the `frontend` workspace with one spec per critical flow: login per role, patient register and search, encounter and prescribe, FEFO dispense, admit and discharge, invoice and payment, self-serve onboarding, and a tenant-isolation probe asserting that a tenant-A session cannot retrieve a tenant-B patient. Each spec asserts absence of console errors and failed network requests. This is a permanent gate, not a one-off pass.
5. Run the Layer 1 database integrity checks from §8.

**Output:** `docs/release/defect-baseline.md` — each defect with severity, reproduction, and triage decision.

### Stage 1 — Deploy and supply-chain integrity

- **1.1** Restore the hardened compose: `migrate` service, `medos-private` / `medos-edge` networks, `medos-db-data` / `medos-redis-data` volumes, `${VAR:?}` guards on every secret, and no published database or cache port. Add a `caddy` service that mounts `caddy/Caddyfile` and is the only service publishing 80/443; the frontend is then reachable only from `medos-edge`. Add the missing security-header directives to `frontend/nginx.conf` at the same time. Then delete `docker-compose.yml.bak` and `.bak2` so exactly one compose file exists.
- **1.2** Rotate, then purge, committed secrets. Order matters: rotation first, because the committed values are burned. Rotate `JWT_SECRET`, `PII_ENCRYPTION_KEY`, `DB_PASSWORD`, `REDIS_PASSWORD`, and `BOOTSTRAP_ADMIN_PASSWORD`; write a re-encryption job for data protected by the old PII key. Purge `secrets/` from history, add it to `.gitignore`, ship `.env.example` only.
- **1.3** Make CI green: fix the `containers` job, add a Playwright job, add a job that boots the real stack and runs `api-test.sh`. Make the compose the single source of truth that CI validates.
- **1.4** Enable branch protection on `main` requiring these checks.
- **1.5** Land the 50 in-flight files as reviewed commits, grouped by concern; untrack `.DS_Store`.
- **1.6** One migration owner: `database/migrations/` per `database/README.md`. Either generate the classpath copy at build time or delete it and mount the directory. Delete `caddy/migrations/`. Reconcile PostgreSQL 15 and 16.9 on one version.
- **1.7** Fix `tools/seed-dev.sql`: encrypt PII through the application, or seed only non-PII tables and document that patients come from the API. Supply `version` values.
- **1.8** Documentation truth pass: correct the `/api/v1` prefixes, the migration-container claims, the credentials table, and the PostgreSQL version in `README.md`; correct the stale entries in `PRODUCTION_READINESS.md` and `docs/PRODUCTION_READINESS_VERDICT.md`; deduplicate `NEXT_STEPS.md`.

### Stage 2 — Onboarding gating and tenant abuse controls

Self-serve hospital signup stays open as a product feature, gated rather than removed.

- **2.1** Tenant lifecycle: `PENDING → ACTIVE`, with `SUSPENDED` reserved. `POST /api/v1/onboarding/register` creates a `PENDING` tenant and issues no token.
- **2.2** Email verification of the tenant administrator: single-use token, expiry, stored hashed, resend throttled. This introduces the project's only new external dependency, a transactional email provider.
- **2.3** Captcha on `/api/v1/onboarding/register` and `/api/v1/auth/login`, complementing the existing 5-attempt / 15-minute lockout.
- **2.4** Per-tenant rate limiting across `/api/v1/**` using a Redis token bucket keyed by tenant, plus per-IP limits on authentication and onboarding.
- **2.5** Correct failure semantics: duplicate username or email returns 409 rather than 500; request size caps; every onboarding action audited with actor, IP, and user agent.
- **2.6** Ship `medos.onboarding.public-signup` defaulting to `false`. Support uses the invite path on day one; open self-serve becomes a per-environment configuration change once email deliverability is trusted.

### Stage 3 — Observability, backups, key management

- **3.1** micrometer with the Prometheus registry, exposed at `/manage/prometheus` behind the existing `admin` requirement. Structured JSON logs with a correlation ID propagated from nginx through the backend. Health groups covering database and Redis in the readiness probe.
- **3.2** Alerting limited to what is actionable: health-check failure, 5xx rate, connection-pool saturation, disk space, and backup or PITR failure.
- **3.3** Managed PostgreSQL PITR targeting RPO 5 minutes with 30-day snapshot retention. **Rehearse the restore** into a scratch instance and record the measured RTO against a 60-minute target. Document the Redis AOF policy.
- **3.4** Secret delivery for a single host: cloud secret manager injected at container start, using the `/run/secrets/*` fallback the entrypoint already implements. Document JWT rotation and PII-key rotation including the re-encryption procedure that `NEXT_STEPS.md` T6 still lists as open.
- **3.5** Resolve staff PII: encrypt `users.email` and `users.full_name`, or record a signed decision not to, with reasoning.

### Stage 4 — Release-candidate validation

- **4.1** Re-run the entire Stage 0 suite against the hardened stack with managed-PostgreSQL-shaped configuration.
- **4.2** Database integrity report per §8, Layer 1 and Layer 2.
- **4.3** Security sweep: dependency vulnerability scan, secret scan over full history, OWASP ZAP baseline against the running stack, RBAC matrix, and `flyway:validate` on a fresh database.
- **4.4** Disaster-recovery drill: restore a snapshot into a scratch database, verify row counts, foreign keys, and audit-log completeness.
- **4.5** Sign-off, then tag `v1.0.0-rc.1`.

---

## 7. Gates

A stage is complete when CI is green on the commit **and** its manual check has been executed with output committed. CI-green alone is precisely what allowed five red runs and 50 dirty files to accumulate.

| Stage | Gate |
|---|---|
| 0 | `defect-baseline.md` committed; every defect triaged to fix or accepted |
| 1 | `containers` job green; branch protection active; `git log --all -- secrets/` free of live values; compose publishes only 80/443; the `prod` profile uses `ddl-auto: validate` (the `dev` profile may keep `update`) |
| 2 | A `PENDING` tenant cannot obtain a token by any path; verification, captcha, and rate limits proven by automated test; duplicate identity returns 409 |
| 3 | Prometheus scraped successfully; restore rehearsed with a measured RTO; each alert fires in a controlled test |
| 4 | Stage 0 suite green, integrity report complete, ZAP baseline reviewed, DR drill passed, sign-off checklist signed |

---

## 8. Database integrity validation

Patient PII columns (`name`, `phone`, `email`, `address`, `blood_group`) contain AES-256-GCM ciphertext. **SQL can establish structural integrity; only the application can establish content integrity.** Any check comparing patient names in SQL validates nothing. Validation is therefore two-layered.

### Layer 1 — structural and financial integrity

Executed with `psql` against the live database. Documented in `docs/release/db-integrity-report.md` with the SQL used, so a hospital's DBA can re-run it.

1. **Referential integrity** — orphan sweep across every foreign key: `tenant_id`, `patient_id`, `encounter_id`, `prescription_id`, `medicine_batch_id`, `invoice_id`. Expect zero.
2. **Tenant isolation at rest** — `SELECT tenant_id, count(*) FROM patients GROUP BY tenant_id` must match precisely what each tenant's API session can enumerate. A count exceeding a session's visible set is a data-layer leak even when the API is correct.
3. **Money** — per invoice, `SUM(charges.amount) = invoices.total`; `payments` reconcile to invoice balance; `patients.outstanding = SUM(open charges) − SUM(payments)`. Any drift is severity P0; billing correctness is the product.
4. **Inventory** — per batch, `SUM(stock_transactions.qty_delta) = medicine_batches.qty_on_hand`; dispensed rows must be the earliest-expiry batches when ordered by timestamp, confirming FEFO; no negative stock.
5. **Sequences** — `uhid_seq`, `invoice_number_seq`, `payment_number_seq` next values exceed every issued value; no duplicate UHID or invoice number.
6. **Concurrency artifacts** — `version` strictly increasing on updated rows, proving optimistic locking engages; no dispense exceeding batch quantity; no duplicate idempotency-key effects.
7. **Encryption posture** — `SELECT count(*) FROM patients WHERE name ~ '^[A-Za-z ]+$'` must be `0` for application-written rows; that expression is a plaintext-PII canary. No NULL in NOT NULL ciphertext columns; ciphertext length consistent with `IV ‖ ciphertext ‖ tag`.
8. **Audit completeness** — every mutation has an `audit_log` row with actor, IP, entity, and diff; no gaps in the sequence; tenant-administrator onboarding actions present.

### Layer 2 — content truth through the API

The Stage 0 end-to-end run emits an expected-values manifest as it creates each record. A companion script re-reads every record through `GET /api/v1/patients/{id}`, the patient invoice and unbilled endpoints, and the admissions endpoints, then diffs manifest against response. The assertion is round-trip equality: the value the application accepted is the value it returns, decrypted. Derived formats are asserted in the same pass — UHID matches `^UHID\d{6}$`, invoice numbers are sequential per tenant, GST fields are populated.

---

## 9. Architecture decision records

Summarized here; full records in `docs/adr/`.

| ADR | Decision |
|---|---|
| 0001 | Single cloud host with Docker Compose and Caddy; managed PostgreSQL. No Kubernetes for v1. |
| 0002 | Row-level multi-tenancy retained; schema-per-tenant and Postgres RLS deferred. |
| 0003 | Self-serve onboarding retained but gated by email verification, captcha, and rate limits, with public signup defaulting off. |
| 0004 | Secrets sourced from a cloud secret manager at container start, injected via the existing entrypoint fallback; no Vault cluster for v1. |
| 0005 | `database/migrations/` is the single owner of Flyway migrations; the classpath copy is build-generated and `caddy/migrations/` is deleted. |
| 0006 | Non-goals for v1: Kubernetes, API gateway, Redis cluster, read replicas, distributed tracing, Postgres RLS, blue/green releases. |
| 0007 | Redis is non-authoritative: cache and lockout counters only; in-memory fallback is acceptable. |

---

## 10. Failure handling and rollback

- **Migrations are forward-only.** Never edit an applied migration — Flyway checksum validation will fail the deploy. Add `V3`, `V4`, and so on.
- **Application rollback** is redeploying the previous image. Tags are immutable and retained.
- **Data rollback** is managed-PostgreSQL PITR to a chosen timestamp, which is what makes the 5-minute RPO target meaningful. Recovery is rehearsed in Stage 3, not assumed.
- **A failed release-candidate gate blocks the tag.** There is no partial release and no "ship and monitor".
- **Secret rotation is not transactional with deploy.** Rotate JWT and PII keys on a schedule with a documented overlap window; the PII key additionally requires the re-encryption job to complete before the old key is destroyed.

---

## 11. Glossary

- **UHID** — Unique Health Identifier, the patient-facing number issued on registration (`UHID` + 6 digits), generated from `uhid_seq`.
- **DPDP** — India's Digital Personal Data Protection Act 2023. Drives consent capture (`patients.dpdp_consent`, `consents` table) and PII encryption obligations.
- **PII** — Personally identifiable information. Encrypted at rest in patient columns with AES-256-GCM; the ciphertext format is Base64 of `IV ‖ ciphertext ‖ auth tag`.
- **FEFO** — First-Expired-First-Out. Dispensing consumes the earliest-expiry batch first, so stock does not expire on the shelf.
- **Tenant** — One hospital. Every domain row carries `tenant_id`; a user gains a role per tenant through `tenant_users`.
- **Tenant boundary** — The isolation guarantee between hospitals, enforced by `TenantStatementInspector` and `TenantEntityListener` and verified by `TenantIsolationTest`.
- **Idempotency key** — A client-supplied unique string on dispense, invoice, and payment operations; a replay returns the original result with `Idempotency-Key-Replayed: true` instead of executing twice.
- **Idempotent** — Safe to retry; produces one effect regardless of repetition.
- **Gate** — A stage's completion criterion: green CI plus an executed, committed manual check.
- **Layer 1 / Layer 2 integrity** — Layer 1 is structural and financial verification in SQL; Layer 2 is content verification through the API, required because PII is ciphertext.
- **Fractured pipeline** — The current state in which some CI jobs pass and the `containers` job fails on every push, which reads as overall success.
- **PITR** — Point-in-time recovery; continuous recovery to an arbitrary timestamp, which is what makes a 5-minute RPO achievable.
- **RC** — Release candidate. `v1.0.0-rc.1` is the first tag that has passed every gate in §7.

---

## 12. Release sequence

1. `v1.0.0-rc.1` deployed to the hardened single host with a production `.env`, Caddy terminating TLS, public signup off, one pilot hospital onboarded through the invite path.
2. Stage 0 suite green against that deployment, followed by one disaster-recovery drill on the real host.
3. Flip `public-signup=true` once email deliverability is trusted.
4. Onboard hospital two, and only then treat v1 as generally available.

---

## 13. Open questions for the reviewer

1. **Which transactional email provider?** Stage 2.2 introduces the project's first external dependency. Cost, deliverability, and DPDP-relevant data-transfer questions apply.
2. **Does existing data need preserving?** If any real PHI exists under the current PII key, Stage 1.2's re-encryption job is on the critical path and its cost depends on row counts. If no real data exists, the key rotation is a clean cut.
3. **Is the pilot hospital identified?** Stage 4.2 needs a real tenant and a real clinical workflow to validate against.
4. **PostgreSQL version** — 15 as compose specifies, or 16.9 as `database/Dockerfile` pins. Affects the managed instance choice and Stage 1.6.
