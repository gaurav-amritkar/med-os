# ADR-0006: Explicit non-goals for v1

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

`PRODUCTION_READINESS.md` and `docs/ARCHITECTURE.md` describe a target architecture with an API gateway, multiple backend instances, Redis cluster, PostgreSQL read replicas, RabbitMQ workers, ELK, Jaeger, and Kubernetes with Helm. None exists. The gap is recorded as risk, but most of it is not required to serve paying hospitals safely on the substrate chosen in ADR-0001.

## Decision

Out of scope for v1, recorded here so they stop appearing as open risks:

- Kubernetes, Helm, blue/green and canary releases
- API gateway and WAF
- Redis cluster, PostgreSQL read replicas
- Distributed tracing, centralized logging (ELK/OpenSearch)
- Asynchronous processing and message queues
- Postgres row-level security (see ADR-0002)
- Feature flags for gradual rollout
- Internationalization

## Consequences

- The risk register reflects real v1 exposure rather than an aspirational architecture.
- Each item has a trigger for reconsideration: sustained load beyond one host (Kubernetes), contractual per-tenant rate limits or compliance-mandated routing (gateway), audit or incident-response requirements needing centralized logs (ELK), or notification and audit throughput problems (queues).
- Single-host failure remains the largest accepted risk and is mitigated by a rehearsed rebuild path and a disaster-recovery drill, not by redundancy.
