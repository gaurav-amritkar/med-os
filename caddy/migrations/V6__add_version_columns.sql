-- MEDOS HMS v3.0 - Add version columns for optimistic locking
-- Fixes schema validation failure: missing column [version] in tables with @Version entities

-- 1. ADMISSIONS (Admission entity has @Version)
ALTER TABLE admissions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. INVOICES (Invoice entity has @Version)
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 3. MEDICINE_BATCHES (MedicineBatch entity has @Version)
ALTER TABLE medicine_batches ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 4. ROOMS (Room entity has @Version)
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
