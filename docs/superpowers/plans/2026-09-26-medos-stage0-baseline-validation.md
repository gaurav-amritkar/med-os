# Stage 0 Implementation Plan — Baseline Validation

- **Date:** 2026-09-26
- **Spec:** `docs/superpowers/specs/2026-09-26-medos-production-release-design.md` §6 Stage 0
- **Goal:** Produce `docs/release/defect-baseline.md` — an evidence-backed defect list observed on the **current** stack, before any of it is replaced.
- **Gate:** Every defect triaged to *fix now* / *fix in a later stage* / *accepted risk*.

## Why this runs first

Findings in the spec are derived from code and config inspection. Stages 1–4 change the deployment substrate, so validating afterwards would test a stack that no longer exists. Stage 0 buys observed failures to plan against, and it establishes the test assets (Playwright suite, integrity checks) that Stage 4 re-runs as the release gate.

## Tech stack

Docker Compose v5.4.0 / Docker 29.7.2 · Spring Boot 3.2 on Temurin 21 · React 19 + Vite 8 · Playwright (new) · Node 20+ for scripts · `psql` via `docker compose exec`

## Scope boundary

Stage 0 adds Playwright **locally**. Wiring it into CI is Stage 1 item 1.3 — do not add a CI job in this stage, or the two will conflict.

## Verified contracts this plan relies on

Confirmed by reading the code on 2026-09-26. Do not re-derive; do not change without updating this list.

| Fact | Source |
|---|---|
| `POST /api/v1/auth/login` body `{username, password}` → `{token, expiresIn, userId, username, fullName, role, specialization, tenantId}` | `dto/LoginRequest.java`, `dto/LoginResponse.java` |
| `POST /api/v1/patients` requires role `RECEPTIONIST` or `ADMIN`; body `{name*, age*, gender*, phone*, email?, address?, bloodGroup?, dpdpConsent*, consentPurpose?}` → 201 `PatientDTO` | `controller/PatientController.java:23-28` |
| `GET /api/v1/patients` returns `PageResponse<PatientDTO>` (paged, default size 20) | `controller/PatientController.java:30-36` |
| `POST /api/v1/onboarding/register` is `permitAll`, creates tenant + active admin user | `SecurityConfig.java:52`, `controller/OnboardingController.java:28` |
| `patients` PII columns hold AES-256-GCM ciphertext; `version` is `NOT NULL` | `V2__initial_schema.sql:69-84` |
| Charge totals are `charges.total_amount`; invoice totals are `invoices.total_amount`; `discount` and `gst_total` are separate | `V2__initial_schema.sql` |
| Stock is `medicine_batches.remaining_qty`; movements are `stock_transactions.quantity` with `transaction_type IN ('in','out','adjustment','return_tx')` | `V2__initial_schema.sql` |
| `audit_log` has **no** `tenant_id` column, by design comment | `V2__initial_schema.sql` |
| `tools/seed-dev.sql` lines 1–55 correctly seed tenant + 7 users + roles, with a known BCrypt hash for `password` | `tools/seed-dev.sql` |
| `tools/seed-dev.sql` lines 57+ (patients) are broken: plaintext into ciphertext columns, and `version` omitted | `tools/seed-dev.sql:57-70` |
| `tests/e2e/api-test.sh` runs `docker compose down` + `up -d --build` itself, targets `http://localhost:8080` (backend directly, bypassing nginx), and **overwrites** `tests/e2e/test-report.md` | `tests/e2e/api-test.sh:102-133, 1124-1128` |

**New finding raised by this plan's own research — verify it in Task 7, check 1:** `tenant_id` is `NOT NULL` on only 4 tables (`tenant_users`, `tenant_configs`, `tenant_settings`, `patients`) and **nullable on 15** (`appointments`, `opd_queue`, `encounters`, `prescriptions`, `lab_orders`, `rooms`, `admissions`, `medicine_catalog`, `medicine_batches`, `stock_transactions`, `charges`, `invoices`, `payments`). Nothing in the schema forces a clinical or financial row to belong to a tenant. `TenantStatementInspector` filters with `tenant_id = :tenantId`, so a NULL-tenant row is invisible to every tenant rather than leaking — silent disappearance, not exposure. Expected fix is a `V3` migration: backfill from the parent row, then `SET NOT NULL`.

---

## Task 0 — Preflight and evidence capture

**Files:** none (creates `docs/release/` output dir)
**Creates:** `docs/release/` directory

```bash
cd /Users/sai/Documents/Gaurav/workspace/GitHubProjects/med-os
mkdir -p docs/release
docker compose version && docker version --format '{{.Server.Version}}'
docker compose config --services
git rev-parse HEAD && git status --porcelain | wc -l
```

**Record** in `docs/release/defect-baseline.md` under a "Environment" heading: commit SHA, the 50-dirty-file count, Docker and Compose versions, and the service list. A defect reproduced on one commit is not evidence about another.

**Verify:** `docs/release/` exists; commit SHA captured.

---

## Task 1 — Bring up the current stack

```bash
docker compose down --remove-orphans
docker compose up -d --build
docker compose ps
```

Wait for health, then capture the evidence:

```bash
curl -sf http://localhost:8080/manage/health; echo
curl -s  http://localhost:8080/manage/info;  echo
docker compose logs backend --tail=100 > docs/release/logs-backend-boot.txt
docker compose logs frontend --tail=50 > docs/release/logs-frontend-boot.txt
```

**Watch for and record:**
- Flyway applying `V1__init.sql` and `V2__initial_schema.sql` — confirm both, and confirm no `migrate` container exists (there is none; the backend runs Flyway in-band).
- `AdminBootstrapRunner` behaviour. `docker-compose.yml:42` sets `BOOTSTRAP_ADMIN_PASSWORD: Admin@123`, so a fresh database provisions an `admin` account. **Record the account that exists and move on — do not treat this as a finding beyond the already-recorded committed-credentials blocker.**
- Hibernate `ddl-auto: validate` passing against the Flyway-owned schema. A validation failure here is a high-severity schema/entity mismatch — capture the full message.

**Stop condition:** if `/manage/health` never returns healthy, capture `docker compose logs backend --tail=200` and record it as defect **D-001** rather than debugging forward. The point of Stage 0 is observation, not repair.

**Verify:** `curl -sf .../manage/health` exits 0; two log files exist.

---

## Task 2 — Seed identity data via SQL

Users, roles, and the tenant are **not** PII-encrypted, so SQL is safe and correct for them. Patients are handled in Task 3 through the API.

```bash
docker compose exec -T db psql -U postgres -d medos -v ON_ERROR_STOP=1 \
  -f - < <(sed -n '1,55p' tools/seed-dev.sql)
docker compose exec -T db psql -U postgres -d medos -c \
  "SELECT username, active FROM users ORDER BY username;"
docker compose exec -T db psql -U postgres -d medos -c \
  "SELECT t.slug, u.username, tu.role FROM tenant_users tu
     JOIN users u ON u.id = tu.user_id JOIN tenants t ON t.id = tu.tenant_id
   ORDER BY u.username;"
```

`ON CONFLICT` clauses make this idempotent. Lines 1–55 are the tenant, seven users, and their tenant memberships, including `doctor2` for cross-doctor testing.

**Verify:** 7 users, 7 `tenant_users` rows, 1 tenant. Record the actual counts.

**Do not run** `tools/seed-dev.sql` in full — its patient block is known broken (Task 9 defect **D-002**).

---

## Task 3 — Register patients through the API

This is the step that proves the encryption path. Creating patients by API means every PII column is written as ciphertext by the application; a round-trip read that returns the submitted name proves decrypt works.

**Create** `tests/e2e/seed-and-manifest.mjs` (Node 20+ global `fetch`, no new dependency):

```js
const API = process.env.API_URL ?? 'http://localhost:8080/api/v1';

async function login(username, password) {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error(`login ${username} -> ${res.status}`);
  return (await res.json()).token;
}

const PATIENTS = [
  { name: 'Rahul Mehta',   age: 34, gender: 'male',   phone: '9876543210', email: 'rahul@example.com',  bloodGroup: 'B+',  dpdpConsent: true,  address: 'Demo City' },
  { name: 'Anita Joshi',   age: 28, gender: 'female', phone: '9876543211', email: 'anita@example.com',  bloodGroup: 'O+',  dpdpConsent: true,  address: 'Demo City' },
  { name: 'Suresh Reddy',  age: 62, gender: 'male',   phone: '9876543212', email: 'suresh@example.com', bloodGroup: 'A+',  dpdpConsent: true,  address: 'Demo City' },
  { name: 'Kavita Nair',   age: 45, gender: 'female', phone: '9876543213', email: 'kavita@example.com', bloodGroup: 'AB+', dpdpConsent: true,  address: 'Demo City' },
  { name: 'Aman Khan',     age: 22, gender: 'male',   phone: '9876543214', email: 'aman@example.com',   bloodGroup: 'O-',  dpdpConsent: false },
];

const token = await login('reception', 'password');
const manifest = { createdAt: new Date().toISOString(), patients: [] };

for (const p of PATIENTS) {
  const res = await fetch(`${API}/patients`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(p),
  });
  if (res.status !== 201) throw new Error(`create ${p.name} -> ${res.status} ${await res.text()}`);
  const dto = await res.json();
  manifest.patients.push({ submitted: p, returned: dto });
  console.log(`${dto.uhid}  ${dto.name}`);
}

await (await import('node:fs/promises')).writeFile(
  'tests/e2e/manifest.json', JSON.stringify(manifest, null, 2));
```

**Verify:**
```bash
node tests/e2e/seed-and-manifest.mjs
docker compose exec -T db psql -U postgres -d medos -c \
  "SELECT uhid, left(name, 24) AS name_col FROM patients ORDER BY uhid;"
```

**Expected:** five `UHID00000N` values, and `name_col` values that are **Base64, not readable names**. If any name reads as `Rahul Mehta` in SQL, the encryption path is not engaged — record as defect **D-003**, severity P0.

Confirm the response names are correct plaintext — that is the contrast that makes the check meaningful.

---

## Task 4 — Re-run the existing API suite and diff

The script overwrites its own report, so preserve the committed one first — it is the only record of the 2026-09-13 run.

```bash
mkdir -p docs/release
cp tests/e2e/test-report.md docs/release/test-report-2026-09-13-baseline.md
./tests/e2e/api-test.sh --skip-start
cp tests/e2e/test-report.md docs/release/test-report-$(date -u +%Y%m%d-%H%M%S).md
diff docs/release/test-report-2026-09-13-baseline.md tests/e2e/test-report.md
```

`--skip-start` matters: without it the script runs `docker compose down` and rebuilds, discarding the Tasks 2–3 data.

**Note in the baseline document:** this suite targets `http://localhost:8080` — the backend container directly. **It never traverses nginx**, so it cannot detect a broken proxy rule, a missing `proxy_set_header`, or a frontend build defect. That gap is the reason for Task 5.

**Verify:** report regenerated; diff either empty or explained line by line in `defect-baseline.md`.

---

## Task 5 — Add the Playwright harness

**Files:** `frontend/package.json` (modify), `frontend/playwright.config.js` (new), `frontend/e2e/fixtures.js` (new), `frontend/e2e/auth.setup.js` (new), `frontend/.gitignore` (modify — it exists but does not cover Playwright output)

Append to `frontend/.gitignore`:

```
e2e/.auth/
test-results/
playwright-report/
```

```bash
cd frontend
npm install --save-dev @playwright/test
npx playwright install chromium
```

`package.json` scripts — add without touching existing entries:

```json
"test:e2e": "playwright test",
"test:e2e:ui": "playwright test --ui"
```

`playwright.config.js`:

```js
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  outputDir: 'test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'setup', testMatch: /auth\.setup\.js/ }, { name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
```

`workers: 1` and `fullyParallel: false` are deliberate. Tests share one database and create bills, stock movements, and admissions; parallel workers would produce false failures that mask real ones.

**`e2e/fixtures.js`** — one fixture that fails a test on browser-side errors, since "the page rendered" is not the same as "the page worked":

```js
import { test as base, expect } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    const errors = [];
    page.on('console', (m) => { if (m.type() === 'error') errors.push(`console: ${m.text()}`); });
    page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
    page.on('response', (r) => { if (r.status() >= 500) errors.push(`http ${r.status()} ${r.url()}`); });
    await use(page);
    expect(errors, 'browser reported errors').toEqual([]);
  },
});
export { expect };
```

**`e2e/auth.setup.js`** — log in through the real UI once per role and save state, so role coverage does not re-login on every spec:

```js
import { test as setup, expect } from '@playwright/test';

const ROLES = ['admin', 'doctor', 'nurse', 'reception', 'pharmacy', 'billing'];

for (const role of ROLES) {
  setup(`authenticate as ${role}`, async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/username/i).fill(role);
    await page.getByLabel(/password/i).fill('password');
    await page.getByRole('button', { name: /sign in|login/i }).click();
    await expect(page).toHaveURL(/\/dashboard|\/$/);
    await page.context().storageState({ path: `e2e/.auth/${role}.json` });
  });
}
```

**Verify:** `npx playwright test --project=setup` produces six files in `frontend/e2e/.auth/`. The selectors above are confirmed against `frontend/src/pages/Login.jsx` (labels `Username` / `Password` via `htmlFor`, submit button text `Sign In →`), and `ProtectedRoute.jsx:8` redirects to `/login` without a token — so `getByLabel(/username/i)` and `getByRole('button', { name: /sign in/i })` are correct as written.

---

## Task 6 — Write the critical-flow specs

**Files:** `frontend/e2e/01-login.spec.js`, `02-patients.spec.js`, `03-encounters.spec.js`, `04-pharmacy-fefo.spec.js`, `05-admissions.spec.js`, `06-billing.spec.js`, `07-onboarding.spec.js`, `08-tenant-isolation.spec.js` (all new)

| Spec | Asserts |
|---|---|
| `01-login` | Each of the 6 roles reaches the dashboard; a wrong password shows an error and no token; a logged-out visit to `/patients` redirects to `/login`. |
| `02-patients` | Register a patient, find them by search, open the detail view, confirm the returned name matches what was typed. |
| `03-encounters` | Create an encounter with vitals, add a prescription, sign and close it; confirm the AI-suggest endpoint answers. |
| `04-pharmacy-fefo` | Add two batches with **different expiry dates**, dispense, and confirm the earliest-expiry batch is the one decremented. This is the FEFO guarantee and must be proven in the browser, not just in SQL. |
| `05-admissions` | Admit to an available room, confirm the bed is no longer offered, discharge, confirm auto-billing posted room charges. |
| `06-billing` | Generate an invoice for unbilled charges, record a payment, confirm the balance and the patient's outstanding figure update. Include the `Idempotency-Key` path: the same key twice must not double-bill. |
| `07-onboarding` | Register a second tenant through `/onboarding`; confirm its admin can log in **today**. After Stage 2 this must invert — a `PENDING` tenant must not obtain a token. Flag it so the change is not mistaken for a regression. |
| `08-tenant-isolation` | With tenant A's session, request tenant B's patient by id and assert **not found**; assert tenant A's patient list excludes tenant B's patients. This is the multi-tenant guarantee proven end to end. |

Each spec loads the role state saved by `auth.setup.js` via `test.use({ storageState: 'e2e/.auth/<role>.json' })`.

**Verify:** `npm run test:e2e` runs. Every failure becomes a numbered entry in `defect-baseline.md` — a failing spec here is a **finding**, not an obstacle. Do not fix application code in Stage 0.

---

## Task 7 — Layer 1 database integrity checks

**Files:** `tests/db/integrity.sh` (new), output to `docs/release/db-integrity-<timestamp>.md`

Structure: each check is a named `psql` invocation through a `check "<name>" "<expected>" "<sql>"` helper that prints `PASS`/`FAIL` plus the offending rows, and accumulates a failure count. Exit non-zero if any check fails.

**The checks, in priority order:**

1. **Tenant coverage.** `SELECT count(*) FROM <table> WHERE tenant_id IS NULL` for each of the 15 tables with a nullable `tenant_id`. Expect 0 everywhere. Also assert `audit_log` has no `tenant_id` column at all and report it as a design gap.
2. **Money — invoice reconciliation.** Per invoice, `SUM(charges.total_amount) - COALESCE(invoices.discount,0) = invoices.total_amount`. Expect 0 mismatches. Any mismatch is **P0**.
3. **Money — payment reconciliation.** `SUM(payments.amount) WHERE payments.status='success' GROUP BY invoice_id` equals `invoices.paid_amount`. Expect 0 mismatches.
4. **Money — patient outstanding.** `SUM(charges.total_amount) WHERE status IN ('unbilled','billed')` minus successful payments equals `patients.outstanding`, per patient.
5. **Referential integrity.** Orphan counts for every foreign key: `charges.patient_id`, `invoices.patient_id`, `payments.invoice_id`, `prescriptions.encounter_id`, `stock_transactions.batch_id`, `admissions.patient_id`. Expect 0.
6. **Inventory.** Per batch, `SUM(quantity) WHERE transaction_type='in'` minus `SUM(quantity) WHERE transaction_type='out'` equals `remaining_qty`, adjusted for `adjustment` and `return_tx` rows. Report any batch where `remaining_qty < 0`. Confirm the sign convention against real data before asserting, and state the convention used.
7. **FEFO.** For each medicine with an `out` movement, the referenced batch's `expiry_date` must be the earliest among batches that had stock at that moment. Report any violation with both dates.
8. **Sequences.** `last_value` of `uhid_seq`, `invoice_number_seq`, `payment_number_seq` must exceed the highest issued value; `count(*)` versus `count(DISTINCT ...)` on `uhid` and `invoice_number` must match.
9. **Concurrency artifacts.** `version >= 1` on every row whose `updated_at` is later than `created_at`, proving optimistic locking engaged. Also assert no `dispense` transaction exceeds the batch quantity.
10. **Plaintext-PII canary.** `SELECT count(*) FROM patients WHERE name ~ '^[A-Za-z ]+$'` — expect **0** for application-written rows. Add the same for `phone`, `email`, `address`, `blood_group`.
11. **Ciphertext sanity.** No NULL in any NOT NULL PII column; `length(name)` consistent with `IV ‖ ciphertext ‖ tag` for AES-GCM.
12. **Audit completeness.** Row counts in `audit_log` for `action IN ('CREATE','UPDATE','DELETE')` should be within tolerance of row counts created in the Task 3–6 window, and no `audit_log` row may have a NULL `user_id` or `ip_address` for an authenticated mutation.

```bash
chmod +x tests/db/integrity.sh
./tests/db/integrity.sh | tee docs/release/db-integrity-$(date -u +%Y%m%d-%H%M%S).md
```

**Verify:** the script runs to completion and exits non-zero when at least one check fails. Confirm that behaviour by temporarily breaking a check — a check suite that cannot fail is worthless.

---

## Task 8 — Layer 2 content verification

SQL can prove structure; only the application can prove content, because PII is ciphertext.

**Files:** `tests/e2e/verify-content.mjs` (new)

Read `tests/e2e/manifest.json`, re-fetch every patient through `GET /api/v1/patients/{id}` and `GET /api/v1/patients?search=`, and diff against what was submitted:

- `returned.name`, `.age`, `.gender`, `.phone`, `.email`, `.bloodGroup` equal `submitted` — this is the decrypt round-trip assertion.
- `uhid` matches `/^UHID\d{6}$/`.
- `dpdpConsent` matches what was submitted, and `dpdpConsentAt` is non-null exactly when consent was true — a DPDP-specific check.
- `outstanding` is `0.00` for a freshly registered patient.

```bash
node tests/e2e/verify-content.mjs | tee -a docs/release/db-integrity-$(date -u +%Y%m%d-%H%M%S).md
```

**Verify:** all five patients round-trip. A name mismatch means the manifest or the crypto path is wrong — record it as **P0** and stop.

---

## Task 9 — Write the defect baseline and close the gate

**Files:** `docs/release/defect-baseline.md` (new)

Template:

```markdown
# MedOS Stage 0 Defect Baseline

- Commit: <SHA from Task 0>
- Environment: <Docker/Compose versions, 50 dirty files at start>
- Date: <UTC timestamp>
- Suites: api-test.sh, Playwright (8 specs), 12 Layer 1 checks, 5 Layer 2 round-trips

## Summary
| ID | Severity | Area | Summary | Triage |
|----|----------|------|---------|--------|
| D-0XX | P0 | schema | ... | Stage 1 |

## Findings
### D-001 — <title>
- **Severity:** P0 / P1 / P2 / P3
- **Observed:** <exact command and actual output>
- **Expected:** <what should have happened>
- **Repro:** <numbered steps>
- **Spec reference:** <spec section, e.g. §4 Blocker>
- **Triage:** fix in Stage N / accept as risk / already tracked as <finding>

## Confirmed-clean
<checks that passed — as important as the failures>
```

Severity definitions: **P0** patient-data or billing correctness, cross-tenant exposure, or PHI plaintext at rest. **P1** a critical flow is broken. **P2** degraded but usable. **P3** cosmetic or documentation.

**Triage rules:**
- Anything found here that contradicts the spec's §4 findings is itself a spec correction — update the spec.
- A P0 in the *baseline* means Stage 1 gains a task before the compose work.
- A failure that cannot be reproduced twice is recorded as *flaky*, with the reproduction attempt noted, not silently dropped.

**Gate check:**

```bash
test -f docs/release/defect-baseline.md && echo GATE-OK
ls docs/release/
```

Stage 0 is complete when `defect-baseline.md` exists, every defect has a triage decision, and the `Confirmed-clean` section is non-empty. Then the Stage 1 plan is written against observed reality.

---

## What Stage 0 deliberately does not do

- No application code is modified. A defect found here is documented, not fixed.
- No CI job is added (Stage 1.3 owns that).
- No compose, secret, or migration change. The stack under test must stay as-is to be representative.
- `tools/seed-dev.sql` is not repaired here; its broken patient block is recorded as **D-002** and fixed in Stage 1.7.
