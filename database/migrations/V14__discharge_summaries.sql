-- V14__discharge_summaries.sql — item #69: a discharge creates a signed clinical
-- document, not just a status flip.
--
-- Until now `admissions` recorded discharge as two timestamps and a diagnosis
-- string. That cannot express document identity, signing, or corrections: a
-- later edit to an encounter silently changes what the clinician "signed", and
-- there is nowhere to record that a diagnosis was revised after discharge.
--
-- The design (docs/superpowers/specs/2026-10-29-discharge-summary-design.md)
-- stores one summary row per admission plus append-only addenda:
--
--   * `discharge_summaries` is the snapshot signed at discharge. Narrative
--     sections are frozen at signing: the service aggregates admission-window
--     encounters, vitals, prescriptions, and labs into them, so later edits to
--     the source rows cannot rewrite a signed clinical record.
--   * Corrections are courses of record, not edits: `discharge_summary_addenda`
--     rows append. No update or delete path exists for either table.
--
-- Narrative columns hold AES-GCM ciphertext (Base64 IV || ct || tag) from the
-- same EncryptionUtil converter as encounter clinical notes, so they are TEXT —
-- longer than any plaintext, like the other PII columns in V1.
--
-- Boundary wiring per the tenant-scoping contract: `tenant_id` is NOT NULL,
-- both tables are in TenantStatementInspector.TENANT_TABLES, and the composite
-- FKs follow the V9/V12 pattern so a cross-tenant reference fails at the
-- storage layer, not only in the application's ThreadLocal.
--
-- Clinician references are soft (UUID + name snapshot): `users` is a global
-- lookup table with no (tenant_id, id) key, and the signed document must not
-- change when a clinician is deactivated later — that is the point of copying
-- the name onto the row.

-- ---------------------------------------------------------------- domain: clinical records
CREATE TABLE discharge_summaries (
    id           UUID          PRIMARY KEY,
    tenant_id    UUID          NOT NULL,
    admission_id UUID          NOT NULL,
    patient_id   UUID          NOT NULL,
    -- Snapshot of the signer: the FK-capable id, plus the name as it read at
    -- signing time, so a later rename or deactivation cannot edit the record.
    discharging_clinician_id   UUID NOT NULL,
    discharging_clinician_name VARCHAR(128) NOT NULL,

    -- Signed narrative sections. All NOT NULL: empty sections are stored as a
    -- definite 'Not recorded' rather than an ambiguous NULL (design §Outcome).
    -- Every value is ciphertext; the DB never sees the plaintext diagnosis.
    admission_diagnosis     TEXT NOT NULL,
    discharge_diagnosis     TEXT NOT NULL,
    clinical_course         TEXT NOT NULL,
    procedures_performed    TEXT NOT NULL,
    investigations_summary  TEXT NOT NULL,
    vitals_summary          TEXT NOT NULL,
    medications_at_discharge TEXT NOT NULL,
    condition_at_discharge  TEXT NOT NULL,
    follow_up_advice        TEXT NOT NULL,

    -- NULL for patients not asked to return.
    next_review_date        DATE,
    signed_at               TIMESTAMP(6) NOT NULL,

    -- One summary per admission: the primary defence against a second
    -- concurrent discharge creating a duplicate document (or a second room-
    -- charge run) is the service's admission lock; this is the backstop.
    CONSTRAINT uq_discharge_summaries_tenant_admission
        UNIQUE (tenant_id, admission_id),

    -- Composite unique on (tenant_id, id) makes this table a composite-FK
    -- parent for its addenda, matching the V9 wave on every other child table.
    CONSTRAINT uq_discharge_summaries_tenant_id
        UNIQUE (tenant_id, id),

    CONSTRAINT fk_discharge_summaries_admission FOREIGN KEY (tenant_id, admission_id)
        REFERENCES admissions (tenant_id, id),
    CONSTRAINT fk_discharge_summaries_patient FOREIGN KEY (tenant_id, patient_id)
        REFERENCES patients (tenant_id, id)
);

COMMENT ON TABLE discharge_summaries IS
    'Signed discharge document; immutable snapshot of the clinical state at discharge. Corrections are append-only addenda';
COMMENT ON COLUMN discharge_summaries.tenant_id IS
    'Hospital this document belongs to; equal to the tenant of the referenced admission';

CREATE TABLE discharge_summary_addenda (
    id                   UUID          PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    discharge_summary_id UUID          NOT NULL,
    -- Ciphertext of the correction text; same converter as the summary body.
    text                 TEXT          NOT NULL,
    authored_by          UUID          NOT NULL,
    author_name          VARCHAR(128)  NOT NULL,
    created_at           TIMESTAMP(6)  NOT NULL,

    CONSTRAINT fk_discharge_summary_addenda_summary
        FOREIGN KEY (tenant_id, discharge_summary_id)
            REFERENCES discharge_summaries (tenant_id, id)
            ON DELETE CASCADE
);

COMMENT ON COLUMN discharge_summary_addenda.text IS
    'AES-GCM ciphertext of the correction; appended after signing and never rewritten';

-- Summary reads key off the admission history; the unique constraint above
-- already covers (tenant_id, admission_id).
CREATE INDEX idx_discharge_summaries_tenant   ON discharge_summaries (tenant_id);
CREATE INDEX idx_discharge_summaries_patient  ON discharge_summaries (tenant_id, patient_id);

-- "All corrections to this summary, oldest first" — the addendum read path.
CREATE INDEX idx_discharge_summary_addenda_summary
    ON discharge_summary_addenda (discharge_summary_id, created_at);
