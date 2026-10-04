-- Platform super-admin + per-tenant feature selection (#127).
--
-- 1. users.is_super_admin marks the platform account that provisioning is gated on.
--    It deliberately has no tenant_users row: a platform admin belongs to no one tenant,
--    and tenant_users.tenant_id is NOT NULL, so we do not borrow that table for it.
-- 2. How the enabled features reach the UI is through Tenant.config, which already exists
--    as a free-form map. The contracted key is config["features"], a comma-separated list
--    drawn from a controlled vocabulary (admissions, billing, encounters, pharmacy).

ALTER TABLE users
    ADD COLUMN is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.is_super_admin IS
    'True only for the platform account that owns tenant provisioning. It holds no '
    'tenant_users row and signs in with tenantId=null.';

CREATE INDEX IF NOT EXISTS idx_users_is_super_admin ON users(is_super_admin)
    WHERE is_super_admin;
