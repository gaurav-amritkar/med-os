# ADR-0007: Redis is non-authoritative

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

Redis backs the response cache and the login lockout counters, with an in-memory fallback for lockouts. `PRODUCTION_READINESS.md` lists "no Redis cluster" as a high risk, implying patient data depends on it. The authoritative state is PostgreSQL: patients, encounters, stock ledgers, invoices, and payments.

## Decision

Declare Redis non-authoritative. It holds cache entries and lockout counters only. A Redis outage costs cache warmth and in-flight lockout state, never clinical or financial data. An AOF persistence policy is documented for the lockout counters' benefit; no Redis cluster for v1.

## Consequences

- The "Redis cluster" and "single point of failure" risk entries are removed from the register as misclassified.
- Losing Redis briefly weakens brute-force protection because lockout counters fall back to per-instance memory; this is accepted and mitigated by captcha and per-IP rate limits.
- Cache correctness must not be assumed: every cached read path needs a fallback to PostgreSQL, and Stage 4's integrity checks run against PostgreSQL only.
