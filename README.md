# MedOS HMS v3.0

**MedOS** is an ultra-premium, role-based Hospital Management System designed to digitize, streamline, and intelligently assist end-to-end hospital workflows — from patient registration and clinical documentation to pharmacy dispensing and financial reconciliation.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17 + Spring Boot 3.5.16 |
| **Frontend** | React 19 + Vite 8 |
| **Database** | PostgreSQL 16 (primary) + Redis 7.4 (cache) |
| **Auth** | JWT (stateless) + BCrypt |
| **Security** | Spring Security, RBAC, per-tenant row scoping |
| **API** | RESTful (JSON) + WebSocket (real-time) |
| **Persistence** | JPA/Hibernate + Flyway migrations |
| **Encryption** | Envelope: AES-256-GCM KEK wraps a per-tenant DEK; AES-256-GCM on patient PII; independent keyed blind index for name search |
| **AI** | Built-in keyword-based medicine advisor |

## Architecture

```
frontend/ (React + Vite)
  ├── src/
  │   ├── api/        # Axios client & API modules
  │   ├── components/ # Layout, Sidebar, Header, Toast
  │   ├── pages/      # Login, Dashboard, Patients, Encounters,
  │   │               # Pharmacy, Admissions, Billing
  │   └── store/      # Zustand (auth, toast)
  └── frontend/

backend/ (Spring Boot)
  ├── src/main/java/com/medos/
  │   ├── config/     # Security, encryption init, Redis cache, WebSocket
  │   ├── controller/ # REST controllers
  │   ├── dto/        # Request/Response DTOs
  │   ├── entity/     # JPA entities (25 tables)
  │   ├── exception/  # Global error handling
  │   ├── mapper/     # Entity <-> DTO mapping
  │   ├── modules/    # Feature packages: billing, clinical, pharmacy, payment
  │   ├── repository/ # Spring Data JPA repos
  │   ├── security/   # JWT, tenant context, KEK/DEK key management, key rotation
  │   ├── service/    # Business logic
  │   └── util/       # PII cipher, blind index, audit logger
database/
  ├── migrations/        # Flyway SQL migrations (the single schema owner)
  ├── Dockerfile         # PostgreSQL 16 runtime image (development only)
  └── migrations.Dockerfile # versioned Flyway migration job

cache/
  ├── Dockerfile
  ├── entrypoint.sh
  └── redis.conf

docs/adr/    # Architecture decision records
tools/       # Migration gate, secret-wiring gate, seeding, Trivy baseline
```

## Database Schema (25 Tables)

Multi-tenancy first: every clinical table carries a `tenant_id`, and queries are
scoped per tenant by `TenantStatementInspector`.

- `tenants` - Hospital/organisation, the isolation boundary
- `tenant_users` - Memberships: one user may hold a different role per tenant
- `tenant_configs` - Per-tenant configuration
- `tenant_settings` - Per-tenant settings
- `tenant_keys` - Per-tenant wrapped DEK + blind-index key, with generation counters
- `key_rotations` - Append-only audit log of KEK re-wrap operations
- `users` - Role-based login (admin, doctor, nurse, receptionist, pharmacist, billing, super_admin)
- `patients` - Demographics, UHID, DPDP consent, outstanding balance
- `appointments` - Scheduling, check-in/out tracking
- `encounters` - Clinical visits, vitals, diagnosis, AI notes
- `prescriptions` - Medicine orders with status tracking
- `rooms` - Ward/bed inventory with daily rates
- `admissions` - IPD bed allocation, discharge, room charges
- `medicine_catalog` - Drug master with pricing, keywords
- `medicine_batches` - Lot tracking with expiry (FEFO key)
- `stock_transactions` - Complete inventory ledger
- `charges` - Auto-generated billing line items
- `invoices` - GST-compliant invoices
- `payments` - Cash, card, UPI, insurance
- `lab_orders` - Diagnostic test tracking
- `disease_medicine_map` - AI suggestion engine
- `audit_log` - Full audit trail
- `consents` - DPDP compliance records
- `opd_queue` - Appointment queue management
- `notifications` - Real-time alerts

## Getting Started

### Prerequisites

**Docker is the only hard requirement.** Every tool the build needs is inside the
images, so you do not need to install Java, Maven, Node, PostgreSQL or Redis.

- **Docker** 24+ with the Compose plugin (`docker compose version`)

Installed tool versions, for reference or for running the backend outside Docker:

| Tool | Version | Where it comes from |
|------|---------|---------------------|
| Java | 17 | `backend/pom.xml` |
| Spring Boot | 3.5.16 | `backend/pom.xml` |
| Maven | 3.9 | `backend/Dockerfile` builder stage |
| Node | 20 | `frontend/Dockerfile` |
| PostgreSQL | 16.9 | `database/Dockerfile` |
| Redis | 7.4.2 | `cache/Dockerfile` |

### Run the full stack locally

1. **Create your `.env`.** It is gitignored and must never be committed.

   ```bash
   cp .env.example .env
   ```

2. **Fill in the secrets.** Generate real values — do not leave the placeholders.
   These *replace* the existing lines; do not append, or you will end up with
   duplicate keys in the file:

   ```bash
   set -a; . ./.env; set +a
   sed -i '' \
     -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 16)|" \
     -e "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=$(openssl rand -hex 16)|" \
     -e "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 \| tr -d '\n')|" \
     -e "s|^PII_ENCRYPTION_KEY=.*|PII_ENCRYPTION_KEY=$(openssl rand -base64 32 \| tr -d '\n')|" \
     -e "s|^BOOTSTRAP_ADMIN_PASSWORD=.*|BOOTSTRAP_ADMIN_PASSWORD=$(openssl rand -base64 18 \| tr -d '\n')|" \
     .env
   ```

   `PII_ENCRYPTION_KEY` must decode to **exactly 32 bytes**; `JWT_SECRET` must
   decode to at least 32. Both are validated at startup and the application
   **fails closed** rather than falling back to a default. Trailing newlines are
   stripped because these values are read verbatim.

   Confirm it worked, and that no key is left duplicated:

   ```bash
   grep -c '^PII_ENCRYPTION_KEY=' .env                    # must print 1
   grep '^PII_ENCRYPTION_KEY=' .env | cut -d= -f2- | base64 -d | wc -c   # must print 32
   ```

   On Linux, `sed -i ''` needs no argument; use `sed -i` instead.

3. **Start everything.**

   ```bash
   docker compose up -d --build
   ```

4. **Wait for it to become healthy, then sign in.**

   ```bash
   docker compose ps          # every service should read (healthy)
   ```

   Open **http://localhost:8080** and sign in as `admin` with the
   `BOOTSTRAP_ADMIN_PASSWORD` you generated.

Only the frontend port is published; the database, Redis and backend are reachable
only on the internal Docker network. The frontend proxies `/api` to the backend, so
`http://localhost:8080/api/v1/...` is the API too. The dev frontend listens on
`FRONTEND_EXTERNAL_PORT` (8080 by default) — port 80 is production only, behind
Caddy.

Stop with `docker compose down`. Add `-v` **only** when you deliberately want to
delete the local database and start over.

### Optional: seed demo users and patients

The `admin` account above is the only one created automatically. To get the full
set of roles and some sample clinical data:

```bash
./tools/seed-dev.sh
```

This seeds `admin`, `doctor`, `doctor2`, `nurse`, `reception`, `pharmacy` and
`billing`, all with the password `password`, plus demo patients. It is a
development convenience: **never run it against a real database.** Note that
seeding overwrites the `admin` password, so re-check the login afterwards.

### Common commands

| Command | What it does |
|---------|--------------|
| `docker compose up -d --build` | Build and start everything |
| `docker compose ps` | Service status and health |
| `docker compose logs -f backend` | Follow backend logs |
| `docker compose logs migrate` | Migration output specifically |
| `docker compose down` | Stop, keep data |
| `docker compose down -v` | Stop and **delete** the local database |
| `./tools/verify-migrations.sh` | Migration drift gate |
| `./tools/test-verify-migrations.sh` | Tests for the gate itself |
| `./tools/verify-prod-secrets.sh` | Checks every secret the app reads at runtime is actually mounted |
| `./tools/test-verify-prod-secrets.sh` | Tests for the secret gate itself |
| `cd backend && mvn clean test` | Backend unit tests (needs Java 17 + Maven) |
| `cd frontend && npm test` | Frontend unit tests (needs Node 20 + `npm install`) |

### Troubleshooting

**A service exits immediately, or the port is already in use.** Set
`FRONTEND_EXTERNAL_PORT` in `.env` to a free port, then
`docker compose up -d`.

**`required variable DB_PASSWORD is missing`.** `.env` is absent or incomplete.
Copy `.env.example` and fill in the four required values.

**`PII encryption key must be 32 bytes (256 bits) after Base64 decode`.** The key
was not generated with `openssl rand -base64 32`, or a trailing newline crept in.
Check with:

```bash
grep PII_ENCRYPTION_KEY .env | cut -d= -f2- | base64 -d | wc -c   # must print 32
```

**The migrate container exits without applying anything.** Check
`docker compose logs migrate`. It must log `Successfully applied ... migration`.
An image that prints its usage text and exits `0` is the old
`migrate`-without-a-command bug; the current descriptor passes `command: [migrate]`.

**`relation "flyway_schema_history" does not exist` after changing migrations.**
An applied migration was edited. Flyway stores a checksum of what it applied, so
edits must be a new `V*` file. See [database/README.md](database/README.md).


### Schema changes

The project uses one migration tool, Flyway, via the dedicated `migrate`
container. Do not add Liquibase alongside it: two tools create competing schema
histories. Follow the versioning, validation, and zero-downtime change guidance
in [database/README.md](database/README.md) whenever adding or changing tables.

### Default Credentials

> **Dev only** — production never ships default accounts. See [Production Deployment](#production-deployment).

Only `admin` is created automatically, and its password is the
`BOOTSTRAP_ADMIN_PASSWORD` **you generated** — there is no default. The rest come
from `./tools/seed-dev.sh`, which sets every account below to `password`:

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `password` (after seeding) |
| Doctor | `doctor` | `password` |
| Doctor | `doctor2` | `password` |
| Nurse | `nurse` | `password` |
| Receptionist | `reception` | `password` |
| Pharmacist | `pharmacy` | `password` |
| Billing | `billing` | `password` |

These accounts are **not** created by migrations. Before seeding, the admin
password is whatever you set in `.env`; seeding overwrites it with `password`.

## API Endpoints

An OpenAPI 3 document is served by springdoc at `/v3/api-docs`, with a Swagger UI
at `/swagger-ui.html`. **Both require the `admin` role** — they are not public.

### Idempotency
`POST /api/v1/pharmacy/dispense`, `POST /api/v1/billing/invoices` and `POST /api/v1/billing/payments` **require an `Idempotency-Key` header** (any unique string per logical operation, e.g. a UUID). Retrying with the same key returns the cached result with `Idempotency-Key-Replayed: true` instead of executing twice.

Keys are scoped to the calling **tenant and user**, so two hospitals using the same client key cannot collide or see each other's cached payment. The original request wins a single atomic claim, so concurrent requests with one key produce exactly one execution; a request that arrives while another is still running gets **409** and should retry rather than double-charging. A replay carries the **original status code**, not a blanket 200. A failed request releases its claim, so a client can retry a payment it was legitimately attempting.

### Auth
- `POST /api/v1/auth/login` - Authenticate & get JWT

### Patients
- `GET /api/v1/patients` - List/search patients
- `POST /api/v1/patients` - Register new patient
- `GET /api/v1/patients/{id}` - Get patient details
- `GET /api/v1/patients/uhid/{uhid}` - Lookup by UHID

### Encounters (OPD)
- `POST /api/v1/encounters` - Create encounter with vitals
- `GET /api/v1/encounters/{id}` - Get encounter
- `POST /api/v1/encounters/{id}/sign` - Sign & close
- `POST /api/v1/encounters/suggest-medicines` - AI advisor
- `GET /api/v1/encounters/prescriptions/pending` - Pending Rx

### Pharmacy
- `GET /api/v1/pharmacy/medicines` - Medicine catalog
- `POST /api/v1/pharmacy/medicines` - Add medicine
- `POST /api/v1/pharmacy/medicines/{id}/stock-in` - Add stock batch
- `POST /api/v1/pharmacy/dispense` - FEFO dispense
- `GET /api/v1/pharmacy/transactions` - Stock ledger

### Admissions (IPD)
- `POST /api/v1/admissions` - Admit patient to room
- `PUT /api/v1/admissions/{id}/discharge` - Discharge & auto-bill
- `GET /api/v1/admissions/active` - Active admissions
- `GET /api/v1/admissions/rooms` - All rooms
- `GET /api/v1/admissions/rooms/available` - Available beds

### Billing
- `POST /api/v1/billing/invoices` - Generate GST invoice
- `POST /api/v1/billing/payments` - Record payment
- `GET /api/v1/billing/patients/{id}/invoices` - Patient invoices
- `GET /api/v1/billing/patients/{id}/unbilled` - Unbilled charges

### Dashboard & Notifications
- `GET /api/v1/dashboard` - Role-based analytics
- `GET /api/v1/notifications` - User notifications
- `GET /api/v1/notifications/unread-count` - Unread count

## Key Features

1. **FEFO Dispensing** - First-Expired-First-Out algorithm auto-selects oldest batches when dispensing
2. **Auto-billing** - Pharmacy dispenses and room charges auto-post to patient ledger
3. **AI Medicine Advisor** - Keyword/catalog matching suggests medicines from hospital formulary
4. **DPDP Compliance** - Patient consent tracking for data privacy
5. **Audit Trail** - All mutations logged with user, IP, and diff
6. **Real-time Notifications** - WebSocket-based alerts for critical events
7. **Role-based Access** - 7 roles with granular route and API protection

## Production Deployment

Production uses a **separate compose file**, a **managed PostgreSQL** (Supabase,
per [ADR-0008](docs/adr/0008-supabase-managed-postgres-auth-stays-inhouse.md)),
and **no in-host database**. Do not use `docker-compose.yml` for production.

### Required configuration

Copy the template and fill it in:

```bash
cp .env.prod.example .env.prod
```

`.env.prod` is gitignored. **Never commit it.**

| Variable | Purpose | Notes |
|----------|---------|-------|
| `SUPABASE_POOLER_URL` | JDBC URL for managed PostgreSQL | `sslmode=require` is mandatory — patient records cross this connection. **No password in the URL**: it arrives as the `db-password` secret. |
| `DB_USER` | Database role | Must be `postgres.<PROJECT_REF>` for the shared pooler. A bare `postgres` fails with `no tenant identifier provided`. |
| `MEDOS_DOMAIN` | Public hostname | Needs an A/AAAA record resolving to the host, or Caddy cannot obtain an ACME certificate. |
| `ACME_EMAIL` | Certificate expiry contact | |
| `MEDOS_TAG` | Image tag | Pin to an immutable tag or digest, not `local`. |
| `DB_POOL_SIZE` | Client-side pool | Keep modest; the pooler multiplexes connections. |
| `SECRETS_DIR` | Where secret files live | Defaults to `./secrets`. |

The **KEK/DEK key lifecycle** is documented in
[ADR-0009](docs/adr/0009-envelope-encryption-per-tenant-deks.md) and is **implemented
on `main`**. `PII_ENCRYPTION_KEY` is the *key-encryption key*, not the data key: it
wraps a per-tenant DEK stored in `tenant_keys.wrapped_dek`, so one hospital's key
can never decrypt another's records. The blind-index key is a third, separately
wrapped key, so rotating PII data does not silently break name search.

Ciphertext carries its DEK generation as `kv1.d<generation>:<base64>`, and legacy
unversioned values are still readable. **KEK rotation is supported** — see below.

### Rotating the KEK

```bash
# 1. Dry run: reports how many tenant keys would be re-wrapped, writes nothing
docker compose -f docker-compose.prod.yml run --rm \
  -e MEDOS_KEY_ROTATION_MODE=dry-run backend

# 2. Apply: re-wraps each tenant DEK under the new KEK. Resumable, and fails
#    closed with zero writes if any tenant cannot be unwrapped with the old key.
docker compose -f docker-compose.prod.yml run --rm \
  -e MEDOS_KEY_ROTATION_MODE=apply \
  -e MEDOS_KEY_ROTATION_TARGET_VERSION=2 \
  -e MEDOS_KEY_ROTATION_NEW_KEK="$(cat secrets/pii-encryption-key.next)" \
  backend

# 3. Verify: unwraps every tenant key with the new KEK, then exits.
docker compose -f docker-compose.prod.yml run --rm \
  -e MEDOS_KEY_ROTATION_MODE=verify \
  -e MEDOS_KEY_ROTATION_NEW_KEK="$(cat secrets/pii-encryption-key.next)" backend
```

Modes come from environment variables, **not** CLI flags — a key on a command line
is visible in `docker inspect`. Full runbook: `docs/operations.md` §7d. Every
rotation is recorded in `key_rotations`.

> **Not yet implemented: DEK rotation.** The KEK can be rotated today without
> touching a single patient row. Rotating an *individual tenant's* DEK (the
> `dek_rotation` operation the schema reserves) requires re-encrypting that
> tenant's rows and is not wired up — `TenantKeyHolder.currentGeneration()` still
> returns `1`, so ciphertext cannot express a generation above 1.

### Secrets

Secret values are **files**, not environment variables, so they never appear in
`docker compose config` output. They are gitignored:

```bash
umask 077
printf '%s' '<db password>'            > secrets/db-password.txt
printf '%s' "$(openssl rand -base64 48 | tr -d '\n')" > secrets/jwt-secret.txt
printf '%s' "$(openssl rand -base64 32 | tr -d '\n')" > secrets/pii-encryption-key.txt
printf '%s' "$(openssl rand -base64 32 | tr -d '\n')" > secrets/redis-password.txt
printf '%s' "$(openssl rand -base64 18 | tr -d '\n')" > secrets/bootstrap-admin-password.txt
```

No trailing newlines: the values are read with `cat` into environment variables.
A missing secret **stops startup** rather than falling back to a default.

### Boot a production stack

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

curl http://<MEDOS_DOMAIN>/manage/health
```

After the first successful login, remove `BOOTSTRAP_ADMIN_PASSWORD` from
`secrets/` — the bootstrap runner is a no-op once an active admin exists.

### Security posture (what's enforced)

- **No demo accounts.** Migrations never create users. The admin is created once
  from `BOOTSTRAP_ADMIN_PASSWORD`; demo accounts exist only via the dev seeder.
- **Fail closed.** No inline default for `JWT_SECRET` or `PII_ENCRYPTION_KEY`;
  both are length-validated at startup.
- **Tenant isolation.** Every clinical table carries `tenant_id`; reads are scoped
  per tenant in the persistence layer.
- **Revocation is per request.** A token lives 10 hours, so both the tenant's
  `active` flag **and** the person's `tenant_users.active` membership are
  re-checked on every authenticated request. Removing someone from a hospital's
  staff page therefore ends their access immediately rather than at token
  expiry. A token with no `tenantId` — the platform super-admin — is skipped,
  since it holds no membership by design.
- **PII at rest.** Nine columns across `patients` and `encounters` are
  AES-256-GCM encrypted under a **per-tenant DEK**, itself wrapped by the KEK.
  Name search uses an independently keyed blind index, so the database alone
  cannot reveal or confirm a name.
- **Tenant provisioning is gated** — `/api/v1/onboarding/register` is *not*
  public: it requires a shared platform secret (constant-time compared, blank
  means deny) or a `super_admin` token. Opt-in public signup exists but is off by
  default and documented as requiring prerequisites that do not yet exist.
- **Actuator** — only `/manage/health` and `/manage/info` are public; all other
  `/manage/**` and the OpenAPI docs require the `admin` role.
- **Login brute-force** — 5 failed attempts per (user, socket peer) → 15-minute
  lockout, plus a username-wide counter at 20 to bound a distributed attacker.
  The peer comes from the socket, never from `X-Forwarded-For`, which a client can
  rotate.
  (Redis-backed, with an in-memory fallback).
- **WebSocket** — allowed origins restricted to `CORS_ORIGINS`; STOMP `CONNECT`
  rejected without a valid Bearer token.
- **TLS** — Caddy is the sole terminator, with ACME certificates, an HTTP→HTTPS
  redirect, and HSTS. Only ports 80/443 are published; database, Redis and backend
  are on an `internal: true` network.
- **Image scanning** — CI runs Trivy against the backend, frontend and Caddy
  images, ratcheted against a `.trivyignore` baseline so new HIGH/CRITICAL
  findings fail the build.

### Known gaps

Tracked in GitHub issues rather than papered over:

- **`/api/v1/users/me` returns the password hash** — [#106](https://github.com/gaurav-amritkar/med-os/issues/106), P0.
- **Per-tenant DEK rotation is not implemented** — only fleet-wide KEK re-wrap is.
  See the note above and `docs/adr/0009-envelope-encryption-per-tenant-deks.md`.
- **No observability** — no Prometheus metrics, structured JSON logging, or
  correlation IDs. Actuator is limited to `/manage/health` and `/manage/info`.
- **Tenant row scoping covers `SELECT` only** — `TenantStatementInspector` injects
  `tenant_id` into the root `FROM` of reads; writes rely on services setting
  `tenantId` explicitly.
- **No container hardening beyond non-root images** — no resource limits,
  `read_only`, `cap_drop` or `no-new-privileges` on any service; the `redis`,
  `caddy` and `migrate` images still run as root.
- **CI scans images but not dependencies** — no dependency scanning, SAST/CodeQL,
  or secret scanning.
- **FHIR / ABDM interoperability** is not implemented — [#92](https://github.com/gaurav-amritkar/med-os/issues/92) onwards.

### Operations

- **Backups**: use `docker compose exec -T db pg_dump -U "$DB_USER" "$DB_NAME"` or managed-Postgres snapshots. Test restores periodically.
- **Migrate**: the dedicated `migrate` job runs before the backend; add migrations under `database/migrations/` following the `V<n>__name.sql` convention.
- **Rollback**: app rollback = redeploy previous image. DB migrations are forward-only; never edit an applied migration (checksum validation will fail) — add a new one instead.

### Testing & CI

```bash
# Backend — H2 test profile, no Docker needed
cd backend && mvn clean test        # use `clean`: stale reports in target/ misreport results

# Frontend — unit tests, lint, production build
cd frontend && npm install && npm test && npm run lint && npm run build

# End-to-end, against a running dev stack
cd frontend
E2E_PASSWORD='<admin password>' npm run probe:flows
E2E_BASE_URL="http://localhost:8080" E2E_PASSWORD='<admin password>' npx playwright test e2e/smoke.spec.js
E2E_BASE_URL="http://localhost:8080" E2E_PASSWORD='<admin password>' npx playwright test e2e/full.spec.js
```

`E2E_PASSWORD` is required and the specs skip without it. Use the password you set
in `.env`, or `password` if you have run `./tools/seed-dev.sh`.

`e2e/full.spec.js` includes a **currently failing** assertion that
`/api/v1/users/me` must not return a password hash. It fails on purpose until
[#106](https://github.com/gaurav-amritkar/med-os/issues/106) is fixed — do not
weaken it to make the suite green.

CI (`.github/workflows/ci.yml`) runs four required checks on every PR: backend
build and tests, frontend lint/test/build, container definitions, and an image
vulnerability scan gated by `.trivyignore`. The `containers` job additionally runs
the migration gate (`tools/verify-migrations.sh`), its own self-test
(`tools/test-verify-migrations.sh`), the secret-wiring gate
(`tools/verify-prod-secrets.sh`, which is why a secret the backend read but never
mounted could not reach production again), a live Flyway `validate` against Postgres 16,
and a set of assertions over the resolved `docker-compose.prod.yml` model (only
Caddy may publish a port; no in-host `db`; no `*_PASSWORD_FILE` keys).

CI does **not** scan application dependencies, run SAST/CodeQL, or scan for
committed secrets.
