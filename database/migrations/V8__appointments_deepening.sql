-- V8__appointments_deepening.sql — Wave 1: the Appointments board deepens.
--
-- The appointments table existed as a schedule (a patient, a doctor, a moment,
-- a status). The MedWorks Appointments module needs it to be an addressable
-- day-of-care record: a per-tenant reference number (Appt No.), a PER-DAY token
-- (issued at check-in, resets every morning — a global token would make the
-- second patient of every day number 1 impossible), a visit type (first visit /
-- follow-up), and the doctor's note written during the visit.
--
-- Two numbering rules, both partial-unique (columns are NULL until assigned):
--   (tenant_id, appointment_no)           — the per-tenant serial, like V7's
--                                           enquiry_no; the desk retries on
--                                           conflict rather than reusing.
--   (tenant_id, token_date, token_no)     — the token, unique per tenant PER
--                                           DAY; tomorrow morning restarts at 1.
--
-- Status grows in_consultation (the doctor has the patient), and the V1 CHECK
-- is replaced under a NAMED constraint so the test can pin it and failures name
-- themselves. Legacy rows are backfilled with their per-tenant serial ordered
-- by creation, so the board shows a continuous history, not two numbering eras.

ALTER TABLE appointments
    ADD COLUMN appointment_no   INT,
    ADD COLUMN token_no         INT,
    ADD COLUMN token_date       DATE,
    ADD COLUMN appointment_type TEXT NOT NULL DEFAULT 'first_visit',
    ADD COLUMN doc_note         TEXT,
    ADD COLUMN created_by       UUID,
    ADD COLUMN updated_at       TIMESTAMP(6);

UPDATE appointments a
SET appointment_no = t.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY created_at, id) AS rn
    FROM appointments
    WHERE tenant_id IS NOT NULL
) t
WHERE a.id = t.id;

-- The V1 CHECK was an unnamed column constraint; drop it by its deterministic
-- default name (<table>_<column>_check) and re-add under a named one.
ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;
ALTER TABLE appointments ADD CONSTRAINT chk_appointments_status
    CHECK (status IN
        ('scheduled', 'checked_in', 'in_consultation', 'completed', 'cancelled', 'no_show'));

ALTER TABLE appointments ADD CONSTRAINT chk_appointments_type
    CHECK (appointment_type IN ('first_visit', 'follow_up'));

CREATE UNIQUE INDEX uq_appointments_tenant_no
    ON appointments (tenant_id, appointment_no) WHERE appointment_no IS NOT NULL;

CREATE UNIQUE INDEX uq_appointments_tenant_day_token
    ON appointments (tenant_id, token_date, token_no) WHERE token_no IS NOT NULL;

-- The board's shape: one tenant's day, newest moments first.
CREATE INDEX idx_appointments_tenant_date
    ON appointments (tenant_id, appointment_date DESC);
