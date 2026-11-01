-- V15__lab_role.sql — add the dedicated LAB role (issue #63).
--
-- lab_orders needs a role that can record results and update order status
-- without being a doctor or a nurse. Reusing NURSE would over-privilege
-- lab staff (they could sign encounters, prescribe, etc.), so a dedicated
-- role is the honest model.
--
-- role lives on tenant_users (per-membership), not on users (per-person),
-- so only the tenant_users.role CHECK constraint needs updating.

-- ---------------------------------------------------------------- tenant_users.role CHECK
-- V5 named it tenant_users_role_check; if a prior migration already dropped it,
-- IF EXISTS keeps this idempotent.
ALTER TABLE tenant_users DROP CONSTRAINT IF EXISTS tenant_users_role_check;
ALTER TABLE tenant_users
    ADD CONSTRAINT tenant_users_role_check
    CHECK (role IN ('admin', 'doctor', 'nurse', 'receptionist', 'pharmacist', 'billing', 'super_admin', 'lab'));
