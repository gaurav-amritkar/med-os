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
('00000000-0000-4000-8000-000000000303', '00000000-0000-4000-8000-000000000001', 'G-103', 'General Ward A', 'general', 1500.00, 1, FALSE, 1, 0),
('00000000-0000-4000-8000-000000000304', '00000000-0000-4000-8000-000000000001', 'G-104', 'General Ward B', 'general', 1500.00, 1, FALSE, 1, 0),
('00000000-0000-4000-8000-000000000305', '00000000-0000-4000-8000-000000000001', 'G-105', 'General Ward B', 'general', 1500.00, 1, FALSE, 1, 0),
('00000000-0000-4000-8000-000000000306', '00000000-0000-4000-8000-000000000001', 'S-201', 'Semi-Private Wing', 'semi_private', 3000.00, 1, FALSE, 2, 0),
('00000000-0000-4000-8000-000000000307', '00000000-0000-4000-8000-000000000001', 'S-202', 'Semi-Private Wing', 'semi_private', 3000.00, 1, FALSE, 2, 0),
('00000000-0000-4000-8000-000000000308', '00000000-0000-4000-8000-000000000001', 'S-203', 'Semi-Private Wing', 'semi_private', 3000.00, 1, FALSE, 2, 0),
('00000000-0000-4000-8000-000000000309', '00000000-0000-4000-8000-000000000001', 'P-301', 'Private Wing', 'private_room', 5000.00, 1, FALSE, 3, 0),
('00000000-0000-4000-8000-000000000310', '00000000-0000-4000-8000-000000000001', 'P-302', 'Private Wing', 'private_room', 5000.00, 1, FALSE, 3, 0),
('00000000-0000-4000-8000-000000000311', '00000000-0000-4000-8000-000000000001', 'ICU-01', 'Intensive Care', 'icu', 12000.00, 1, FALSE, 4, 0),
('00000000-0000-4000-8000-000000000312', '00000000-0000-4000-8000-000000000001', 'ICU-02', 'Intensive Care', 'icu', 12000.00, 1, FALSE, 4, 0),
('00000000-0000-4000-8000-000000000313', '00000000-0000-4000-8000-000000000001', 'NICU-01', 'Neonatal ICU', 'nicu', 15000.00, 1, FALSE, 5, 0),
('00000000-0000-4000-8000-000000000314', '00000000-0000-4000-8000-000000000001', 'OT-01', 'Operation Theatre', 'operation', 25000.00, 1, FALSE, 6, 0),
('00000000-0000-4000-8000-000000000315', '00000000-0000-4000-8000-000000000001', 'OT-02', 'Operation Theatre', 'operation', 25000.00, 1, FALSE, 6, 0)
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

-- ---------------------------------------------------------------- medicine catalog
-- Master data for the pharmacy. No API dependency; seed directly.
INSERT INTO medicine_catalog (id, tenant_id, name, generic_name, category, unit, unit_price, manufacturer, reorder_level, indications, keywords, active, created_at) VALUES
('00000000-0000-4000-8000-000000000401', '00000000-0000-4000-8000-000000000001', 'Paracetamol 500mg', 'Acetaminophen', 'Analgesic', 'tablet', 2.50, 'Cipla', 100, 'Fever, mild to moderate pain', 'fever pain headache', TRUE, NOW()),
('00000000-0000-4000-8000-000000000402', '00000000-0000-4000-8000-000000000001', 'Amoxicillin 250mg', 'Amoxicillin', 'Antibiotic', 'capsule', 8.00, 'Sun Pharma', 50, 'Bacterial infections', 'infection bacterial', TRUE, NOW()),
('00000000-0000-4000-8000-000000000403', '00000000-0000-4000-8000-000000000001', 'Cetirizine 10mg', 'Cetirizine', 'Antihistamine', 'tablet', 1.50, 'Dr. Reddys', 100, 'Allergic rhinitis, urticaria', 'allergy sneezing itching', TRUE, NOW()),
('00000000-0000-4000-8000-000000000404', '00000000-0000-4000-8000-000000000001', 'Metformin 500mg', 'Metformin', 'Antidiabetic', 'tablet', 3.00, 'USV', 80, 'Type 2 diabetes mellitus', 'diabetes sugar', TRUE, NOW()),
('00000000-0000-4000-8000-000000000405', '00000000-0000-4000-8000-000000000001', 'Amlodipine 5mg', 'Amlodipine', 'Antihypertensive', 'tablet', 2.00, 'Pfizer', 80, 'Hypertension, angina', 'bp blood pressure hypertension', TRUE, NOW()),
('00000000-0000-4000-8000-000000000406', '00000000-0000-4000-8000-000000000001', 'Omeprazole 20mg', 'Omeprazole', 'Proton Pump Inhibitor', 'capsule', 4.50, 'Cipla', 60, 'GERD, peptic ulcer', 'acidity ulcer gerd', TRUE, NOW()),
('00000000-0000-4000-8000-000000000407', '00000000-0000-4000-8000-000000000001', 'Azithromycin 500mg', 'Azithromycin', 'Antibiotic', 'tablet', 15.00, 'Sun Pharma', 40, 'Bacterial infections, pneumonia', 'infection pneumonia bacterial', TRUE, NOW()),
('00000000-0000-4000-8000-000000000408', '00000000-0000-4000-8000-000000000001', 'Ibuprofen 400mg', 'Ibuprofen', 'NSAID', 'tablet', 3.50, 'Sun Pharma', 80, 'Pain, inflammation, fever', 'pain inflammation fever', TRUE, NOW()),
('00000000-0000-4000-8000-000000000409', '00000000-0000-4000-8000-000000000001', 'Atorvastatin 20mg', 'Atorvastatin', 'Statin', 'tablet', 6.00, 'Pfizer', 60, 'Hyperlipidemia, cardiovascular prevention', 'cholesterol lipid statin', TRUE, NOW()),
('00000000-0000-4000-8000-000000000410', '00000000-0000-4000-8000-000000000001', 'Salbutamol Inhaler 100mcg', 'Salbutamol', 'Bronchodilator', 'inhaler', 120.00, 'Cipla', 30, 'Asthma, COPD', 'asthma breathing wheeze', TRUE, NOW()),
('00000000-0000-4000-8000-000000000411', '00000000-0000-4000-8000-000000000001', 'Pantoprazole 40mg', 'Pantoprazole', 'Proton Pump Inhibitor', 'tablet', 5.50, 'Aristo', 50, 'GERD, peptic ulcer', 'acidity ulcer gerd ppi', TRUE, NOW()),
('00000000-0000-4000-8000-000000000412', '00000000-0000-4000-8000-000000000001', 'Dolo 650mg', 'Paracetamol', 'Analgesic', 'tablet', 3.00, 'Micro Labs', 100, 'Fever, pain', 'fever pain headache dolo', TRUE, NOW()),
('00000000-0000-4000-8000-000000000413', '00000000-0000-4000-8000-000000000001', 'Cough Syrup 100ml', 'Dextromethorphan', 'Antitussive', 'syrup', 45.00, 'Mankind', 20, 'Dry cough', 'cough cold', TRUE, NOW()),
('00000000-0000-4000-8000-000000000414', '00000000-0000-4000-8000-000000000001', 'ORS Sachet', 'Oral Rehydration Salts', 'Electrolyte', 'sachet', 10.00, 'FDC', 200, 'Dehydration', 'dehydration diarrhea ors', TRUE, NOW()),
('00000000-0000-4000-8000-000000000415', '00000000-0000-4000-8000-000000000001', 'Insulin Glargine 100IU', 'Insulin Glargine', 'Antidiabetic', 'injection', 350.00, 'Sanofi', 20, 'Type 1 and Type 2 diabetes', 'diabetes insulin sugar', TRUE, NOW()),
('00000000-0000-4000-8000-000000000416', '00000000-0000-4000-8000-000000000001', 'Aspirin 75mg', 'Acetylsalicylic Acid', 'Antiplatelet', 'tablet', 1.00, 'Sun Pharma', 100, 'Cardiovascular prevention', 'aspirin heart bp blood', TRUE, NOW()),
('00000000-0000-4000-8000-000000000417', '00000000-0000-4000-8000-000000000001', 'Losartan 50mg', 'Losartan', 'Antihypertensive', 'tablet', 4.00, 'Micro Labs', 60, 'Hypertension', 'bp blood pressure hypertension', TRUE, NOW()),
('00000000-0000-4000-8000-000000000418', '00000000-0000-4000-8000-000000000001', 'Vitamin D3 60K IU', 'Cholecalciferol', 'Supplement', 'capsule', 12.00, 'Sun Pharma', 100, 'Vitamin D deficiency', 'vitamin d bone deficiency', TRUE, NOW()),
('00000000-0000-4000-8000-000000000419', '00000000-0000-4000-8000-000000000001', 'Zinc 50mg', 'Zinc', 'Supplement', 'tablet', 3.00, 'FDC', 80, 'Zinc deficiency, immune support', 'zinc immunity supplement', TRUE, NOW()),
('00000000-0000-4000-8000-000000000420', '00000000-0000-4000-8000-000000000001', 'Azithromycin 250mg', 'Azithromycin', 'Antibiotic', 'tablet', 10.00, 'Sun Pharma', 40, 'Bacterial infections', 'infection bacterial azithro', TRUE, NOW())
ON CONFLICT (name) DO UPDATE SET
  generic_name = EXCLUDED.generic_name,
  category = EXCLUDED.category,
  unit = EXCLUDED.unit,
  unit_price = EXCLUDED.unit_price,
  manufacturer = EXCLUDED.manufacturer,
  reorder_level = EXCLUDED.reorder_level,
  indications = EXCLUDED.indications,
  keywords = EXCLUDED.keywords,
  active = TRUE;

-- ---------------------------------------------------------------- medicine batches
-- Multiple batches per medicine with different expiry dates for realistic stock.
INSERT INTO medicine_batches (id, medicine_id, batch_no, expiry_date, remaining_qty, received_date, purchase_price, supplier, tenant_id, version) VALUES
('00000000-0000-4000-8000-000000000501', '00000000-0000-4000-8000-000000000401', 'PCM-A123', '2027-03-31', 500, '2025-01-15', 1.80, 'Cipla Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000502', '00000000-0000-4000-8000-000000000401', 'PCM-B456', '2026-11-30', 200, '2025-06-01', 1.75, 'Cipla Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000503', '00000000-0000-4000-8000-000000000402', 'AMX-A789', '2027-01-31', 300, '2025-02-01', 6.00, 'Sun Pharma Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000504', '00000000-0000-4000-8000-000000000402', 'AMX-B012', '2026-08-31', 150, '2025-07-01', 5.80, 'Sun Pharma Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000505', '00000000-0000-4000-8000-000000000403', 'CTR-A345', '2027-04-30', 400, '2025-01-20', 1.10, 'Dr Reddys Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000506', '00000000-0000-4000-8000-000000000404', 'MET-A678', '2027-02-28', 350, '2025-03-01', 2.20, 'USV Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000507', '00000000-0000-4000-8000-000000000405', 'AML-A901', '2027-05-31', 250, '2025-02-15', 1.50, 'Pfizer Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000508', '00000000-0000-4000-8000-000000000406', 'OMP-A234', '2027-06-30', 200, '2025-01-10', 3.50, 'Cipla Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000509', '00000000-0000-4000-8000-000000000407', 'AZI-A567', '2027-03-31', 180, '2025-04-01', 12.00, 'Sun Pharma Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000510', '00000000-0000-4000-8000-000000000408', 'IBU-A890', '2027-04-30', 300, '2025-02-20', 2.80, 'Sun Pharma Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000511', '00000000-0000-4000-8000-000000000409', 'ATV-A112', '2027-05-31', 200, '2025-03-10', 4.50, 'Pfizer Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000512', '00000000-0000-4000-8000-000000000410', 'SAL-A334', '2027-07-31', 100, '2025-01-05', 95.00, 'Cipla Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000513', '00000000-0000-4000-8000-000000000411', 'PNT-A556', '2027-06-30', 180, '2025-02-28', 4.20, 'Aristo Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000514', '00000000-0000-4000-8000-000000000412', 'DOL-A778', '2027-08-31', 400, '2025-03-15', 2.30, 'Micro Labs Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000515', '00000000-0000-4000-8000-000000000413', 'CSY-A990', '2027-01-31', 80, '2025-04-01', 35.00, 'Mankind Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000516', '00000000-0000-4000-8000-000000000414', 'ORS-A123', '2027-12-31', 600, '2025-01-01', 8.00, 'FDC Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000517', '00000000-0000-4000-8000-000000000415', 'INS-A456', '2026-12-31', 50, '2025-06-01', 300.00, 'Sanofi Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000518', '00000000-0000-4000-8000-000000000416', 'ASP-A789', '2027-09-30', 500, '2025-02-10', 0.80, 'Sun Pharma Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000519', '00000000-0000-4000-8000-000000000417', 'LOS-A012', '2027-04-30', 220, '2025-03-20', 3.20, 'Micro Labs Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000520', '00000000-0000-4000-8000-000000000418', 'VTD-A345', '2027-10-31', 350, '2025-01-15', 9.50, 'Sun Pharma Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000521', '00000000-0000-4000-8000-000000000419', 'ZNC-A678', '2027-08-31', 400, '2025-02-05', 2.40, 'FDC Dist', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000000522', '00000000-0000-4000-8000-000000000420', 'AZI-A901', '2027-03-31', 200, '2025-04-15', 8.00, 'Sun Pharma Dist', '00000000-0000-4000-8000-000000000001', 0)
ON CONFLICT (medicine_id, batch_no) DO UPDATE SET
  expiry_date = EXCLUDED.expiry_date,
  remaining_qty = EXCLUDED.remaining_qty,
  purchase_price = EXCLUDED.purchase_price,
  supplier = EXCLUDED.supplier;

-- ---------------------------------------------------------------- stock transactions
-- Initial stock-in for each batch.
INSERT INTO stock_transactions (id, medicine_id, batch_id, patient_id, performed_at, performed_by, prescription_id, quantity, tenant_id, transaction_type, reference_no, notes) VALUES
('00000000-0000-4000-8000-000000000601', '00000000-0000-4000-8000-000000000401', '00000000-0000-4000-8000-000000000501', NULL, '2025-01-15 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 500, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-001', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000602', '00000000-0000-4000-8000-000000000402', '00000000-0000-4000-8000-000000000503', NULL, '2025-02-01 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 300, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-002', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000603', '00000000-0000-4000-8000-000000000403', '00000000-0000-4000-8000-000000000505', NULL, '2025-01-20 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 400, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-003', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000604', '00000000-0000-4000-8000-000000000404', '00000000-0000-4000-8000-000000000506', NULL, '2025-03-01 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 350, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-004', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000605', '00000000-0000-4000-8000-000000000405', '00000000-0000-4000-8000-000000000507', NULL, '2025-02-15 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 250, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-005', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000606', '00000000-0000-4000-8000-000000000406', '00000000-0000-4000-8000-000000000508', NULL, '2025-01-10 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 200, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-006', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000607', '00000000-0000-4000-8000-000000000407', '00000000-0000-4000-8000-000000000509', NULL, '2025-04-01 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 180, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-007', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000608', '00000000-0000-4000-8000-000000000408', '00000000-0000-4000-8000-000000000510', NULL, '2025-02-20 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 300, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-008', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000609', '00000000-0000-4000-8000-000000000409', '00000000-0000-4000-8000-000000000511', NULL, '2025-03-10 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 200, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-009', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000610', '00000000-0000-4000-8000-000000000410', '00000000-0000-4000-8000-000000000512', NULL, '2025-01-05 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 100, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-010', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000611', '00000000-0000-4000-8000-000000000411', '00000000-0000-4000-8000-000000000513', NULL, '2025-02-28 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 180, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-011', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000612', '00000000-0000-4000-8000-000000000412', '00000000-0000-4000-8000-000000000514', NULL, '2025-03-15 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 400, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-012', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000613', '00000000-0000-4000-8000-000000000413', '00000000-0000-4000-8000-000000000515', NULL, '2025-04-01 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 80, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-013', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000614', '00000000-0000-4000-8000-000000000414', '00000000-0000-4000-8000-000000000516', NULL, '2025-01-01 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 600, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-014', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000615', '00000000-0000-4000-8000-000000000415', '00000000-0000-4000-8000-000000000517', NULL, '2025-06-01 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 50, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-015', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000616', '00000000-0000-4000-8000-000000000416', '00000000-0000-4000-8000-000000000518', NULL, '2025-02-10 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 500, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-016', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000617', '00000000-0000-4000-8000-000000000417', '00000000-0000-4000-8000-000000000519', NULL, '2025-03-20 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 220, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-017', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000618', '00000000-0000-4000-8000-000000000418', '00000000-0000-4000-8000-000000000520', NULL, '2025-01-15 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 350, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-018', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000619', '00000000-0000-4000-8000-000000000419', '00000000-0000-4000-8000-000000000521', NULL, '2025-02-05 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 400, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-019', 'Initial stock receipt'),
('00000000-0000-4000-8000-000000000620', '00000000-0000-4000-8000-000000000420', '00000000-0000-4000-8000-000000000522', NULL, '2025-04-15 10:00:00', '00000000-0000-4000-8000-000000000106', NULL, 200, '00000000-0000-4000-8000-000000000001', 'in', 'PO-2025-020', 'Initial stock receipt')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------- disease-medicine map
-- Maps diseases to recommended medicines for AI prescription suggestions.
INSERT INTO disease_medicine_map (id, medicine_id, disease_keyword, dosage, frequency, priority, created_at) VALUES
('00000000-0000-4000-8000-000000000701', '00000000-0000-4000-8000-000000000401', 'fever', '500mg', 'three times daily', 1, NOW()),
('00000000-0000-4000-8000-000000000702', '00000000-0000-4000-8000-000000000408', 'fever', '400mg', 'twice daily', 2, NOW()),
('00000000-0000-4000-8000-000000000703', '00000000-0000-4000-8000-000000000401', 'headache', '500mg', 'as needed', 1, NOW()),
('00000000-0000-4000-8000-000000000704', '00000000-0000-4000-8000-000000000403', 'allergy', '10mg', 'once daily', 1, NOW()),
('00000000-0000-4000-8000-000000000705', '00000000-0000-4000-8000-000000000404', 'diabetes', '500mg', 'twice daily', 1, NOW()),
('00000000-0000-4000-8000-000000000706', '00000000-0000-4000-8000-000000000405', 'hypertension', '5mg', 'once daily', 1, NOW()),
('00000000-0000-4000-8000-000000000707', '00000000-0000-4000-8000-000000000417', 'hypertension', '50mg', 'once daily', 2, NOW()),
('00000000-0000-4000-8000-000000000708', '00000000-0000-4000-8000-000000000406', 'acidity', '20mg', 'once daily before breakfast', 1, NOW()),
('00000000-0000-4000-8000-000000000709', '00000000-0000-4000-8000-000000000411', 'acidity', '40mg', 'once daily before breakfast', 2, NOW()),
('00000000-0000-4000-8000-000000000710', '00000000-0000-4000-8000-000000000402', 'infection', '250mg', 'three times daily', 1, NOW()),
('00000000-0000-4000-8000-000000000711', '00000000-0000-4000-8000-000000000407', 'infection', '500mg', 'once daily for 3 days', 2, NOW()),
('00000000-0000-4000-8000-000000000712', '00000000-0000-4000-8000-000000000410', 'asthma', '100mcg', 'as needed', 1, NOW()),
('00000000-0000-4000-8000-000000000713', '00000000-0000-4000-8000-000000000409', 'cholesterol', '20mg', 'once daily at bedtime', 1, NOW()),
('00000000-0000-4000-8000-000000000714', '00000000-0000-4000-8000-000000000416', 'cardiovascular', '75mg', 'once daily', 1, NOW()),
('00000000-0000-4000-8000-000000000715', '00000000-0000-4000-8000-000000000413', 'cough', '10ml', 'three times daily', 1, NOW()),
('00000000-0000-4000-8000-000000000716', '00000000-0000-4000-8000-000000000414', 'dehydration', '1 sachet', 'after each loose stool', 1, NOW()),
('00000000-0000-4000-8000-000000000717', '00000000-0000-4000-8000-000000000401', 'pain', '500mg', 'as needed', 1, NOW()),
('00000000-0000-4000-8000-000000000718', '00000000-0000-4000-8000-000000000408', 'pain', '400mg', 'twice daily', 2, NOW()),
('00000000-0000-4000-8000-000000000719', '00000000-0000-4000-8000-000000000408', 'inflammation', '400mg', 'twice daily', 1, NOW()),
('00000000-0000-4000-8000-000000000720', '00000000-0000-4000-8000-000000000418', 'vitamin_d_deficiency', '60K IU', 'once weekly', 1, NOW()),
('00000000-0000-4000-8000-000000000721', '00000000-0000-4000-8000-000000000419', 'zinc_deficiency', '50mg', 'once daily', 1, NOW())
ON CONFLICT (disease_keyword) DO UPDATE SET
  medicine_id = EXCLUDED.medicine_id,
  dosage = EXCLUDED.dosage,
  frequency = EXCLUDED.frequency,
  priority = EXCLUDED.priority;

-- ---------------------------------------------------------------- tenant config & settings
INSERT INTO tenant_configs (id, tenant_id, config_key, config_value) VALUES
('00000000-0000-4000-8000-000000000801', '00000000-0000-4000-8000-000000000001', 'hospital.name', 'MedOS Demo Hospital'),
('00000000-0000-4000-8000-000000000802', '00000000-0000-4000-8000-000000000001', 'hospital.phone', '9000000000'),
('00000000-0000-4000-8000-000000000803', '00000000-0000-4000-8000-000000000001', 'hospital.email', 'admin@medos.local'),
('00000000-0000-4000-8000-000000000804', '00000000-0000-4000-8000-000000000001', 'hospital.address', 'Demo City'),
('00000000-0000-4000-8000-000000000805', '00000000-0000-4000-8000-000000000001', 'gst.number', '27AABCM1234F1Z5'),
('00000000-0000-4000-8000-000000000806', '00000000-0000-4000-8000-000000000001', 'billing.gst_percent', '18'),
('00000000-0000-4000-8000-000000000807', '00000000-0000-4000-8000-000000000001', 'appointment.slot_duration_minutes', '15'),
('00000000-0000-4000-8000-000000000808', '00000000-0000-4000-8000-000000000001', 'opd.opening_time', '09:00'),
('00000000-0000-4000-8000-000000000809', '00000000-0000-4000-8000-000000000001', 'opd.closing_time', '17:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenant_settings (tenant_id, config_key, config_value) VALUES
('00000000-0000-4000-8000-000000000001', 'theme', 'light'),
('00000000-0000-4000-8000-000000000001', 'language', 'en'),
('00000000-0000-4000-8000-000000000001', 'timezone', 'Asia/Kolkata'),
('00000000-0000-4000-8000-000000000001', 'currency', 'INR'),
('00000000-0000-4000-8000-000000000001', 'date_format', 'dd-MM-yyyy'),
('00000000-0000-4000-8000-000000000001', 'appointment.reminder_hours', '2')
ON CONFLICT (tenant_id, config_key) DO UPDATE SET config_value = EXCLUDED.config_value;

-- ---------------------------------------------------------------- notifications
INSERT INTO notifications (id, recipient_id, role_target, type, title, message, link, read, created_at) VALUES
('00000000-0000-4000-8000-000000000901', '00000000-0000-4000-8000-000000000101', 'admin', 'info', 'Welcome to MedOS', 'Your demo environment is ready. Explore the dashboard to get started.', '/dashboard', FALSE, NOW()),
('00000000-0000-4000-8000-000000000902', '00000000-0000-4000-8000-000000000102', 'doctor', 'warning', 'Pending Encounters', 'You have 2 encounters awaiting sign-off.', '/encounters', FALSE, NOW() - INTERVAL '1 hour'),
('00000000-0000-4000-8000-000000000903', '00000000-0000-4000-8000-000000000106', 'pharmacist', 'critical', 'Low Stock Alert', 'Salbutamol Inhaler stock is below reorder level.', '/pharmacy', FALSE, NOW() - INTERVAL '2 hours'),
('00000000-0000-4000-8000-000000000904', '00000000-0000-4000-8000-000000000107', 'billing', 'success', 'Invoice Generated', 'Invoice INV-2026-0001 has been generated for Rahul Mehta.', '/billing', FALSE, NOW() - INTERVAL '30 minutes'),
('00000000-0000-4000-8000-000000000905', NULL, 'doctor', 'info', 'OPD Queue Update', '3 patients are waiting in the OPD queue.', '/encounters', FALSE, NOW() - INTERVAL '15 minutes'),
('00000000-0000-4000-8000-000000000906', '00000000-0000-4000-8000-000000000101', 'admin', 'warning', 'Expiring Stock', '5 medicine batches expiring within 30 days.', '/pharmacy', TRUE, NOW() - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;
