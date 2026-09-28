-- =============================================================================
-- V1__initial_schema.sql — complete MedOS schema in a single migration.
--
-- A fresh database is fully provisioned by running this one file; there is no
-- ordered chain of migrations to replay. Consolidated from the former
-- V1__init.sql (application sequences) and V2__initial_schema.sql (all tables),
-- which were squashed on 2026-09-27 so that provisioning a database is a single
-- step. Sequences are declared first because application tables are created
-- immediately after and the repositories use them via nextval.
--
-- Originally generated from the JPA entities (PostgreSQL dialect) via
-- SchemaExportTest, then normalized: statements upper-cased, UUID primary keys
-- given defaults so inserts always carry a key even when the application layer
-- omits one, and hot-path indexes added for tenant filtering and common lookups.
--
-- This file is the single owner of the schema (see docs/adr/0005-migration-ownership.md).
-- Schema changes after this file: add V2, V3, ... — never edit this one.
-- ============================================================================

-- ---------------------------------------------------------------- application sequences
CREATE SEQUENCE IF NOT EXISTS uhid_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS invoice_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS payment_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE tenants (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    active        BOOLEAN,
    address       VARCHAR(255),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(255),
    name          VARCHAR(255),
    slug          VARCHAR(255),
    type          VARCHAR(255) CHECK (type IN ('HOSPITAL', 'CLINIC', 'INDIVIDUAL_PRACTITIONER', 'PHARMACY')),
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- domain: identity
CREATE TABLE users (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    active         BOOLEAN      NOT NULL,
    created_at     TIMESTAMP(6),
    last_login     TIMESTAMP(6),
    email          VARCHAR(128) UNIQUE,
    full_name      VARCHAR(128) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    specialization VARCHAR(128),
    username       VARCHAR(64)  NOT NULL UNIQUE,
    PRIMARY KEY (id)
);

CREATE TABLE tenant_users (
    id        UUID         NOT NULL DEFAULT gen_random_uuid(),
    role      VARCHAR(255) CHECK (role IN ('admin', 'doctor', 'nurse', 'receptionist', 'pharmacist', 'billing')),
    tenant_id UUID         NOT NULL,
    user_id   UUID         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants,
    FOREIGN KEY (user_id)   REFERENCES users
);

CREATE TABLE tenant_configs (
    id           UUID NOT NULL DEFAULT gen_random_uuid(),
    config_key   VARCHAR(64)  NOT NULL,
    config_value TEXT,
    tenant_id    UUID         NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenants
);

CREATE TABLE tenant_settings (
    tenant_id    UUID        NOT NULL,
    config_key   VARCHAR(64) NOT NULL,
    config_value TEXT,
    PRIMARY KEY (tenant_id, config_key),
    FOREIGN KEY (tenant_id) REFERENCES tenants
);

-- ---------------------------------------------------------------- domain: patients
CREATE TABLE patients (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    version         BIGINT      NOT NULL,
    age             INTEGER,
    created_at      TIMESTAMP(6),
    dpdp_consent    BOOLEAN     NOT NULL,
    dpdp_consent_at TIMESTAMP(6),
    gender          VARCHAR(16),
    outstanding     NUMERIC(12, 2),
    tenant_id       UUID        NOT NULL,
    uhid            VARCHAR(32) NOT NULL UNIQUE,
    updated_at      TIMESTAMP(6),
    -- PII columns are AES-GCM encrypted at rest; values are Base64(IV||ct||tag),
    -- longer than the plaintext, hence TEXT instead of varchar.
    address         TEXT,
    blood_group     TEXT,
    email           TEXT,
    name            TEXT        NOT NULL,
    phone           TEXT,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- domain: scheduling
CREATE TABLE appointments (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    appointment_date TIMESTAMP(6) NOT NULL,
    created_at       TIMESTAMP(6),
    doctor_id        UUID        NOT NULL,
    patient_id       UUID        NOT NULL,
    tenant_id        UUID,
    status           VARCHAR(32) CHECK (status IN ('scheduled', 'checked_in', 'completed', 'cancelled', 'no_show')),
    reason           TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE opd_queue (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    queue_number INTEGER     NOT NULL,
    called_at    TIMESTAMP(6),
    check_in_at  TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    doctor_id    UUID        NOT NULL,
    patient_id   UUID        NOT NULL,
    tenant_id    UUID,
    queue_status VARCHAR(32) CHECK (queue_status IN ('waiting', 'in_consultation', 'completed', 'cancelled')),
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- domain: clinical
CREATE TABLE encounters (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    appointment_id  UUID,
    created_at      TIMESTAMP(6),
    doctor_id       UUID        NOT NULL,
    patient_id      UUID        NOT NULL,
    signed_by       UUID,
    signed_at       TIMESTAMP(6),
    tenant_id       UUID,
    status          VARCHAR(32) CHECK (status IN ('open', 'signed', 'cancelled')),
    ai_note         TEXT,
    chief_complaint TEXT,
    clinical_notes  TEXT,
    diagnosis       TEXT,
    vitals_json     TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE prescriptions (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    dispensed_at   TIMESTAMP(6),
    encounter_id   UUID        NOT NULL,
    medicine_id    UUID        NOT NULL,
    patient_id     UUID        NOT NULL,
    prescribed_at  TIMESTAMP(6),
    prescribed_by  UUID        NOT NULL,
    tenant_id      UUID,
    status         VARCHAR(32) CHECK (status IN ('pending', 'dispensed', 'partially_dispensed', 'cancelled')),
    dosage         VARCHAR(64),
    duration       VARCHAR(64),
    frequency      VARCHAR(64),
    instructions   TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE lab_orders (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    doctor_id   UUID        NOT NULL,
    encounter_id UUID,
    ordered_at  TIMESTAMP(6),
    patient_id  UUID        NOT NULL,
    result_at   TIMESTAMP(6),
    tenant_id   UUID,
    priority    VARCHAR(32) CHECK (priority IN ('normal', 'urgent', 'stat')),
    status      VARCHAR(32) CHECK (status IN ('ordered', 'collected', 'in_progress', 'completed', 'cancelled')),
    test_code   VARCHAR(64),
    test_name   VARCHAR(128) NOT NULL,
    result      TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE rooms (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    capacity    INTEGER      NOT NULL,
    created_at  TIMESTAMP(6),
    daily_rate  NUMERIC(10, 2) NOT NULL,
    floor       INTEGER,
    occupied    BOOLEAN      NOT NULL,
    tenant_id   UUID,
    version     BIGINT       NOT NULL,
    room_number VARCHAR(32)  NOT NULL UNIQUE,
    room_type   VARCHAR(32)  NOT NULL CHECK (room_type IN ('general', 'semi_private', 'private_room', 'icu', 'nicu', 'operation')),
    ward        VARCHAR(64)  NOT NULL,
    notes       TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE admissions (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    admission_date      TIMESTAMP(6),
    created_at          TIMESTAMP(6),
    days_admitted       INTEGER,
    discharge_date      TIMESTAMP(6),
    doctor_id           UUID,
    patient_id          UUID        NOT NULL,
    room_id             UUID        NOT NULL,
    room_charges        NUMERIC(12, 2),
    tenant_id           UUID,
    version             BIGINT      NOT NULL,
    status              VARCHAR(32) CHECK (status IN ('admitted', 'discharged', 'transferred')),
    discharge_diagnosis TEXT,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- domain: pharmacy
CREATE TABLE medicine_catalog (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    active         BOOLEAN       NOT NULL,
    created_at     TIMESTAMP(6),
    reorder_level  INTEGER,
    tenant_id      UUID,
    unit           VARCHAR(32),
    unit_price     NUMERIC(10, 2) NOT NULL,
    category       VARCHAR(64),
    generic_name   VARCHAR(128),
    manufacturer   VARCHAR(128),
    name           VARCHAR(128)  NOT NULL UNIQUE,
    indications    TEXT,
    keywords       TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE medicine_batches (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    expiry_date    DATE         NOT NULL,
    remaining_qty  INTEGER      NOT NULL,
    received_date  TIMESTAMP(6),
    medicine_id    UUID         NOT NULL,
    purchase_price NUMERIC(10, 2),
    tenant_id      UUID,
    version        BIGINT       NOT NULL,
    batch_no       VARCHAR(64)  NOT NULL,
    supplier       VARCHAR(128),
    PRIMARY KEY (id),
    UNIQUE (medicine_id, batch_no)
);

CREATE TABLE stock_transactions (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    batch_id         UUID,
    medicine_id      UUID        NOT NULL,
    patient_id       UUID,
    performed_at     TIMESTAMP(6),
    performed_by     UUID,
    prescription_id  UUID,
    quantity         INTEGER     NOT NULL,
    tenant_id        UUID,
    transaction_type VARCHAR(32) NOT NULL CHECK (transaction_type IN ('in', 'out', 'adjustment', 'return_tx')),
    reference_no     VARCHAR(64),
    notes            TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE disease_medicine_map (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMP(6),
    medicine_id     UUID         NOT NULL,
    priority        INTEGER,
    dosage          VARCHAR(64),
    disease_keyword VARCHAR(128) NOT NULL UNIQUE,
    frequency       VARCHAR(64),
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- domain: billing
CREATE TABLE charges (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    admission_id UUID,
    amount       NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMP(6),
    encounter_id UUID,
    gst_amount   NUMERIC(10, 2),
    gst_percent  NUMERIC(5, 2),
    invoice_id   UUID,
    patient_id   UUID         NOT NULL,
    quantity     INTEGER,
    tenant_id    UUID,
    total_amount NUMERIC(12, 2) NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL,
    version      BIGINT       NOT NULL,
    charge_type  VARCHAR(32)  NOT NULL CHECK (charge_type IN ('consultation', 'pharmacy', 'room', 'procedure', 'lab', 'misc')),
    status       VARCHAR(32)  CHECK (status IN ('unbilled', 'billed', 'paid', 'cancelled')),
    description  VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE invoices (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    created_at     TIMESTAMP(6),
    discount       NUMERIC(10, 2),
    generated_by   UUID,
    gst_total      NUMERIC(10, 2),
    invoice_date   TIMESTAMP(6),
    paid_amount    NUMERIC(12, 2),
    patient_id     UUID         NOT NULL,
    subtotal       NUMERIC(12, 2) NOT NULL,
    tenant_id      UUID,
    total_amount   NUMERIC(12, 2) NOT NULL,
    version        BIGINT       NOT NULL,
    invoice_number VARCHAR(32)  NOT NULL UNIQUE,
    status         VARCHAR(32)  CHECK (status IN ('draft', 'issued', 'paid', 'partially_paid', 'cancelled')),
    notes          TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE payments (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    amount         NUMERIC(12, 2) NOT NULL,
    invoice_id     UUID,
    patient_id     UUID         NOT NULL,
    received_at    TIMESTAMP(6),
    received_by    UUID,
    tenant_id      UUID,
    version        BIGINT       NOT NULL,
    payment_method VARCHAR(32)  NOT NULL CHECK (payment_method IN ('cash', 'card', 'upi', 'netbanking', 'insurance', 'cheque')),
    payment_number VARCHAR(32)  NOT NULL UNIQUE,
    status         VARCHAR(32)  CHECK (status IN ('success', 'pending', 'failed', 'refunded')),
    transaction_ref VARCHAR(128),
    notes          TEXT,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- domain: compliance & ops
CREATE TABLE consents (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    expiry_date  DATE,
    granted      BOOLEAN      NOT NULL,
    granted_at   TIMESTAMP(6),
    patient_id   UUID         NOT NULL,
    consent_type VARCHAR(64)  NOT NULL,
    ip_address   VARCHAR(64),
    granted_by   VARCHAR(128),
    purpose      TEXT,
    PRIMARY KEY (id)
);

CREATE TABLE notifications (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    created_at   TIMESTAMP(6),
    read         BOOLEAN      NOT NULL,
    recipient_id UUID,
    type         VARCHAR(32)  CHECK (type IN ('info', 'warning', 'critical', 'success')),
    role_target  VARCHAR(64),
    link         VARCHAR(255),
    message      TEXT         NOT NULL,
    title        VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE audit_log (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    entity_id    UUID,
    performed_at TIMESTAMP(6),
    user_id      UUID,
    action       VARCHAR(64) NOT NULL,
    entity_type  VARCHAR(64) NOT NULL,
    ip_address   VARCHAR(64),
    new_value    TEXT,
    old_value    TEXT,
    user_agent   TEXT,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- indexes
-- tenant_id leads nearly every query through TenantStatementInspector.
CREATE INDEX idx_patients_tenant            ON patients (tenant_id);
CREATE INDEX idx_appointments_tenant        ON appointments (tenant_id);
CREATE INDEX idx_encounters_tenant          ON encounters (tenant_id);
CREATE INDEX idx_prescriptions_tenant       ON prescriptions (tenant_id);
CREATE INDEX idx_lab_orders_tenant          ON lab_orders (tenant_id);
CREATE INDEX idx_rooms_tenant               ON rooms (tenant_id);
CREATE INDEX idx_admissions_tenant          ON admissions (tenant_id);
CREATE INDEX idx_medicine_catalog_tenant    ON medicine_catalog (tenant_id);
CREATE INDEX idx_medicine_batches_tenant    ON medicine_batches (tenant_id);
CREATE INDEX idx_stock_transactions_tenant  ON stock_transactions (tenant_id);
CREATE INDEX idx_charges_tenant             ON charges (tenant_id);
CREATE INDEX idx_invoices_tenant            ON invoices (tenant_id);
CREATE INDEX idx_payments_tenant            ON payments (tenant_id);
CREATE INDEX idx_opd_queue_tenant           ON opd_queue (tenant_id);

-- Common lookups.
CREATE INDEX idx_appointments_patient       ON appointments (patient_id);
CREATE INDEX idx_encounters_patient         ON encounters (patient_id);
CREATE INDEX idx_prescriptions_patient      ON prescriptions (patient_id);
CREATE INDEX idx_prescriptions_status       ON prescriptions (status);
CREATE INDEX idx_lab_orders_patient         ON lab_orders (patient_id);
CREATE INDEX idx_admissions_patient         ON admissions (patient_id);
CREATE INDEX idx_admissions_status          ON admissions (status);
CREATE INDEX idx_charges_patient_status     ON charges (patient_id, status);
CREATE INDEX idx_charges_invoice            ON charges (invoice_id);
CREATE INDEX idx_invoices_patient           ON invoices (patient_id);
CREATE INDEX idx_payments_invoice           ON payments (invoice_id);
CREATE INDEX idx_stock_tx_medicine          ON stock_transactions (medicine_id);
CREATE INDEX idx_stock_tx_batch             ON stock_transactions (batch_id);
CREATE INDEX idx_medicine_batches_fefo      ON medicine_batches (medicine_id, expiry_date);
CREATE INDEX idx_medicine_batches_expiry    ON medicine_batches (expiry_date);
CREATE INDEX idx_audit_log_performed_at     ON audit_log (performed_at);
CREATE INDEX idx_notifications_recipient    ON notifications (recipient_id, read);
CREATE INDEX idx_opd_queue_doctor           ON opd_queue (doctor_id, queue_status);
