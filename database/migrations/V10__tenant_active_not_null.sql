-- V10__tenant_active_not_null.sql — issue #53 (Tenant.active is never enforced → schema part)
--
-- The access-enforcement half of this issue already ships: AuthService.login
-- scopes a multi-facility user to an active membership and rejects every
-- account whose organisations are all suspended, and JwtAuthenticationFilter
-- re-checks both the tenant and the membership on every request (a 10-hour
-- token must not outlive the deboard that issued it).
--
-- This migration closes the schema half, and pins the NULL policy:
--
-- * The column was `BOOLEAN` with no NOT NULL and no default (squashed V1).
-- * The application treats a NULL as *inactive* — the deliberately closed
--   reading, because "a value that was never written" must not authorise
--   access (see JwtAuthenticationFilter.isTenantActive and
--   AuthService.firstActiveTenantMembership). That reading stays in the code
--   as defence in depth; this migration removes the way it can occur.
-- * The backfill maps a NULL to TRUE. The only writers — onboarding,
--   activateTenant, deactivateTenant — always write an explicit boolean, so a
--   NULL is a row from before the flag was ever decided, not a suspended
--   hospital. Reading it as "deactivated" would cut live tenants off. This is
--   the documented decision the issue required.
--
-- Single statement per change; safe for the current production data (the
-- table is small — onboarded hospitals only — so no batched backfill,
-- and TRUE defaults cost nothing).

UPDATE tenants SET active = TRUE WHERE active IS NULL;

ALTER TABLE tenants ALTER COLUMN active SET DEFAULT TRUE;
ALTER TABLE tenants ALTER COLUMN active SET NOT NULL;
