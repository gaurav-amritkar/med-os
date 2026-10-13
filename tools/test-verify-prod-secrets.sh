#!/usr/bin/env bash
# Tests for tools/verify-prod-secrets.sh.
#
# A gate that silently stops detecting is worse than no gate, because CI stays
# green and the operator gets false assurance. #137 is the proof: the missing
# BOOTSTRAP_ADMIN_PASSWORD mount survived a green CI for as long as it existed.
#
# Each case builds a throwaway repository, mutates it, and asserts the gate's
# exit code — including the case where the gate MUST fail, so a future edit that
# silently disables the check is caught here rather than in an incident.
#
# Usage: tools/test-verify-prod-secrets.sh

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GATE="$REPO_ROOT/tools/verify-prod-secrets.sh"

pass=0
fail=0

# A minimal repo the gate accepts: an entrypoint reading two secrets, both
# declared and mounted. Echoes the temp directory.
make_repo() {
    local dir
    dir="$(mktemp -d)"
    mkdir -p "$dir/backend" "$dir/cache" "$dir/tools"

    cat > "$dir/backend/entrypoint.sh" <<'EOF'
#!/bin/sh
if [ -f /run/secrets/jwt-secret ]; then
    export JWT_SECRET=$(cat /run/secrets/jwt-secret)
fi
if [ -f /run/secrets/bootstrap-admin-password ]; then
    export BOOTSTRAP_ADMIN_PASSWORD=$(cat /run/secrets/bootstrap-admin-password)
fi
exec "$@"
EOF

    cat > "$dir/cache/entrypoint.sh" <<'EOF'
#!/bin/sh
exec redis-server --requirepass "$(cat /run/secrets/redis-password)"
EOF

    cat > "$dir/docker-compose.prod.yml" <<'EOF'
services:
  backend:
    image: medos-backend
    secrets: [db-password, jwt-secret, bootstrap-admin-password]
  redis:
    image: redis
    secrets: [redis-password]
secrets:
  db-password:
    file: ./secrets/db-password.txt
  jwt-secret:
    file: ./secrets/jwt-secret.txt
  bootstrap-admin-password:
    file: ./secrets/bootstrap-admin-password.txt
  redis-password:
    file: ./secrets/redis-password.txt
EOF

    cp "$GATE" "$dir/tools/verify-prod-secrets.sh"
    chmod +x "$dir/tools/verify-prod-secrets.sh"
    echo "$dir"
}

expect_exit() {
    local label="$1" want="$2" dir="$3"
    local out rc
    out="$("$dir/tools/verify-prod-secrets.sh" 2>&1)"
    rc=$?
    if [ "$rc" -eq "$want" ]; then
        pass=$((pass + 1))
        printf 'ok   %s\n' "$label"
    else
        fail=$((fail + 1))
        printf 'FAIL %s (expected exit %s, got %s)\n%s\n' \
            "$label" "$want" "$rc" "$out"
    fi
    rm -rf "$dir"
}

# 1. A correctly wired repo passes.
expect_exit "correctly mounted secrets pass" 0 "$(make_repo)"

# 2. The #137 case: declared but not mounted by the service. This is the
#    regression that motivated the gate, so it must fail.
d="$(make_repo)"
sed -i '' 's/secrets: \[db-password, jwt-secret, bootstrap-admin-password\]/secrets: [db-password, jwt-secret]/' \
    "$d/docker-compose.prod.yml"
expect_exit "declared but unmounted secret fails (#137)" 1 "$d"

# 3. Read by the entrypoint but absent from the compose file entirely.
d="$(make_repo)"
python3 - "$d" <<'PY'
import re, sys, pathlib
p = pathlib.Path(sys.argv[1], "docker-compose.prod.yml")
p.write_text(re.sub(r"  bootstrap-admin-password:\n    file: .*\n", "", p.read_text()))
PY
expect_exit "undeclared secret fails" 1 "$d"

# 4. The secret belongs to the wrong service.
d="$(make_repo)"
sed -i '' 's/secrets: \[redis-password\]/secrets: [redis-password, bootstrap-admin-password]/' \
    "$d/docker-compose.prod.yml"
sed -i '' 's/secrets: \[db-password, jwt-secret, bootstrap-admin-password\]/secrets: [db-password, jwt-secret]/' \
    "$d/docker-compose.prod.yml"
expect_exit "secret mounted only on another service fails" 1 "$d"

# 5. Redis's own read is covered too, so a future redis entrypoint change is
#    not silently out of scope.
d="$(make_repo)"
sed -i '' 's/secrets: \[redis-password\]/secrets: []/' "$d/docker-compose.prod.yml"
expect_exit "uncovered redis secret read fails" 1 "$d"

echo
echo "verify-prod-secrets self-test: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
