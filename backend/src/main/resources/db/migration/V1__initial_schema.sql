-- MEDOS HMS v3.0 - Initial Schema
-- 19 entities interconnected through UUID primary keys
-- Tables are ordered to satisfy foreign key dependencies.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================
-- CORE ENTITIES (no FK dependencies)
-- =========================================================

-- 1. USERS
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(64) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    email VARCHAR(128) UNIQUE,
    role VARCHAR(32) NOT NULL CHECK (role IN ('admin','doctor','nurse','receptionist','pharmacist','billing')),
    specialization VARCHAR(128),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- 2. PATIENTS
CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    uhid VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    age INT CHECK (age >= 0 AND age <= 150),
    gender VARCHAR(16),
    phone VARCHAR(20),
    email VARCHAR(128),
    address TEXT,
    blood_group VARCHAR(8),
    dpdp_consent BOOLEAN DEFAULT FALSE,
    dpdp_consent_at TIMESTAMP,
    outstanding DECIMAL(12,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. ROOMS
CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    room_number VARCHAR(32) UNIQUE NOT NULL,
    ward VARCHAR(64) NOT NULL,
    room_type VARCHAR(32) NOT NULL CHECK (room_type IN ('general','semi_private','private_room','icu','nicu','operation')),
    daily_rate DECIMAL(10,2) NOT NULL,
    capacity INT DEFAULT 1,
    occupied BOOLEAN DEFAULT FALSE,
    floor INT DEFAULT 1,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. MEDICINE CATALOG (must be before prescriptions/stock)
CREATE TABLE medicine_catalog (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(128) UNIQUE NOT NULL,
    generic_name VARCHAR(128),
    manufacturer VARCHAR(128),
    category VARCHAR(64),
    unit VARCHAR(32),
    unit_price DECIMAL(10,2) NOT NULL,
    reorder_level INT DEFAULT 10,
    keywords TEXT,
    indications TEXT,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- SECONDARY ENTITIES (depend on core)
-- =========================================================

-- 5. APPOINTMENTS (depends on patients, users)
CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    appointment_date TIMESTAMP NOT NULL,
    status VARCHAR(32) DEFAULT 'scheduled' CHECK (status IN ('scheduled','checked_in','completed','cancelled','no_show')),
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. ENCOUNTERS (depends on patients, users, appointments)
CREATE TABLE encounters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    appointment_id UUID REFERENCES appointments(id),
    status VARCHAR(32) DEFAULT 'open' CHECK (status IN ('open','signed','cancelled')),
    chief_complaint TEXT,
    diagnosis TEXT,
    clinical_notes TEXT,
    vitals_json TEXT,
    ai_note TEXT,
    signed_at TIMESTAMP,
    signed_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. MEDICINE BATCHES (depends on medicine_catalog)
CREATE TABLE medicine_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    medicine_id UUID NOT NULL REFERENCES medicine_catalog(id),
    batch_no VARCHAR(64) NOT NULL,
    expiry_date DATE NOT NULL,
    remaining_qty INT NOT NULL DEFAULT 0,
    purchase_price DECIMAL(10,2),
    supplier VARCHAR(128),
    received_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(medicine_id, batch_no)
);

-- 8. ADMISSIONS (depends on patients, rooms, users)
CREATE TABLE admissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    room_id UUID NOT NULL REFERENCES rooms(id),
    doctor_id UUID REFERENCES users(id),
    admission_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    discharge_date TIMESTAMP,
    status VARCHAR(32) DEFAULT 'admitted' CHECK (status IN ('admitted','discharged','transferred')),
    discharge_diagnosis TEXT,
    room_charges DECIMAL(12,2) DEFAULT 0,
    days_admitted INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. PRESCRIPTIONS (depends on encounters, patients, medicine_catalog, users)
CREATE TABLE prescriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    encounter_id UUID NOT NULL REFERENCES encounters(id),
    patient_id UUID NOT NULL REFERENCES patients(id),
    medicine_id UUID NOT NULL REFERENCES medicine_catalog(id),
    dosage VARCHAR(64),
    frequency VARCHAR(64),
    duration VARCHAR(64),
    instructions TEXT,
    status VARCHAR(32) DEFAULT 'pending' CHECK (status IN ('pending','dispensed','partially_dispensed','cancelled')),
    prescribed_by UUID NOT NULL REFERENCES users(id),
    prescribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. LAB ORDERS (depends on encounters, patients, users)
CREATE TABLE lab_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    encounter_id UUID REFERENCES encounters(id),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    test_name VARCHAR(128) NOT NULL,
    test_code VARCHAR(64),
    priority VARCHAR(32) DEFAULT 'normal' CHECK (priority IN ('normal','urgent','stat')),
    status VARCHAR(32) DEFAULT 'ordered' CHECK (status IN ('ordered','collected','in_progress','completed','cancelled')),
    result TEXT,
    result_at TIMESTAMP,
    ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PHARMACY TRANSACTIONS
-- =========================================================

-- 11. STOCK TRANSACTIONS (depends on medicine_catalog, medicine_batches, patients, prescriptions, users)
CREATE TABLE stock_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    medicine_id UUID NOT NULL REFERENCES medicine_catalog(id),
    batch_id UUID REFERENCES medicine_batches(id),
    transaction_type VARCHAR(32) NOT NULL CHECK (transaction_type IN ('in','out','adjustment','return_tx')),
    quantity INT NOT NULL,
    patient_id UUID REFERENCES patients(id),
    prescription_id UUID REFERENCES prescriptions(id),
    reference_no VARCHAR(64),
    notes TEXT,
    performed_by UUID REFERENCES users(id),
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- BILLING CHAIN
-- =========================================================

-- 12. INVOICES (depends on patients, users)
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number VARCHAR(32) UNIQUE NOT NULL,
    patient_id UUID NOT NULL REFERENCES patients(id),
    invoice_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    subtotal DECIMAL(12,2) NOT NULL,
    gst_total DECIMAL(10,2) DEFAULT 0,
    discount DECIMAL(10,2) DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    paid_amount DECIMAL(12,2) DEFAULT 0,
    status VARCHAR(32) DEFAULT 'issued' CHECK (status IN ('draft','issued','paid','partially_paid','cancelled')),
    generated_by UUID REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 13. CHARGES (depends on patients, encounters, admissions, invoices)
CREATE TABLE charges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    encounter_id UUID REFERENCES encounters(id),
    admission_id UUID REFERENCES admissions(id),
    charge_type VARCHAR(32) NOT NULL CHECK (charge_type IN ('consultation','pharmacy','room','procedure','lab','misc')),
    description VARCHAR(255) NOT NULL,
    quantity INT DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    gst_percent DECIMAL(5,2) DEFAULT 0,
    gst_amount DECIMAL(10,2) DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    invoice_id UUID REFERENCES invoices(id),
    status VARCHAR(32) DEFAULT 'unbilled' CHECK (status IN ('unbilled','billed','paid','cancelled')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14. PAYMENTS (depends on invoices, patients, users)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_number VARCHAR(32) UNIQUE NOT NULL,
    invoice_id UUID REFERENCES invoices(id),
    patient_id UUID NOT NULL REFERENCES patients(id),
    amount DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(32) NOT NULL CHECK (payment_method IN ('cash','card','upi','netbanking','insurance','cheque')),
    transaction_ref VARCHAR(128),
    status VARCHAR(32) DEFAULT 'success' CHECK (status IN ('success','pending','failed','refunded')),
    received_by UUID REFERENCES users(id),
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- =========================================================
-- COMPLIANCE & QUEUE & NOTIFICATIONS
-- =========================================================

-- 15. DISEASE MEDICINE MAP (depends on medicine_catalog)
CREATE TABLE disease_medicine_map (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    disease_keyword VARCHAR(128) UNIQUE NOT NULL,
    medicine_id UUID NOT NULL REFERENCES medicine_catalog(id),
    dosage VARCHAR(64),
    frequency VARCHAR(64),
    priority INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 16. AUDIT LOG (depends on users)
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID,
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(64),
    user_agent TEXT,
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 17. CONSENTS / DPDP (depends on patients)
CREATE TABLE consents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    consent_type VARCHAR(64) NOT NULL,
    granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(128),
    purpose TEXT,
    expiry_date DATE,
    ip_address VARCHAR(64)
);

-- 18. OPD QUEUE (depends on patients, users)
CREATE TABLE opd_queue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    queue_number INT NOT NULL,
    queue_status VARCHAR(32) DEFAULT 'waiting' CHECK (queue_status IN ('waiting','in_consultation','completed','cancelled')),
    check_in_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- 19. NOTIFICATIONS (depends on users)
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient_id UUID REFERENCES users(id),
    role_target VARCHAR(64),
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(32) DEFAULT 'info' CHECK (type IN ('info','warning','critical','success')),
    read BOOLEAN DEFAULT FALSE,
    link VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- INDEXES for performance
-- =========================================================
CREATE INDEX idx_patients_uhid ON patients(uhid);
CREATE INDEX idx_appointments_patient ON appointments(patient_id);
CREATE INDEX idx_appointments_doctor ON appointments(doctor_id);
CREATE INDEX idx_appointments_date ON appointments(appointment_date);
CREATE INDEX idx_encounters_patient ON encounters(patient_id);
CREATE INDEX idx_encounters_doctor ON encounters(doctor_id);
CREATE INDEX idx_encounters_status ON encounters(status);
CREATE INDEX idx_prescriptions_patient ON prescriptions(patient_id);
CREATE INDEX idx_prescriptions_encounter ON prescriptions(encounter_id);
CREATE INDEX idx_prescriptions_status ON prescriptions(status);
CREATE INDEX idx_admissions_patient ON admissions(patient_id);
CREATE INDEX idx_admissions_room ON admissions(room_id);
CREATE INDEX idx_admissions_status ON admissions(status);
CREATE INDEX idx_batches_medicine ON medicine_batches(medicine_id);
CREATE INDEX idx_batches_expiry ON medicine_batches(expiry_date);
CREATE INDEX idx_stock_medicine ON stock_transactions(medicine_id);
CREATE INDEX idx_stock_date ON stock_transactions(performed_at);
CREATE INDEX idx_charges_patient ON charges(patient_id);
CREATE INDEX idx_charges_status ON charges(status);
CREATE INDEX idx_charges_invoice ON charges(invoice_id);
CREATE INDEX idx_invoices_patient ON invoices(patient_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);
CREATE INDEX idx_payments_patient ON payments(patient_id);
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_date ON audit_log(performed_at);
CREATE INDEX idx_notif_recipient ON notifications(recipient_id);
CREATE INDEX idx_notif_read ON notifications(read);
