#!/usr/bin/env bash
# Verify that every secret the application reads at runtime is actually mounted.
#
# The failure this exists to prevent: an entrypoint reads /run/secrets/<name>,
# the compose file does not mount it, the fallback branch never fires, and the
# application silently starts without the credential. That happened to
# BOOTSTRAP_ADMIN_PASSWORD (issue #137) — the backend could not start in
# production at all, and nothing in CI noticed because compose validates wiring
# that exists and is silent about wiring that does not.
#
# Reads for a missing file are not errors to a POSIX shell: the -f test just
# goes false and the variable stays unset. Whether that then fails startup or
# falls back to a default is application-specific, which is exactly why the
# omission is invisible.
#
# Usage: tools/verify-prod-secrets.sh
# Exits non-zero and prints the offending names.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="$ROOT/docker-compose.prod.yml"

if ! command -v python3 >/dev/null 2>&1; then
    echo "verify-prod-secrets: python3 is required" >&2
    exit 2
fi

python3 - "$ROOT" "$COMPOSE" <<'PYEOF'
import json
import re
import subprocess
import sys
from pathlib import Path

root, compose_path = Path(sys.argv[1]), Path(sys.argv[2])

SECRET_FILE = re.compile(r"/run/secrets/([A-Za-z0-9._-]+)")

# What each service's entrypoint (or command) actually reads off disk.
readers = {
    "backend": ["backend/entrypoint.sh"],
    "redis": ["cache/entrypoint.sh"],
}

# Files whose contents legitimately reach a service another way.
mounted = {}
problems = []

declared = set()
raw_compose = compose_path.read_text()

# Prefer the resolved model: `docker compose config` is authoritative and already
# rejects a service that mounts a secret it never declared. If docker is
# unavailable, fall back to reading the file — but check declarations too, or the
# fallback quietly accepts a dangling mount, which is the defect this gate
# exists to catch.
model = None
docker_error = None
try:
    out = subprocess.run(
        ["docker", "compose", "-f", str(compose_path), "config", "--format", "json"],
        capture_output=True, text=True, cwd=root,
        env={
            **__import__("os").environ,
            "SUPABASE_POOLER_URL": "jdbc:postgresql://postgres.verify:5432/postgres",
            "DB_USER": "postgres",
            "MEDOS_DOMAIN": "verify.invalid",
            # ACME_EMAIL is required (`:?`) by the caddy service; supplying a
            # placeholder lets `compose config` resolve so the model check —
            # not the textual fallback — is the gate that runs.
            "ACME_EMAIL": "verify@invalid",
            "SECRETS_DIR": str(root / "verify-prod-secrets-tmp"),
        },
    )
    if out.returncode == 0:
        model = json.loads(out.stdout)
        declared = set((model.get("secrets") or {}).keys())
    else:
        docker_error = (out.stderr or out.stdout or "").strip()
except FileNotFoundError:
    pass  # docker absent: use the textual fallback below.

if model is None:
    block = re.search(
        r"^secrets:\n(.*?)(?=^\S|\Z)", raw_compose, re.M | re.S
    )
    if block:
        declared = set(re.findall(r"^  ([A-Za-z0-9._-]+):", block.group(1), re.M))

def mounted_by(service):
    """Secret names the service mounts, from the resolved model when possible."""
    if model is not None and service in model.get("services", {}):
        found = set()
        for entry in model["services"][service].get("secrets", []) or []:
            # `--format json` emits objects; accept a bare string too so this
            # stays robust across compose versions.
            found.add(entry["source"] if isinstance(entry, dict) else str(entry))
        return found
    found = set()
    block = re.search(
        rf"^  {re.escape(service)}:\n(.*?)(?=^  \S|\Z)", raw_compose, re.M | re.S
    )
    if block:
        listing = re.search(r"^\s*secrets:\s*\[(.*?)\]", block.group(1), re.M)
        if listing:
            found = {s.strip() for s in listing.group(1).split(",") if s.strip()}
        else:
            # Long-form `secrets:` list under a service.
            listing = re.search(r"^\s*secrets:\n((?:\s+- .*\n)+)", block.group(1), re.M)
            if listing:
                found = set(re.findall(r"-\s*([A-Za-z0-9._-]+)", listing.group(1)))
    return found

for service, scripts in readers.items():
    for script in scripts:
        path = root / script
        if not path.exists():
            continue
        wanted = set(SECRET_FILE.findall(path.read_text()))
        if not wanted:
            continue

        have = mounted_by(service)
        mounted[service] = wanted & have

        for missing in sorted(wanted - have):
            problems.append((service, script, missing, "is not mounted by this service"))
        for dangling in sorted(have - declared):
            if dangling in wanted:
                problems.append((service, script, dangling, "is mounted but never declared"))

if docker_error and not problems:
    print("verify-prod-secrets: could not resolve the compose model:", file=sys.stderr)
    print(f"  {docker_error}", file=sys.stderr)
    print("  Falling back to a textual check; run `docker compose -f "
          "docker-compose.prod.yml config` for the real error.", file=sys.stderr)
    sys.exit(2)

if problems:
    print("verify-prod-secrets: FAIL — secret wiring does not match runtime reads:",
          file=sys.stderr)
    for service, script, name, why in problems:
        print(f"  {service}: {script} reads /run/secrets/{name}, but {name} {why}",
              file=sys.stderr)
    print(
        "\nA missing secret file is not an error to `sh`: the fallback branch is "
        "simply skipped and the variable stays unset. Declare the secret in the "
        "top-level `secrets:` block and list it in the service's `secrets:`.",
        file=sys.stderr,
    )
    sys.exit(1)

for service, names in sorted(mounted.items()):
    print(f"  {service}: {', '.join(sorted(names))} — read and mounted")
print("verify-prod-secrets: every runtime secret read is mounted")
PYEOF
