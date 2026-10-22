-- V6__tenant_dek_history.sql — retain past DEK generations so rotation is safe.
--
-- A DEK rotation generates new key bytes for a tenant and moves the tenant's
-- current generation from N to N+1. Ciphertext written before the rotation is
-- labelled kv1.d<N>:<payload> and is still on disk, unreadable except with the
-- generation-N key. Without this table, rotating meant deleting the only key
-- generation N could ever be decrypted with — every historical name, diagnosis
-- and address in the tenant became unreadable.
--
-- tenant_keys keeps the CURRENT generation only (its row is the authoritative
-- (dek_generation, wrapped_dek) pair); past generations are archived here the
-- moment they stop being current, and never resurrected. The ciphertext format
-- (PiiCiphertextFormat) resolves historical generations from this table and
-- throws UnknownGenerationException for generations that were never issued.
--
-- Design: docs/architecture-review/tenant-key-holder-deepening.md (DEK rotation),
--         docs/architecture-review/key-lifecycle-orchestration-deepening.md.
-- Schema parent: V2__pii_key_lifecycle.sql. Decision: docs/adr/0009.
--
-- ONLY WRAPPED KEY MATERIAL IS STORED HERE (same rule as tenant_keys).

CREATE TABLE tenant_dek_history (
    tenant_id           UUID         NOT NULL,
    -- The generation this material encrypted. Monotonic per tenant; the archive
    -- is append-only — a generation, once superseded, is never current again.
    dek_generation      INT          NOT NULL,
    -- AES-GCM(KEK-current-at-archive-time, DEK-of-this-generation). BYTEA, never TEXT.
    wrapped_dek         BYTEA        NOT NULL,
    -- Which KEK version wrapped this material when it was archived. A KEK re-wrap
    -- must move these wrappers too, or historical generations become unreadable
    -- while current data keeps working.
    wrapped_kek_version INT          NOT NULL DEFAULT 1,
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, dek_generation),
    -- Retiring a tenant deletes its history with its keys: material that would
    -- outlive the tenant is a liability, not a backup.
    FOREIGN KEY (tenant_id) REFERENCES tenants ON DELETE CASCADE,
    CONSTRAINT chk_tenant_dek_history_generation CHECK (dek_generation >= 1),
    CONSTRAINT chk_tenant_dek_history_kek_version CHECK (wrapped_kek_version >= 1)
);

CREATE INDEX idx_tenant_dek_history_tenant ON tenant_dek_history (tenant_id, dek_generation DESC);
