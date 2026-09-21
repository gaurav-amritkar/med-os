-- Sequences required by native queries (UHID / invoice / payment number generation).
-- Idempotent: applied on every backend boot via spring.sql.init.
-- uhid_seq starts at 6 so dev seeds (UHID000001-000005) never collide.
CREATE SEQUENCE IF NOT EXISTS uhid_seq START WITH 6 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS invoice_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS payment_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
