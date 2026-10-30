# Caddy Edge Proxy

## Overview

This directory builds the production Caddy image. Caddy terminates TLS for the
configured MedOS hostname and proxies application traffic to the frontend, with a
dedicated health route to the backend.

## Interface

- `Caddyfile` configures `{$MEDOS_DOMAIN}`, compression, security headers,
  frontend reverse proxying, and `/manage/health` forwarding to the backend.
- The `:80` site block aborts requests with an unmatched host.
- `Dockerfile` packages the configuration in the `caddy:2-alpine` image and starts
  Caddy with `/etc/caddy/Caddyfile`.

## Invariants

- Caddy is the production TLS terminator and relies on a real `MEDOS_DOMAIN` and
  `ACME_EMAIL` for publicly trusted certificates.
- Unmatched host requests are aborted; there is no internal/self-signed TLS
  fallback.
- Proxy headers carry the host, remote address, forwarded address, and scheme to
  the frontend upstream.

## Side Effects

- Caddy obtains and renews certificates through ACME and stores certificate state
  in its configured data volume.
- Requests are forwarded to `frontend:8080`, except `/manage/health`, which is
  forwarded to `backend:8080`.

## Verification

- Run: `docker compose -f docker-compose.prod.yml config`
