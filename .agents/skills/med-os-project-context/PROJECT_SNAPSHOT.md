# MedOS Project Snapshot (2026-08 refresh)

This snapshot is optimized for quick agent orientation. Treat as a starting point, then verify targeted files before edits.

## Top-level layout
- `backend/` — Spring Boot backend (Java, Maven)
- `frontend/` — React + Vite frontend
- `database/` — database-related assets/scripts (project-specific)
- `requirements/` — BRD/PRD docs
- `tools/` — utility tooling
- `.github/workflows/ci.yml` — CI pipeline
- `docker-compose.yml` — local/full-stack orchestration
- `NEXT_STEPS.md` — active roadmap and phased release priorities

## Backend quick map
- Build config: `backend/pom.xml`
- App code: `backend/src/main/java/com/medos/`
  - `config/`, `controller/`, `service/`, `repository/`, `entity/`, `security/`, `exception/`
- Config: `backend/src/main/resources/application*.yml`
- DB migrations: `backend/src/main/resources/db/migration/`
- Tests: `backend/src/test/java/com/medos/` (present)

## Frontend quick map
- Build config/scripts: `frontend/package.json`
- App code: `frontend/src/`
  - `api/`, `components/`, `pages/`, `store/`, `test/`
- Tests present (examples):
  - `frontend/src/pages/Login.test.jsx`
  - `frontend/src/store/authStore.test.js`
- Runtime proxy/hardening: `frontend/nginx.conf`

## CI status in repo
- Workflow exists: `.github/workflows/ci.yml`
- Jobs include backend build/tests, frontend lint/tests/build, and container/compose checks.

## Known planning artifacts
- `PRODUCTION_READINESS_PLAN_gpt5.3codex.md`
- `GITHUB_ISSUES_PRODUCTION_READINESS_gpt5.3codex.md`
- `NEXT_STEPS.md` (most up-to-date execution roadmap)

## Recommended default commands
From `backend/`:
- `mvn test`
- `mvn -DskipTests package`

From `frontend/`:
- `npm run lint`
- `npm test`
- `npm run build`

From repo root:
- `docker compose config --quiet`
- `docker compose build`

## Hotspots for production readiness
- Security config and auth flow:
  - `backend/src/main/java/com/medos/config/SecurityConfig.java`
  - `backend/src/main/java/com/medos/security/*`
  - `backend/src/main/java/com/medos/service/AuthService.java`
- API consistency and error contract:
  - `backend/src/main/java/com/medos/controller/*`
  - `backend/src/main/java/com/medos/exception/*`
- Data integrity and critical business logic:
  - `backend/src/main/java/com/medos/service/*`
  - migrations under `backend/src/main/resources/db/migration/`

## Token-saving playbook
1. Read roadmap + one relevant config file first.
2. Locate exact target file with `find_path`/`grep` in scoped subtree.
3. Read only the needed file sections.
4. Validate with minimal relevant command(s), then broaden only if needed.
