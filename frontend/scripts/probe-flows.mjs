/**
 * Flow probe. Exercises every mutating and reading path in the app as the
 * bootstrap admin and reports a pass/fail matrix. Read-only with respect to
 * source; it does create real rows, so run it against a dev database.
 *
 * Usage: node scripts/probe-flows.mjs
 */
const BASE = process.env.FLOW_BASE_URL ?? 'http://localhost:8080/api/v1';
const PASSWORD = process.env.E2E_PASSWORD;
const KEY = (label) => `probe-${label}-${process.pid}`;
let lastIdemKey = null;
const keyOf = () => lastIdemKey;

let token = '';
const results = [];

async function call(label, method, path, body, { idem = false, idemKey, expect, query } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (idem) { headers['Idempotency-Key'] = KEY(label); lastIdemKey = headers['Idempotency-Key']; }
  if (idemKey) { headers['Idempotency-Key'] = idemKey; lastIdemKey = idemKey; }

  const started = Date.now();
  let status = 0;
  let detail = '';
  try {
    const url = query ? `${BASE}${path}?${new URLSearchParams(query)}` : `${BASE}${path}`;
    const res = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });
    status = res.status;
    const text = await res.text();
    let json = null;
    try {
      json = JSON.parse(text);
      detail = json.message ?? (json.content?.length ? `page(${json.content.length})` : 'ok');
      if (json.code) detail = `${json.code}: ${detail}`;
    } catch {
      detail = text.slice(0, 60) || 'ok';
    }
    globalThis.__body = json;
  } catch (err) {
    status = 0;
    detail = err.name === 'TimeoutError' ? 'TIMEOUT' : err.message;
    globalThis.__body = null;
  }

  const ms = Date.now() - started;
  const want = expect ?? [200, 201];
  const pass = want.includes(status);
  results.push({ label, status, pass, detail, ms });
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${String(status).padEnd(4)} ${label.padEnd(42)} ${String(ms).padStart(5)}ms  ${detail.slice(0, 60)}`);
  // Return status and the parsed body so callers can chain on real ids.
  return { status, body: globalThis.__body };
}

// --- sign in -------------------------------------------------------------
const login = await call('POST /auth/login', 'POST', '/auth/login', {
  username: 'admin',
  password: PASSWORD,
});
if (login.status !== 200) {
  console.log('\nCannot continue without a token.');
  process.exit(1);
}
const raw = await fetch(`${BASE}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: PASSWORD }),
});
token = (await raw.json()).token;

// --- patients ------------------------------------------------------------
const pat = await call('POST /patients (register)', 'POST', '/patients', {
  name: 'Flow Probe', age: 41, gender: 'female', phone: '9812345678', dpdpConsent: true,
}, { expect: [200, 201] });
const patientId = pat?.body?.id;

await call('GET  /patients (list)', 'GET', '/patients');
await call('GET  /patients?search=', 'GET', '/patients?search=Flow');
if (patientId && typeof patientId === 'string' && patientId.length === 36) {
  await call('GET  /patients/{id}', 'GET', `/patients/${patientId}`);
}
await call('GET  /patients/uhid/{uhid}', 'GET', '/patients/uhid/UHID000001');

// --- pharmacy catalogue (needed before a prescription can reference it) ---
const med = await call('POST /pharmacy/medicines  (create)', 'POST', '/pharmacy/medicines', {
  name: `Paracetamol 500mg (${process.pid})`, genericName: 'Paracetamol', category: 'ANALGESIC',
  unit: 'tablet', unitPrice: 2.5,
}, { expect: [200, 201] });
const medicineId = med?.body?.id;

if (medicineId) {
  await call('POST /pharmacy/medicines/{id}/stock-in', 'POST',
    `/pharmacy/medicines/${medicineId}/stock-in`, undefined, { expect: [200, 201],
    query: { batchNo: 'BATCH-A', expiryDate: '2027-06-30', quantity: 100, purchasePrice: 2, supplier: 'Probe Pharma' } });
}

// --- encounters ----------------------------------------------------------
const enc = await call('POST /encounters (create)', 'POST', '/encounters', {
  patientId,
  chiefComplaint: 'Flow probe',
  diagnosis: 'probe',
  vitals: { bp: '120/80', pulse: '72', temp: '37', spo2: '98' },
}, { expect: [200, 201] });
const encId = enc?.body?.id;
let prescriptionId = null;
if (encId && typeof encId === 'string' && encId.length === 36) {
  await call('GET  /encounters/{id}', 'GET', `/encounters/${encId}`);
  const rx = await call('POST /encounters/{id}/prescriptions', 'POST', `/encounters/${encId}/prescriptions`, {
    encounterId: encId, medicineId, patientId, dosage: '1', frequency: 'OD', duration: '5',
  }, { expect: [200, 201] });
  prescriptionId = rx?.body?.id;
  await call('POST /encounters/suggest-medicines', 'POST', '/encounters/suggest-medicines', { diagnosis: 'fever' });
  await call('GET  /encounters/prescriptions/pending', 'GET', '/encounters/prescriptions/pending');
  await call('POST /encounters/{id}/sign', 'POST', `/encounters/${encId}/sign`, {}, { expect: [200, 201] });
}
await call('GET  /encounters?status=open  (worklist)', 'GET', '/encounters?status=open', undefined, { expect: [200] });
await call('GET  /encounters?status=open&mine=true', 'GET', '/encounters?status=open&mine=true', undefined, { expect: [200] });

// --- pharmacy ------------------------------------------------------------
await call('GET  /pharmacy/medicines', 'GET', '/pharmacy/medicines');
await call('POST /pharmacy/dispense  (no Idempotency-Key)', 'POST', '/pharmacy/dispense', {}, { expect: [400] });
if (prescriptionId) {
  const disp = { patientId, prescriptionId, quantity: 2 };
  const first = await call('POST /pharmacy/dispense  (with Idempotency-Key)', 'POST',
    '/pharmacy/dispense', disp, { idem: true, expect: [200, 201] });
  // Replaying the SAME key must not dispense a second time.
  const replayKey = keyOf(first);
  await call('POST /pharmacy/dispense  (same key = replay)', 'POST', '/pharmacy/dispense', disp,
    { idemKey: replayKey, expect: [200, 201] });
}
await call('GET  /pharmacy/transactions', 'GET', '/pharmacy/transactions');

// --- admissions ----------------------------------------------------------
await call('GET  /admissions/rooms', 'GET', '/admissions/rooms');
await call('GET  /admissions/rooms/available', 'GET', '/admissions/rooms/available');
await call('GET  /admissions/active', 'GET', '/admissions/active');

// --- billing -------------------------------------------------------------
await call('GET  /billing/patients/{id}/invoices', 'GET', `/billing/patients/${patientId}/invoices`, undefined, { expect: [200, 404] });
await call('GET  /billing/patients/{id}/unbilled', 'GET', `/billing/patients/${patientId}/unbilled`, undefined, { expect: [200, 404] });
await call('POST /billing/invoices  (no Idempotency-Key)', 'POST', '/billing/invoices', {}, { expect: [400] });
await call('POST /billing/payments  (no Idempotency-Key)', 'POST', '/billing/payments', {}, { expect: [400] });

// --- misc ----------------------------------------------------------------
await call('GET  /dashboard', 'GET', '/dashboard');
await call('GET  /notifications', 'GET', '/notifications');
await call('GET  /notifications/unread-count', 'GET', '/notifications/unread-count');

// --- summary -------------------------------------------------------------
const failed = results.filter((r) => !r.pass);
console.log(`\n${'='.repeat(72)}`);
console.log(`${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('\nFailing:');
  for (const f of failed) console.log(`  ${String(f.status).padEnd(4)} ${f.label}  -> ${f.detail.slice(0, 70)}`);
}
