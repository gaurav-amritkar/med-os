# ADR-0008: Supabase as the managed PostgreSQL provider; authentication stays in-house

- **Status:** Accepted
- **Date:** 2026-09-30
- **Supersedes:** nothing. Narrows ADR-0001, which permitted "managed PostgreSQL (RDS or Supabase)" without choosing.
- **Related:** ADR-0001 (topology), ADR-0002 (row-level tenancy), ADR-0004 (secrets), ADR-0007 (Redis is non-authoritative)

## Context

ADR-0001 requires PostgreSQL as a managed service reached over a private endpoint,
with no in-host database, and names Supabase as a candidate without choosing
between it and RDS. The choice has been blocked, not deferred: without a decided
database target there is no way to write a production deployment descriptor, so
nothing is deployable. Issue #16 is blocked on exactly this.

Two things are being decided together in this ADR, and they have opposite answers.
The database and the authentication are separable, and separating them is the point.

## Decision

**1. PostgreSQL will be Supabase**, with these constraints:

- **A specific region, not a general group.** `ap-south-1` (Mumbai). Supabase's own
  documentation warns that a general group such as "Asia Pacific" deploys to
  *an available* region that may not be in India. For a system holding patient
  records, the region is a data-location control, not a preference. Supabase also
  states plainly that region selection is "a data-location control, not proof of
  regulatory compliance" — see Consequences.
- **Reached through the connection pooler (Supavisor), over TLS**, not the direct
  database port. This is the "private endpoint" ADR-0001 asked for, and it matters
  for connection count as well as for reachability.
- **No in-host PostgreSQL.** The `db` service exists in the development compose and
  is absent from production. Backups, PITR and snapshots are the provider's
  responsibility, which is what makes the 5-minute RPO and 60-minute RTO targets
  achievable without operating a database.
- **Redis stays in-host** and unpublished. It is a cache and a rate-limit counter,
  never authoritative (ADR-0007), so losing it costs a cold cache and not data.

**2. Authentication stays in-house.** Supabase Auth is explicitly out of scope.

## Why not Supabase Auth

The application's authorisation model is a *membership* model, not a login. A single
user may belong to several tenants and hold a **different role in each** — doctor in
one clinic, administrator in another. `AuthService.login` selects the first membership
whose tenant is active and issues a token scoped to it, and that `tenantId` claim is
load-bearing: `TenantStatementInspector` reads it to rewrite the tenant predicate onto
every SQL statement. Tenant isolation is enforced by that mechanism.

Supabase Auth has `auth.roles`: one role per user, globally, with no concept of
per-tenant membership. Adopting it would mean either flattening a model the business
needs, or building a mapping layer over it that reimplements what is already correct
and tested.

Two further costs:

- **Isolation boundary.** Coupling the `tenantId` claim to a third party's token
  issuance puts the tenant-isolation boundary outside the application.
- **Critical-path dependency.** A network round trip to an external identity provider
  for every sign-in is an availability risk in an environment where access to records
  is an operational necessity. The current path is a local, tested, stateless check.

The cost of switching is also concrete rather than theoretical: roughly 450 lines
across the security package, a 22-case RBAC matrix test, and eight security test
classes, all reworked during a production-readiness push.

**Where Supabase Auth would be a good fit later:** a patient-facing portal, where
passwordless or OTP sign-in and MFA genuinely are better served by a provider than
hand-rolled. That would be a *new* path, not a replacement for internal staff
authentication.

## Consequences

- **Data residency is a control, not a compliance conclusion.** The DPDP Rules 2025
  were notified on 13 November 2025 with an 18-month phased timeline, so full
  substantive compliance falls due around 13 May 2027. Localisation is conditional:
  the government may restrict transfers of personal data to specified jurisdictions
  "where required". Pinning Mumbai is prudent now and is not by itself compliance.
  DPDP obligations that are *not* satisfied by hosting choice remain open in #17 —
  consent notices, data-principal request handling within 90 days, and the stronger
  duties that apply to Significant Data Fiduciaries.
- **The `service_role` key becomes the most sensitive secret in the system.** It
  bypasses row-level security and is equivalent to full database access. It is
  handled under ADR-0004 and must never reach the repository or a log line.
- **Application-layer encryption is unaffected and continues to protect PII.**
  Patient names and clinical notes are encrypted with AES-256-GCM before they reach
  the database, so a dashboard or SQL-editor session with `service_role` sees
  ciphertext. This property must not be given up: it is what makes the provider's
  staff, backups and any future read replica non-events rather than disclosures.
- **Schema migrations run against the provider.** Flyway keeps its job; only the
  connection target changes. `tenant_users` is not inspector-scoped and
  `users`/`tenants` are not tenant-owned, so the existing isolation behaviour is
  unchanged by the move.
- **An escape route exists.** Supabase is standard PostgreSQL reached over the wire.
  Leaving later is a connection-string change and a DNS change, not a migration. The
  cost of the decision is therefore reversible; the cost of the alternative — writing
  the deployment descriptor against "managed Postgres" without choosing — is that
  nothing ships.
- **A provider outage becomes a product outage.** Accepted, with the same blast radius
  as any managed dependency. The application keeps a documented fallback in the sense
  that the failure mode is a connection error rather than data loss.

## Alternatives considered

| Alternative | Why not |
|---|---|
| AWS RDS or a similar managed PostgreSQL | A perfectly good choice, and the most conservative one. Deferred only because the team is already familiar with the Supabase workflow, which reduces operational learning risk. Revisit if Supabase's free/pro tier limits or residency controls prove inadequate. |
| An in-host PostgreSQL container | Directly contradicts ADR-0001. Reintroduces backup, PITR and upgrade work, and the single-writer contention that motivated the ADR. |
| Supabase Auth for staff sign-in | See above. The per-tenant role model has no equivalent, and the `tenantId` claim is load-bearing for isolation. |
| Supabase Auth for a future patient portal | **Recommended** when that portal is built. Not now, and additive rather than a replacement. |
| Supabase Row Level Security as the isolation mechanism | Genuinely a stronger primitive than SQL rewriting, and worth evaluating — but it is a migration, not a toggle, and `TenantIsolationTest` covers the current mechanism. Deliberately not bundled into a database-provider change. |
