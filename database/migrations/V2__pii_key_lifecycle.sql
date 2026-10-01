-- =============================================================================
-- V2__pii_key_lifecycle.sql — schema for rotatable PII keys.
--
-- One data encryption key (DEK) per tenant, wrapped by a key-encryption key
-- (KEK). Rotating the KEK re-wraps the rows in tenant_keys; it never
-- re-encrypts patient data. That is what makes rotation an online operation
-- with no downtime, and it is the reason the DEK is per tenant rather than
-- global: rotating one hospital's key rewrites only that hospital's rows, and
-- offboarding a tenant means destroying its DEK alone.
--
-- Design: docs/superpowers/specs/2026-10-01-pii-key-lifecycle-fhir-abdm-design.md
-- Decision: docs/adr/0009-envelope-encryption-per-tenant-deks.md
--
-- ONLY WRAPPED KEY MATERIAL IS STORED HERE. wrapped_dek and wrapped_bi_key are
-- ciphertext produced by the KEK. A plaintext DEK reaching this table would
-- defeat envelope encryption entirely: a database-only compromise would then be
-- enough to read every patient's records.
--
-- The blind index gets its own wrapped key rather than sharing the PII secret.
-- It currently derives from that secret, which means a PII rotation silently
-- invalidates every patients.name_index and search returns nothing with no
-- error. An independent key makes that a deliberate choice instead of a side
-- effect.
-- ============================================================================

-- ---------------------------------------------------------------- key material
CREATE TABLE tenant_keys (
    tenant_id          UUID         NOT NULL,
    -- AES-GCM(KEK, DEK) and AES-GCM(KEK, blindIndexKey). BYTEA, never TEXT: a
    -- DEK must not be storable or transmittable as a string.
    wrapped_dek        BYTEA        NOT NULL,
    wrapped_bi_key     BYTEA,
    -- Which DEK generation encrypted this tenant's data. Starts at 1. A DEK
    -- rotation increments it, and ciphertext carries the generation it was
    -- written with so a partially-rotated tenant still reads correctly.
    dek_generation     INT          NOT NULL DEFAULT 1,
    bi_key_generation  INT          NOT NULL DEFAULT 1,
    -- Which KEK version currently wraps the columns above. This is what makes a
    -- re-wrap resumable: a row already wrapped by version N can be identified
    -- and skipped, and the dry run can report real progress. Without it a
    -- re-wrap cannot tell finished rows from pending ones.
    wrapped_kek_version INT         NOT NULL DEFAULT 1,
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- One row per tenant. A tenant must not be able to accumulate two competing
    -- DEKs, which would make it ambiguous which one its data is under.
    PRIMARY KEY (tenant_id),
    -- Deleting a tenant must take its key material with it. A retained DEK for a
    -- departed tenant would keep that hospital's data readable forever.
    FOREIGN KEY (tenant_id) REFERENCES tenants ON DELETE CASCADE,
    CONSTRAINT chk_tenant_keys_dek_generation  CHECK (dek_generation >= 1),
    CONSTRAINT chk_tenant_keys_bi_generation  CHECK (bi_key_generation >= 1),
    CONSTRAINT chk_tenant_keys_kek_version    CHECK (wrapped_kek_version >= 1)
);

-- ---------------------------------------------------------------- rotation log
-- An append-only record of every key operation, so a rotation is demonstrable
-- rather than merely asserted. DPDP Act 2023 s8(5) requires "reasonable
-- security safeguards"; evidence that keys are rotated, when, and by whom is
-- part of meeting that.
CREATE TABLE key_rotations (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    -- 'kek_rewrap' re-wraps DEKs and touches no patient data; 'dek_rotation'
    -- re-encrypts one tenant's ciphertext. They are recorded separately because
    -- they differ in cost, blast radius, and whether they are reversible.
    operation        VARCHAR(32)  NOT NULL,
    -- KEK version installed by a re-wrap, or the DEK generation a rotation moved to.
    kek_version      INT,
    dek_generation   INT,
    -- NULL for a fleet-wide re-wrap, which is the normal case: one row records the
    -- whole operation rather than one per tenant.
    tenant_id        UUID,
    started_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at     TIMESTAMP(6),
    rows_rewritten   INT,
    -- 'in_progress' then 'completed' or 'failed'. A re-wrap interrupted midway
    -- stays 'in_progress' and is completed by re-running it.
    status           VARCHAR(16)  NOT NULL DEFAULT 'in_progress',
    initiated_by     VARCHAR(255),
    notes            TEXT,
    PRIMARY KEY (id),
    CONSTRAINT fk_key_rotations_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants ON DELETE CASCADE,
    -- Constrained rather than free text: a typo here would make rotation reports
    -- quietly wrong, which is worse than no report.
    CONSTRAINT chk_key_rotations_operation CHECK (
        operation IN ('kek_rewrap', 'dek_rotation')),
    CONSTRAINT chk_key_rotations_status CHECK (
        status IN ('in_progress', 'completed', 'failed')),
    -- A single-tenant DEK rotation must name its tenant; a fleet-wide re-wrap must
    -- not. Enforced here so an operator cannot record an operation that is
    -- unattributable.
    CONSTRAINT chk_key_rotations_scope CHECK (
        (operation = 'dek_rotation' AND tenant_id IS NOT NULL) OR
        (operation = 'kek_rewrap'    AND tenant_id IS NULL))
);

CREATE INDEX idx_key_rotations_operation ON key_rotations (operation, started_at DESC);
CREATE INDEX idx_key_rotations_tenant   ON key_rotations (tenant_id) WHERE tenant_id IS NOT NULL;
