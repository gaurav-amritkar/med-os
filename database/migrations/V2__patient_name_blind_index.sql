-- =============================================================================
-- V2__patient_name_blind_index.sql — make patient search work on an
-- encrypted column.
--
-- patients.name holds AES-256-GCM ciphertext, which is randomised per value, so
-- `WHERE name LIKE '%anita%'` can never match. Patient search therefore returned
-- nothing at all, and a receptionist who knew only a name could not find an
-- existing patient — which silently turns every repeat visit into a duplicate
-- registration.
--
-- This adds a keyed blind index: a deterministic HMAC-SHA256 of the normalised
-- name, derived from the PII encryption key through a fixed domain label. It is
-- matched by equality only, since a blind index cannot support substring
-- matching. UHID remains unencrypted and supports partial matching directly.
--
-- The index is as sensitive as the plaintext it protects: anyone holding the
-- database but not the key cannot read names, but can confirm a guessed name by
-- comparing digests. Rotating the PII key requires recomputing every row
-- (NEXT_STEPS.md item T6).
--
-- V1 remains the single complete baseline for a fresh database; this is a
-- subsequent change and is never folded into it.
-- ============================================================================

-- VARCHAR, not CHAR(64): PostgreSQL maps CHAR(n) to bpchar, which Hibernate's
-- ddl-auto=validate rejects for a length-declared String (found bpchar,
-- expecting varchar). Always VARCHAR(64).
--
-- Note: this file was first applied with CHAR(64) in a development database.
-- That column was corrected in place with
--   ALTER TABLE patients ALTER COLUMN name_index TYPE VARCHAR(64);
-- so the checksum here matches what has been applied. A fresh database is
-- unaffected. Do not change the type below without a V3.
ALTER TABLE patients ADD COLUMN IF NOT EXISTS name_index VARCHAR(64);

-- Supporting index for UHID-substring search, which is now part of patient
-- search. The unique index on uhid already exists; this one serves LIKE 'UHID%'.
CREATE INDEX IF NOT EXISTS idx_patients_uhid_prefix ON patients (uhid);
