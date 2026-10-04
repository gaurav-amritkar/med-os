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
docker run --name postgres-test -e POSTGRES_PASSWORD=test -d postgres:15

# Restore backup
gunzip -c backup_20240115_030000.sql.gz | docker exec -i postgres-test psql -U postgres -d postgres

# Run migrations
docker run --rm -v $(pwd)/database/migrations:/flyway/sql flyway/flyway:9 \
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
| `ratelimit:login:{ip}:{username}` | 15 min | Login brute-force protection | Low - resets on restart |
| `ratelimit:api:{ip}` | 1 min | API rate limiting | Low - resets on restart |
| `session:{token}` | 10 hr | JWT token blacklist (logout) | Medium - users re-login |
| `websocket:session:{id}` | Connection | STOMP session tracking | Low - reconnects |

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

> **The PII encryption key has no rotation path.** It encrypts patient names and
> clinical notes, and the codebase has no re-encryption job, so changing it after
> patient data exists makes that data unreadable. Generate it once and treat it as
> permanent. See issue #73.

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
- [ ] Sign in as each of the six roles and confirm the expected access
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
| `PII_ENCRYPTION_KEY` | 180 days | Generate new, re-encrypt all PII fields (see below), deploy |
| `BOOTSTRAP_ADMIN_PASSWORD` | One-time | Remove after first admin login |

### PII Encryption Key Rotation

Rotating the PII encryption key requires re-encrypting all encrypted fields:

1. Generate new key: `openssl rand -base64 32`
2. Run re-encryption script (decrypt with old key, encrypt with new key):
   ```sql
   -- Example for patients table (run for each encrypted column)
   UPDATE patients SET name = encrypt(decrypt(name, old_key), new_key);
   ```
3. Update `PII_ENCRYPTION_KEY` in environment
4. Redeploy application
5. Verify decryption works for all roles

### Certificate Management
- TLS termination at load balancer / reverse proxy (Caddy, Traefik, ALB)
- Certificate renewal: Automated via Let's Encrypt / ACME
- HSTS header: Enable in `frontend/nginx.conf` after TLS verified

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
- Keys are automatically expired after 24 hours
- Monitor Redis memory for idempotency key accumulation
- If Redis is unavailable, requests without idempotency keys will be rejected for configured endpoints
- To manually clear a key: `redis-cli DEL "idempotency:billing/payments:test-key-123"`

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

`MEDOS_KEY_ROTATION_INITIATED_BY` is recorded on the `key_rotations` row — always set it,
it is the DPDP §8(5) evidence trail. An unrecognised mode fails the boot rather than
being read as "no mode requested".

### Procedure

```bash
# 0. Record the current state. You need this to roll back.
docker compose exec backend printenv PII_ENCRYPTION_KEY > /secure/kek-v1.b64
chmod 600 /secure/kek-v1.b64

# 1. Dry run. Read the output. If it says ABORT, stop and fix that tenant first.
docker compose run --rm -e MEDOS_KEY_ROTATION_MODE=dry-run \
  -e MEDOS_KEY_ROTATION_TARGET_VERSION=2 backend

# 2. Generate the new KEK. Never overwrite v1.
openssl rand -base64 32 > /secure/kek-v2.b64
chmod 600 /secure/kek-v2.b64

# 3. Re-wrap. Safe to re-run: tenants already at the target version are skipped, so an
#    interrupted run finishes by being run again.
docker compose run --rm \
  -e MEDOS_KEY_ROTATION_MODE=apply \
  -e MEDOS_KEY_ROTATION_NEW_KEK="$(cat /secure/kek-v2.b64)" \
  -e MEDOS_KEY_ROTATION_TARGET_VERSION=2 \
  -e MEDOS_KEY_ROTATION_INITIATED_BY="ops@example.test" backend

# 4. Install v2 and restart.
docker compose up -d --force-recreate backend

# 5. Verify against the running application. Do not skip this.
docker compose run --rm -e MEDOS_KEY_ROTATION_MODE=verify backend

# 6. Only now, retire v1. Not before step 5 passes for every tenant.
```

### Hard rules

1. **Never delete the old KEK version first.** Keeping v1 is what makes this reversible.
2. **Keep v1 until `--verify` has passed for every tenant** after a restart. Not before.
3. **Restore-and-restart is a total rollback**, because no data was rewritten. To roll
   back: put `kek-v1.b64` back, `docker compose up -d --force-recreate backend`, then
   `--verify`. There is nothing to restore in the database.
4. **Do not retire v1 while any tenant still fails `--verify`.** The runner will tell you
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

**This procedure is not reversible for DEK rotation.** Rotating a *tenant's DEK*
re-encrypts that tenant's patient rows; restoring a key file does not undo it, because
the old DEK is gone. DEK rotation is only for when the DEK itself is suspected
compromised, it is scoped to one hospital, it requires a verified backup first, and it
must be handled as a separate procedure. Do not assume the rollback above applies.

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