# MedOS Production Readiness Assessment

> ⚠️ **Heavily corrected 12 Oct 2026.** This assessment was written on
> 2026-09-22 against a pre-hardening codebase and had drifted into actively
> misleading territory: it listed `ddl-auto: update` in production as the **#1
> critical issue** when the profile has said `validate` for weeks, and it scored
> "no production deployment descriptor", "no TLS", "no image scanning" and "no
> key rotation" as gaps when all four are implemented and on `main`.
>
> **Current status: far more ready than this document says.** The genuinely open
> work is listed under [Real remaining gaps](#real-remaining-gaps) below. Treat
> the historical sections as history unless flagged **CORRECTED**.

## Executive Summary
MedOS is a Spring Boot 3.5 + React 19 multi-tenant Hospital Management System
with core functionality implemented, a production deployment descriptor, TLS
termination, a hardened CI pipeline and envelope encryption for patient PII.
Security controls are present and mostly enforced. What remains is
**observability** (nothing is instrumented), **container hardening**, and **two
concrete blockers** listed below.

## Current State Assessment (corrected 2026-10-12)

### Architecture
- **Backend**: Spring Boot 3.5.16 monolith — 10 controllers, 20 `*Service` classes,
      24 `@Entity` types across 25 tables
- **Frontend**: React 19 + Vite with auth/loading/toast stores
- **Database**: PostgreSQL 16. Managed via Supabase in production (ADR-0008);
      an in-host `db` service exists for development only
- **Multi-tenancy**: row-level via `tenant_id` + `TenantStatementInspector`
      ⚠️ **`SELECT` root-`FROM` only** — `INSERT`/`UPDATE`/`DELETE` are not
      scoped, so writes depend on services setting `tenantId` explicitly
- **Deployment**: `docker-compose.yml` (dev) and **`docker-compose.prod.yml`
      (production)** — the latter publishes only Caddy, runs migrations as a
      one-shot gated job, and bridges an `internal: true` network

### Security Controls ✅
- JWT authentication with secret validation (≥32 bytes Base64) and issuer assertion
- Tenant isolation at the read path; ⚠️ not at the write path (see above)
- Password hashing with BCrypt
- Rate limiting: 5 failed attempts per **(username + IP)** → 15 min lockout.
      ⚠️ Login only — there is no general API rate limiter, and
      `/api/v1/onboarding/register` is unrated
- Tenant provisioning gated by a platform secret (constant-time, blank ⇒ deny) or
      `super_admin`
- PII encryption: **envelope** — a KEK wraps a per-tenant DEK; the blind-index
      key is separately wrapped. KEK re-wrap is implemented and audited
- Idempotency filter for duplicate request prevention
- CSRF disabled (appropriate for stateless JWT)
- CORS configured with specific origins; WebSocket `CONNECT` requires a valid
      Bearer token and a permitted origin
- Actuator at `/manage` exposing only `health,info`; swagger/OpenAPI is ADMIN-only
- TLS via Caddy with ACME, HTTP→HTTPS redirect and HSTS

### Identified Gaps

#### ~~Critical Issues (Must Fix Before Production)~~
**CORRECTED — the two blockers below are the real critical issues.**

1. ~~**DDL Auto Update in Prod** (`application-prod.yml`)~~
   ❌ **False.** It is `ddl-auto: validate` (`application-prod.yml:16`), with
   app-side Flyway disabled. Schema ownership is Flyway via the `migrate`
   container, enforced by a three-layer CI gate.

2. **God Services** — still valid in spirit, but the named classes no longer
   exist; pharmacy is now split into `DispenseService` / `InventoryService` /
   `MedicineCatalogService` / `PrescriptionService`. The point stands.

3. **Infrastructure** — mixed, corrected inline below:
   - ✅ No API gateway — **and in production there is no direct exposure either**:
     only Caddy publishes 80/443, backend and Redis sit on `internal: true`
   - ✅ No distributed tracing — still true (a v1 non-goal, ADR-0006)
   - ✅ No centralized logging — still true
   - ✅ No monitoring stack (Prometheus/Grafana) — still true, and this is the
     largest remaining gap
   - ✅ Secrets in `.env` files — **production reads file-backed secrets from
     `/run/secrets`** (ADR-0004). Dev still uses `.env`.
   - ~~No Redis cluster~~ — **not a defect.** ADR-0007 declares Redis
     non-authoritative: it holds cache and idempotency keys, never system of
     record. Remove this entry.
   - ❌ No database read replicas — still true, deferred per ADR-0001.

#### The one remaining blocker
1. **`/api/v1/users/me` returns the password hash** (#106), P0.

   ✅ **Resolved since this assessment:** `BOOTSTRAP_ADMIN_PASSWORD` was not
   wired into `docker-compose.prod.yml`, so the backend could not start in
   production at all. Fixed in #137 — the secret is now declared and mounted,
   `.env.prod.example` documents it, and `tools/verify-prod-secrets.sh` fails CI
   if any secret the app reads at runtime is not mounted, so the class of defect
   cannot recur silently.

#### Important Improvements (Recommended for Production)
1. **Resilience Patterns**
   - Circuit breaker (Resilience4j) for external calls (Stripe, etc.)
   - Retry mechanisms with exponential backoff
   - Bulkhead isolation for critical resources
   - Timeout configurations

2. **Observability**
   - OpenTelemetry tracing with Jaeger
   - Prometheus metrics endpoint exposure
   - Structured logging with correlation IDs
   - Health checks for all dependencies
   - API monitoring and alerting

3. **Deployment Hardening**
   - Kubernetes/ECS deployment with Helm charts
   - Blue/green or canary release strategy
   - Pod disruption budgets and resource limits
   - Automated backup verification procedures
   - Secret rotation automation — ⚠️ the KEK path **is** automated
     (`docs/operations.md` §7d); the DB/Redis password path is not
   - ✅ Container image scanning in CI — **done** (Trivy, digest-pinned,
     ratcheted against `.trivyignore`, SARIF uploaded)

4. **Data & Reliability**
   - PostgreSQL read replicas for read-heavy workloads
   - ~~Redis cluster for high availability~~ — **not required**; ADR-0007 makes
     Redis non-authoritative
   - ✅ Automated PII **KEK** rotation — **done** (dry-run/apply/verify, resumable,
     fail-closed, audited to `key_rotations`)
   - ❌ Automated **DEK** rotation (`dek_rotation`) — still missing; needs a data
     migration, and `currentGeneration()` hardcodes `1`
   - Regular disaster recovery drills
   - Backup restoration testing procedures
   - Database connection pool tuning — ⚠️ `DB_POOL_SIZE` in `.env.prod` is
     **inert**; `application-prod.yml` hardcodes the pool size

#### Security Enhancements
1. **Regular Testing**
   - OWASP ZAP penetration testing in CI
   - PII encryption end-to-end validation — 🟡 partial (key isolation,
     blind-index independence, versioned/legacy ciphertext and migration tests
     exist; no full encrypt → rotate → decrypt journey)
   - ✅ Migration validation — **done** (live Flyway `validate` + checksum
     immutability gate + gate self-test, in CI)
   - ✅ Role-based access control testing — **done** (`RbacMatrixTest`, 22 cases;
     ⚠️ but no row for users, tenants or onboarding)
   - ❌ Dependency vulnerability scanning — still missing; `npm ci` runs with
     `--no-audit`, and there is no OWASP Dependency-Check or CodeQL

2. **API Security**
   - API gateway with rate limiting per tenant — ❌ no gateway exists; ⚠️ and
     there is **no general API rate limiter at all** (login only)
   - Input validation strengthening
   - Output encoding where applicable
   - Security headers — 🟡 **HSTS is set** (Caddy); ❌ still missing `CSP`,
     `X-Frame-Options`, `Permissions-Policy`
   - JWT refresh token rotation — ❌ no refresh tokens exist; sessions are
     stateless with a 10h TTL, so `JWT_SECRET` rotation is the only way to
     invalidate outstanding tokens

## Real remaining gaps (2026-10-12)

If you only read one section, read this one. Everything above is the original
assessment, corrected inline.

**Blockers**
1. ~~Wire `BOOTSTRAP_ADMIN_PASSWORD` into `docker-compose.prod.yml`~~ ✅ **done**
   (#137), with `tools/verify-prod-secrets.sh` in CI to stop it recurring.
2. `/api/v1/users/me` returns the password hash (#106).

**Largest genuine gap**
3. **Observability is at zero.** No `micrometer-registry-prometheus`, no JSON
   logging (no `logback*.xml` — plain console text), no correlation ID, no
   tracing, and actuator exposes only `health,info`. Operationally you are flying
   blind: no way to tell a slow query from a slow payment.

**Security hardening**
4. Tenant scoping does not cover `INSERT`/`UPDATE`/`DELETE`.
5. `/api/v1/onboarding/register` has no rate limit and no secret rotation; its
   guard is a single shared secret with no per-operator attribution, and tenant
   creation is not audited.
6. No CSP, `X-Frame-Options` or `Permissions-Policy`.
7. `LoginRateLimiter` and `AuditLogger` trust `X-Forwarded-For` directly — safe
   only because Caddy overwrites it. Direct-to-backend access makes both spoofable.
8. No secret scanning or pre-commit hook (ADR-0004 flags this as still open after
   the #73 credential purge).
9. `redis`, `caddy` and `migrate` images still run as root.
10. `.trivyignore` is a ~4.8 KB accepted-finding ratchet; the image gate is only
    as good as that baseline.

**Reliability / infra**
11. No resource limits, `read_only`, `cap_drop` or `no-new-privileges` on any
    service in either compose file.
12. `ACME_EMAIL` is not passed to the Caddy service — expiry notices go nowhere.
13. No DR drill; no Redis persistence/failover test (T7, T10).
14. Springdoc is wired and ADMIN-gated, but there is no API versioning plan (#11).

## Production Readiness Checklist

### ✅ Completed
- [x] Core authentication and authorization
- [x] Tenant isolation implementation
- [x] Basic error handling and validation
- [x] Docker compose configuration
- [x] Swagger/OpenAPI documentation
- [x] Idempotency protection
- [x] Rate limiting for authentication
- [x] PII encryption framework
- [x] Basic test suite (backend unit, some e2e)

### 🔧 In Progress / Required
- [ ] Fix DDL auto in prod profile (`validate` instead of `update`)
- [ ] Refactor god services into bounded contexts
- [ ] Add API gateway (Kong or Spring Cloud Gateway)
- [ ] Implement async processing (RabbitMQ/SQS)
- [ ] Add distributed tracing (OpenTelemetry)
- [ ] Implement monitoring stack (Prometheus/Grafana)
- [ ] Add centralized logging (ELK/OpenSearch)
- [ ] Move secrets to Vault/AWS Secrets Manager
- [ ] ~~Set up Redis cluster~~ (not required — ADR-0007)
- [ ] Configure PostgreSQL read replicas
- [ ] Implement circuit breaker pattern
- [ ] Add comprehensive health checks
- [ ] Create Kubernetes deployment manifests
- [ ] Implement backup and disaster recovery procedures
- [ ] Run OWASP ZAP penetration testing
- [ ] Validate PII encryption end-to-end
- [ ] Test migration rollforward/backward procedures
- [ ] Conduct disaster recovery drill

## Risk Assessment

### High Risk (Address Immediately)
1. **Schema instability** from `ddl-auto: update` in production
2. **Service coupling** in god services affecting maintainability
3. **Single points of failure** (Redis, DB without replicas)
4. **Lack of observability** hindering incident response

### Medium Risk (Address in Next Release)
1. **No API gateway** increasing attack surface
2. **Synchronous processing** affecting scalability
3. **Secrets in environment files** (though .gitignored)
4. **Missing resilience patterns** for external dependencies

### Low Risk (Address Long-term)
1. **Feature flag system** for gradual rollouts
2. **Advanced analytics** capabilities
3. **ML/AI enhancements** for clinical decision support
4. **Internationalization/i18n** support

## Recommendations

### Immediate Actions (Week 1)
1. Update `application-prod.yml`: set `ddl-auto: validate`
2. Create spike for service boundary identification (focus on Pharmacy/Billing)
3. Set up secrets management proof-of-concept (Vault or AWS Secrets Manager)
4. Add basic health checks for DB and Redis

### Short-term (Weeks 2-4)
1. Refactor PharmacyService into PrescriptionService and InventoryService
2. Implement API gateway with rate limiting
3. Add Prometheus metrics endpoint and basic Grafana dashboard
4. Set up centralized logging with correlation IDs
5. Implement circuit breaker for Stripe payments

### Medium-term (Months 2-3)
1. Add async processing for non-critical operations (notifications, audit logs)
2. Implement Redis cluster and PostgreSQL read replicas
3. Add distributed tracing with Jaeger
4. Create Kubernetes deployment manifests and Helm charts
5. Conduct first disaster recovery drill

### Long-term (Months 4+)
1. Implement comprehensive monitoring and alerting
2. Add advanced security testing to CI (OWASP ZAP, dependency scanning)
3. Implement feature flag system for safe rollouts
4. Add performance benchmarking and load testing
5. Conduct regular penetration testing and code reviews

## Success Criteria
- [ ] All critical and high-risk items addressed
- [ ] Automated testing includes security and penetration tests
- [ ] Deployment process includes blue/green or canary strategy
- [ ] Observability provides <5 minute MTTR for incidents
- [ ] Regular disaster recovery tests demonstrate <15 minute RTO
- [ ] Security scanning shows no critical/high vulnerabilities
- [ ] Performance benchmarks meet SLAs under expected load

---
*Originally a code review as of 2026-09-22; corrected against `main` on 2026-10-12. See [Real remaining gaps](#real-remaining-gaps-2026-10-12) for current status.*