#!/usr/bin/env bash
# Tests for tools/verify-migrations.sh.
#
# The gate exists to catch migration drift, so it needs its own tests: a gate
# that silently stops detecting drift is worse than no gate, because CI stays
# green. Each case builds a throwaway repository in a temp directory and asserts
# the gate's exit code.
#
# Usage:  tools/test-verify-migrations.sh

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATE="$REPO_ROOT/tools/verify-migrations.sh"

pass=0
fail=0

# Build a minimal repository that the gate accepts, then let a caller mutate it.
# Echoes the temp directory path.
make_repo() {
  local dir
  dir="$(mktemp -d)"
  mkdir -p "$dir/database/migrations" "$dir/tools"
  cat > "$dir/database/migrations/V1__initial_schema.sql" <<'SQL'
CREATE TABLE patients (id UUID PRIMARY KEY);
SQL
  # Paths in the manifest are relative to the repository root, matching
  # tools/update-migration-manifest.sh.
  ( cd "$dir" && sha256sum database/migrations/V1__initial_schema.sql \
      > database/migrations/SHA256SUMS )
  cp "$GATE" "$dir/tools/verify-migrations.sh"
  chmod +x "$dir/tools/verify-migrations.sh"
  echo "$dir"
}

# assert_gate <expect: pass|fail> <description> <mutator-function>
assert_gate() {
  local expect="$1" desc="$2" mutate="${3:-}"
  local dir actual status
  dir="$(make_repo)"
  [ -n "$mutate" ] && "$mutate" "$dir"

  ( cd "$dir" && ./tools/verify-migrations.sh >/dev/null 2>&1 )
  status=$?
  rm -rf "$dir"

  case "$expect" in
    pass) want=0 ;;
    fail) want=1 ;;
  esac

  if [ "$status" -eq "$want" ]; then
    pass=$((pass + 1))
    printf '    ok   %s\n' "$desc"
  else
    fail=$((fail + 1))
    printf '    FAIL %s (expected %s, got %s)\n' "$desc" "$expect" "$status"
  fi
  actual="$status"
}

# --- fixtures ---------------------------------------------------------------

# Simulate the real drift: a migration bundled inside a built jar. This is what
# backend/Dockerfile used to do, and what the on-disk `find` in the gate could
# not see. A tiny zip containing the SQL is a faithful stand-in.
bundle_into_jar() {
  local dir="$1"
  mkdir -p "$dir/backend/target/classes/db/migration"
  cp "$dir/database/migrations/V1__initial_schema.sql" \
     "$dir/backend/target/classes/db/migration/"
  ( cd "$dir/backend/target/classes" && zip -qr app.jar db )
}

edit_applied_migration() {
  local dir="$1"
  echo "ALTER TABLE patients ADD COLUMN name_index VARCHAR(64);" \
    >> "$dir/database/migrations/V1__initial_schema.sql"
}

add_unregistered_migration() {
  local dir="$1"
  cat > "$dir/database/migrations/V2__patient_name_blind_index.sql" <<'SQL'
ALTER TABLE patients ADD COLUMN name_index VARCHAR(64);
SQL
}

stray_copy_on_disk() {
  local dir="$1"
  mkdir -p "$dir/backend/src/main/resources/db/migration"
  cp "$dir/database/migrations/V1__initial_schema.sql" \
     "$dir/backend/src/main/resources/db/migration/"
}

echo "==> gate accepts an unmodified repository"
assert_gate pass "clean repository passes"

echo "==> gate rejects drift it can see on disk"
assert_gate fail "edited applied migration is rejected" edit_applied_migration
assert_gate fail "unregistered new migration is rejected" add_unregistered_migration
assert_gate fail "stray copy in the source tree is rejected" stray_copy_on_disk

echo "==> gate rejects a migration bundled inside a built jar"
assert_gate fail "migrations packaged in a jar are rejected" bundle_into_jar

echo
echo "==> $pass passed, $fail failed"
[ "$fail" -eq 0 ]