#!/usr/bin/env bash
# Seeds demo users/patients for LOCAL DEVELOPMENT only.
# NEVER run against a production database.
#
# Usage (from repo root):
#   docker compose up -d db            # start the DB first
#   ./tools/seed-dev.sh                # seeds via the running medos-db container
#
# Local Postgres without Docker:
#   ./tools/seed-dev.sh --local
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$DIR/seed-dev.sql"

if [[ "${1:-}" == "--local" ]]; then
  DB_NAME="${DB_NAME:-medos}"
  echo "Seeding dev data into local Postgres database '$DB_NAME'..."
  psql -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE"
  echo "Done. Demo users: admin/doctor/nurse/reception/pharmacy/billing — all password 'password'."
  exit 0
fi

echo "Seeding dev data into the 'medos-db' container..."
docker compose exec -T db psql -U "${DB_USER:-postgres}" -d "${DB_NAME:-medos}" -v ON_ERROR_STOP=1 -f - < "$SQL_FILE"
echo "Done. Demo users: admin/doctor/nurse/reception/pharmacy/billing — all password 'password'."
