#!/usr/bin/env bash
# Seeds demo users and patients for LOCAL DEVELOPMENT only.
# NEVER run against a production database.
#
# Usage (from repo root):
#   docker compose up -d                # start the whole stack
#   ./tools/seed-dev.sh                # seeds via the running medos-db container
#
# Local Postgres without Docker:
#   ./tools/seed-dev.sh --local
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$DIR/seed-dev.sql"

# Users are seeded with SQL because a bcrypt hash can be precomputed. Patients cannot be:
# their PII columns hold AES-GCM ciphertext produced with the tenant's DEK, which is
# generated at runtime and never leaves the database. Demo patients are therefore created
# over HTTP, through the only path that encrypts and computes the name index.
#
# Seeding them with SQL left plaintext PII in the table. That was not a visible failure:
# PiiCiphertextFormat deliberately tolerates a value with no version prefix so pre-encryption
# rows can be migrated, so a plaintext row was served back to callers as if it were fine —
# patient names readable in a table meant to hold only ciphertext, and no name index, so
# those patients were also unfindable by name.

seed_patients_api() {
  local base="${SEED_BASE_URL:-http://localhost:${FRONTEND_EXTERNAL_PORT:-8080}}"
  local username="${SEED_USERNAME:-admin}"
  local password="${SEED_PASSWORD:-password}"

  echo "Seeding demo patients through the API at $base ..."
  local token
  token="$(curl -sS -X POST "$base/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

  if [[ -z "$token" ]]; then
    # Not fatal: the SQL part is the part that matters for a fresh database, and an operator
    # seeding before the app is up should not be told the whole thing failed.
    echo "  could not sign in as '$username'; skipping demo patients."
    echo "  start the stack first (docker compose up -d) and re-run to add them."
    return 0
  fi

  local created=0
  local no_consent_uhid=""
  # name|age|gender|phone|email|consent
  while IFS='|' read -r name age gender phone email consent; do
    [[ -z "${name// }" ]] && continue

    # The API refuses to register a patient without DPDP consent, which is a known open
    # defect (#116): consent must not be a precondition of receiving care. To keep a
    # not-yet-consented patient in the demo data, such a row is created WITH consent and
    # then flipped by UHID afterwards — a non-PII column, so it does not reintroduce the
    # plaintext problem this script exists to avoid.
    local send_consent="$consent"
    [[ "$consent" == "false" ]] && send_consent="true"

    local response code uhid
    response="$(curl -sS -w '\n%{http_code}' -X POST "$base/api/v1/patients" \
      -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' \
      -d "{\"name\":\"$name\",\"age\":$age,\"gender\":\"$gender\",\"phone\":\"$phone\",\"email\":\"$email\",\"address\":\"Demo\",\"bloodGroup\":\"\",\"dpdpConsent\":$send_consent}")"
    code="$(printf '%s' "$response" | tail -n1)"
    uhid="$(printf '%s' "$response" | head -n1 | sed -n 's/.*"uhid"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

    if [[ "$code" == "201" || "$code" == "200" ]]; then
      created=$((created + 1))
      if [[ "$consent" == "false" && -n "$uhid" ]]; then
        no_consent_uhid="$uhid"
      fi
    else
      echo "  skipped '$name' (HTTP $code)"
    fi
  done <<'PATIENTS'
Rahul Mehta|34|male|9876543210|rahul@example.test|true
Anita Joshi|28|female|9876543211|anita@example.test|true
Suresh Reddy|62|male|9876543212|suresh@example.test|true
Kavita Nair|45|female|9876543213|kavita@example.test|true
Aman Khan|22|male|9876543214|aman@example.test|false
PATIENTS

  if [[ -n "$no_consent_uhid" ]]; then
    # Not PII, so writing it directly is safe and keeps the demo set covering both consent
    # states. See the note above the loop.
    docker compose exec -T db psql -U "${DB_USER:-medos}" -d "${DB_NAME:-medos}" -q \
      -c "UPDATE patients SET dpdp_consent = FALSE, dpdp_consent_at = NULL WHERE uhid = '$no_consent_uhid';" \
      >/dev/null 2>&1 || echo "  note: could not mark $no_consent_uhid as not-yet-consented"
    echo "  marked $no_consent_uhid as registered without DPDP consent"
  fi

  echo "  created $created demo patient(s), with encrypted PII and a name index."
}

if [[ "${1:-}" == "--local" ]]; then
  DB_NAME="${DB_NAME:-medos}"
  echo "Seeding dev data into local Postgres database '$DB_NAME'..."
  psql -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE"
  echo "Done. Demo users: admin/doctor/nurse/reception/pharmacy/billing — all password 'password'."
  # Needs a reachable API to encrypt, so it only runs in the Docker path.
  echo "Skipping demo patients: --local seeds SQL only. Run the app and use this script without --local."
  exit 0
fi

echo "Seeding dev data into the 'medos-db' container..."
docker compose exec -T db psql -U "${DB_USER:-medos}" -d "${DB_NAME:-medos}" -v ON_ERROR_STOP=1 -f - < "$SQL_FILE"
echo "Done. Demo users: admin/doctor/nurse/reception/pharmacy/billing — all password 'password'."
seed_patients_api