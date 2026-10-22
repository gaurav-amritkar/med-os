-- V7__enquiry_desk.sql — Wave 1, first front-desk module: the Enquiry Desk.
--
-- A receptionist logged the walk-in/phone contacts MedWorks' Enquiry Desk
-- captures before a person becomes a patient: who called, why they came
-- (reason/department), where it went (status trail). Beyond the word "lead",
-- this is a DPDP-friendly capture surface: contact details are plaintext
-- business-card data (not PII clinical fields), and every status change leaves
-- its own history row so the desk answers "who promised to call back when?"
-- from the record, not from memory.
--
-- Conventions: UUID pk, tenant_id on both tables, created/updated stamps,
-- explicit CHECKs on the enum-like text columns (Postgres-native CHECKs, not
-- DB types — matching V1..V6), FK to tenants ON DELETE CASCADE.
--
-- Display ref: enquiries.enquiry_no is an INT serial *per tenant* ("ENQ-<no>"
-- is rendered on the page). Per-tenant numbering keeps the desk's board
-- faithful to the MedWorks grammar (Enquiry #, Contact, Reason/Department...)
-- without leaking global counts.

CREATE TABLE enquiries (
    id                  UUID         PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    -- Per-tenant sequence number starting at 1. Assigned by the service as
    -- (tenant max + 1) inside a transaction; the unique index below makes a
    -- concurrent double-assign fail loudly so the desk retries, never reuses.
    enquiry_no          INT          NOT NULL,
    -- Who made the enquiry. Business-card fields, deliberately not in the
    -- PII-encryption path (no diagnosis/clinical context lives here).
    contact_name        TEXT         NOT NULL,
    contact_phone       TEXT,
    contact_email       TEXT,
    -- Why they came: the MedWorks column is Reason/Department — typed
    -- enqiry_type + optional department + free-text reason.
    enquiry_type        TEXT         NOT NULL DEFAULT 'general',
    department          TEXT,
    reason              TEXT         NOT NULL,
    -- Desk lifecycle: new_enquiry | follow_up | converted | cancelled. (Java
    -- cannot name a constant `new`; the page renders the labels.) Transitions
    -- are audited into enquiry_status_history; conversions may later reference
    -- the patient they became (Patients Hub deepening, this wave).
    status              TEXT         NOT NULL DEFAULT 'new_enquiry',
    notes               TEXT,
    converted_patient_id UUID,
    created_by          UUID,
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP(6),
    CONSTRAINT fk_enquiries_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants ON DELETE CASCADE,
    CONSTRAINT fk_enquiries_converted_patient FOREIGN KEY (converted_patient_id)
        REFERENCES patients ON DELETE SET NULL,
    CONSTRAINT chk_enquiries_no CHECK (enquiry_no >= 1),
    CONSTRAINT chk_enquiries_status CHECK (status IN
        ('new_enquiry', 'follow_up', 'converted', 'cancelled')),
    CONSTRAINT chk_enquiries_type CHECK (enquiry_type IN
        ('general', 'appointment', 'admission', 'emergency', 'pharmacy', 'other'))
);

-- The desk board lists one tenant's enquiries newest-first; the status filter
-- is the radio row, so status leads the composite.
CREATE INDEX idx_enquiries_tenant_board ON enquiries (tenant_id, status, created_at DESC);
-- Uniqueness of the human reference inside a tenant (global numbers would
-- count other tenants' volume into the desk's numbering).
CREATE UNIQUE INDEX uq_enquiries_tenant_no ON enquiries (tenant_id, enquiry_no);

CREATE TABLE enquiry_status_history (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    enquiry_id   UUID         NOT NULL,
    -- NULL from_status = the enquiry's creation row.
    from_status  TEXT,
    to_status    TEXT         NOT NULL,
    note         TEXT,
    changed_by   UUID,
    changed_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_enquiry_status_history_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants ON DELETE CASCADE,
    CONSTRAINT fk_enquiry_status_history_enquiry FOREIGN KEY (enquiry_id)
        REFERENCES enquiries ON DELETE CASCADE,
    CONSTRAINT chk_enquiry_status_history_status CHECK (to_status IN
        ('new_enquiry', 'follow_up', 'converted', 'cancelled'))
);

-- The desk's question about an enquiry is "what happened to it" — answer it
-- newest-first.
CREATE INDEX idx_enquiry_status_history_enquiry
    ON enquiry_status_history (enquiry_id, changed_at DESC);
