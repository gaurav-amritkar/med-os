-- V16__prescription_quantity.sql — track the total quantity prescribed and the
-- cumulative quantity dispensed per prescription (issue #160).
--
-- The `status` CHECK constraint already admits `partially_dispensed` (V1:185),
-- but nothing ever transitioned into it: dispensing any quantity marked the
-- whole prescription `dispensed`, silently losing the undispensed remainder.
--
-- Add a `quantity` column (total units prescribed, nullable so legacy rows keep
-- the old behaviour) and a `dispensed_qty` column (cumulative units dispensed,
-- defaults to 0). DispenseService compares cumulative dispensed against total to
-- decide between `partially_dispensed` and `dispensed`.

ALTER TABLE prescriptions
    ADD COLUMN IF NOT EXISTS quantity INTEGER,
    ADD COLUMN IF NOT EXISTS dispensed_qty INTEGER NOT NULL DEFAULT 0;

-- Helpful for the pharmacy list/screen queries that filter by remaining need.
CREATE INDEX IF NOT EXISTS idx_prescriptions_quantity_dispensed
    ON prescriptions (quantity, dispensed_qty);
