# ADR-0001: Single cloud host with Docker Compose and managed PostgreSQL

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

MedOS will be sold as multi-tenant SaaS to multiple hospitals. The current deployment is a single `docker-compose.yml` that publishes database and cache ports, declares no volumes, and supplies development credentials under the `prod` profile. `NEXT_STEPS.md` and `PRODUCTION_READINESS.md` reference Kubernetes, Helm, an API gateway, and read replicas, none of which exist.

## Decision

Run the application tier as Docker Compose on one cloud host, fronted by Caddy for TLS, with PostgreSQL as a managed service (RDS or Supabase) reached over a private endpoint. No Kubernetes, no Helm, no in-host database.

## Consequences

- One host is a single point of failure for the application tier; recovery means rebuilding it from documentation, which Stage 1 and §12 verify explicitly.
- Managed PostgreSQL supplies PITR and snapshots, so the 5-minute RPO and 60-minute RTO targets are achievable without operating a database.
- Horizontal scale and rolling zero-downtime deploys are deferred. If one host cannot serve the load, this decision is revisited.
- Compose remains the single artifact that CI validates and that runbooks reference.
