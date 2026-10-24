-- V11__tenant_user_permissions.sql — #195 shipped, schema never did.
--
-- The staff custom-permissions feature (PR #195, issue work for role/nav
-- mapping) added `TenantUser.permissions` — an ADMIN-granted, comma-separated
-- CSV of extra navigation routes a staff member may see ("grant the
-- receptionist the billing page"). Testing and dev happened against
-- Hibernate `ddl-auto: update`; the Docker/production stack runs the migrate
-- container with `ddl-auto: validate`, so the first boot there failed with
-- "missing column [permissions] in table [tenant_users]".
--
-- This is the migration that should have shipped with it: nullable — a
-- membership starts with no extra grants, which is the same thing "the column
-- doesn't exist" used to mean, and the service layer already treats both as
-- "no additional permissions".
--
-- Follows the V3 pattern for per-membership access control: one row per
-- (user_id, tenant_id), column on that row, never on users.

ALTER TABLE tenant_users
    ADD COLUMN permissions VARCHAR(512);

COMMENT ON COLUMN tenant_users.permissions IS
    'ADMIN-granted CSV of additional API routes this member may access; NULL means none beyond the role baseline';
