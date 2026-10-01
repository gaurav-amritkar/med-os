#!/usr/bin/env bash
# Flyway deploy gate — fail fast on migration drift.
#
# Two invariants, both of which cause a production incident rather than a tidy
# error message:
#
#   1. An applied migration is immutable. Flyway stores a checksum for every
#      applied version, so editing V1 after it has run in production makes
#      `flyway validate` fail and leaves the database and the repository
#      disagreeing about what the schema is. The correct move is a new V3+.
#      This gate catches the edit in CI, where it is cheap.
#
#   2. `database/migrations/` is the single owner of the schema. ADR-0005 records
#      that this repository once carried three copies of the migration set, and
#      two of them drifted. A copy reappearing anywhere else reintroduces the
#      same failure, so the gate asserts there is none.
#
# A new migration is expected and must be added to the manifest, which is the
# explicit part: `tools/update-migration-manifest.sh`.
#
# Usage:  tools/verify-migrations.sh
# Exits non-zero with an explanation on violation.

set -euo pipefail

cd "$(dirname "$0")/.."

MIGRATION_DIR="database/migrations"
MANIFEST="$MIGRATION_DIR/SHA256SUMS"
status=0

echo "==> 1. Applied migrations must be unmodified"

if [ ! -f "$MANIFEST" ]; then
  echo "    FAIL: $MANIFEST is missing." >&2
  echo "          If this is the first migration, run tools/update-migration-manifest.sh." >&2
  exit 1
fi

if sha256sum --check --strict --quiet "$MANIFEST" 2>/dev/null; then
  echo "    OK: every recorded migration matches its recorded checksum."
else
  echo "    FAIL: a migration file has changed since it was recorded." >&2
  echo "          An applied migration is immutable — Flyway stores its checksum and" >&2
  echo "          will refuse to validate against a database where it has already run." >&2
  echo "          Add a new V3+ migration instead of editing an existing one." >&2
  echo "          If the change is genuinely a new baseline (a database that has never" >&2
  echo "          been migrated), run tools/update-migration-manifest.sh deliberately." >&2
  sha256sum --check --strict "$MANIFEST" 2>&1 | grep -E "FAILED|WARNING" | sed 's/^/      /' >&2
  status=1
fi

# A migration present in the directory but absent from the manifest was added
# without registering it. That is usually an accident — a file staged for a
# future release, or a merge that brought in a new version.
recorded=$(awk '{print $2}' "$MANIFEST" | sed 's#.*/##' | sort)
present=$(cd "$MIGRATION_DIR" && ls -1 V*.sql 2>/dev/null | sort)

unregistered=$(comm -13 <(echo "$recorded") <(echo "$present") || true)
if [ -n "$unregistered" ]; then
  echo "    FAIL: these migrations are not in $MANIFEST:" >&2
  echo "$unregistered" | sed 's/^/      /' >&2
  echo "          Run tools/update-migration-manifest.sh to register them." >&2
  status=1
fi

echo "==> 2. $MIGRATION_DIR must be the only copy of the schema"

# ADR-0005: the schema used to exist in three places. Anything outside the owner
# can drift silently, and the application container and the migration runner
# would then disagree about the schema at deploy time.
strays=$(find . -name 'V[0-9]*__*.sql' -not -path "./$MIGRATION_DIR/*" \
         -not -path './.git/*' -not -path '*/node_modules/*' -not -path '*/target/*' 2>/dev/null || true)
if [ -n "$strays" ]; then
  echo "    FAIL: migration files found outside $MIGRATION_DIR:" >&2
  echo "$strays" | sed 's/^/      /' >&2
  echo "          ADR-0005 makes $MIGRATION_DIR the single owner. Remove the copies," >&2
  echo "          or amend the ADR if the duplication is intentional." >&2
  status=1
else
  echo "    OK: no copies of the migration set exist outside $MIGRATION_DIR."
fi

# The check above can only see the working tree. A migration can also be
# packaged into a build artifact, where `find` will not see it: backend/Dockerfile
# copied database/migrations onto the application classpath, producing
# BOOT-INF/classes/db/migration/V1__*.sql inside the jar. That copy was the one
# the application actually migrated from at runtime, and it drifted silently for
# exactly as long as the build artifact existed.
#
# So inspect build outputs too. Only unpack what is needed, and treat an
# unreadable archive as inconclusive rather than as a failure: a corrupt or
# unsupported jar must not block a deploy on its own.
bundled=$(for archive in $(find . -name '*.jar' -not -path './.git/*' \
                -not -path '*/node_modules/*' 2>/dev/null); do
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "$archive" 2>/dev/null | grep -E '(^|/)db/migration/V[0-9]+__.*\.sql$' \
      | sed "s#^#      $archive: #" || true
  fi
done)

if [ -n "$bundled" ]; then
  echo "    FAIL: migration files are bundled inside build artifacts:" >&2
  echo "$bundled" >&2
  echo "          A packaged copy is invisible to the checks above but is still a" >&2
  echo "          second copy of the schema, and if the application migrates from" >&2
  echo "          its own classpath it can win over $MIGRATION_DIR silently." >&2
  echo "          Remove the COPY that packages the migrations into the build." >&2
  status=1
elif [ -n "$(find . -name '*.jar' -not -path './.git/*' -not -path '*/node_modules/*' 2>/dev/null)" ]; then
  echo "    OK: no build artifact bundles a copy of the migration set."
fi

if [ "$status" -eq 0 ]; then
  echo "==> Migration gate passed."
fi
exit "$status"
