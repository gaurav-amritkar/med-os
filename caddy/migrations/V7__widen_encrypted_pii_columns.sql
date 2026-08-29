-- MEDOS HMS v3.0 - Widen PII columns for AES-GCM ciphertext
-- V5 enabled at-rest encryption of patients.* via EncryptionUtil (JPA AttributeConverter),
-- which stores Base64(12-byte IV + ciphertext + 16-byte GCM tag) ~= 1.34 x plaintext + 40 chars.
-- The column widths were never widened, so phone VARCHAR(20) and blood_group VARCHAR(8)
-- reject every ciphertext value (minimum ~40 chars even for a 1-character plaintext) and
-- patient registration always fails with "value too long for type character varying".

ALTER TABLE patients ALTER COLUMN name TYPE VARCHAR(512),
                ALTER COLUMN phone TYPE VARCHAR(128),
                ALTER COLUMN email TYPE VARCHAR(512),
                ALTER COLUMN blood_group TYPE VARCHAR(64);
-- address is TEXT (unbounded) and encounters.* PII columns are TEXT: no change needed.
