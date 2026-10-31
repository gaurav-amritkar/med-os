# Observability: Metrics and Structured Logging for MedOS HMS

* **Author:** MedOS team
* **Date:** 2026-10-31

## Overview

Add production observability to MedOS HMS so oncall engineers can triage incidents at 3 AM and developers can debug locally. Two parts:

1. **Prometheus metrics** — request rate, latency, errors, plus custom metrics for encryption and key-rotation operations — exposed on a protected `/manage/metrics` endpoint.
2. **Structured JSON logs** with `X-Request-Id` correlation across the backend.

The existing `RequestCorrelationFilter` already generates and echoes `X-Request-Id` and puts it in MDC. This brief formalizes what to emit, where, and who can see it.

## Analysis

**Contrarian / YAGNI** — Does this feature need to exist?
MedOS is a hospital system handling patient records, billing, and prescriptions. Running production without metrics or structured logs means operating blind. The counter-argument is that the app is still pre-production and a full observability stack may be over-engineering. However, a minimal Prometheus client and JSON log formatter are standard Spring Boot dependencies, not new custom code, so the build cost is low. The stronger counter-argument stands: you cannot safely run a healthcare system in production without knowing error rates or latency by endpoint.

**Cost, Quota & Token Economics** — What is the footprint?
MedOS is an internal hospital system, so peak QPS is low (dozens, not thousands). A Prometheus client adds negligible memory and CPU. Local TSDB storage for metrics is modest. Structured JSON logs are larger than plain text, so log rotation and retention must be configured to prevent disk pressure. No LLM token cost is involved. The main recurring cost is log storage and rotation management.

**UX & Developer Ergonomics** — Is it intuitive?
Adding Micrometer and Logback-Spring JSON logging follows Spring Boot conventions, so developers will recognize the pattern. Configuration goes in `application.properties` or YAML, no new DSL. The friction points: developers must learn to query Prometheus and parse JSON logs, and oncall needs dashboards built before an incident. Error messages become more actionable because every log line carries `X-Request-Id`, letting engineers trace a request from ingress to database.

**Migration & Backward Compatibility** — Does it break anything?
No database schema changes. No API changes. The only behavioral change is log format shifting from plain text to JSON, which could break any existing log parser or tailing habit. Rollback is simple: remove the dependencies and revert `logback-spring.xml`. Metrics endpoint addition is additive and does not affect existing clients.

**Observability & SRE Debuggability** — Is it traceable and actionable?
Key operations already carry `X-Request-Id` via `RequestCorrelationFilter`, but nothing emits it as a metric or structures it for querying. With this change:
- HTTP server metrics (count, duration, status code, endpoint) let oncall spot error spikes.
- Custom metrics for encryption, decryption, key rotation, and tenant scoping surface security-path latency and failures.
- Structured JSON logs let engineers filter by `X-Request-Id`, tenant, user, or endpoint.
- `/manage/metrics` is protected behind the admin role, consistent with the existing OpenAPI docs restriction.
Alerting rules for error-rate spikes, encryption failures, and key-rotation failures are part of this brief and will be wired to a notification channel. Pre-built dashboards remain out of scope.

**Long-Term Tech Debt & Maintenance** — Will it hurt in two years?
Micrometer and Logback-Spring are maintained Spring ecosystem standards, not brittle custom code. Maintenance burden is low: keep dependencies updated, tune log retention, and review cardinality (avoid labeling metrics by high-cardinality values like patient ID). No tight coupling or state machines introduced.

## Goals vs. Non-Goals

**In-Scope Goals**
- Add Micrometer with Prometheus export to the backend.
- Expose `/manage/metrics` restricted to the admin role.
- Emit HTTP server metrics (request count, duration, status, endpoint).
- Emit custom metrics for encryption/decryption, key wrap/unwrap, DEK rotation, and tenant-scoped queries.
- Switch backend logs to structured JSON with `X-Request-Id` in every line.
- Configure log rotation and retention to bound disk use.
- Add alerting rules for error-rate spikes, encryption failures, and key-rotation failures, routed to a notification channel.
- Keep the dev stack (`docker compose up`) working without observability unless opted in.

**Out-of-Scope Non-Goals**
- Grafana dashboards or pre-built visualizations.
- Distributed tracing (OpenTelemetry/Jaeger) beyond `X-Request-Id` correlation.
- Frontend metrics or RUM (real-user monitoring).
- Metrics or logs for the PostgreSQL, Redis, or Caddy containers themselves (infrastructure metrics are a separate concern).
- Changing the public API contract or authentication model.

## Alternatives Weighed

**Option A: Spring Boot Actuator + Micrometer + Logback-Spring JSON (chosen)**
Reason: Standard Spring Boot stack, minimal custom code, admin-role protection already understood by the team, consistent with existing `/manage/health` and `/manage/info` exposure.

**Option B: OpenTelemetry with OTLP exporter and a tracing backend**
Reason rejected: Higher operational complexity (collector, backend, sampling config) for a system with low QPS. Distributed tracing is valuable but out of scope for the first observability step. Can be added later.

**Option C: Do nothing, rely on `RequestCorrelationFilter` and plain-text logs**
Reason rejected: The filter generates `X-Request-Id` but nothing indexes or structures it. Plain-text logs cannot be filtered or alerted on programmatically. Unacceptable for a production healthcare system handling patient data.

## Key Decisions & Staging for Next Steps

1. Metrics endpoint `/manage/metrics` requires the `admin` role, matching the existing OpenAPI/docs restriction.
2. Log format changes to JSON in all non-test profiles; dev can opt in via property.
3. Avoid high-cardinality labels (patient ID, UHID) on metrics to prevent Prometheus cardinality explosion.
4. Custom metrics namespaced under `medos.*` to avoid colliding with Micrometer defaults.
5. Next step after this brief: implement the backend metrics and logging changes, then implement alerting rules and notification routing.
