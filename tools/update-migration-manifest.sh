#!/usr/bin/env bash
# Record the checksum of every migration in database/migrations.
#
# Run this only when ADDING a migration, or when deliberately re-baselining a
# database that has never been migrated. Running it after editing an applied
# migration defeats the gate it feeds: Flyway stores the checksum of what it
# actually applied, so agreeing with a rewritten file only moves the failure from
# CI to the first `flyway validate` against production.
#
# Usage:  tools/update-migration-manifest.sh

set -euo pipefail

cd "$(dirname "$0")/.."

MIGRATION_DIR="database/migrations"
MANIFEST="$MIGRATION_DIR/SHA256SUMS"

if [ ! -d "$MIGRATION_DIR" ]; then
  echo "FAIL: $MIGRATION_DIR does not exist" >&2
  exit 1
fi

# Paths are recorded relative to the repository root so that
# `sha256sum --check` works from the root, which is where the gate runs.
( cd "$MIGRATION_DIR" && ls -1 V*.sql 2>/dev/null | sort ) | sed "s#^#$MIGRATION_DIR/#" | xargs sha256sum > "$MANIFEST"

echo "Recorded $(wc -l < "$MANIFEST" | tr -d ' ') migration(s) in $MANIFEST:"
sed 's/^/  /' "$MANIFEST"
echo
echo "Review the diff. An added line is a new migration; a changed line means an"
echo "applied migration was edited, which tools/verify-migrations.sh exists to catch."
