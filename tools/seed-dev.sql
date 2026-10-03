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


-- IPD room inventory (master data; no API exists to create rooms).
-- version column is NOT NULL (optimistic locking) — default 0.
INSERT INTO rooms (id, tenant_id, room_number, ward, room_type, daily_rate, capacity, occupied, floor, version) VALUES
('00000000-0000-4000-8000-000000000301', '00000000-0000-4000-8000-000000000001', 'G-101', 'General Ward A', 'general', 1500.00, 1, FALSE, 1, 0),
('00000000-0000-4000-8000-000000000302', '00000000-0000-4000-8000-000000000001', 'G-102', 'General Ward A', 'general', 1500.00, 1, FALSE, 1, 0),
('00000000-0000-4000-8000-000000000303', '00000000-0000-4000-8000-000000000001', 'S-201', 'Semi-Private Wing', 'semi_private', 3000.00, 1, FALSE, 2, 0),
('00000000-0000-4000-8000-000000000304', '00000000-0000-4000-8000-000000000001', 'P-301', 'Private Wing', 'private_room', 5000.00, 1, FALSE, 3, 0),
('00000000-0000-4000-8000-000000000305', '00000000-0000-4000-8000-000000000001', 'ICU-01', 'Intensive Care', 'icu', 12000.00, 1, FALSE, 4, 0)
ON CONFLICT (room_number) DO UPDATE SET
  tenant_id = EXCLUDED.tenant_id,
  ward = EXCLUDED.ward,
  room_type = EXCLUDED.room_type,
  daily_rate = EXCLUDED.daily_rate,
  capacity = EXCLUDED.capacity,
  floor = EXCLUDED.floor;

-- Keep uhid_seq safely above every seeded patient UHID (V1 creates it with
-- START WITH 1). Without this, the first API-registered patient gets UHID000001
-- and dies on patients_uhid_key. Dev-only script; fresh prod DBs are unaffected.
SELECT setval('uhid_seq',
  GREATEST(
    5,
    COALESCE((SELECT MAX(NULLIF(regexp_replace(uhid, '\D', '', 'g'), '')::bigint) FROM patients), 0)
  ));
