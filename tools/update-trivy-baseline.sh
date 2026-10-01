#!/usr/bin/env bash
# Regenerate .trivyignore from a real scan of the deployable images.
#
# .trivyignore is a ratchet: the CI job fails on any high or critical finding
# that is NOT listed, so this file only acknowledges what already exists. Adding
# an entry to it is a decision to defer a fix, and should be made deliberately
# with a tracking issue — not by running this and committing whatever appears.
#
# Run it when a base image is rebuilt, after a dependency upgrade, or on the
# monthly review. Then read the diff: an entry disappearing means a fix landed,
# and an entry appearing means new debt arrived with an upstream bump.
#
# Usage:  tools/update-trivy-baseline.sh
# Requires: docker, and the deployable images built.

set -euo pipefail

cd "$(dirname "$0")/.."

TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:latest}"
CACHE_DIR="${TRIVY_CACHE_DIR:-/tmp/medos-trivy-cache}"
OUT_DIR="$(mktemp -d)"
trap 'rm -rf "$OUT_DIR"' EXIT

# The images that reach a production host. `db` is deliberately absent: ADR-0008
# moved PostgreSQL to a managed provider, so the in-host database image is never
# deployed. `migrate` and `redis` are included because they do run in production
# even though `migrate` is short-lived.
IMAGES=(
  medos-backend:${MEDOS_TAG:-local}
  medos-frontend:${MEDOS_TAG:-local}
  medos-caddy:${MEDOS_TAG:-local}
  medos-redis:${MEDOS_TAG:-local}
  medos-migrations:${MEDOS_TAG:-local}
)

echo "==> Scanning ${#IMAGES[@]} deployable images with $TRIVY_IMAGE"
for image in "${IMAGES[@]}"; do
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "    missing image: $image — build it first" >&2
    exit 1
  fi
  printf '    %s ... ' "$image"
  docker run --rm \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$CACHE_DIR":/root/.cache/trivy \
    -v "$OUT_DIR":/out \
    "$TRIVY_IMAGE" image --scanners vuln --quiet \
    --format json --output "/out/$(echo "$image" | tr ':' '-').json" "$image" >/dev/null
  echo "done"
done

echo "==> Collapsing findings to distinct high/critical identifiers"
python3 - "$OUT_DIR" > .trivyignore <<'PYTHON'
import collections, json, pathlib, sys, datetime

out = pathlib.Path(sys.argv[1])
findings = collections.defaultdict(set)
total = 0

for report in sorted(out.glob("*.json")):
    stem = report.stem.removeprefix("medos-").removesuffix("-local")
    service = stem
    for result in json.loads(report.read_text()).get("Results", []):
        for vuln in result.get("Vulnerabilities") or []:
            if vuln.get("Severity") in ("CRITICAL", "HIGH") and vuln.get("VulnerabilityID"):
                findings[vuln["VulnerabilityID"]].add(f"{service}/{vuln['Severity'][:4]}")
                total += 1

header = f"""# Trivy ignore file — a ratchet, not an allowlist of comfort.
#
# Generated {datetime.date.today().isoformat()} by tools/update-trivy-baseline.sh
# against the images that actually deploy. The CI job runs Trivy with
# --ignorefile .trivyignore and --exit-code 1, so this file makes the gate
# meaningful rather than permanently red: a NEW high or critical finding fails
# the build, and an entry here only acknowledges one that already existed.
#
# Do not add an ID here without reading the finding. If a fix is available,
# record that on the tracking issue rather than suppressing it here.
#
# Review cadence: monthly, and whenever a base image is rebuilt.
#
# Format: one identifier per line, optionally followed by a comment.
"""

print(header)
for vid in sorted(findings):
    print(f"{vid}  # {', '.join(sorted(findings[vid]))}")
import sys as _s
print(f"# {total} high/critical findings across {len(findings)} identifiers", file=_s.stderr)
_s.stderr.write("==> Read the diff before committing.\n")
PYTHON

echo "==> .trivyignore regenerated. Review the diff, then commit deliberately."
