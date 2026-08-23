#!/bin/bash
# Supabase Option C Sync — Deploy MedOS with Supabase DB + Auth
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.supabase}"
echo "Loading environment from $ENV_FILE"
set -a
source "$ENV_FILE"
set +a

echo "=== Step 1: Verify Supabase DB Connection ==="
export PGPASSWORD="$DB_PASSWORD"
psql -h "db.your-project.supabase.co" -p 5432 -U "$DB_USER" -d "$DB_NAME" -c "SELECT version();" || echo "DB unreachable — check DB_URL in .env.supabase"

echo "=== Step 2: Apply Flyway Migrations to Supabase ==="
cd /Users/sai/Documents/Gaurav/workspace/GitHubProjects/med-os/backend || exit 1
mvn flyway:migrate -Dflyway.url="$DB_URL" -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASSWORD"

echo "=== Step 3: Verify Auth Connection ==="
curl -sf -H "apikey: $SUPABASE_SERVICE_ROLE_KEY" \
  "$SUPABASE_URL/auth/v1/admin/users" || echo "Auth unreachable — check keys"

echo "=== Step 4: Deploy (use docker-compose with .env.supabase) ==="
echo "Run: docker compose --env-file .env.supabase up -d --build"

echo "=== Done ==="
echo "Files required: .env.supabase, backend/, frontend/, database/migrations/"
echo "Removed local DB dependency — Supabase provides PostgreSQL + Auth"
