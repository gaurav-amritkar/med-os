-- Seed essential data for MedOS HMS
-- Password for all demo users: "password" (BCrypt hashed)

-- BCrypt hash for 'password' (real generation, prefix 2a)
INSERT INTO users (username, password_hash, full_name, email, role, specialization, active) VALUES
('admin', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'System Administrator', 'admin@medos.local', 'admin', NULL, TRUE),
('doctor', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Dr. Aisha Sharma', 'aisha@medos.local', 'doctor', 'General Medicine', TRUE),
('doctor2', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Dr. Rajesh Kumar', 'rajesh@medos.local', 'doctor', 'Cardiology', TRUE),
('nurse', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Priya Singh', 'priya@medos.local', 'nurse', NULL, TRUE),
('reception', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Maya Verma', 'maya@medos.local', 'receptionist', NULL, TRUE),
('pharmacy', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Anil Patel', 'anil@medos.local', 'pharmacist', NULL, TRUE),
('billing', '$2a$10$CorPoO6aevJRZ9OxCmzs9Olr6R.cnhZTFEV6izYKLd9I/GEkQY5Xu', 'Sneha Iyer', 'sneha@medos.local', 'billing', NULL, TRUE);

-- Medicine catalog seed
INSERT INTO medicine_catalog (name, generic_name, manufacturer, category, unit, unit_price, reorder_level, keywords, indications) VALUES
('Paracetamol 500mg', 'Acetaminophen', 'Cipla', 'Analgesic', 'tablet', 2.00, 100, 'fever,pain,headache,analgesic', 'fever pain headache'),
('Amoxicillin 500mg', 'Amoxicillin', 'Sun Pharma', 'Antibiotic', 'capsule', 8.50, 50, 'infection,antibiotic,bacterial', 'bacterial infections'),
('Cetirizine 10mg', 'Cetirizine', 'Dr. Reddy''s', 'Antihistamine', 'tablet', 3.00, 80, 'allergy,histamine,urticaria', 'allergic rhinitis urticaria'),
('Omeprazole 20mg', 'Omeprazole', 'Ranbaxy', 'Antacid', 'capsule', 5.50, 60, 'acidity,gerd,reflux,ulcer', 'GERD peptic ulcer'),
('Metformin 500mg', 'Metformin HCl', 'USV', 'Antidiabetic', 'tablet', 4.00, 100, 'diabetes,glucose,insulin', 'type 2 diabetes'),
('Atorvastatin 20mg', 'Atorvastatin', 'Lupin', 'Statin', 'tablet', 12.00, 40, 'cholesterol,statin,lipid', 'hyperlipidemia'),
('Amlodipine 5mg', 'Amlodipine', 'Cipla', 'Antihypertensive', 'tablet', 6.00, 50, 'bp,blood pressure,hypertension', 'hypertension'),
('Aspirin 75mg', 'Acetylsalicylic Acid', 'Bayer', 'Antiplatelet', 'tablet', 2.50, 80, 'cardiac,blood thinner,antiplatelet', 'cardioprotection'),
('Ibuprofen 400mg', 'Ibuprofen', 'Cipla', 'NSAID', 'tablet', 4.50, 70, 'pain,inflammation,fever', 'pain inflammation'),
('Azithromycin 500mg', 'Azithromycin', 'Pfizer', 'Antibiotic', 'tablet', 22.00, 30, 'antibiotic,infection,respiratory', 'respiratory infections'),
('Salbutamol Inhaler', 'Salbutamol', 'GSK', 'Bronchodilator', 'inhaler', 180.00, 20, 'asthma,wheez,bronchodilator', 'asthma bronchospasm'),
('Pantoprazole 40mg', 'Pantoprazole', 'Alkem', 'Antacid', 'tablet', 7.00, 60, 'acidity,reflux,ulcer', 'GERD'),
('Cough Syrup (Benadryl)', 'Diphenhydramine', 'Johnson', 'Antitussive', 'bottle', 95.00, 30, 'cough,cold,respiratory', 'cough suppression'),
('ORS Powder', 'Oral Rehydration Salts', 'FDC', 'Rehydration', 'sachet', 12.00, 200, 'dehydration,diarrhea,ors', 'diarrhea dehydration'),
('Insulin (Regular)', 'Insulin', 'Novo Nordisk', 'Antidiabetic', 'vial', 320.00, 15, 'diabetes,insulin,glucose', 'diabetes mellitus'),
('Vitamin D3 60K', 'Cholecalciferol', 'Mankind', 'Supplement', 'sachet', 28.00, 100, 'vitamin,deficiency,bone', 'vitamin D deficiency'),
('Iron Supplement', 'Ferrous Sulfate', 'Glenmark', 'Supplement', 'tablet', 5.00, 80, 'anemia,iron,deficiency', 'iron deficiency anemia'),
('Hydrochlorothiazide 25mg', 'HCTZ', 'Sanofi', 'Diuretic', 'tablet', 4.50, 40, 'diuretic,edema,bp', 'hypertension edema');

-- Disease-medicine map (references medicine_catalog names)
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'fever', id, '500mg', 'every 6 hours', 1 FROM medicine_catalog WHERE name = 'Paracetamol 500mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'headache', id, '500mg', 'as needed', 1 FROM medicine_catalog WHERE name = 'Paracetamol 500mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'infection', id, '500mg', 'three times daily', 1 FROM medicine_catalog WHERE name = 'Amoxicillin 500mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'allergy', id, '10mg', 'once daily', 1 FROM medicine_catalog WHERE name = 'Cetirizine 10mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'acidity', id, '20mg', 'before breakfast', 1 FROM medicine_catalog WHERE name = 'Omeprazole 20mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'diabetes', id, '500mg', 'twice daily', 1 FROM medicine_catalog WHERE name = 'Metformin 500mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'cholesterol', id, '20mg', 'once daily at night', 1 FROM medicine_catalog WHERE name = 'Atorvastatin 20mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'hypertension', id, '5mg', 'once daily', 1 FROM medicine_catalog WHERE name = 'Amlodipine 5mg';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'asthma', id, '2 puffs', 'as needed', 1 FROM medicine_catalog WHERE name = 'Salbutamol Inhaler';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'diarrhea', id, '1 sachet', 'after each loose motion', 1 FROM medicine_catalog WHERE name = 'ORS Powder';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'cough', id, '10ml', 'three times daily', 1 FROM medicine_catalog WHERE name = 'Cough Syrup (Benadryl)';
INSERT INTO disease_medicine_map (disease_keyword, medicine_id, dosage, frequency, priority)
SELECT 'pain', id, '400mg', 'as needed', 1 FROM medicine_catalog WHERE name = 'Ibuprofen 400mg';

-- Rooms
INSERT INTO rooms (room_number, ward, room_type, daily_rate, capacity, occupied, floor) VALUES
('G-101', 'General Ward A', 'general', 800, 4, TRUE, 1),
('G-102', 'General Ward A', 'general', 800, 4, FALSE, 1),
('G-103', 'General Ward A', 'general', 800, 4, FALSE, 1),
('SP-201', 'Semi-Private Wing', 'semi_private', 1800, 2, TRUE, 2),
('SP-202', 'Semi-Private Wing', 'semi_private', 1800, 2, FALSE, 2),
('P-301', 'Private Suite', 'private_room', 4500, 1, TRUE, 3),
('P-302', 'Private Suite', 'private_room', 4500, 1, FALSE, 3),
('ICU-1', 'ICU', 'icu', 8000, 1, TRUE, 4),
('ICU-2', 'ICU', 'icu', 8000, 1, FALSE, 4),
('NICU-1', 'NICU', 'nicu', 6500, 1, FALSE, 4),
('OT-1', 'Operation Theatre', 'operation', 15000, 1, FALSE, 4);

-- Sample patient
INSERT INTO patients (uhid, name, age, gender, phone, email, blood_group, dpdp_consent, dpdp_consent_at) VALUES
('UHID000001', 'Rahul Mehta', 34, 'male', '9876543210', 'rahul@example.com', 'B+', TRUE, CURRENT_TIMESTAMP),
('UHID000002', 'Anita Joshi', 28, 'female', '9876543211', 'anita@example.com', 'O+', TRUE, CURRENT_TIMESTAMP),
('UHID000003', 'Suresh Reddy', 62, 'male', '9876543212', 'suresh@example.com', 'A+', TRUE, CURRENT_TIMESTAMP),
('UHID000004', 'Kavita Nair', 45, 'female', '9876543213', 'kavita@example.com', 'AB+', TRUE, CURRENT_TIMESTAMP),
('UHID000005', 'Aman Khan', 22, 'male', '9876543214', 'aman@example.com', 'O-', FALSE, NULL);
