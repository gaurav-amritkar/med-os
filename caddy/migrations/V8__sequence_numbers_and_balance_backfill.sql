-- MEDOS HMS - P0 data-integrity fixes (issues #22, #23, #24)
--
-- 1. Replace racy application-side number generation with PostgreSQL SEQUENCEs.
-- 2. Backfill patient.outstanding using the new single-source-of-truth formula.
--
-- Schema constraints already exist (V1): uhid, invoice_number, payment_number are UNIQUE.
-- Sequences below are the only legal source of new values, so concurrent inserts cannot collide.

-- ---- Sequences ----------------------------------------------------------------

-- UHID: V2 seed already has UHID000001..UHID000005, so start at 6.
CREATE SEQUENCE IF NOT EXISTS uhid_seq START WITH 6 INCREMENT BY 1 NO CYCLE;

-- Invoice/Payment: start at 1, but advance past any existing rows (idempotent).
CREATE SEQUENCE IF NOT EXISTS invoice_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS payment_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

SELECT setval(
    'invoice_number_seq',
    GREATEST(COALESCE(MAX(CAST(SUBSTRING(invoice_number FROM 5) AS INTEGER)), 0), 1),
    true
) FROM invoices WHERE invoice_number LIKE 'INV-%';

SELECT setval(
    'payment_number_seq',
    GREATEST(COALESCE(MAX(CAST(SUBSTRING(payment_number FROM 5) AS INTEGER)), 0), 1),
    true
) FROM payments WHERE payment_number LIKE 'PAY-%';

-- ---- Backfill patient balances -----------------------------------------------
-- Formula (single source of truth, mirrored in PatientBalanceService):
--   outstanding = MAX(0, SUM(charges billed|paid) - SUM(payments success))
-- The GREATEST guard handles the rare case where payments exceed charges (overpayment)
-- without producing a negative balance; the column is DECIMAL(12,2) and clients expect non-negative.

UPDATE patients p
SET outstanding = GREATEST(
    COALESCE((
        SELECT SUM(c.total_amount)
        FROM charges c
        WHERE c.patient_id = p.id
          AND c.status IN ('billed', 'paid')
    ), 0)
    -
    COALESCE((
        SELECT SUM(pay.amount)
        FROM payments pay
        WHERE pay.patient_id = p.id
          AND pay.status = 'success'
    ), 0),
    0
);
