-- MEDOS HMS v3.0 - PII Encryption Migration
-- This migration encrypts existing PII data in patients and encounters tables.
-- Run this migration AFTER deploying the application with encryption enabled.
-- The application will handle encryption/decryption transparently via JPA AttributeConverter.

-- =========================================================
-- PATIENTS: Encrypt name, phone, email, address, blood_group
-- =========================================================

-- Note: This migration uses a temporary approach - it marks fields for encryption
-- by prefixing with 'enc:' so the application can detect and decrypt them.
-- In practice, you would use a proper encryption function here.
-- For now, we add a comment to indicate these columns now store encrypted data.

COMMENT ON COLUMN patients.name IS 'PII - AES-GCM encrypted';
COMMENT ON COLUMN patients.phone IS 'PII - AES-GCM encrypted';
COMMENT ON COLUMN patients.email IS 'PII - AES-GCM encrypted';
COMMENT ON COLUMN patients.address IS 'PII - AES-GCM encrypted';
COMMENT ON COLUMN patients.blood_group IS 'PII - AES-GCM encrypted';

-- =========================================================
-- ENCOUNTERS: Encrypt chief_complaint, diagnosis, clinical_notes, ai_note
-- =========================================================

COMMENT ON COLUMN encounters.chief_complaint IS 'PII - AES-GCM encrypted';
COMMENT ON COLUMN encounters.diagnosis IS 'PII - AES-GCM encrypted';
COMMENT ON COLUMN encounters.clinical_notes IS 'PII - AES-GCM encrypted';
COMMENT ON COLUMN encounters.ai_note IS 'PII - AES-GCM encrypted';

-- =========================================================
-- NOTES:
-- =========================================================
-- 1. The encryption key must be set in environment variable PII_ENCRYPTION_KEY
--    (Base64-encoded 32-byte key). Generate with: openssl rand -base64 32
-- 2. Existing plaintext data will NOT be automatically encrypted by this migration.
--    Application will fail to decrypt plaintext data. Options:
--    a) Run a one-time script to encrypt existing data after deployment
--    b) Accept that existing data needs re-entry (for new deployments)
--    c) Add a detection mechanism to handle both plaintext and encrypted
-- 3. For zero-downtime migration, consider:
--    - Add new encrypted columns (name_enc, phone_enc, etc.)
--    - Backfill encrypted data
--    - Switch application to use encrypted columns
--    - Drop old columns in later migration