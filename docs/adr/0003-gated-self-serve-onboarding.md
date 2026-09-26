# ADR-0003: Gated self-serve onboarding

- **Status:** Accepted
- **Date:** 2026-09-26

## Context

`POST /api/v1/onboarding/register` is `permitAll` and creates a tenant, an active user, and an `admin` membership from the request body. Deployed publicly, any anonymous caller can provision a hospital tenant with full administrative rights, without limit. Self-serve signup is a product requirement: hospitals should be able to onboard without engineering intervention.

## Decision

Retain self-serve signup, gated rather than removed:

- Tenant lifecycle `PENDING → ACTIVE`, with `SUSPENDED` reserved. Registration creates a `PENDING` tenant and issues no token.
- The tenant administrator must verify email via a single-use, expiring, hashed token before any token is issued.
- Captcha on registration and login, complementing the existing 5-attempt / 15-minute lockout.
- Per-tenant rate limiting on all of `/api/v1/**` via a Redis token bucket, plus per-IP limits on authentication and onboarding.
- `medos.onboarding.public-signup` defaults to `false`. Support uses the invite path on day one; open self-serve is a per-environment configuration change.

## Consequences

- The project gains its first external dependency: a transactional email provider. This is also a new PHI-adjacent data transfer and needs a vendor decision (see spec §13.1).
- Real hospitals onboard without an engineering ticket, which is the point.
- Defaulting the flag off means the self-serve path is not exercised by the pilot hospital; Stage 4 must test it explicitly in a staging environment before the flag is enabled in production.
