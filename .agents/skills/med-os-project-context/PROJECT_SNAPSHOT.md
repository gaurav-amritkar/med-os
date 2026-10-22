# MedOS Project Snapshot

Optimized for quick agent orientation. Treat as a starting point, then verify
targeted files before edits. **The code is the source of truth — this snapshot
and `docs/roadmap.md` have both been stale in the past.**

## Top-level layout
- `backend/` — Spring Boot backend (Java 17, Maven, Spring Boot 3.5.x)
- `frontend/` — React 19 + Vite frontend
- `database/migrations/` — **Flyway SQL migrations — the single schema owner.**
  This is the one path agents most often get wrong: there is **no**
  `backend/src/main/resources/db/migration/`. Migrations are owned by the
  dedicated `migrate` container (`database/migrations.Dockerfile`), enforced by
  `tools/verify-migrations.sh` (ADR-0005). Never edit an applied migration.
- `cache/` — Redis image + `redis.conf` + entrypoint
- `caddy/` — TLS terminator (`Caddyfile`, `Dockerfile`); production only
- `docker-compose.yml` — local/dev stack (includes an in-host `db`)
- `docker-compose.prod.yml` — production stack (**no** `db`; managed Postgres)
- `docs/adr/` — architecture decision records (0001–0010, all Accepted)
- `docs/operations.md` — runbooks, incl. §7d PII key rotation
- `tools/` — migration gate, dev seeder, Trivy baseline
- `.github/workflows/ci.yml` — CI (4 required jobs)

## Backend quick map
- Build config: `backend/pom.xml`
- App code: `backend/src/main/java/com/medos/`
  - `config/`, `controller/`, `dto/`, `entity/`, `exception/`, `mapper/`,
    `modules/` (billing, clinical, pharmacy, payment), `repository/`,
    `security/`, `service/`, `util/`
- Config: `backend/src/main/resources/application.yml`, `application-prod.yml`
- Tests: `backend/src/test/java/com/medos/` — ~41 test classes

### PII encryption (easy to get wrong — verify before claiming anything)
`PII_ENCRYPTION_KEY` is the **KEK**, not the data key. It wraps a **per-tenant
DEK** stored in `tenant_keys.wrapped_dek`.

| Concern | Where |
|---|---|
| Wiring / startup init | `config/EncryptionConfig.java` |
| KEK owner, DEK resolution, 5-min cache | `security/TenantKeyHolder.java` |
| AES-256-GCM KEK↔DEK wrap/unwrap | `security/KeyWrapCipher.java` |
| Ciphertext `kv1.d<gen>:<b64>` + legacy read | `util/PiiCiphertextFormat.java` |
| Blind index (independent key) | `util/BlindIndexUtil.java`, `security/BlindIndexKeyService.java` |
| KEK re-wrap (dry-run/apply/verify) | `security/KeyRewrapService.java`, `security/PiiKeyRotationRunner.java` |
| Acting-tenant `ThreadLocal` | `security/TenantContext.java` |

Nine encrypted columns: 5 on `Patient`, 4 on `Encounter`.
25 tables total. 7 roles (`super_admin` is easy to forget).

**Implemented:** per-tenant DEK, versioned ciphertext, independent blind-index
key, KEK re-wrap + runbook.
**Not implemented:** per-tenant DEK rotation (`dek_rotation`) — `currentGeneration()`
hardcodes `1`; blind-index key rotation has no operator surface.

## Frontend quick map
- Build config/scripts: `frontend/package.json`
- App code: `frontend/src/` — `api/`, `components/`, `pages/`, `store/`, `test/`
- E2E: `frontend/e2e/` (Playwright; needs `E2E_PASSWORD`), `probe:flows` script
- Runtime proxy/hardening: `frontend/nginx.conf`

## Known hotspots
- Security/auth: `config/SecurityConfig.java`, `security/*`, `service/AuthService.java`
- Tenant isolation: `TenantStatementInspector.java` — **`SELECT` root-`FROM` only;
  writes rely on services setting `tenantId` explicitly**
- Error contract: `exception/*`, `controller/*`
- Prod deploy: `docker-compose.prod.yml`, `caddy/Caddyfile`, `secrets/*`
  (gitignored — never read values, only presence)

## Open blockers worth knowing before you start
- `/api/v1/users/me` no longer returns the password hash — fixed (#108); the
  Playwright assertion is green. Formerly P0.
- Consent is a precondition of patient registration (`PatientService.java:51`),
  recorded as a P0 DPDP defect; the parity programme's Wave 0 decouples it.
- Observability (#9) is at zero: no Prometheus, no JSON logs, no correlation IDs.

## Recommended default commands
From `backend/`:
- `mvn clean test`   (use `clean` — stale reports in `target/` misreport results)
- `mvn -DskipTests package`

From `frontend/`:
- `npm run lint`
- `npm test`
- `npm run build`

From repo root:
- `docker compose config --quiet`
- `docker compose build`
- `./tools/verify-migrations.sh` — run this after any migration change

## Token-saving playbook
1. Read `docs/roadmap.md` + one relevant config file first.
2. Locate the exact target with `grep`/`glob` scoped to `backend/**` or
   `frontend/**`.
3. Read only the needed file sections.
4. Validate with the smallest relevant command, then broaden only if needed.
