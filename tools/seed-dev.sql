-- Dev-only demo seed (NOT a Flyway migration).
-- Production never seeds users with known passwords; use BOOTSTRAP_ADMIN_PASSWORD.
-- Run via: tools/seed-dev.sh (or psql -d medos -f tools/seed-dev.sql)
-- BCrypt hash below is for the password "password" (demo only).

INSERT INTO tenants (id, name, slug, type, contact_email, contact_phone, address, active) VALUES
('00000000-0000-4000-8000-000000000001', 'MedOS Demo Hospital', 'demo-hospital', 'HOSPITAL', 'admin@medos.local', '9000000000', 'Demo City', TRUE)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  slug = EXCLUDED.slug,
  type = EXCLUDED.type,
  contact_email = EXCLUDED.contact_email,
  contact_phone = EXCLUDED.contact_phone,
  address = EXCLUDED.address,
  active = EXCLUDED.active;

WITH demo_users(id, username, full_name, email, role, specialization) AS (
  VALUES
    ('00000000-0000-4000-8000-000000000101'::uuid, 'admin', 'System Administrator', 'admin@medos.local', 'admin', NULL),
    ('00000000-0000-4000-8000-000000000102'::uuid, 'doctor', 'Dr. Aisha Sharma', 'aisha@medos.local', 'doctor', 'General Medicine'),
    ('00000000-0000-4000-8000-000000000103'::uuid, 'doctor2', 'Dr. Rajesh Kumar', 'rajesh@medos.local', 'doctor', 'Cardiology'),
    ('00000000-0000-4000-8000-000000000104'::uuid, 'nurse', 'Priya Singh', 'priya@medos.local', 'nurse', NULL),
    ('00000000-0000-4000-8000-000000000105'::uuid, 'reception', 'Maya Verma', 'maya@medos.local', 'receptionist', NULL),
    ('00000000-0000-4000-8000-000000000106'::uuid, 'pharmacy', 'Anil Patel', 'anil@medos.local', 'pharmacist', NULL),
    ('00000000-0000-4000-8000-000000000107'::uuid, 'billing', 'Sneha Iyer', 'sneha@medos.local', 'billing', NULL)
)
INSERT INTO users (id, username, password_hash, full_name, email, specialization, active)
SELECT id, username, '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', full_name, email, specialization, TRUE
FROM demo_users
ON CONFLICT (username) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  full_name = EXCLUDED.full_name,
  email = EXCLUDED.email,
  specialization = EXCLUDED.specialization,
  active = TRUE;

WITH demo_roles(username, role) AS (
  VALUES
    ('admin', 'admin'),
    ('doctor', 'doctor'),
    ('doctor2', 'doctor'),
    ('nurse', 'nurse'),
    ('reception', 'receptionist'),
    ('pharmacy', 'pharmacist'),
    ('billing', 'billing')
)
INSERT INTO tenant_users (id, user_id, tenant_id, role)
SELECT
  ('10000000-0000-4000-8000-' || lpad(row_number() OVER (ORDER BY u.username)::text, 12, '0'))::uuid,
  u.id,
  '00000000-0000-4000-8000-000000000001'::uuid,
  r.role
FROM demo_roles r
JOIN users u ON u.username = r.username
ON CONFLICT (user_id, tenant_id) DO UPDATE SET role = EXCLUDED.role;

INSERT INTO patients (id, tenant_id, uhid, name, age, gender, phone, email, blood_group, dpdp_consent, dpdp_consent_at) VALUES
('00000000-0000-4000-8000-000000000201', '00000000-0000-4000-8000-000000000001', 'UHID000001', 'Rahul Mehta', 34, 'male', '9876543210', 'rahul@example.com', 'B+', TRUE, CURRENT_TIMESTAMP),
('00000000-0000-4000-8000-000000000202', '00000000-0000-4000-8000-000000000001', 'UHID000002', 'Anita Joshi', 28, 'female', '9876543211', 'anita@example.com', 'O+', TRUE, CURRENT_TIMESTAMP),
('00000000-0000-4000-8000-000000000203', '00000000-0000-4000-8000-000000000001', 'UHID000003', 'Suresh Reddy', 62, 'male', '9876543212', 'suresh@example.com', 'A+', TRUE, CURRENT_TIMESTAMP),
('00000000-0000-4000-8000-000000000204', '00000000-0000-4000-8000-000000000001', 'UHID000004', 'Kavita Nair', 45, 'female', '9876543213', 'kavita@example.com', 'AB+', TRUE, CURRENT_TIMESTAMP),
('00000000-0000-4000-8000-000000000205', '00000000-0000-4000-8000-000000000001', 'UHID000005', 'Aman Khan', 22, 'male', '9876543214', 'aman@example.com', 'O-', FALSE, NULL)
ON CONFLICT (uhid) DO UPDATE SET
  tenant_id = EXCLUDED.tenant_id,
  name = EXCLUDED.name,
  age = EXCLUDED.age,
  gender = EXCLUDED.gender,
  phone = EXCLUDED.phone,
  email = EXCLUDED.email,
  blood_group = EXCLUDED.blood_group,
  dpdp_consent = EXCLUDED.dpdp_consent,
  dpdp_consent_at = EXCLUDED.dpdp_consent_at;
