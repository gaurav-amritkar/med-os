# MedOS HMS v3.0

**MedOS** is an ultra-premium, role-based Hospital Management System designed to digitize, streamline, and intelligently assist end-to-end hospital workflows — from patient registration and clinical documentation to pharmacy dispensing and financial reconciliation.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17 + Spring Boot 3.2 |
| **Frontend** | React 19 + Vite |
| **Database** | PostgreSQL (primary) + Redis (cache) |
| **Auth** | JWT (stateless) + BCrypt |
| **Security** | Spring Security, RBAC |
| **API** | RESTful (JSON) + WebSocket (real-time) |
| **Persistence** | JPA/Hibernate + Flyway Migrations |
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
  │   ├── config/     # Security, Redis Cache, WebSocket
  │   ├── controller/ # REST controllers
  │   ├── dto/        # Request/Response DTOs
  │   ├── entity/     # JPA entities (19 tables)
  │   ├── exception/  # Global error handling
  │   ├── repository/ # Spring Data JPA repos
  │   ├── security/   # JWT, UserDetails, Auth filter
  │   ├── service/    # Business logic
  │   └── util/       # Audit logger
  └── src/main/resources/
      └── db/migration/  # Flyway SQL migrations
```

## Database Schema (19 Tables)

- `users` - Role-based login (admin, doctor, nurse, receptionist, pharmacist, billing)
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

- Java 17+
- Node.js 18+
- PostgreSQL 15+
- Redis 7+
- Maven 3.8+

### Backend Setup

```bash
cd backend

# Create PostgreSQL database
createdb medos

# Configure application.yml (update DB credentials)

# Run with Maven
mvn spring-boot:run
```

### Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

### Default Credentials

> **Dev only** — production never ships default accounts. See [Production Deployment](#production-deployment).

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `password` |
| Doctor | `doctor` | `password` |
| Nurse | `nurse` | `password` |
| Receptionist | `reception` | `password` |
| Pharmacist | `pharmacy` | `password` |
| Billing | `billing` | `password` |

These accounts are **not** created by Flyway migrations (removed in `V3__remove_demo_seed.sql`).
For local development, seed them explicitly:

```bash
docker compose up -d db     # start the DB
./tools/seed-dev.sh         # seeds demo users + patients (never run against prod)
```

## API Endpoints

### Auth
- `POST /api/auth/login` - Authenticate & get JWT

### Patients
- `GET /api/patients` - List/search patients
- `POST /api/patients` - Register new patient
- `GET /api/patients/{id}` - Get patient details
- `GET /api/patients/uhid/{uhid}` - Lookup by UHID

### Encounters (OPD)
- `POST /api/encounters` - Create encounter with vitals
- `GET /api/encounters/{id}` - Get encounter
- `POST /api/encounters/{id}/sign` - Sign & close
- `POST /api/encounters/suggest-medicines` - AI advisor
- `GET /api/encounters/prescriptions/pending` - Pending Rx

### Pharmacy
- `GET /api/pharmacy/medicines` - Medicine catalog
- `POST /api/pharmacy/medicines` - Add medicine
- `POST /api/pharmacy/medicines/{id}/stock-in` - Add stock batch
- `POST /api/pharmacy/dispense` - FEFO dispense
- `GET /api/pharmacy/transactions` - Stock ledger

### Admissions (IPD)
- `POST /api/admissions` - Admit patient to room
- `PUT /api/admissions/{id}/discharge` - Discharge & auto-bill
- `GET /api/admissions/active` - Active admissions
- `GET /api/admissions/rooms` - All rooms
- `GET /api/admissions/rooms/available` - Available beds

### Billing
- `POST /api/billing/invoices` - Generate GST invoice
- `POST /api/billing/payments` - Record payment
- `GET /api/billing/patients/{id}/invoices` - Patient invoices
- `GET /api/billing/patients/{id}/unbilled` - Unbilled charges

### Dashboard & Notifications
- `GET /api/dashboard` - Role-based analytics
- `GET /api/notifications` - User notifications
- `GET /api/notifications/unread-count` - Unread count

## Key Features

1. **FEFO Dispensing** - First-Expired-First-Out algorithm auto-selects oldest batches when dispensing
2. **Auto-billing** - Pharmacy dispenses and room charges auto-post to patient ledger
3. **AI Medicine Advisor** - Keyword/catalog matching suggests medicines from hospital formulary
4. **DPDP Compliance** - Patient consent tracking for data privacy
5. **Audit Trail** - All mutations logged with user, IP, and diff
6. **Real-time Notifications** - WebSocket-based alerts for critical events
7. **Role-based Access** - 6 roles with granular route and API protection

## Production Deployment

### Required environment variables

| Variable | Purpose | Notes |
|----------|---------|-------|
| `JWT_SECRET` | HMAC signing key | **Required** in prod. `openssl rand -base64 48`, must decode to ≥32 bytes. App fails fast if unset/weak. |
| `DB_PASSWORD` | Postgres password | Required (no default fallback in prod compose). |
| `CORS_ORIGINS` | Allowed browser origins | Comma-separated; required in prod. |
| `BOOTSTRAP_ADMIN_PASSWORD` | One-time initial admin password | Only needed on the very first boot of a fresh DB. Unset after first login. |

Copy `.env.example` to `.env` and fill in real values. **Never commit `.env`** (it is gitignored) and never reuse the dev fallback secret.

### Boot a production stack

```bash
# generate a strong secret and admin bootstrap password
export JWT_SECRET=$(openssl rand -base64 48)
export BOOTSTRAP_ADMIN_PASSWORD=$(openssl rand -base64 24)

# prod override: DB/Redis ports not published, JWT_SECRET/CORS_ORIGINS required
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# verify health (public) — detail-free endpoint
curl http://<host>/manage/health
```

After the first successful login, unset `BOOTSTRAP_ADMIN_PASSWORD` (the runner is a no-op once an active admin exists).

### Security posture (what's enforced)

- **No demo/default accounts** — Flyway migrations never create users; `V3` deletes/deactivates any that predate it. Admin is created once via `BOOTSTRAP_ADMIN_PASSWORD`.
- **JWT_SECRET** — required in prod profile, validated at startup (Base64, ≥32 bytes).
- **Actuator** — moved to `/manage/health` + `/manage/info` (public, no details); all other `/manage/**` require the `admin` role.
- **Login brute-force** — 5 failed attempts per (user+IP) → 15-minute lockout (Redis-backed with in-memory fallback).
- **WebSocket** — allowed origins restricted to `CORS_ORIGINS`; STOMP `CONNECT` rejected without a valid Bearer token.
- **TLS** — terminate TLS in front of the nginx container (Caddy/Traefik/ALB). Enable the `Strict-Transport-Security` header in `frontend/nginx.conf` once TLS is live.

### Operations

- **Backups**: `pg_dump` the `medos-db-data` volume (cron sidecar or managed Postgres snapshots). Test restores periodically.
- **Migrate**: Flyway runs at boot (`validate-on-migrate: true`); add migrations under `backend/src/main/resources/db/migration/` following the `V<n>__name.sql` convention.
- **Rollback**: app rollback = redeploy previous image. DB migrations are forward-only; never edit an applied migration (checksum validation will fail) — add a new one instead.

### Testing & CI

- Backend: `cd backend && mvn test` (uses H2 test profile; no Docker required).
- Frontend: `cd frontend && npm test` (Vitest + Testing Library), `npm run lint`, `npm run build`.
- CI: `.github/workflows/ci.yml` runs all of the above on every PR and push to `main`. Require it as a status check in branch protection.
