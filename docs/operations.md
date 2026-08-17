# MedOS Operations Runbook

This document covers backup/restore procedures, Redis persistence policy, and operational tasks for production deployments.

---

## 1. Database Backup & Restore

### 1.1 Automated Backups (Recommended)

For production, use a managed PostgreSQL service (AWS RDS, Cloud SQL, Azure Database) with automated backups enabled:
- **Backup retention**: 7-30 days minimum
- **Point-in-time recovery (PITR)**: Enabled
- **Backup window**: During low-traffic hours (e.g., 03:00-04:00 UTC)

### 1.2 Manual Backup (Docker Compose / Self-Hosted)

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

## 3. Application Deployment Checklist

### Pre-Deployment
- [ ] Run full test suite: `cd backend && mvn test` and `cd frontend && npm test`
- [ ] Build images: `docker compose --env-file .env.production build`
- [ ] Verify migration status: `docker compose run --rm migrate info`
- [ ] Confirm `.env.production` has all required secrets

### Deployment
```bash
# Blue-green style (zero-downtime)
docker compose --env-file .env.production up -d --build --scale backend=2
# Wait for health checks
docker compose --env-file .env.production ps

# Scale down old version (if using blue-green)
docker compose --env-file .env.production up -d --scale backend=1
```

### Post-Deployment
- [ ] Verify `/manage/health` returns UP
- [ ] Verify `/manage/health/readiness` returns UP
- [ ] Test login flow for each role
- [ ] Check application logs for errors
- [ ] Monitor metrics for 10 minutes

---

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