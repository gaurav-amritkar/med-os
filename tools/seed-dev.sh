#!/usr/bin/env bash
# Seeds demo users and patients for LOCAL DEVELOPMENT only.
# NEVER run against a production database.
#
# Usage (from repo root):
#   docker compose up -d                # start the whole stack
#   ./tools/seed-dev.sh                # seeds via the running medos-db container
#
# Local Postgres without Docker:
#   ./tools/seed-dev.sh --local
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$DIR/seed-dev.sql"

# Users are seeded with SQL because a bcrypt hash can be precomputed. Patients cannot be:
# their PII columns hold AES-GCM ciphertext produced with the tenant's DEK, which is
# generated at runtime and never leaves the database. Demo patients are therefore created
# over HTTP, through the only path that encrypts and computes the name index.
#
# Seeding them with SQL left plaintext PII in the table. That was not a visible failure:
# PiiCiphertextFormat deliberately tolerates a value with no version prefix so pre-encryption
# rows can be migrated, so a plaintext row was served back to callers as if it were fine —
# patient names readable in a table meant to hold only ciphertext, and no name index, so
# those patients were also unfindable by name.

seed_patients_api() {
  local base="${SEED_BASE_URL:-http://localhost:${FRONTEND_EXTERNAL_PORT:-8080}}"
  local username="${SEED_USERNAME:-admin}"
  local password="${SEED_PASSWORD:-password}"

  echo "Seeding demo patients through the API at $base ..."
  local token
  token="$(curl -sS -X POST "$base/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}" \
    | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

  if [[ -z "$token" ]]; then
    # Not fatal: the SQL part is the part that matters for a fresh database, and an operator
    # seeding before the app is up should not be told the whole thing failed.
    echo "  could not sign in as '$username'; skipping demo patients."
    echo "  start the stack first (docker compose up -d) and re-run to add them."
    return 0
  fi

  local created=0
  local no_consent_uhid=""
  # name|age|gender|phone|email|consent
  while IFS='|' read -r name age gender phone email consent; do
    [[ -z "${name// }" ]] && continue

    # The API refuses to register a patient without DPDP consent, which is a known open
    # defect (#116): consent must not be a precondition of receiving care. To keep a
    # not-yet-consented patient in the demo data, such a row is created WITH consent and
    # then flipped by UHID afterwards — a non-PII column, so it does not reintroduce the
    # plaintext problem this script exists to avoid.
    local send_consent="$consent"
    [[ "$consent" == "false" ]] && send_consent="true"

    local response code uhid
    response="$(curl -sS -w '\n%{http_code}' -X POST "$base/api/v1/patients" \
      -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' \
      -d "{\"name\":\"$name\",\"age\":$age,\"gender\":\"$gender\",\"phone\":\"$phone\",\"email\":\"$email\",\"address\":\"Demo\",\"bloodGroup\":\"\",\"dpdpConsent\":$send_consent}")"
    code="$(printf '%s' "$response" | tail -n1)"
    uhid="$(printf '%s' "$response" | head -n1 | sed -n 's/.*"uhid"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

    if [[ "$code" == "201" || "$code" == "200" ]]; then
      created=$((created + 1))
      if [[ "$consent" == "false" && -n "$uhid" ]]; then
        no_consent_uhid="$uhid"
      fi
    else
      echo "  skipped '$name' (HTTP $code)"
    fi
  done <<'PATIENTS'
Rahul Mehta|34|male|9876543210|rahul@example.test|true
Anita Joshi|28|female|9876543211|anita@example.test|true
Suresh Reddy|62|male|9876543212|suresh@example.test|true
Kavita Nair|45|female|9876543213|kavita@example.test|true
Aman Khan|22|male|9876543214|aman@example.test|false
PATIENTS

  if [[ -n "$no_consent_uhid" ]]; then
    # Not PII, so writing it directly is safe and keeps the demo set covering both consent
    # states. See the note above the loop.
    docker compose exec -T db psql -U "${DB_USER:-medos}" -d "${DB_NAME:-medos}" -q \
      -c "UPDATE patients SET dpdp_consent = FALSE, dpdp_consent_at = NULL WHERE uhid = '$no_consent_uhid';" \
      >/dev/null 2>&1 || echo "  note: could not mark $no_consent_uhid as not-yet-consented"
    echo "  marked $no_consent_uhid as registered without DPDP consent"
  fi

  echo "  created $created demo patient(s), with encrypted PII and a name index."
}

# Seed patient-dependent data (appointments, encounters, prescriptions, lab orders,
# admissions, charges, invoices, payments, consents) after patients exist. Uses UHID
# subqueries to reference patients without knowing their UUIDs at authoring time.
seed_patient_data() {
  echo "Seeding appointments, encounters, prescriptions, and billing data..."

  docker compose exec -T db psql -U "${DB_USER:-medos}" -d "${DB_NAME:-medos}" -v ON_ERROR_STOP=1 -q <<'PATIENT_DATA'
-- Appointments
INSERT INTO appointments (id, doctor_id, patient_id, appointment_date, status, reason, tenant_id)
VALUES
('00000000-0000-4000-8000-000000001001', '00000000-0000-4000-8000-000000000102', (SELECT id FROM patients WHERE uhid='UHID000006'), NOW() + INTERVAL '1 day', 'scheduled', 'Fever consultation', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001002', '00000000-0000-4000-8000-000000000102', (SELECT id FROM patients WHERE uhid='UHID000007'), NOW() + INTERVAL '2 days', 'scheduled', 'Follow-up', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001003', '00000000-0000-4000-8000-000000000103', (SELECT id FROM patients WHERE uhid='UHID000008'), NOW() - INTERVAL '1 day', 'completed', 'Chest pain', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001004', '00000000-0000-4000-8000-000000000102', (SELECT id FROM patients WHERE uhid='UHID000009'), NOW() - INTERVAL '3 days', 'completed', 'Diabetes review', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001005', '00000000-0000-4000-8000-000000000103', (SELECT id FROM patients WHERE uhid='UHID000010'), NOW(), 'checked_in', 'Allergy consultation', '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- OPD Queue
INSERT INTO opd_queue (id, doctor_id, patient_id, queue_number, queue_status, check_in_at, tenant_id)
VALUES
('00000000-0000-4000-8000-000000001101', '00000000-0000-4000-8000-000000000102', (SELECT id FROM patients WHERE uhid='UHID000006'), 1, 'in_consultation', NOW() - INTERVAL '10 minutes', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001102', '00000000-0000-4000-8000-000000000102', (SELECT id FROM patients WHERE uhid='UHID000007'), 2, 'waiting', NOW() - INTERVAL '5 minutes', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001103', '00000000-0000-4000-8000-000000000103', (SELECT id FROM patients WHERE uhid='UHID000008'), 1, 'completed', NOW() - INTERVAL '2 hours', '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- Encounters
INSERT INTO encounters (id, appointment_id, doctor_id, patient_id, status, chief_complaint, clinical_notes, diagnosis, vitals_json, signed_by, signed_at, tenant_id)
VALUES
('00000000-0000-4000-8000-000000001201', '00000000-0000-4000-8000-000000001003', '00000000-0000-4000-8000-000000000103', (SELECT id FROM patients WHERE uhid='UHID000008'), 'signed', 'Chest pain', 'Patient reports chest pain radiating to left arm. ECG normal.', 'Stable angina', '{"bp":"140/90","hr":88,"temp":98.6}', '00000000-0000-4000-8000-000000000103', NOW() - INTERVAL '1 day', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001202', '00000000-0000-4000-8000-000000001004', '00000000-0000-4000-8000-000000000102', (SELECT id FROM patients WHERE uhid='UHID000009'), 'signed', 'Diabetes follow-up', 'HbA1c 7.2%. Continue current medication. Diet counseling done.', 'Type 2 diabetes mellitus', '{"bp":"130/80","hr":72,"temp":98.4}', '00000000-0000-4000-8000-000000000102', NOW() - INTERVAL '3 days', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001203', NULL, '00000000-0000-4000-8000-000000000102', (SELECT id FROM patients WHERE uhid='UHID000006'), 'open', 'Fever since 2 days', 'Vitals stable. Likely viral fever. Symptomatic treatment.', 'Viral fever', '{"bp":"120/80","hr":76,"temp":101.2}', NULL, NULL, '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- Prescriptions
INSERT INTO prescriptions (id, encounter_id, medicine_id, patient_id, prescribed_by, prescribed_at, status, dosage, frequency, duration, instructions, tenant_id)
VALUES
('00000000-0000-4000-8000-000000001301', '00000000-0000-4000-8000-000000001201', '00000000-0000-4000-8000-000000000409', (SELECT id FROM patients WHERE uhid='UHID000008'), '00000000-0000-4000-8000-000000000103', NOW() - INTERVAL '1 day', 'dispensed', '20mg', 'once daily at bedtime', '30 days', 'Take after dinner', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001302', '00000000-0000-4000-8000-000000001201', '00000000-0000-4000-8000-000000000416', (SELECT id FROM patients WHERE uhid='UHID000008'), '00000000-0000-4000-8000-000000000103', NOW() - INTERVAL '1 day', 'dispensed', '75mg', 'once daily', '30 days', 'Take with food', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001303', '00000000-0000-4000-8000-000000001202', '00000000-0000-4000-8000-000000000404', (SELECT id FROM patients WHERE uhid='UHID000009'), '00000000-0000-4000-8000-000000000102', NOW() - INTERVAL '3 days', 'dispensed', '500mg', 'twice daily', '90 days', 'Take after meals', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001304', '00000000-0000-4000-8000-000000001202', '00000000-0000-4000-8000-000000000405', (SELECT id FROM patients WHERE uhid='UHID000009'), '00000000-0000-4000-8000-000000000102', NOW() - INTERVAL '3 days', 'dispensed', '5mg', 'once daily', '90 days', 'Take in the morning', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001305', '00000000-0000-4000-8000-000000001203', '00000000-0000-4000-8000-000000000401', (SELECT id FROM patients WHERE uhid='UHID000006'), '00000000-0000-4000-8000-000000000102', NOW(), 'pending', '500mg', 'three times daily', '5 days', 'Take after food. Complete full course.', '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- Lab Orders
INSERT INTO lab_orders (id, doctor_id, encounter_id, patient_id, test_code, test_name, priority, status, ordered_at, result_at, result, tenant_id)
VALUES
('00000000-0000-4000-8000-000000001401', '00000000-0000-4000-8000-000000000103', '00000000-0000-4000-8000-000000001201', (SELECT id FROM patients WHERE uhid='UHID000008'), 'ECG-001', 'ECG', 'normal', 'completed', NOW() - INTERVAL '1 day', NOW() - INTERVAL '23 hours', 'Normal sinus rhythm', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001402', '00000000-0000-4000-8000-000000000103', '00000000-0000-4000-8000-000000001201', (SELECT id FROM patients WHERE uhid='UHID000008'), 'LIPID-001', 'Lipid Profile', 'normal', 'completed', NOW() - INTERVAL '1 day', NOW() - INTERVAL '20 hours', 'Cholesterol: 210 mg/dL (high)', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001403', '00000000-0000-4000-8000-000000000102', '00000000-0000-4000-8000-000000001202', (SELECT id FROM patients WHERE uhid='UHID000009'), 'HBA1C-001', 'HbA1c', 'normal', 'completed', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', '7.2%', '00000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000001404', '00000000-0000-4000-8000-000000000102', '00000000-0000-4000-8000-000000001203', (SELECT id FROM patients WHERE uhid='UHID000006'), 'CBC-001', 'Complete Blood Count', 'normal', 'ordered', NOW(), NULL, NULL, '00000000-0000-4000-8000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- Admissions
INSERT INTO admissions (id, patient_id, room_id, doctor_id, admission_date, status, days_admitted, room_charges, discharge_diagnosis, tenant_id, version)
VALUES
('00000000-0000-4000-8000-000000001501', (SELECT id FROM patients WHERE uhid='UHID000008'), '00000000-0000-4000-8000-000000000304', '00000000-0000-4000-8000-000000000103', NOW() - INTERVAL '2 days', 'admitted', 2, 3000.00, NULL, '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001502', (SELECT id FROM patients WHERE uhid='UHID000009'), '00000000-0000-4000-8000-000000000311', '00000000-0000-4000-8000-000000000102', NOW() - INTERVAL '1 day', 'admitted', 1, 12000.00, NULL, '00000000-0000-4000-8000-000000000001', 0)
ON CONFLICT (id) DO NOTHING;

-- Mark rooms as occupied for admitted patients
UPDATE rooms SET occupied = TRUE WHERE room_number IN ('P-301', 'ICU-01');

-- Charges
INSERT INTO charges (id, patient_id, encounter_id, admission_id, invoice_id, charge_type, description, quantity, unit_price, amount, gst_percent, gst_amount, total_amount, status, tenant_id, version)
VALUES
-- Consultation charges
('00000000-0000-4000-8000-000000001601', (SELECT id FROM patients WHERE uhid='UHID000008'), '00000000-0000-4000-8000-000000001201', NULL, NULL, 'consultation', 'Cardiology consultation', 1, 800.00, 800.00, 0, 0, 800.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001602', (SELECT id FROM patients WHERE uhid='UHID000009'), '00000000-0000-4000-8000-000000001202', NULL, NULL, 'consultation', 'General Medicine consultation', 1, 500.00, 500.00, 0, 0, 500.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001603', (SELECT id FROM patients WHERE uhid='UHID000006'), '00000000-0000-4000-8000-000000001203', NULL, NULL, 'consultation', 'General Medicine consultation', 1, 500.00, 500.00, 0, 0, 500.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0),
-- Lab charges
('00000000-0000-4000-8000-000000001604', (SELECT id FROM patients WHERE uhid='UHID000008'), '00000000-0000-4000-8000-000000001201', NULL, NULL, 'lab', 'ECG', 1, 500.00, 500.00, 0, 0, 500.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001605', (SELECT id FROM patients WHERE uhid='UHID000008'), '00000000-0000-4000-8000-000000001201', NULL, NULL, 'lab', 'Lipid Profile', 1, 700.00, 700.00, 0, 0, 700.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001606', (SELECT id FROM patients WHERE uhid='UHID000009'), '00000000-0000-4000-8000-000000001202', NULL, NULL, 'lab', 'HbA1c', 1, 600.00, 600.00, 0, 0, 600.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0),
-- Room charges
('00000000-0000-4000-8000-000000001607', (SELECT id FROM patients WHERE uhid='UHID000008'), NULL, '00000000-0000-4000-8000-000000001501', NULL, 'room', 'Private room - 2 days', 2, 5000.00, 10000.00, 12, 1200.00, 11200.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001608', (SELECT id FROM patients WHERE uhid='UHID000009'), NULL, '00000000-0000-4000-8000-000000001502', NULL, 'room', 'ICU - 1 day', 1, 12000.00, 12000.00, 12, 1440.00, 13440.00, 'unbilled', '00000000-0000-4000-8000-000000000001', 0)
ON CONFLICT (id) DO NOTHING;

-- Invoices
INSERT INTO invoices (id, patient_id, invoice_number, invoice_date, subtotal, gst_total, discount, total_amount, paid_amount, status, generated_by, notes, tenant_id, version)
VALUES
('00000000-0000-4000-8000-000000001701', (SELECT id FROM patients WHERE uhid='UHID000008'), 'INV-2026-0001', NOW() - INTERVAL '1 day', 2000.00, 0, 0, 2000.00, 2000.00, 'paid', '00000000-0000-4000-8000-000000000107', 'ECG + Lipid Profile + Consultation', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001702', (SELECT id FROM patients WHERE uhid='UHID000009'), 'INV-2026-0002', NOW() - INTERVAL '3 days', 1100.00, 0, 0, 1100.00, 500.00, 'partially_paid', '00000000-0000-4000-8000-000000000107', 'Consultation + HbA1c', '00000000-0000-4000-8000-000000000001', 0)
ON CONFLICT (id) DO NOTHING;

-- Link charges to invoices
UPDATE charges SET invoice_id = '00000000-0000-4000-8000-000000001701', status = 'billed'
WHERE id IN ('00000000-0000-4000-8000-000000001604', '00000000-0000-4000-8000-000000001605', '00000000-0000-4000-8000-000000001601');

UPDATE charges SET invoice_id = '00000000-0000-4000-8000-000000001702', status = 'billed'
WHERE id IN ('00000000-0000-4000-8000-000000001602', '00000000-0000-4000-8000-000000001606');

-- Mark invoiced charges as paid (INV-2026-0001 is fully paid)
UPDATE charges SET status = 'paid' WHERE invoice_id = '00000000-0000-4000-8000-000000001701';

-- Payments
INSERT INTO payments (id, invoice_id, patient_id, amount, payment_method, received_at, received_by, status, payment_number, transaction_ref, notes, tenant_id, version)
VALUES
('00000000-0000-4000-8000-000000001801', '00000000-0000-4000-8000-000000001701', (SELECT id FROM patients WHERE uhid='UHID000008'), 2000.00, 'cash', NOW() - INTERVAL '1 day', '00000000-0000-4000-8000-000000000107', 'success', 'PAY-001', 'CASH-001', 'Full payment', '00000000-0000-4000-8000-000000000001', 0),
('00000000-0000-4000-8000-000000001802', '00000000-0000-4000-8000-000000001702', (SELECT id FROM patients WHERE uhid='UHID000009'), 500.00, 'upi', NOW() - INTERVAL '2 days', '00000000-0000-4000-8000-000000000107', 'success', 'PAY-002', 'UPI-9876543210', 'Partial payment', '00000000-0000-4000-8000-000000000001', 0)
ON CONFLICT (id) DO NOTHING;

-- Consents
INSERT INTO consents (id, patient_id, consent_type, purpose, granted, granted_at, granted_by, ip_address, expiry_date)
VALUES
('00000000-0000-4000-8000-000000001901', (SELECT id FROM patients WHERE uhid='UHID000006'), 'TREATMENT', 'General treatment consent', TRUE, NOW() - INTERVAL '30 days', 'Maya Verma', '192.168.1.100', NOW() + INTERVAL '335 days'),
('00000000-0000-4000-8000-000000001902', (SELECT id FROM patients WHERE uhid='UHID000006'), 'DATA_PROCESSING', 'DPDP data processing consent', TRUE, NOW() - INTERVAL '30 days', 'Maya Verma', '192.168.1.100', NOW() + INTERVAL '335 days'),
('00000000-0000-4000-8000-000000001903', (SELECT id FROM patients WHERE uhid='UHID000007'), 'TREATMENT', 'General treatment consent', TRUE, NOW() - INTERVAL '25 days', 'Maya Verma', '192.168.1.101', NOW() + INTERVAL '340 days'),
('00000000-0000-4000-8000-000000001904', (SELECT id FROM patients WHERE uhid='UHID000008'), 'TREATMENT', 'General treatment consent', TRUE, NOW() - INTERVAL '10 days', 'Maya Verma', '192.168.1.102', NOW() + INTERVAL '355 days'),
('00000000-0000-4000-8000-000000001905', (SELECT id FROM patients WHERE uhid='UHID000008'), 'DATA_PROCESSING', 'DPDP data processing consent', TRUE, NOW() - INTERVAL '10 days', 'Maya Verma', '192.168.1.102', NOW() + INTERVAL '355 days'),
('00000000-0000-4000-8000-000000001906', (SELECT id FROM patients WHERE uhid='UHID000009'), 'TREATMENT', 'General treatment consent', TRUE, NOW() - INTERVAL '5 days', 'Maya Verma', '192.168.1.103', NOW() + INTERVAL '360 days')
ON CONFLICT (id) DO NOTHING;

-- Stock transactions for dispensed medicines
INSERT INTO stock_transactions (id, medicine_id, batch_id, patient_id, performed_at, performed_by, prescription_id, quantity, tenant_id, transaction_type, reference_no, notes)
VALUES
('00000000-0000-4000-8000-000000001601', '00000000-0000-4000-8000-000000000409', '00000000-0000-4000-8000-000000000511', (SELECT id FROM patients WHERE uhid='UHID000008'), NOW() - INTERVAL '1 day', '00000000-0000-4000-8000-000000000106', '00000000-0000-4000-8000-000000001301', 30, '00000000-0000-4000-8000-000000000001', 'out', 'DISP-001', 'Dispensed against prescription'),
('00000000-0000-4000-8000-000000001602', '00000000-0000-4000-8000-000000000416', '00000000-0000-4000-8000-000000000518', (SELECT id FROM patients WHERE uhid='UHID000008'), NOW() - INTERVAL '1 day', '00000000-0000-4000-8000-000000000106', '00000000-0000-4000-8000-000000001302', 30, '00000000-0000-4000-8000-000000000001', 'out', 'DISP-002', 'Dispensed against prescription'),
('00000000-0000-4000-8000-000000001603', '00000000-0000-4000-8000-000000000404', '00000000-0000-4000-8000-000000000506', (SELECT id FROM patients WHERE uhid='UHID000009'), NOW() - INTERVAL '3 days', '00000000-0000-4000-8000-000000000106', '00000000-0000-4000-8000-000000001303', 90, '00000000-0000-4000-8000-000000000001', 'out', 'DISP-003', 'Dispensed against prescription'),
('00000000-0000-4000-8000-000000001604', '00000000-0000-4000-8000-000000000405', '00000000-0000-4000-8000-000000000507', (SELECT id FROM patients WHERE uhid='UHID000009'), NOW() - INTERVAL '3 days', '00000000-0000-4000-8000-000000000106', '00000000-0000-4000-8000-000000001304', 90, '00000000-0000-4000-8000-000000000001', 'out', 'DISP-004', 'Dispensed against prescription'),
('00000000-0000-4000-8000-000000001605', '00000000-0000-4000-8000-000000000401', '00000000-0000-4000-8000-000000000501', (SELECT id FROM patients WHERE uhid='UHID000006'), NOW(), '00000000-0000-4000-8000-000000000106', '00000000-0000-4000-8000-000000001305', 15, '00000000-0000-4000-8000-000000000001', 'out', 'DISP-005', 'Dispensed against prescription')
ON CONFLICT (id) DO NOTHING;

-- Decrement batch remaining quantities to match dispensed amounts
UPDATE medicine_batches SET remaining_qty = remaining_qty - 30 WHERE batch_no = 'ATV-A112';
UPDATE medicine_batches SET remaining_qty = remaining_qty - 30 WHERE batch_no = 'ASP-A789';
UPDATE medicine_batches SET remaining_qty = remaining_qty - 90 WHERE batch_no = 'MET-A678';
UPDATE medicine_batches SET remaining_qty = remaining_qty - 90 WHERE batch_no = 'AML-A901';
UPDATE medicine_batches SET remaining_qty = remaining_qty - 15 WHERE batch_no = 'PCM-A123';

-- Set patient outstanding balances
UPDATE patients SET outstanding = 11200.00 WHERE uhid = 'UHID000008';
UPDATE patients SET outstanding = 13440.00 WHERE uhid = 'UHID000009';
UPDATE patients SET outstanding = 500.00 WHERE uhid = 'UHID000006';
PATIENT_DATA

  echo "  seeded appointments, encounters, prescriptions, lab orders, admissions, charges, invoices, payments, consents, and stock transactions."
}

if [[ "${1:-}" == "--local" ]]; then
  DB_NAME="${DB_NAME:-medos}"
  echo "Seeding dev data into local Postgres database '$DB_NAME'..."
  psql -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE"
  echo "Done. Demo users: admin/doctor/nurse/reception/pharmacy/billing — all password 'password'."
  # Needs a reachable API to encrypt, so it only runs in the Docker path.
  echo "Skipping demo patients: --local seeds SQL only. Run the app and use this script without --local."
  exit 0
fi

echo "Seeding dev data into the 'medos-db' container..."
docker compose exec -T db psql -U "${DB_USER:-medos}" -d "${DB_NAME:-medos}" -v ON_ERROR_STOP=1 -f - < "$SQL_FILE"
echo "Done. Demo users: admin/doctor/nurse/reception/pharmacy/billing — all password 'password'."
seed_patients_api
seed_patient_data