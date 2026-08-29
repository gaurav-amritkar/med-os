-- Remove demo users and demo patients seeded by V2__seed_data.sql.
--
-- Why: production must never ship with known default credentials (admin/password)
-- or fake patient records. This migration cleans both fresh and existing databases.
--
-- Safety: rows that are already referenced by clinical/financial data are
-- deactivated / pseudonymized instead of deleted, so long-lived dev databases
-- with real usage do not violate FK constraints.

-- ---------------------------------------------------------------------------
-- 1) Demo users created by V2 (all share the known 'password' BCrypt hash).
--    Deactivate any that are referenced by other tables; delete the rest.
-- ---------------------------------------------------------------------------
UPDATE users SET active = FALSE
WHERE username IN ('admin', 'doctor', 'doctor2', 'nurse', 'reception', 'pharmacy', 'billing')
  AND id IN (
        SELECT doctor_id   FROM appointments
        UNION ALL SELECT doctor_id   FROM encounters
        UNION ALL SELECT signed_by   FROM encounters
        UNION ALL SELECT prescribed_by FROM prescriptions
        UNION ALL SELECT performed_by FROM stock_transactions
        UNION ALL SELECT generated_by FROM invoices
        UNION ALL SELECT doctor_id   FROM lab_orders
        UNION ALL SELECT doctor_id   FROM admissions
        UNION ALL SELECT doctor_id   FROM opd_queue
        UNION ALL SELECT user_id     FROM audit_log
        UNION ALL SELECT received_by FROM payments
        UNION ALL SELECT recipient_id FROM notifications
  );

DELETE FROM users
WHERE username IN ('admin', 'doctor', 'doctor2', 'nurse', 'reception', 'pharmacy', 'billing')
  AND active = TRUE;

-- ---------------------------------------------------------------------------
-- 2) Demo patients created by V2 (UHID000001..UHID000005) hold fake demographics.
--    Delete unreferenced rows; pseudonymize any that have clinical history.
-- ---------------------------------------------------------------------------
DELETE FROM patients
WHERE uhid LIKE 'UHID0000%'
  AND id NOT IN (
        SELECT patient_id FROM appointments
        UNION ALL SELECT patient_id FROM encounters
        UNION ALL SELECT patient_id FROM prescriptions
        UNION ALL SELECT patient_id FROM stock_transactions
        UNION ALL SELECT patient_id FROM invoices
        UNION ALL SELECT patient_id FROM charges
        UNION ALL SELECT patient_id FROM payments
        UNION ALL SELECT patient_id FROM lab_orders
        UNION ALL SELECT patient_id FROM admissions
        UNION ALL SELECT patient_id FROM opd_queue
        UNION ALL SELECT patient_id FROM consents
  );

UPDATE patients
SET name = 'PSEUDONYMISED', phone = NULL, email = NULL
WHERE uhid LIKE 'UHID0000%' AND name <> 'PSEUDONYMISED';
