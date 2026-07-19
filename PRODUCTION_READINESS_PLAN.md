# MedOS HMS — Production Readiness Plan

> Snapshot assessment of the `med-os` repo (Spring Boot 3.2 + React 19 + PostgreSQL/Redis, Docker Compose).
> Goal: turn a strong v3.0 MVP into a ship-to-hospital, DPDP-compliant production system.

---

## 1. Current State Assessment

**What's already done well**
- Clean layered Spring Boot backend (config / controller / dto / entity / repository / service / security).
- 19 numbered Flyway migrations with FK integrity and CHECK constraints.
- Stateless JWT auth (BCrypt + HS JWT via `jjwt`), `JwtAuthenticationFilter`, `CustomUserDetailsService`.
- Method-level RBAC via `@PreAuthorize` on all controllers; `CurrentUserProvider` for audit attribution.
- DTOs with Bean Validation (`@Valid`); `GlobalExceptionHandler` + `ApiError`.
- FEFO dispensing, auto-billing of charges, GST computation, patient outstanding sync.
- Audit logger, DPDP consent fields, notifications, WebSocket config.
- Docker Compose with healthchecks, named volumes, multi-stage Dockerfiles, nginx with security headers + SPA fallback + reverse proxy.
- Per-profile config (`application-dev.yml` / `application-prod.yml`), Hikari tuning, `forward-headers-strategy`.

**Critical gaps blocking production**
1. Default credentials everywhere (`admin/password`) and **demo seed data for real patients/doctors in a migration** (`V2__seed_data.sql`).
2. Hardcoded weak JWT secret committed in `.env`, `application.yml`, `application-prod.yml`, and `docker-compose.yml` defaults — same on all four. The secret is also only 44 bytes of repeated base64.
3. `actuator/**` and `ws/**` are `permitAll()` in `SecurityConfig` — `/actuator/health` shows DB/Redis details (`when-authorized` is mitigated, but env/heap/metrics are off; still need a hardening pass).
4. No rate limiting / brute-force protection on `/api/auth/login`.
5. No CSRF/origin checks on WebSocket handshake; `/ws/**` fully open.
6. No automated tests (no `src/test/java`).
7. No CI/CD pipeline; no branch protection; secrets in repo history (`.env` is gitignored but `JWT_SECRET` defaults are baked into source).
8. No structured logging / log shipping; no trace IDs / correlation IDs.
9. No backup strategy for `medos-db-data` and Redis overflow / eviction policy tuning.
10. Integer `quantity` / `DECIMAL` money math without rounding mode in some places; GST applied unevenly (pharmacy charges 5% GST; service/lab charges likely none).
11. No PII encryption at rest (patient demographics, clinical notes stored in plain text).
12. No password policy / rotation / lockout / account deactivation flow.
13. Docker image runs as root (nginx + JRE images); backend container exposes DB port externally in default compose.
14. Frontend stores JWT in `localStorage` (XSS-exposed) and `user` object — no refresh token, no XSS hardening review.
15. No feature flags, no maintenance mode, no graceful shutdown wiring (Spring Boot has it; not configured).
16. `EncouterService` is misspelled (typo) — minor but should be fixed before external API freezes.

---

## 2. Production Readiness Plan — Phased

### Phase 0 — Security Hotfixes (must-do before any prod deploy)  🚨
**Owner: backend + ops • Effort: 2–3 days**

- [ ] **Replace JWT secret defaults.** Remove the hardcoded `ZmFlZmFl...` from `application.yml`, `application-prod.yml`, and `docker-compose.yml`. Make `JWT_SECRET` a required env var (fail fast on startup if missing/weak; min 32 bytes, validate length). Add a `@ConfigurationProperties` validator.
- [ ] **Remove demo seed data from migrations.** Split `V2__seed_data.sql` into:
  - `V2__reference_data.sql` (medicine catalog, disease map, rooms) — keep.
  - `V3__demo_seed.sql` (users with `password`, sample patients) — move into a `dev`-only profile-loaded script or a `tools/seed-dev.sh` that runs **outside** Flyway. Production Flyway must never create `admin/password`.
- [ ] **Force-change default passwords** on first login; document admin bootstrap via env var (`BOOTSTRAP_ADMIN_PASSWORD`) generated per-deploy.
- [ ] **Harden `/actuator`.** Move to `/manage/**` (internal), restrict to localhost / management network or admin role; expose only `health,info,prometheus`. Set `show-details: never` for `health` unless authenticated.
- [ ] **Secure WebSocket.** Add a `HandshakeInterceptor` that validates JWT (query param or `Sec-WebSocket-Protocol` header); reject anonymous connections. Configure allowed origins explicitly (`setAllowedOrigins` from `medos.cors.allowed-origins`).
- [ ] **Login hardening.** Add rate limiting (bucket4j + Redis, or a simple failed-attempts counter in Redis with lockout after N failures / exponential backoff). Return generic `Invalid credentials` and consistent timing to avoid user enumeration.
- [ ] **CSRF:** keep disabled (JWT stateless), but add `SameSite=Strict` note documented; ensure no cookie-based auth.
- [ ] **Secrets management.** Remove `.env` from being committed anywhere in history via `git filter-repo` (note: it's gitignored, but defaults still baked in source — remove those). Provide `.env.example` with placeholders. Document use of Docker secrets / Vault / SSM for real deploys.
- [ ] **DB external port.** In prod compose, do **not** expose `db:5432` and `redis:6379` externally. Add a `docker-compose.prod.yml` override that drops the `ports:` for db/redis.
- [ ] **Container users.** Backend Dockerfile: run as non-root (`USER 10001`), nginx: use the unprivileged image / run with `USER nginx` and bind to 8080 internally behind TLS.
- [ ] **PII at rest.** Plan encryption for `patients.phone`, `email`, `address`, `encounters.diagnosis/clinical_notes` (DB-level column encryption or app-level envelope encryption via a KMS key per tenant). At minimum add a `V4__pii_encryption` migration path + service-layer encrypt/decrypt with reserved columns.

### Phase 1 — Reliability & Data Integrity  🔧
**Effort: 3–4 days**

- [ ] **Tests.** Add module:
  - Unit: services (Pharmacy FEFO, Billing GST math, Admission discharge auto-charge, AI suggest).
  - Slice: `@WebMvcTest` for each controller with `@WithMockUser` per role → assert RBAC matrix.
  - Integration: `@SpringBootTest` + Testcontainers (PostgreSQL 16 + Redis 7) running Flyway → smoke tests of full flows.
  - Frontend: Vitest + React Testing Library on stores and at least the Login + Pharmacy pages.
- [ ] **Concurrency.** Audit `dispense`, `discharge`, `room.occupied`, `admission` for race conditions. Add `@Version` optimistic locking on `MedicineBatch`, `Admission`, `Room`, `Invoice`; convert critical updates to row-level `SELECT ... FOR UPDATE` (e.g., batch deduction). Add retry on `OptimisticLockException`.
- [ ] **Money math.** Centralize money/rounding in a `Money` util (`MathContext.DECIMAL64`, half-up). Verify GST inclusions/exclusions consistently; add `gst_inclusive BOOLEAN` to make intent explicit. Add tests asserting rounding on edge values.
- [ ] **Idempotency.** Add idempotency keys to `POST /api/billing/payments`, `POST /api/pharmacy/dispense`, `POST /api/billing/invoices` (header `Idempotency-Key`) to prevent double-submit double-charge.
- [ ] **Pagination.** All list endpoints (`/patients`, `/pharmacy/transactions`, `/billing/invoices`, `/notifications`) currently return all rows. Add `Pageable` with `Page<T>` responses + index supporting columns (`created_at`, `status`, `patient_id`).
- [ ] **Database hardening.** Add indexes for FK columns and common filters (patient_id, status, created_at, expiry_date on batches). Add `ON DELETE` policy review. Add partial unique indexes where needed (e.g., active username). Add `updated_at` trigger function for entities that claim it.
- [ ] **Audit log completeness.** Verify `performed_by` set on every `StockTransaction`, `Charge`, `Invoice`, `Payment`; audit log captures before/after diffs (currently `auditLogger.log` only records a string). Extend to JSONB `before`/`after` columns.
- [ ] **Graceful shutdown.** `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`. Health readiness group for orchestrator.
- [ ] **Cache invalidation.** Review `@Cacheable` keys, add invalidation on mutation; confirm Redis TTLs set explicitly (not just default). Set `spring.data.redis.lettuce.shutdown-timeout`.

### Phase 2 — Observability & Operations  📈
**Effort: 2–3 days**

- [ ] **Metrics.** Add Micrometer + Prometheus registry; expose `/manage/prometheus` (internal). Track: HTTP timings, dispensing throughput, stock-out count, invoice aging, DB pool (`HikariDataSource` meters), Redis latency.
- [ ] **Logging.** Structured JSON logging (logstash-encoder / Spring Boot 3.4 structured logs). Inject `traceId`/`userId` via MDC in `JwtAuthenticationFilter`. Configure rolling file appender. Ship to Loki/CloudWatch via sidecars (document).
- [ ] **Tracing.** Optional but recommended: OpenTelemetry agent for distributed tracing (especially once mobile/IoT patients added).
- [ ] **Alerting.** Document SLOs; alerts on: login failures spike, 5xx rate, DB connection saturation, Flyway migration failures, healthcheck restarts, low-stock reorder triggers.
- [ ] **Health checks.** Add custom `HealthIndicator` for DB-migrations-applied, Redis, and an "AI service" health for the unused Anthropic path (or remove the AI config entirely since `enabled:false`).
- [ ] **Backups.** Document scheduled `pg_dump` of `medos-db-data` volume (cron sidecar) + restore runbook + tested restore. Set Redis `maxmemory-policy=allkeys-lru` and RDB snapshotting.
- [ ] **Runbook.** Add `docs/operations.md`: how to rotate JWT secret without breaking sessions, how to add a new role, how to run a Flyway repair, retry procedure for a stuck IPD patient.

### Phase 3 — API Hardening & Contract  🧱
**Effort: 2–3 days**

- [ ] **API versioning.** Namespace as `/api/v1/...` consistently (currently mixed). Pin contract for external integrations.
- [ ] **OpenAPI docs.** Add `springdoc-openapi` with security scheme; generate UI gated behind admin role. Use for client/moқbile handoff.
- [ ] **Response DTOs.** Stop returning JPA entities directly from controllers (leaks internal ids, lazy-load N+1, schema drift). Introduce `*Response` DTOs and mappers (MapStruct).audit log timestamps.
- [ ] **Validation review.** Tighten DTOs: `@Size`, `@Pattern` on UHID/phone/email/amounts; `@Future` on expiry dates; ban negative quantities. Add cross-field validators (discharge date >= admit date).
- [ ] **Error contract.** Standardize `ApiError` with `code`, `message`, `details`, `traceId`. Map `BusinessException`, `MethodArgumentNotValidException`, `ConstraintViolation`, `DataIntegrityViolation` consistently. Document error codes.
- [ ] **Renaming.** Rename `EncouterService` → `EncounterService` (file move) before external freeze.
- [ ] **RBAC matrix test.** Enumerate every (role × endpoint) explicitly and enforce via a test fixture so future drift is caught.

### Phase 4 — Frontend Hardening  🖥️
**Effort: 2–3 days**

- [ ] **Token storage.** Migrate JWT from `localStorage` to in-memory (Zustand) + httpOnly refresh-token cookie; or at minimum harden against XSS (strict CSP via nginx). Add token rotation (access 15min + refresh 7d).
- [ ] **CSP & headers.** nginx: add `Content-Security-Policy` (script-src self), `Permissions-Policy`, `Strict-Transport-Security` (once TLS in place), `X-XSRF-TOKEN` only if using cookies.
- [ ] **Error boundaries.** Add React error boundaries + a global toast for 5xx; ensure `401 → logout` already handled.
- [ ] **Loading/empty/error states.** Audit each page for skeleton loaders and empty states (currently bare tables expected).
- [ ] **Accessibility (WCAG 2.1 AA).** Keyboard nav, focus traps in modals, `aria-*`, color contrast, form labels. Hospitals need accessible software.
- [ ] **i18n.** Even English-only should be strings-keyed for future Hindi/regional deployment.
- [ ] **Build.** Vite production source maps gated (off for prod, on for staging). Tree-shaking verified; `react-router` lazy-route split.

### Phase 5 — Deployment & Infra  🚀
**Effort: 2–3 days**

- [ ] **CI/CD pipeline** (GitHub Actions):
  - lint (oxlint for FE; checkstyle/checkstyle for BE or SpotBugs).
  - unit + integration tests (Testcontainers).
  - build images, scan with Trivy, push to registry.
  - Flyway `migrate` as part of deploy (gated), with automatic rollback path.
- [ ] **Environment split.** `dev`, `staging`, `prod` orchestras. Provide `docker-compose.prod.yml` (no db/redis ports, TLS via Caddy/Traefik in front, secrets via file mounts / `secrets:`).
- [ ] **TLS termination.** Put Caddy or Traefik in front of nginx with auto-LE certs; force HTTPS, HSTS, HTTP→HTTPS redirect.
- [ ] **Secrets.** Docker `secrets:` for DB_PASSWORD, JWT_SECRET; Spring Boot reads from `/run/secrets/...`. Document rotation.
- [ ] **Resource limits.** Compose: memory/CPU limits; JVM `-XX:MaxRAMPercentage=75` instead of fixed `-Xmx512m`.
- [ ] **Zero-downtime deploys.** Backend behind nginx with health-gated rolling restart; Flyway pre-deploy step to fail fast on migration drift.
- [ ] **DB & Redis in managed mode for real prod.** Replace embedded docker services with managed Postgres (RDS/Aurora/CloudSQL) and managed Redis (Elasticache) for real hospital deploys; keep docker-compose for self-host/edge.

### Phase 6 — Compliance, Privacy & Clinical Safety (DPDP / ABDM-aligned)  🛡️
**Effort: ongoing + 3–5 days initial**

- [ ] **DPDP Act.** Implement consent lifecycle (record, version, revoke) with `consents` table populated everywhere patient data is touched; add `data_principal_request` endpoint (export / delete) honoring patient rights.
- [ ] **Data retention policy.** Define retention windows per entity; add a cleanup/archival job with audit "soft purged" records (don't hard-delete medical records — instead pseudonymize per regulatory retention).
- [ ] **ABDM / Health ID.** Plan integration with Ayushman Bharat Digital Mission (Health ID, HFR, HRP, FHIR R4) if targeting Indian govt hospitals — note as a future epic, not MVP-gating.
- [ ] **Clinical safety.** Classify AI medicine advisor as decision-support only (already keyword-based, good) — add a visible "non-prescriptive" disclaimer and require doctor sign-off before any prescription is committed (already gated by `prescribed_by`).
- [ ] **PHI segregation.** Separate `requests/` (PRD/BRD with stakeholder data) from repository, or move to `docs/` with access control.

---

## 3. Recommended Sequencing

```
Phase 0  (Security hotfixes)         ─── blocks everything
   ↓
Phase 1  (Reliability & data)         ─── can parallel with Phase 2/3 on different people
   ↓
Phase 2  (Observability)
Phase 3  (API contract)              ─── lock contract before mobile/moқbile clients
   ↓
Phase 4  (Frontend hardening)
Phase 3  (CI/CD + infra)             ─── enables safe repeated deploys
   ↓
Phase 6  (Compliance)                ─── continuously thereafter
```

Target: **Phase 0–3 + minimal CI** = first auditable production release (~2 weeks, 1–2 engineers).
Phases 4–5 polish and infra = release candidate ready for pilot hospital.
Phase 6 + ABDM = govt-grade deployment.

---

## 4. Quick Wins (single sitting, low risk)

1. Remove `ZmFlZmFl...` defaults and require `JWT_SECRET` env.
2. Remove `admin/password` demo users from Flyway; document admin bootstrap.
3. Tighten `/actuator/**` exposure (move path, lock to admin).
4. Rename `EncouterService` → `EncounterService`.
5. Add `index` migrations for FK columns and `created_at` filters (V4).
6. Disable DB/Redis external ports in a prod compose override.
7. Add the README "Production Deployment" section pointing at this doc.

---

_Last updated: 2026-07-19. Treat items as checkboxes — update as you progress._
