-- Dev-only demo seed (NOT a Flyway migration).
-- Production never seeds users with known passwords — use BOOTSTRAP_ADMIN_PASSWORD.
-- Run via:  tools/seed-dev.sh   (or  psql -d medos -f tools/seed-dev.sql)
-- BCrypt hash below is for the password "password" (demo only).

INSERT INTO users (username, password_hash, full_name, email, role, specialization, active) VALUES
('admin', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'System Administrator', 'admin@medos.local', 'admin', NULL, TRUE),
('doctor', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Dr. Aisha Sharma', 'aisha@medos.local', 'doctor', 'General Medicine', TRUE),
('doctor2', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Dr. Rajesh Kumar', 'rajesh@medos.local', 'doctor', 'Cardiology', TRUE),
('nurse', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Priya Singh', 'priya@medos.local', 'nurse', NULL, TRUE),
('reception', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Maya Verma', 'maya@medos.local', 'receptionist', NULL, TRUE),
('pharmacy', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Anil Patel', 'anil@medos.local', 'pharmacist', NULL, TRUE),
('billing', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Sneha Iyer', 'sneha@medos.local', 'billing', NULL, TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO patients (uhid, name, age, gender, phone, email, blood_group, dpdp_consent, dpdp_consent_at) VALUES
('UHID000001', 'Rahul Mehta', 34, 'male', '9876543210', 'rahul@example.com', 'B+', TRUE, CURRENT_TIMESTAMP),
('UHID000002', 'Anita Joshi', 28, 'female', '9876543211', 'anita@example.com', 'O+', TRUE, CURRENT_TIMESTAMP),
('UHID000003', 'Suresh Reddy', 62, 'male', '9876543212', 'suresh@example.com', 'A+', TRUE, CURRENT_TIMESTAMP),
('UHID000004', 'Kavita Nair', 45, 'female', '9876543213', 'kavita@example.com', 'AB+', TRUE, CURRENT_TIMESTAMP),
('UHID000005', 'Aman Khan', 22, 'male', '9876543214', 'aman@example.com', 'O-', FALSE, NULL)
ON CONFLICT (uhid) DO NOTHING;
