-- Performance indexes for common filters and lookups not covered by V1.
-- All are non-blocking (CONCURRENTLY would require being run outside a
-- transaction; Flyway runs each migration in a transaction, so plain CREATE
-- INDEX is used — these tables are small at migration time).

-- Patients: search by name/phone/email (registration desk lookups)
CREATE INDEX idx_patients_name ON patients(name);
CREATE INDEX idx_patients_phone ON patients(phone);
CREATE INDEX idx_patients_email ON patients(email);
CREATE INDEX idx_patients_created ON patients(created_at);

-- Appointments: status is the dominant filter for queues
CREATE INDEX idx_appointments_status ON appointments(status);

-- Encounters: signing workflow and patient timeline
CREATE INDEX idx_encounters_created ON encounters(created_at);

-- Prescriptions: pending-pharmacy-dispatch queue
CREATE INDEX idx_prescriptions_prescribed_at ON prescriptions(prescribed_at);

-- Admissions: active-admissions lookup and discharge history
CREATE INDEX idx_admissions_created ON admissions(created_at);

-- Medicine batches: low-stock / expiry sweeps
CREATE INDEX idx_batches_remaining ON medicine_batches(remaining_qty);

-- Stock transactions: performed_by audit queries
CREATE INDEX idx_stock_performed_by ON stock_transactions(performed_by);

-- Invoices & payments: financial aging by date
CREATE INDEX idx_invoices_created ON invoices(created_at);
CREATE INDEX idx_payments_received_at ON payments(received_at);

-- Lab orders: status queue and per-patient history
CREATE INDEX idx_lab_patient ON lab_orders(patient_id);
CREATE INDEX idx_lab_status ON lab_orders(status);
CREATE INDEX idx_lab_ordered ON lab_orders(ordered_at);

-- OPD queue: active queue lookups
CREATE INDEX idx_opd_doctor ON opd_queue(doctor_id);
CREATE INDEX idx_opd_status ON opd_queue(queue_status);

-- Consents: DPDP lookup by patient
CREATE INDEX idx_consents_patient ON consents(patient_id);
