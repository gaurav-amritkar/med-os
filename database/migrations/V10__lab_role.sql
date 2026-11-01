-- V10__lab_role.sql — add the dedicated LAB role (issue #63).
--
-- lab_orders needs a role that can record results and update order status
-- without being a doctor or a nurse. Reusing NURSE would over-privilege
-- lab staff (they could sign encounters, prescribe, etc.), so a dedicated
-- role is the honest model.
--
-- Two CHECK constraints need updating: the users.role inline CHECK and
-- the tenant_users.role CHECK (if it exists from V5).

-- ---------------------------------------------------------------- users.role CHECK
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('admin', 'doctor', 'nurse', 'receptionist', 'pharmacist', 'billing', 'lab'));

-- ---------------------------------------------------------------- tenant_users.role CHECK
-- V5 named it tenant_users_role_check; if a prior migration already dropped it,
-- IF EXISTS keeps this idempotent.
ALTER TABLE tenant_users DROP CONSTRAINT IF EXISTS tenant_users_role_check;
ALTER TABLE tenant_users
    ADD CONSTRAINT tenant_users_role_check
    CHECK (role IN ('admin', 'doctor', 'nurse', 'receptionist', 'pharmacist', 'billing', 'super_admin', 'lab'));
