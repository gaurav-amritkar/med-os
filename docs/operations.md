# MedOS Operations Runbook

This document covers backup/restore procedures, Redis persistence policy, and operational tasks for production deployments.

---

## 1. Database Backup & Restore

> **In production the database is Supabase** (ADR-0008), not a container on this
> host. There is no `db` service in `docker-compose.prod.yml`; the `docker compose
> exec db` commands in 1.2 apply to the **development** compose only. Continuous
> backup and point-in-time recovery are the provider's responsibility — verify
> retention and PITR in the Supabase dashboard rather than assuming them. The
> 1.2 commands remain useful against the managed instance over the pooler for a
> logical export or a restore drill.

### 1.1 Automated Backups (Recommended)

For production, use a managed PostgreSQL service (Supabase per ADR-0008, or AWS RDS / Cloud SQL / Azure Database) with automated backups enabled:
- **Backup retention**: 7-30 days minimum
- **Point-in-time recovery (PITR)**: Enabled
- **Backup window**: During low-traffic hours (e.g., 03:00-04:00 UTC)

### 1.2 Manual Backup

> The commands below assume the **development** compose, where the database is an
> in-host container. For production, run `pg_dump` from any host that can reach
> the pooler endpoint, with the credentials from the secret manager.

```bash
# Full backup (schema + data)
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges > backup_$(date +%Y%m%d_%H%M%S).sql

# Schema only (for migration verification)
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" --schema-only > schema_$(date +%Y%m%d_%H%M%S).sql

# Data only (for staging refresh)
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" --data-only --no-owner --no-privileges > data_$(date +%Y%m%d_%H%M%S).sql

# Compressed backup
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

### 1.3 Restore Procedures

#### Full Restore (Disaster Recovery)
```bash
# 1. Stop the application
docker compose stop backend frontend

# 2. Drop and recreate database (CAREFUL - destructive!)
docker compose exec -T db psql -U "$DB_USER" -c "DROP DATABASE IF EXISTS $DB_NAME;"
docker compose exec -T db psql -U "$DB_USER" -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;"

# 3. Restore from backup
docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" < backup_20240115_030000.sql

# 4. Run Flyway migrations (to catch any pending migrations)
docker compose run --rm migrate migrate

# 5. Restart application
docker compose start backend frontend
```

#### Point-in-Time Recovery (PITR)
If using managed PostgreSQL with WAL archiving:
1. Use provider console/cli to initiate PITR to a new instance
2. Update `DB_HOST` in `.env` to point to recovered instance
3. Run `docker compose run --rm migrate migrate`
4. Verify application health
5. Switch DNS/traffic to recovered instance

### 1.4 Backup Verification

**Monthly**: Test restore to a staging environment
```bash
# Create test database
docker run --name postgres-test -e POSTGRES_PASSWORD=test -d postgres:16

# Restore backup
gunzip -c backup_20240115_030000.sql.gz | docker exec -i postgres-test psql -U postgres -d postgres

# Run migrations
docker run --rm -v $(pwd)/database/migrations:/flyway/sql flyway/flyway:10.22-alpine \
  -url=jdbc:postgresql://postgres-test:5432/postgres \
  -user=postgres -password=test migrate

# Verify table counts
docker exec postgres-test psql -U postgres -d postgres -c "
  SELECT schemaname, relname, n_live_tup
  FROM pg_stat_user_tables
  ORDER BY n_live_tup DESC;"
```

### 1.5 Backup Retention Policy

| Backup Type | Retention | Storage |
|-------------|-----------|---------|
| Daily full | 30 days | Off-site / S3 / GCS |
| Weekly full | 12 weeks | Off-site / S3 / GCS |
| Monthly full | 12 months | Off-site / S3 / GCS (archive tier) |
| Pre-migration snapshot | Until next migration + 1 week | Local + Off-site |

---

## 2. Redis Persistence Policy

### 2.1 Configuration (cache/redis.conf)

```conf
# Persistence: AOF (Append-Only File) - recommended for rate-limiting data
appendonly yes
appendfsync everysec        # Balance of durability and performance
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# RDB snapshots (backup only, not primary persistence)
save 900 1
save 300 10
save 60 10000

# Memory management
maxmemory 256mb
maxmemory-policy allkeys-lru

# Security
requirepass ${REDIS_PASSWORD}  # Set in .env.production
```

### 2.2 What is Stored in Redis

| Key Pattern | TTL | Purpose | Recovery Impact |
|-------------|-----|---------|-----------------|
| `medos:login:fail:{username}:{peer}` | 15 min | Login brute-force, per source (5 failures → lockout) | Low - resets on restart |
| `medos:login:fail:any:{username}` | 15 min | Login brute-force, username-wide (20 failures → lockout) | Low - resets on restart |
| `idempotency:{tenant}:{user}:{endpoint}:{key}` | 24h completed, 2m in-flight | Replay protection for `/billing/payments`, `/billing/invoices`, `/pharmacy/dispense` | **Medium - a lost key permits a replayed request** |

Two login counters per username. `{peer}` is the **socket** address, never a
`X-Forwarded-For` value — behind Caddy that is always the proxy, so the per-source
counter no longer separates one client from another, and the username-wide one is
what bounds a distributed attacker.

⚠️ The username-wide counter is **also a lockout lever**: anyone who knows a
username can push it over and deny that account access for the window. That is
why its ceiling is 20 rather than 5, and why the per-source counter still fires
at 5 for anyone from a single address. Tuning it lower re-opens the
unlimited-guess hole; tuning it higher re-opens the denial of service.

Note the login keys are keyed by **username and peer**, not IP alone — a shared
NAT outage locks out per-account, not per-network.

That is the whole list. Three things this table used to claim and that **do not
exist** in the code, so must not be relied on during an incident:

- ❌ `ratelimit:api:{ip}` — there is **no general API rate limiter**. Only login
  is rate limited.
- ❌ `session:{token}` — there is **no session store and no JWT blacklist**. No
  logout endpoint exists; sessions are stateless and end when the 10-hour token
  expires. Rotating `JWT_SECRET` is the only way to invalidate outstanding tokens.
- ❌ `websocket:session:{id}` — WebSocket auth is validated on STOMP `CONNECT`
  from the token; no server-side session is kept.

Note the login key is keyed by **username *and* IP**, not IP alone — a shared NAT
outage locks out per-account, not per-network.

### 2.3 Redis Backup Strategy

**AOF file** is the primary persistence mechanism. Back up the AOF file:
```bash
# Backup AOF (run on Redis host)
docker compose exec redis cp /data/appendonly.aof /backup/appendonly_$(date +%Y%m%d_%H%M%S).aof

# Or use Redis BGREWRITEAOF to compact first
docker compose exec redis redis-cli BGREWRITEAOF
```

**RDB snapshots** are secondary. Copy dump.rdb for point-in-time recovery.

### 2.4 Redis Restore

```bash
# 1. Stop Redis
docker compose stop redis

# 2. Replace AOF/RDB files
docker compose run --rm -v $(pwd)/backup:/backup redis \
  cp /backup/appendonly_20240115_030000.aof /data/appendonly.aof

# 3. Start Redis
docker compose start redis

# 4. Verify
docker compose exec redis redis-cli INFO persistence
```

### 2.5 Redis High Availability (Production)

For production, consider:
- **Redis Sentinel**: Automatic failover (3+ nodes)
- **Redis Cluster**: Sharding + HA (6+ nodes)
- **Managed Redis**: AWS ElastiCache, Azure Cache for Redis, Google Memorystore

---

## 3. Application Deployment

The production descriptor is `docker-compose.prod.yml`. It differs from the
development compose in ways that matter operationally — see the header of that
file and `docs/adr/0008-supabase-managed-postgres-auth-stays-inhouse.md`.

### One-time host setup

1. **Create the Supabase project** in the **specific** region `ap-south-1`
   (Mumbai). A general "Asia Pacific" group may deploy outside India, and the
   project region cannot be changed after creation.
2. **Use the pooler connection string** (port 6543, `sslmode=require`) from the
   project dashboard, not the direct database port.
3. **Point DNS** at this host for the intended hostname, on ports 80 and 443.
   Caddy obtains a publicly-trusted certificate over ACME. There is no
   self-signed fallback.
4. **Create the environment file:** `cp .env.prod.example .env`, then fill in
   `SUPABASE_POOLER_URL`, `DB_USER` and `MEDOS_DOMAIN`.
5. **Populate the secret files** in `SECRETS_DIR` from the cloud secret manager
   (ADR-0004): `db-password.txt`, `redis-password.txt`, `jwt-secret.txt`,
   `pii-encryption-key.txt`, `cors-origins.txt`.
   Generate keys with `openssl rand -base64 48` and `openssl rand -base64 32`.
6. **Validate before deploying:** `docker compose -f docker-compose.prod.yml config --quiet`

> **The PII encryption key can be rotated.** It is the *key-encryption key*
> (KEK), not the data key: it wraps a per-tenant DEK, so rotating it rewrites
> `tenant_keys` rows only and never touches a patient record. The blind-index key
> is separately wrapped, so patient-name search keeps working. Follow
> **§7d PII key rotation** below — dry-run first, then apply, then verify. Issue
> #73 was the committed-credentials purge, not key rotation.
> Also available: rotating an *individual tenant's* DEK (`dek_rotation`) —
> **§7e DEK rotation** below. The tenant's superseded DEK generation is archived,
> so ciphertext already written stays readable; no re-encryption job is required
> to rotate (a backfill that rewrites rows to the current generation is a separate,
> optional follow-up).

### Deploying

```bash
docker compose -f docker-compose.prod.yml build --pull
docker compose -f docker-compose.prod.yml up -d

# Flyway runs to completion before the backend starts.
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs migrate
```

Startup order is enforced: `migrate` completes, then `redis` becomes healthy, then
`backend`, then `frontend`, and Caddy proxies to the frontend. A failed migration
stops the deploy rather than starting the application against a stale schema.

### Post-deployment checks

- [ ] `docker compose -f docker-compose.prod.yml ps` — every service healthy or running
- [ ] `curl -fsS https://$MEDOS_DOMAIN/manage/health` returns UP
- [ ] Certificate is publicly trusted: `echo | openssl s_client -connect $MEDOS_DOMAIN:443 2>/dev/null | openssl x509 -noout -issuer`
- [ ] Sign in as each of the **seven** roles (`admin`, `doctor`, `nurse`,
      `receptionist`, `pharmacist`, `billing`, `super_admin`) and confirm the
      expected access — `super_admin` most of all, since it is the highest
      privilege and is easy to leave untested
- [ ] Confirm the database holds no in-host copy: the descriptor has no `db` service
- [ ] **Change the bootstrap admin password**, then clear `BOOTSTRAP_ADMIN_PASSWORD` from the secret manager
- [ ] Confirm no secret appears in `docker compose -f docker-compose.prod.yml logs`

### Notes

- **No rolling deploy.** ADR-0001 accepts a brief restart on redeploy. `--scale
  backend=2` is not a valid strategy here: the backend is not directly reachable,
  and two instances would not share a session.
- **Logs are rotated** (`max-size: 10m`, `max-file: 3`). An unbounded log fills the
  disk on a single host, which takes the application down.
- **Caddy certificate state is in a named volume.** Removing that volume makes
  Caddy re-request a certificate on the next boot and can hit issuer rate limits.

## 4. Rollback Procedures

### Application Rollback (Code Only)
```bash
# Redeploy previous image tag
docker compose --env-file .env.production up -d --build \
  --pull always backend:v1.2.3
```

### Database Rollback (Migrations)
> **Flyway migrations are forward-only.** Never edit applied migrations.

To rollback a schema change:
1. Create a new migration `V{n+1}__rollback_previous_change.sql` that reverses the change
2. Deploy the new migration
3. If data was lost, restore from backup (Section 1.3)

---

## 5. Health Check Endpoints

| Endpoint | Purpose | Expected Response |
|----------|---------|-------------------|
| `GET /manage/health` | Liveness (K8s livenessProbe) | 200 UP |
| `GET /manage/health/readiness` | Readiness (K8s readinessProbe) | 200 UP (DB + Redis connected) |
| `GET /manage/health/liveness` | Liveness (K8s livenessProbe) | 200 UP |
| `GET /manage/info` | Build/info | 200 JSON |

---

## 6. Monitoring & Alerting

### Key Metrics to Alert On

| Metric | Warning | Critical |
|--------|---------|----------|
| DB connections used / max | > 70% | > 90% |
| Redis memory used / max | > 70% | > 90% |
| API error rate (5xx) | > 1% | > 5% |
| API latency (p95) | > 500ms | > 2s |
| Disk usage (DB volume) | > 70% | > 85% |
| Backup age | > 25 hours | > 48 hours |

### Log Aggregation
- Ship application logs to ELK/Loki/Datadog
- Key log patterns to alert:
  - `ERROR` level from `com.medos`
  - `BusinessException` rate spikes
  - `LoginRateLimiter` lockout events

---

## 7. Security Operations

### Secret Rotation
| Secret | Rotation Frequency | Procedure |
|--------|-------------------|-----------|
| `JWT_SECRET` | 90 days | Generate new, deploy, invalidate all sessions |
| `DB_PASSWORD` | 90 days | Update in managed DB, update `.env`, redeploy |
| `REDIS_PASSWORD` | 90 days | Update Redis, update `.env`, redeploy |
| `PII_ENCRYPTION_KEY` | 180 days | **KEK re-wrap — no data migration.** See §7d and §7d.1 below |
| `BOOTSTRAP_ADMIN_PASSWORD` | One-time | Remove after first admin login |

### PII Encryption Key Rotation

`PII_ENCRYPTION_KEY` is the **key-encryption key (KEK)**. It wraps a per-tenant
DEK; it does not encrypt patient data directly. Rotating it therefore re-wraps
key material in `tenant_keys` and **leaves every ciphertext column untouched** —
there is no re-encryption job, and none is needed.

> ⚠️ **There are no `encrypt()` / `decrypt()` SQL functions in this schema.** An
> earlier revision of this runbook described a rotation as
> `UPDATE patients SET name = encrypt(decrypt(name, old_key), new_key);`.
> Those functions do not exist in any migration, the procedure could not have
> run, and it contradicted §7d of this same document. Do not reconstruct it.

The real procedure is **§7d PII key rotation** (dry-run → apply → verify). For
the *data key itself*, see **§7e DEK rotation** — the tenant's archived DEK
generations keep superseded ciphertext readable, so a rotation is online there
too, and there is still no encrypt()/decrypt() SQL anywhere in this schema.

### Certificate Management
- TLS termination at Caddy (the sole terminator in `docker-compose.prod.yml`; no
  load balancer or gateway sits in front of it)
- Certificate issuance and renewal: automatic via Let's Encrypt / ACME
- HSTS: **already set** by `caddy/Caddyfile`
  (`Strict-Transport-Security "max-age=31536000; includeSubDomains"`). Do **not**
  add an HSTS header to `frontend/nginx.conf` — Caddy terminates TLS and the
  browser only ever sees Caddy's response. A second header there is dead config.
- Not set anywhere: `Content-Security-Policy`, `X-Frame-Options`,
  `Permissions-Policy`. Tracked as open gaps.

---

## 7b. Idempotency Keys

### Overview
Idempotency keys prevent duplicate operations for critical financial endpoints:
- `POST /api/billing/payments`
- `POST /api/billing/invoices`
- `POST /api/pharmacy/dispense`

### Usage

**Client Request:**
```http
POST /api/billing/payments
Idempotency-Key: unique-client-generated-uuid
Content-Type: application/json

{ "invoiceId": "...", "amount": 1000, "paymentMethod": "CASH" }
```

**Response (first request):**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{ "paymentNumber": "PAY-123456", ... }
```

**Response (duplicate request with same key):**
```http
HTTP/1.1 200 OK
Idempotency-Key-Replayed: true
Content-Type: application/json

{ "paymentNumber": "PAY-123456", ... }
```

### Configuration
- Keys stored in Redis with 24-hour TTL
- Key format: `idempotency:{endpoint}:{key}`
- Automatically cleaned up after TTL expiry

### Testing
```bash
# First request
curl -X POST http://localhost:8080/api/billing/payments \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: test-key-123" \
  -H "Content-Type: application/json" \
  -d '{"invoiceId":"...","amount":100,"paymentMethod":"CASH"}'

# Duplicate request - returns cached response
curl -X POST http://localhost:8080/api/billing/payments \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: test-key-123" \
  -H "Content-Type: application/json" \
  -d '{"invoiceId":"...","amount":100,"paymentMethod":"CASH"}'
```

### Operations Notes
- Keys are scoped to tenant **and** user. The full key is
  `idempotency:{tenantId}:{userId}:{endpoint}:{key}` (a platform session with no
  tenant reads as `platform`). Two hospitals choosing the same client key are
  independent, which is what stops one receiving another's cached payment.
- Completed keys expire after **24 hours**. An **in-flight** claim expires after
  **2 minutes**, so a crashed or killed holder does not strand a client's key.
- A concurrent request that arrives while another holds the claim gets **409**
  with a retry message. It must not fall through and execute.
- A replay returns the **original status code**, not always 200. `Idempotency-Key-Replayed: true`
  distinguishes it from a fresh call.
- A failed (non-2xx) request **releases** its claim, so the client can retry.
- Monitor Redis memory for key accumulation.
- If Redis is unavailable, requests without idempotency keys will be rejected for configured endpoints
- To manually clear a key, use its full scoped form, e.g.
  `redis-cli DEL "idempotency:<tenantId>:<userId>:billing/payments:test-key-123"`

---

## 7c. Certificate Management

---

## 7d. Rotating the PII Encryption Key (KEK)

Patient records are encrypted per tenant with a data key (DEK), and every DEK is itself
wrapped by a **key-encryption key (KEK)**. Rotating the KEK re-wraps those DEKs and
**rewrites no patient data at all** — the ciphertext does not change, only the wrapper
around the key that encrypted it. That is what makes this an online operation with no
maintenance window, and it is why rollback is total.

Requires: shell access to the host, the current `PII_ENCRYPTION_KEY`, and a maintenance
window you do not need.

### Modes

The rotation is a runner on the backend. It does nothing unless asked.

Modes are **environment variables**, not command-line flags. `docker compose run SERVICE
--flag` replaces the container command instead of appending to it, so argv flags never
reach the JVM; and a key on a command line is visible in `docker inspect` and in the
host's process list.

| Environment | Effect |
|---|---|
| none set | Ordinary boot. Reports a dry run to the log, writes nothing, keeps serving. |
| `MEDOS_KEY_ROTATION_MODE=dry-run` | **Dry run**, then exits. Reports tenant count, tenants to re-wrap, and any tenant that cannot unwrap. |
| `MEDOS_KEY_ROTATION_MODE=apply` plus `MEDOS_KEY_ROTATION_NEW_KEK`, `MEDOS_KEY_ROTATION_TARGET_VERSION` | Re-wraps, reads it back, then exits. |
| `MEDOS_KEY_ROTATION_MODE=verify` | Reads every tenant with the KEK this process booted with, fails loudly if any cannot unwrap, then exits. |
| `MEDOS_KEY_ROTATION_MODE=dek-rotate` plus `MEDOS_KEY_ROTATION_TENANT_ID`, `MEDOS_KEY_ROTATION_INITIATED_BY` | Rotates **one named tenant's** DEK (`dek_rotation`, §7e), then exits. |

`MEDOS_KEY_ROTATION_INITIATED_BY` is recorded on the `key_rotations` row — always set it,
it is the DPDP §8(5) evidence trail. An unrecognised mode fails the boot rather than
being read as "no mode requested".

### Procedure

> ⚠️ **Every command below must carry `-f docker-compose.prod.yml`.** Without it,
> `docker compose` targets the **development** stack — a different database, and
> in the prod file a different service graph. An earlier revision of this runbook
> omitted the flag throughout; running it verbatim would have rotated the key
> against the dev database and left production untouched.

```bash
PROD="-f docker-compose.prod.yml"

# 0. Record the current state. You need this to roll back.
docker compose $PROD exec backend printenv PII_ENCRYPTION_KEY > /secure/kek-v1.b64
chmod 600 /secure/kek-v1.b64

# 1. Dry run. Read the output. If it says ABORT, stop and fix that tenant first.
docker compose $PROD run --rm -e MEDOS_KEY_ROTATION_MODE=dry-run \
  -e MEDOS_KEY_ROTATION_TARGET_VERSION=2 backend

# 2. Generate the new KEK. Never overwrite v1.
openssl rand -base64 32 > /secure/kek-v2.b64
chmod 600 /secure/kek-v2.b64

# 3. Re-wrap. Safe to re-run: tenants already at the target version are skipped, so an
#    interrupted run finishes by being run again.
docker compose $PROD run --rm \
  -e MEDOS_KEY_ROTATION_MODE=apply \
  -e MEDOS_KEY_ROTATION_NEW_KEK="$(cat /secure/kek-v2.b64)" \
  -e MEDOS_KEY_ROTATION_TARGET_VERSION=2 \
  -e MEDOS_KEY_ROTATION_INITIATED_BY="ops@example.test" backend

# 4. Install v2 and restart.
docker compose $PROD up -d --force-recreate backend

# 5. Verify against the running application. Do not skip this.
docker compose $PROD run --rm -e MEDOS_KEY_ROTATION_MODE=verify backend

# 6. Only now, retire v1. Not before step 5 passes for every tenant.
```

### What `verify` does and does not cover

`verify` unwraps each tenant's **DEK** (`wrapped_dek`) with the KEK the process
booted with, and names any tenant that fails. It does **not** check
`wrapped_bi_key` — the separately wrapped blind-index key — even though the
re-wrap in step 3 rewrites both.

So a clean `verify` proves patient data is readable; it does **not** prove
patient-**name search** still works. After step 5, exercise a name search in the
running application (or `GET /api/v1/patients?search=…` for a known name) before
retiring v1. If that fails, v1 is still on disk: restore it and re-run.

### Hard rules

1. **Never delete the old KEK version first.** Keeping v1 is what makes this reversible.
2. **Keep v1 until `verify` has passed for every tenant** after a restart, *and* a
   name search has been exercised. Not before.
3. **Restore-and-restart is a total rollback**, because no data was rewritten. To roll
   back: put `kek-v1.b64` back, `docker compose $PROD up -d --force-recreate backend`,
   then re-run `verify`. There is nothing to restore in the database.
4. **Do not retire v1 while any tenant still fails `verify`.** The runner will tell you
   which tenants; it will not guess.

### Verifying it actually worked

`key_rotations` holds one row per run, and the row is the evidence:

```sql
SELECT id, operation, kek_version, status, rows_rewritten,
       initiated_by, started_at, completed_at
FROM key_rotations
WHERE operation = 'kek_rewrap'
ORDER BY started_at DESC
LIMIT 5;
```

A completed rotation has `status = 'completed'` and `rows_rewritten` equal to the number
of tenants the dry run reported as pending. An interrupted run stays `in_progress` —
re-run `--apply` to complete it.

### What a KEK rotation is **not**

**This procedure is not a DEK rotation.** §7d changes only the wrapper; the
per-tenant DEK underneath is untouched. A DEK rotation changes the data key
itself (**§7e**) and every row written before it is protected by a generation the
tenant no longer uses for new writes — restoring a KEK file does not undo a DEK
rotation, because the audit row in `key_rotations` records that the data key
changed. The difference: after a KEK re-wrap nothing about ciphertext changed but
its wrapper; after a DEK rotation *new writes* use different bytes. Both keep
superseded material readable, so neither is destructive; but the rollback stories
differ, and only §7d has restore-and-restart.

---

## §7e Per-tenant DEK rotation (`dek_rotation`)

A KEK re-wrap protects against a compromised *wrapper* and never touches the data
key. Only a **DEK rotation** responds to the data key itself being suspected — and
that response is per tenant by design: rotate one hospital, not the whole fleet.

### What it actually does (nothing is destroyed)

1. The tenant's current generation-N wrapped DEK is **archived** into
   `tenant_dek_history` (V6) — it is the evidence that generation-N ciphertext
   stays readable, and it never leaves the database unwrapped.
2. New DEK bytes are generated at generation N+1, wrapped under the **current
   KEK**, and written to the tenant's `tenant_keys` row.
3. `<gen>` in the ciphertext format `kv1.d<gen>:…` names the DEK generation, so
   rows written before the rotation keep decrypting via the archive — the
   rotation is online, with no re-encryption window and no downtime.
   (A backfill that rewrites old rows to the current generation is an optional
   follow-up, not a prerequisite.)
4. Every step is recorded on the `key_rotations` row (`operation='dek_rotation'`):
   who instructed it (`initiated_by`), when, and the target generation — the
   DPDP §8(5) trail for "the data key changed, on whose authority".

Known limitations, stated plainly:

- **No distributed lock.** Run one rotation at a time; concurrent rotations of
  the same tenant would each produce a new generation (harmless but confusing).
- **No bulk mode.** There is deliberately no "rotate every tenant" wildcard; pass
  production through per tenant.
- **Writes within the TTL window stay labelled correctly** because a generation
  label and its key pair are read together from the store — this is a designed
  invariant, not luck (`TenantDekRotationTest` pins it).

### Procedure

```bash
PROD="-f docker-compose.prod.yml"

# 1. Confirm the tenant exists and note its current generation.
TENANT_ID="<uuid>"
docker compose $PROD exec db psql -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT tenant_id, dek_generation, wrapped_kek_version FROM tenant_keys WHERE tenant_id = '$TENANT_ID';"

# 2. Rotate. The mode names the tenant AND the operator; both are mandatory.
docker compose $PROD run --rm \
  -e MEDOS_KEY_ROTATION_MODE=dek-rotate \
  -e MEDOS_KEY_ROTATION_TENANT_ID="$TENANT_ID" \
  -e MEDOS_KEY_ROTATION_INITIATED_BY="ops@example.test" backend

# 3. Prove the old generation still decrypts: read a patient written before the
#    rotation (name search exercises both the DEK and the blind-index key).
# 4. New writes now carry the next generation's prefix:
docker compose $PROD exec db psql -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT left(name, 7) FROM patients WHERE tenant_id = '$TENANT_ID' LIMIT 1;"
-- new rows read kv1.d2:… (or higher); pre-rotation rows still read kv1.d1:… and are correct as-is.
```

### Evidence

```sql
SELECT id, tenant_id, dek_generation, status, initiated_by, started_at
FROM key_rotations
WHERE operation = 'dek_rotation'
ORDER BY started_at DESC
LIMIT 5;
```

### Hard rules

1. **`MEDOS_KEY_ROTATION_INITIATED_BY` is mandatory** — the runner refuses a DEK
   rotation without it. An unnamed operator is no evidence for a data-key change.
2. **One tenant per run, one run at a time.** The runner refuses tenants with no
   `tenant_keys` row — if it looks "unknown", check the UUID, do not broaden the
   command.
3. **The archive is retention, not backup.** `tenant_dek_history` wipes with its
   tenant (FK `ON DELETE CASCADE`) and holds wrapped material only. The point of
   a *verified backup* stands on its own.
4. **Run the KEK re-wrap (§7d) after rotating if the fleet's KEK lags**: archived
   material stays wrapped under the KEK version current *at archive time*, and a
   re-wrap migrates it along with everything else — never leave it behind.

---

## 8. Incident Response

### Database Connection Exhaustion
1. Check `pg_stat_activity` for long-running queries
2. Kill idle transactions: `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state = 'idle in transaction' AND state_change < now() - interval '5 minutes';`
3. Increase `max_pool_size` in HikariCP (requires restart)

### Redis OOM
1. Check `INFO memory` for used/human
2. Increase `maxmemory` in redis.conf
3. Review `maxmemory-policy` - `allkeys-lru` is safe for cache/rate-limit

### Migration Stuck
1. Check `flyway_schema_history` for pending migration
2. If checksum mismatch: investigate, create compensating migration
3. Never run `flyway repair` in production without DBA approval

---

## Appendix: Useful One-Liners

```bash
# Check DB size
docker compose exec db psql -U "$DB_USER" -d "$DB_NAME" -c "SELECT pg_size_pretty(pg_database_size(current_database()));"

# Check table sizes
docker compose exec db psql -U "$DB_USER" -d "$DB_NAME" -c "
  SELECT relname, pg_size_pretty(pg_total_relation_size(relid))
  FROM pg_catalog.pg_statio_user_tables
  ORDER BY pg_total_relation_size(relid) DESC LIMIT 20;"

# Check active connections
docker compose exec db psql -U "$DB_USER" -d "$DB_NAME" -c "
  SELECT count(*), state FROM pg_stat_activity GROUP BY state;"

# Check Flyway history
docker compose exec db psql -U "$DB_USER" -d "$DB_NAME" -c "
  SELECT installed_rank, version, description, type, installed_on, success
  FROM flyway_schema_history ORDER BY installed_rank;"

# Redis info
docker compose exec redis redis-cli INFO memory
docker compose exec redis redis-cli INFO persistence
```
## Observability (Wave 0)

### Correlation IDs

Every HTTP response carries `X-Request-Id`; the edge may supply its own and it
is echoed if it matches `[A-Za-z0-9._-]{1,64}` (unsafe ids are replaced, so a
client cannot forge log lines). The same id prefixes every log line as
`[req:<id>]` for the duration of the request; grep a request's full trace with:

```bash
docker compose logs backend | grep "req:<id>"
```

### Metrics

`/manage/prometheus` (unauthenticated, private compose network only — no PII in
the scrape body, JVM and request-handler counters only). Point Prometheus at
the backend container on `medos-private`; do not route it through the edge.

### Structured logs

Default log format is human-readable text. Run the backend container with
`json-logs` in `SPRING_PROFILES_ACTIVE` for one-JSON-object-per-line
(LogstashEncoder; includes the `requestId` from MDC) — useful for Loki/ECS
shipping. Toggle without redeploying code.
