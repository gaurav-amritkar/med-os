# Production Deployment Steps — MedOS HMS

## Required Files & Data
- `backend/pom.xml` — Java 17, Spring Boot 3.2, dependencies
- `frontend/package.json` — React 19, Vite build
- `database/migrations/` — Flyway V1-V3 (schema + seed removal)
- `docker-compose.yml` — Full stack (db, redis, backend, frontend, migrate)
- `.env` — JWT_SECRET (≥32 bytes Base64), DB_PASSWORD, CORS_ORIGINS, REDIS_PASSWORD
- `cache/redis.conf` — AOF + RDB persistence enabled
- `backend/src/main/resources/application-prod.yml` — Prod profile settings

## Cleaned / Removed
- Removed temp integration files (API_INTEGRATION_TEST_ALL.md, ISSUE_*.md, INTEGRATION_TEST_REPORT.md)
- Removed outdated planning artifacts (PRODUCTION_READINESS_PLAN_*.md, GITHUB_ISSUES_PRODUCTION_READINESS_*.md) — replaced by NEXT_STEPS.md + this doc

## Cloud Deploy Sequence
1. `cp .env.example .env` → set secrets (JWT_SECRET via `openssl rand -base64 48`)
2. `docker compose --env-file .env.production up -d --build --pull always`
3. Verify: `curl http://host/manage/health`
4. Set `BOOTSTRAP_ADMIN_PASSWORD`; login; unset immediately

## CI Workflow Fixes Required
- Add Trivy scan (security)
- Add Flyway deploy gate (database/migrations)
- Add push-to-staging trigger
- Remove old `java-version: '21'` if repo requires 17
