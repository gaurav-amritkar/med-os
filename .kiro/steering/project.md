---
inclusion: always
---

# MedOS HMS — Project Steering

Multi-tenant Hospital Management System. Java 17 + Spring Boot 3.5 backend, React 19 + Vite 8 frontend, PostgreSQL 16 + Redis 7.4.

## Non-negotiable rules

1. **Schema changes** go in `database/migrations/V<n>__name.sql` only. Never `backend/src/main/resources/db/migration/`. Never edit an applied file.
2. **PII columns** must use the AES-256-GCM envelope converter. `PII_ENCRYPTION_KEY` is the KEK, not the data key. The data key is a per-tenant DEK in `tenant_keys.wrapped_dek`.
3. **Tenant isolation** — every clinical table has `tenant_id`. `TenantStatementInspector` enforces scoping at the SQL boundary. Never query a tenant table without a tenant predicate.
4. **Security** — role authority is resolved from the membership row, not the JWT claim. Rate limiter uses socket peer, not `X-Forwarded-For`. No new public endpoints without guards in `SecurityConfig.java`.
5. **Production compose** — only `docker-compose.prod.yml` for prod. Only Caddy publishes ports. Secrets are files, never env vars.
6. **Always `mvn clean test`** — stale Surefire XML in `target/` causes false-green results without `clean`.

## Module layout

Backend modules live in `backend/src/main/java/com/medos/modules/`:
`appointment`, `billing`, `clinical`, `enquiry`, `payment`, `pharmacy`

Cross-cutting services in `service/`: `AuthService`, `PatientService`, `TenantService`, `UserService`, `NotificationService`, `IdempotencyService`

Security package: `security/` — JWT, TenantContext, KeyHolder, KeyWrapCipher, LoginRateLimiter, DekRotationService

## Full context

See `CLAUDE.md` at the repo root for the complete architecture, invariants, open gaps, and ADR summary.
See `AGENTS.md` at the repo root for working rules, validation commands, and do-not-do list.
