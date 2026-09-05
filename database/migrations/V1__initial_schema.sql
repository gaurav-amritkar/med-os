-- ============================================================================
-- MEDOS HMS v3.0 - Complete Schema
-- Single migration that creates the full database from scratch.
--
-- Sections:
--   1. Extensions
--   2. Enumerated types (CHECK constraints via app-level validation)
--   3. Core tables (users, patients, rooms, medicine_catalog)
--   4. Clinical tables (appointments, encounters, medicine_batches, admissions,
--      prescriptions, stock_transactions, lab_orders, opd_queue, disease_medicine_map)
--   5. Billing tables (invoices, charges, payments)
--   6. Compliance & misc (audit_log, consents, notifications)
--   7. Performance indexes
--   8. Optimistic locking columns (@Version)
--   9. Sequences (uhid, invoice_number, payment_number)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Extensions
-- ----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ----------------------------------------------------------------------------
-- 2-6. Tables (ordered to satisfy FK dependencies)
-- ----------------------------------------------------------------------------

-- ===== USERS =====
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

-- ===== PATIENTS (PII columns widened to accommodate AES-GCM ciphertext) =====
CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    uhid VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(512) NOT NULL,                -- encrypted, ~1.34x + 40 chars
    age INT CHECK (age >= 0 AND age <= 150),
    gender VARCHAR(16),
    phone VARCHAR(128),                         -- encrypted
    email VARCHAR(512),                         -- encrypted
    address TEXT,                               -- encrypted
    blood_group VARCHAR(64),                    -- encrypted
    dpdp_consent BOOLEAN DEFAULT FALSE,
    dpdp_consent_at TIMESTAMP,
    outstanding DECIMAL(12,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== ROOMS =====
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ===== MEDICINE CATALOG =====
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

-- ===== APPOINTMENTS =====
CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    appointment_date TIMESTAMP NOT NULL,
    status VARCHAR(32) DEFAULT 'scheduled' CHECK (status IN ('scheduled','checked_in','completed','cancelled','no_show')),
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== ENCOUNTERS (clinical notes encrypted as TEXT) =====
CREATE TABLE encounters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    appointment_id UUID REFERENCES appointments(id),
    status VARCHAR(32) DEFAULT 'open' CHECK (status IN ('open','signed','cancelled')),
    chief_complaint TEXT,                      -- encrypted
    diagnosis TEXT,                            -- encrypted
    clinical_notes TEXT,                       -- encrypted
    vitals_json TEXT,
    ai_note TEXT,                              -- encrypted
    signed_at TIMESTAMP,
    signed_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== MEDICINE BATCHES =====
CREATE TABLE medicine_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    medicine_id UUID NOT NULL REFERENCES medicine_catalog(id),
    batch_no VARCHAR(64) NOT NULL,
    expiry_date DATE NOT NULL,
    remaining_qty INT NOT NULL DEFAULT 0,
    purchase_price DECIMAL(10,2),
    supplier VARCHAR(128),
    received_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(medicine_id, batch_no),
    version BIGINT NOT NULL DEFAULT 0
);

-- ===== ADMISSIONS =====
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ===== PRESCRIPTIONS =====
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
    prescribed_by UUID REFERENCES users(id),
    prescribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    dispensed_at TIMESTAMP
);

-- ===== STOCK TRANSACTIONS =====
CREATE TABLE stock_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    medicine_id UUID NOT NULL REFERENCES medicine_catalog(id),
    batch_id UUID REFERENCES medicine_batches(id),
    transaction_type VARCHAR(16) NOT NULL CHECK (transaction_type IN ('stock_in','stock_out','adjustment','return')),
    quantity INT NOT NULL,
    reference_type VARCHAR(32),                 -- 'prescription', 'purchase', 'manual'
    reference_id UUID,
    patient_id UUID REFERENCES patients(id),
    performed_by UUID NOT NULL REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== INVOICES =====
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number VARCHAR(32) UNIQUE NOT NULL,
    patient_id UUID NOT NULL REFERENCES patients(id),
    generated_by UUID REFERENCES users(id),
    subtotal DECIMAL(12,2) NOT NULL,
    total_gst DECIMAL(10,2) DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) DEFAULT 'issued' CHECK (status IN ('issued','paid','partially_paid','cancelled')),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ===== LAB ORDERS =====
CREATE TABLE lab_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    encounter_id UUID REFERENCES encounters(id),
    test_name VARCHAR(128) NOT NULL,
    test_code VARCHAR(64),
    status VARCHAR(32) DEFAULT 'ordered' CHECK (status IN ('ordered','in_progress','completed','cancelled')),
    result TEXT,
    ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- ===== OPD QUEUE =====
CREATE TABLE opd_queue (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES users(id),
    encounter_id UUID REFERENCES encounters(id),
    queue_number INT NOT NULL,
    priority INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'waiting' CHECK (status IN ('waiting','in_consultation','completed','skipped')),
    checked_in_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- ===== CHARGES =====
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

-- ===== PAYMENTS =====
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

-- ===== AUDIT LOG =====
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64),
    entity_id UUID,
    details JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== CONSENTS =====
CREATE TABLE consents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    consent_type VARCHAR(64) NOT NULL,
    granted BOOLEAN NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    notes TEXT
);

-- ===== NOTIFICATIONS =====
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient_id UUID REFERENCES users(id),
    type VARCHAR(32) NOT NULL,
    title VARCHAR(128),
    message TEXT,
    link VARCHAR(255),
    read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===== DISEASE-MEDICINE MAP (AI advisor lookup) =====
CREATE TABLE disease_medicine_map (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    disease VARCHAR(128) NOT NULL,
    medicine_id UUID NOT NULL REFERENCES medicine_catalog(id),
    confidence_score DECIMAL(3,2) DEFAULT 0.50,
    UNIQUE(disease, medicine_id)
);

-- ----------------------------------------------------------------------------
-- 7. Performance indexes
-- ----------------------------------------------------------------------------

-- Patient search
CREATE INDEX idx_patients_name ON patients(name);
CREATE INDEX idx_patients_phone ON patients(phone);
CREATE INDEX idx_patients_email ON patients(email);
CREATE INDEX idx_patients_created ON patients(created_at);

-- Appointments: queue lookups
CREATE INDEX idx_appointments_status ON appointments(status);

-- Encounters: signing workflow + patient timeline
CREATE INDEX idx_encounters_created ON encounters(created_at);

-- Prescriptions: pharmacy dispatch queue
CREATE INDEX idx_prescriptions_prescribed_at ON prescriptions(prescribed_at);

-- Admissions: active + history
CREATE INDEX idx_admissions_created ON admissions(created_at);

-- Medicine batches: low-stock + expiry
CREATE INDEX idx_batches_remaining ON medicine_batches(remaining_qty);

-- Stock transactions: audit
CREATE INDEX idx_stock_performed_by ON stock_transactions(performed_by);

-- Financial aging
CREATE INDEX idx_invoices_created ON invoices(created_at);
CREATE INDEX idx_payments_received_at ON payments(received_at);

-- Lab orders
CREATE INDEX idx_lab_patient ON lab_orders(patient_id);
CREATE INDEX idx_lab_status ON lab_orders(status);
CREATE INDEX idx_lab_ordered ON lab_orders(ordered_at);

-- OPD queue
CREATE INDEX idx_opd_doctor ON opd_queue(doctor_id);
CREATE INDEX idx_opd_status ON opd_queue(status);

-- Charges (billing lookups)
CREATE INDEX idx_charges_patient ON charges(patient_id);
CREATE INDEX idx_charges_invoice ON charges(invoice_id);
CREATE INDEX idx_charges_status ON charges(status);

-- Audit
CREATE INDEX idx_audit_user ON audit_log(user_id);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);

-- ----------------------------------------------------------------------------
-- 8. Optimistic locking columns are inlined in table definitions above
-- (version BIGINT NOT NULL DEFAULT 0 for @Version entities)
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 9. Sequences for atomic ID generation
--    - uhid_seq: patient UHID counter (used by PatientRepository.getNextUhidSeq)
--    - invoice_number_seq: invoice number counter
--    - payment_number_seq: payment number counter
-- ----------------------------------------------------------------------------
CREATE SEQUENCE uhid_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE payment_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
