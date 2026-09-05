-- Create sequences needed by application
CREATE SEQUENCE IF NOT EXISTS uhid_seq START WITH 6 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS invoice_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE IF NOT EXISTS payment_number_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

-- Create sequence_numbers table if needed by any code
CREATE TABLE IF NOT EXISTS sequence_numbers (
    name VARCHAR(50) PRIMARY KEY,
    current_value BIGINT NOT NULL DEFAULT 0
);
