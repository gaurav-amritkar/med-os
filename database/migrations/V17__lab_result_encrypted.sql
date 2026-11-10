-- V17__lab_result_encrypted.sql — encrypt lab results at rest (issue #237).
--
-- `lab_orders.result` has been TEXT in the clear since V1, so a database dump,
-- a replica, or anyone with read access to that column could read clinical
-- results. Every other clinical free-text column already goes through
-- EncryptionUtil (`@Convert`), which writes `kv1.d<N>:<base64>` ciphertext and
-- never stores plaintext.
--
-- The column itself does not change type — the converter stores its ciphertext
-- in the same TEXT column, so no rewrite of existing rows is needed for the
-- application to read them. What this migration does is the part the
-- application cannot safely do for itself:
--
--   1. Backfill every existing plaintext result into ciphertext, in place,
--      using the same envelope-encryption path the entity converter uses.
--      This must run while the application is stopped, or a concurrent write
--      could store plaintext over the backfill.
--   2. Record which rows were backfilled, so the operation is auditable and a
--      re-run can tell "already encrypted" from "never had a result".
--
-- The backfill is performed by the application's own `EncryptionUtil` through
-- a small Java runner rather than by SQL: AES-256-GCM needs the tenant's DEK,
-- which is wrapped by the KEK held outside the database. SQL cannot do that,
-- and inventing a second encryption path in the migration would be worse than
-- the plaintext it replaces.
--
-- Deployment order: run V17's schema steps (the bookkeeping table below), then
-- the backfill runner, then start the new application version. The runner
-- writes rows that are already in the ciphertext form, so it is idempotent and
-- safe to re-run after a partial failure.
--
-- The bookkeeping table is deliberately tiny: one row per backfilled result,
-- nothing else. It holds no clinical content.

CREATE TABLE IF NOT EXISTS lab_result_encryption_backfill (
    lab_order_id    UUID PRIMARY KEY REFERENCES lab_orders(id) ON DELETE CASCADE,
    backfilled_at   TIMESTAMP(6) NOT NULL DEFAULT now(),
    previous_length INTEGER     NOT NULL
);

COMMENT ON TABLE lab_result_encryption_backfill IS
    'Issue #237: which lab_orders.result rows were converted from plaintext to '
    'envelope ciphertext. Holds no clinical content.';
