-- V12__consent_tenant_scope.sql — consent becomes a tenant-owned, append-only event (issue #114).
--
-- `consents` was created in V1 but never wired to the tenant boundary: it had no
-- `tenant_id`, so the read-side inspector could not scope it and a consent row
-- could not be traced to the hospital that captured it. It was also only written
-- from registration, which meant consent captured later at the front desk had no
-- path at all.
--
-- This migration does three things:
--
--   1. Gives every consent row a tenant, derived from its patient. The patient is
--      the authority: a consent is about that person's data, recorded by the
--      hospital that holds the record.
--   2. Adds `notice_version`, so an append-only history can say which DPDP notice
--      the patient actually saw. Deliberately nullable: rows written before V12
--      were captured without a recorded notice, and inventing a version for them
--      would assert something that never happened.
--   3. Makes `tenant_id` NOT NULL and adds the lookup index the history reads need.
--
-- No data is deleted or rewritten. Existing rows keep their values; they gain the
-- tenant of their patient.
--
-- A consent row that has no matching patient cannot be assigned a tenant. Rather
-- than leave a NULL that would violate the new constraint at the end of this
-- migration (with an opaque error), the migration fails early and says which ids
-- are orphaned. There are no such rows in any current database — V1 wrote them
-- only from `PatientService`, immediately after the patient insert.

DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM consents c
    LEFT JOIN patients p ON p.id = c.patient_id
    WHERE p.id IS NULL;

    IF orphan_count > 0 THEN
        RAISE EXCEPTION
            'V12: % consent row(s) reference a patient that does not exist; '
            'tenant_id cannot be derived. Inspect consents LEFT JOIN patients.',
            orphan_count;
    END IF;
END $$;

ALTER TABLE consents ADD COLUMN tenant_id UUID;
ALTER TABLE consents ADD COLUMN notice_version VARCHAR(32);

UPDATE consents c
SET tenant_id = p.tenant_id
FROM patients p
WHERE p.id = c.patient_id;

ALTER TABLE consents ALTER COLUMN tenant_id SET NOT NULL;

COMMENT ON COLUMN consents.tenant_id IS
    'Hospital that captured this consent event; derived from the patient and enforced NOT NULL';
COMMENT ON COLUMN consents.notice_version IS
    'DPDP notice version shown to the patient. NULL for rows captured before V12';

-- The history query is "all consent events for this patient in this tenant,
-- newest first". The patient is already tenant-scoped, but the leading tenant_id
-- keeps the scoped read cheap and matches the inspector's predicate.
CREATE INDEX idx_consents_tenant_patient_granted_at
    ON consents (tenant_id, patient_id, granted_at DESC);

-- A consent event references a patient in the same tenant. Composite FK, matching
-- the V9 pattern: a cross-tenant consent is then impossible at the storage layer,
-- not just in the application's ThreadLocal.
ALTER TABLE consents
    ADD CONSTRAINT fk_consents_patient FOREIGN KEY (tenant_id, patient_id)
        REFERENCES patients (tenant_id, id) ON DELETE CASCADE;
