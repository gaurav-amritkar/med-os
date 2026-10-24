-- V13__dek_rotation_evidence.sql — the full compromise-rotation flow needs to
-- record two facts the primitive rotation (§7e) did not.
--
-- `backup_ref` is the operator's verified restore point. A DEK rotation rewrites
-- ciphertext and is not reversible by restoring a key file, so the audit row must
-- name the backup that would be used if the run had to be undone. The runner
-- refuses to start without one, and this column is where the acceptance lands.
--
-- `bi_key_rotated` records that the tenant's blind-index key was rotated as part
-- of the same incident. When it is true, `patients.name_index` must be rebuilt
-- for every named patient; a resumable run needs to know whether re-indexing is
-- still required rather than inferring it from an ambiguous partial state.
--
-- Both columns are nullable/defaulted so the migration is safe against rows
-- written by the primitive rotation before this version.
--
-- Issue #91.

ALTER TABLE key_rotations
    ADD COLUMN backup_ref VARCHAR(512);

ALTER TABLE key_rotations
    ADD COLUMN bi_key_rotated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN key_rotations.backup_ref IS
    'Verified backup / restore point that makes this non-reversible rotation recoverable';

COMMENT ON COLUMN key_rotations.bi_key_rotated IS
    'True when the tenant blind-index key was rotated too, so name_index was rebuilt';
