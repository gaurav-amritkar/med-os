# Cache Runtime

## Overview

This directory builds the Redis cache image and configures persistence, memory
limits, and optional password authentication from a mounted secret.

## Interface

- `redis.conf` configures Redis persistence, protected mode, a 256 MB memory cap,
  and the `allkeys-lru` eviction policy.
- `entrypoint.sh` starts Redis with the base config, or creates a private runtime
  config with `requirepass` when `/run/secrets/redis-password` exists.
- `Dockerfile` packages the configuration and entrypoint into the Redis image.

## Invariants

- The Redis password is read from `/run/secrets/redis-password`, not passed in the
  Redis process argument list.
- The generated runtime config is restricted by `umask 077` and remains inside the
  container's `/tmp` directory.

## Side Effects

- `redis-server` persists append-only and periodic snapshot data according to
  `redis.conf` and the container's mounted storage configuration.
- When a password secret exists, the entrypoint writes `/tmp/redis-runtime.conf`.

## Verification

- Run: `docker compose config`
