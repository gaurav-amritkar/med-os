#!/bin/bash
# DB-Only Supabase Sync — Keeps MedOS JWT Auth, swaps DB
set -euo pipefail

echo "=== DB-Only Supabase Deploy ==="
echo "1. Ensure .env.supabase has DB_PASSWORD set"
echo "2. Apply Flyway migrations to Supabase DB"

ENV_FILE=".env.supabase"
set -a
source "$ENV_FILE"
set +a

export PGPASSWORD="$DB_PASSWORD"

echo "Migrating to Supabase DB..."
cd /Users/sai/Documents/Gaurav/workspace/GitHubProjects/med-os/backend
mvn flyway:migrate \
  -Dflyway.url="jdbc:postgresql://aws-0-ap-northeast-1.pooler.supabase.com:6543/postgres?sslmode=require" \
  -Dflyway.user="postgres.rgqsehyqwbermmqkialm" \
  -Dflyway.password="$DB_PASSWORD"

echo "Migration done. Deploy backend with:"
echo "  SPRING_PROFILES_ACTIVE=supabase docker compose --env-file .env.supabase up -d --build backend"
