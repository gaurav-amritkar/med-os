# ADR-0004: Cloud secret manager injection at container start

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

`secrets/` is tracked in git with a live JWT secret, PII encryption key, database password, Redis password, and bootstrap admin password. `docker-compose.yml` additionally hardcodes the same values inline. `PRODUCTION_READINESS.md` recommends Vault. The deployment is one host, and `backend/entrypoint.sh` already falls back to reading `/run/secrets/<name>` when the environment variable is absent.

## Decision

Source secrets from the cloud provider's secret manager and inject them into containers at start, using the entrypoint's existing `/run/secrets/*` path. Do not stand up Vault for v1. Rotate every committed value first, then purge `secrets/` from history.

## Consequences

- Rotation is a cloud-API operation, not a file edit on the host, so it is auditable and does not require redeploying an image.
- Vault is unnecessary operational weight for a single host. Its absence means no central secret audit trail across hosts, which is acceptable while there is one host.
- The PII encryption key is the hard case: rotating it requires re-encrypting existing ciphertext, so `NEXT_STEPS.md` item T6 (key-rotation test) stays open until that job exists and has been run.
- The committed values must be treated as compromised regardless of rotation, because git history and any clone retain them.
