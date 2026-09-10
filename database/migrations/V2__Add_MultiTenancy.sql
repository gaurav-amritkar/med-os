CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('HOSPITAL', 'CLINIC', 'INDIVIDUAL_PRACTITIONER', 'PHARMACY')),
    slug VARCHAR(64) UNIQUE NOT NULL,
    contactEmail VARCHAR(255),
    contactPhone VARCHAR(20),
    address TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('admin', 'doctor', 'nurse', 'receptionist', 'pharmacist', 'billing')),
    UNIQUE (user_id, tenant_id),
    CONSTRAINT fk_tenant_user_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_tenant_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE TABLE tenant_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    config_key VARCHAR(64) NOT NULL,
    config_value TEXT,
    CONSTRAINT fk_tenant_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

ALTER TABLE patients ADD COLUMN tenant_id UUID;
ALTER TABLE appointments ADD COLUMN tenant_id UUID;
ALTER TABLE invoices ADD COLUMN tenant_id UUID;
ALTER TABLE payments ADD COLUMN tenant_id UUID;
ALTER TABLE encounters ADD COLUMN tenant_id UUID;
ALTER TABLE prescriptions ADD COLUMN tenant_id UUID;
ALTER TABLE medicine_batches ADD COLUMN tenant_id UUID;
ALTER TABLE medicine_catalog ADD COLUMN tenant_id UUID;
ALTER TABLE stock_transactions ADD COLUMN tenant_id UUID;
ALTER TABLE lab_orders ADD COLUMN tenant_id UUID;
ALTER TABLE opd_queues ADD COLUMN tenant_id UUID;
ALTER TABLE rooms ADD COLUMN tenant_id UUID;
ALTER TABLE admissions ADD COLUMN tenant_id UUID;
ALTER TABLE charges ADD COLUMN tenant_id UUID;

CREATE INDEX idx_patients_tenant ON patients(tenant_id);
CREATE INDEX idx_appointments_tenant ON appointments(tenant_id);
CREATE INDEX idx_invoices_tenant ON invoices(tenant_id);
CREATE INDEX idx_payments_tenant ON payments(tenant_id);
CREATE INDEX idx_encounters_tenant ON encounters(tenant_id);
CREATE INDEX idx_prescriptions_tenant ON prescriptions(tenant_id);
CREATE INDEX idx_medicine_batches_tenant ON medicine_batches(tenant_id);
CREATE INDEX idx_medicine_catalog_tenant ON medicine_catalog(tenant_id);
CREATE INDEX idx_stock_transactions_tenant ON stock_transactions(tenant_id);
CREATE INDEX idx_lab_orders_tenant ON lab_orders(tenant_id);
CREATE INDEX idx_opd_queues_tenant ON opd_queues(tenant_id);
CREATE INDEX idx_rooms_tenant ON rooms(tenant_id);
CREATE INDEX idx_admissions_tenant ON admissions(tenant_id);
CREATE INDEX idx_charges_tenant ON charges(tenant_id);
