-- V9__foreign_keys.sql — Wave 1 follow-up: close the orphan-capable schema (issue #146).
--
-- Only tenant_users/tenant_configs/tenant_settings carried FKs before this
-- migration. The domain tables (charges, payments, admissions, medicine_batches,
-- stock_transactions, prescriptions, lab_orders) had none, so a row could
-- point at a parent in another tenant and nothing in the storage layer objected.
--
-- Fix: composite unique keys on the parent (tenant_id, id) plus composite FKs
-- on the children. A cross-tenant reference then fails at the database level,
-- not just in the application's tenant ThreadLocal.

-- ---------------------------------------------------------------- parents: composite unique keys
ALTER TABLE patients ADD CONSTRAINT uq_patients_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE encounters ADD CONSTRAINT uq_encounters_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE admissions ADD CONSTRAINT uq_admissions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE rooms ADD CONSTRAINT uq_rooms_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE medicine_catalog ADD CONSTRAINT uq_medicine_catalog_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE medicine_batches ADD CONSTRAINT uq_medicine_batches_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE prescriptions ADD CONSTRAINT uq_prescriptions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE lab_orders ADD CONSTRAINT uq_lab_orders_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_tenant_id UNIQUE (tenant_id, id);

-- ---------------------------------------------------------------- charges
ALTER TABLE charges
    ADD CONSTRAINT fk_charges_patient FOREIGN KEY (tenant_id, patient_id)
        REFERENCES patients (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_charges_invoice FOREIGN KEY (tenant_id, invoice_id)
        REFERENCES invoices (tenant_id, id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_charges_encounter FOREIGN KEY (tenant_id, encounter_id)
        REFERENCES encounters (tenant_id, id) ON DELETE SET NULL;

-- ---------------------------------------------------------------- payments
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_patient FOREIGN KEY (tenant_id, patient_id)
        REFERENCES patients (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_payments_invoice FOREIGN KEY (tenant_id, invoice_id)
        REFERENCES invoices (tenant_id, id) ON DELETE SET NULL;

-- ---------------------------------------------------------------- admissions
ALTER TABLE admissions
    ADD CONSTRAINT fk_admissions_patient FOREIGN KEY (tenant_id, patient_id)
        REFERENCES patients (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_admissions_room FOREIGN KEY (tenant_id, room_id)
        REFERENCES rooms (tenant_id, id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------- medicine_batches
ALTER TABLE medicine_batches
    ADD CONSTRAINT fk_medicine_batches_medicine FOREIGN KEY (tenant_id, medicine_id)
        REFERENCES medicine_catalog (tenant_id, id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------- stock_transactions
ALTER TABLE stock_transactions
    ADD CONSTRAINT fk_stock_transactions_medicine FOREIGN KEY (tenant_id, medicine_id)
        REFERENCES medicine_catalog (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_stock_transactions_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES medicine_batches (tenant_id, id) ON DELETE SET NULL;

-- ---------------------------------------------------------------- prescriptions
ALTER TABLE prescriptions
    ADD CONSTRAINT fk_prescriptions_patient FOREIGN KEY (tenant_id, patient_id)
        REFERENCES patients (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_prescriptions_medicine FOREIGN KEY (tenant_id, medicine_id)
        REFERENCES medicine_catalog (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_prescriptions_encounter FOREIGN KEY (tenant_id, encounter_id)
        REFERENCES encounters (tenant_id, id) ON DELETE SET NULL;

-- ---------------------------------------------------------------- lab_orders
ALTER TABLE lab_orders
    ADD CONSTRAINT fk_lab_orders_patient FOREIGN KEY (tenant_id, patient_id)
        REFERENCES patients (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_lab_orders_encounter FOREIGN KEY (tenant_id, encounter_id)
        REFERENCES encounters (tenant_id, id) ON DELETE SET NULL;