-- #127: allow super_admin in the tenant_users.role check. The inline V1 check on the
-- role column is auto-named, and Postgres names an inline column CHECK
-- `<table>_<column>_check`, so:
ALTER TABLE tenant_users DROP CONSTRAINT IF EXISTS tenant_users_role_check;
ALTER TABLE tenant_users ADD CONSTRAINT tenant_users_role_check CHECK (
    role IN ('admin', 'doctor', 'nurse', 'receptionist', 'pharmacist', 'billing', 'super_admin')
);
