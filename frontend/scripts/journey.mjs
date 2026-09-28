/**
 * UI journey test — drives the real forms a receptionist, doctor and billing
 * clerk use, rather than the API. Verifies the three flows that were broken:
 * patient registration, encounter sign-off, and invoice + payment.
 *
 * Selectors are positional within the form because the pages use <label> without
 * htmlFor, so the labels are not associated with their inputs (tracked
 * separately — see the accessibility finding).
 *
 * Usage: E2E_PASSWORD=... node scripts/journey.mjs
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8080';
const PW = process.env.E2E_PASSWORD;
const OUT = '/tmp/journey';
mkdirSync(OUT, { recursive: true });

if (!PW) {
  console.error('E2E_PASSWORD is required');
  process.exit(1);
}

const errors = [];
let step = 0;
const shot = async (page, name) => {
  step += 1;
  await page.screenshot({ path: `${OUT}/${String(step).padStart(2, '0')}-${name}.png` });
};

const check = (label, ok, extra = '') =>
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${extra ? `  — ${extra}` : ''}`);

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await ctx.newPage();
page.on('console', (m) => { if (m.type() === 'error') errors.push(`console: ${m.text()}`); });
page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
page.on('response', (r) => { if (r.status() >= 500) errors.push(`http ${r.status()} ${r.url()}`); });

// ---------------------------------------------------------------- sign in
await page.goto(`${BASE}/login`);
await page.getByLabel(/username/i).fill('admin');
await page.getByLabel(/password/i).fill(PW);
await page.getByRole('button', { name: /sign in/i }).click();
await page.waitForURL(/dashboard/);
check('sign in and land on the dashboard', true);

// ------------------------------------------- 1. register a patient (was 500)
const stamp = Date.now().toString().slice(-6);
const patientName = `Journey Patient ${stamp}`;

await page.goto(`${BASE}/patients`);
await page.getByRole('button', { name: /register patient/i }).first().click();
const regForm = page.locator('form');
// Select by .form-group position: the labels carry no htmlFor, so there is no
// accessible name to match on, and type-based selectors are ambiguous because
// the phone/address fields have no type attribute.
const g = (n) => regForm.locator('.form-group').nth(n);
// Group order in the markup: 0 name, 1 age, 2 gender, 3 phone, 4 email,
// 5 blood group, 6 address.
await g(0).locator('input').fill(patientName);              // Full Name
await g(1).locator('input').fill('46');                      // Age
await g(2).locator('select').selectOption('female');         // Gender
await g(3).locator('input').fill('9810000011');              // Phone
await g(4).locator('input').fill(`journey${stamp}@example.com`); // Email
await g(5).locator('select').selectOption('O+');             // Blood group
await regForm.locator('input[type=checkbox]').first().check(); // DPDP consent
await shot(page, 'register-patient-filled');
await regForm.getByRole('button', { name: /register patient/i }).click();
await page.waitForTimeout(1500);

const listed = await page.getByText(patientName, { exact: false }).count();
check('patient registered through the form and appears in the list', listed > 0);
const uhidText = await page.locator('table').first().innerText();
const uhid = (uhidText.match(/UHID\d{6}/) ?? [''])[0];
check('a UHID was issued', /^UHID\d{6}$/.test(uhid), uhid);
await shot(page, 'register-patient-done');

// ------------------------------------------ 2. encounter: create then sign
await page.goto(`${BASE}/encounters`);
await page.waitForTimeout(1000);
await page.getByRole('button', { name: new RegExp(patientName) }).first().click();
await page.waitForTimeout(800);
// The complaint/diagnosis textareas and the vitals fields, by their labels.
await page.locator('textarea').first().fill('Follow-up: chest tightness').catch(() => {});
await page.locator('textarea').nth(1).fill('Angina — review needed').catch(() => {});
// The vitals inputs have no htmlFor/id association, so getByLabel finds
// nothing. Select them by their position in the vitals grid instead.
const vitalsGrid = page.locator('.vitals-grid');
const vitalInputs = vitalsGrid.locator('input');
await vitalInputs.nth(0).fill('146/90');   // BP
await vitalInputs.nth(1).fill('96');       // Pulse
await vitalInputs.nth(2).fill('98.6');     // Temp
await shot(page, 'encounter-form');

const createBtn = page.getByRole('button', { name: /start encounter/i }).first();
if (await createBtn.count()) {
  await createBtn.click();
  // Creating does not reload the worklist today, so reload the page to see it.
  await page.waitForTimeout(1500);
  await page.reload();
  await page.waitForTimeout(1500);
}
const worklist = page.locator('.card').filter({ hasText: 'Awaiting sign-off' }).first();
const queued = await worklist.getByText(patientName, { exact: false }).count().catch(() => 0);
check('encounter created and queued for sign-off', queued > 0,
  'this patient has a row in the worklist');
await shot(page, 'encounter-queued');

// Reopen it from the worklist — the flow that did not exist before.
const resume = page.getByRole('button', { name: /resume and sign/i }).first();
if (await resume.count()) {
  await resume.click();
  await page.waitForTimeout(1200);
  const sign = page.getByRole('button', { name: /sign & close|sign and close/i }).first();
  check('encounter reopened and sign-off control reachable', (await sign.count()) > 0);
  // Resume this journey's own encounter rather than whichever sorts first, so
  // the assertion is about a known row. The worklist can hold encounters from
  // other runs; signing one must not be assumed to empty it.
  const own = worklist.locator('tr').filter({ hasText: patientName }).first();
  const ownResume = own.getByRole('button', { name: /resume and sign/i });
  if (await ownResume.count()) {
    await ownResume.click();
    await page.waitForTimeout(1200);
    const before = await worklist.locator('tr').filter({ hasText: patientName }).count();
    const signBtn = page.getByRole('button', { name: /sign & close|sign and close/i }).first();
    if (await signBtn.count()) {
      await signBtn.click();
      await page.waitForTimeout(2000);
      const after = await worklist.locator('tr').filter({ hasText: patientName }).count();
      check('signing removes this encounter from the worklist', after < before, `${before} -> ${after}`);
    } else {
      check('sign-off control reachable for this encounter', false);
    }
  } else {
    check('this journey encounter is listed in the worklist', false);
  }
  await shot(page, 'encounter-signed');
} else {
  check('encounter reopened from the worklist', false, 'no Resume and sign button');
}

// ------------------------------------------------ 3. invoice and payment
// Billing only offers an invoice for a patient who already has unbilled
// charges, and the only write path that creates one is a pharmacy dispense —
// which needs a medicine, a batch and a signed prescription to set up first.
// The API flow probe covers that chain; here we drive the billing screens for
// a patient that already has an unbilled charge.
const BILLABLE_UHID = process.env.E2E_BILLABLE_UHID;

await page.goto(`${BASE}/billing`);
await page.waitForTimeout(1200);

if (BILLABLE_UHID) {
  const search = page.getByPlaceholder(/search/i).first();
  await search.fill(BILLABLE_UHID);
  await page.waitForTimeout(1800);
  // The list renders the patient's name, not their UHID, so take the first
  // result after searching.
  await page.locator('button.btn-ghost').filter({ hasText: /Outstanding/ }).first().click();
  await page.waitForTimeout(1500);

  const billBtn = page.getByRole('button', { name: /^Bill( All| \(\d+\))?$/ }).first();
  check('unbilled charges surfaced for the selected patient', (await billBtn.count()) > 0);
  await shot(page, 'billing-unbilled');

  if (await billBtn.count()) {
    await billBtn.click();
    await page.waitForTimeout(1000);
    await shot(page, 'billing-invoice-modal');
    // "Generate Invoice" stays disabled until charges are selected, with no
    // visible reason; select them first.
    const invoiceModal = page.locator('.modal');
    await invoiceModal.getByRole('button', { name: /Select All/i }).click();
    await page.waitForTimeout(400);
    await invoiceModal.getByRole('button', { name: /Generate Invoice/i }).click();
    await page.waitForTimeout(2200);

    const invoiceNo = (await page.locator('table').last().innerText().catch(() => ''))
      .match(/INV-[\w-]+/)?.[0] ?? '';
    check('invoice generated from unbilled charges', invoiceNo.length > 0, invoiceNo);
    await shot(page, 'billing-invoice-created');

    const pay = page.getByRole('button', { name: /^Pay$/ }).first();
    if (await pay.count()) {
      await pay.click();
      await page.waitForTimeout(800);
      // Pay the exact balance due: the backend rejects an overpayment, and an
      // arbitrary amount is not a valid test.
      const dueText = await page.locator('.modal').innerText();
      const due = (dueText.match(/([\d,]+\.\d{2})/) ?? ['5.25'])[1].replace(/,/g, '');
      await page.locator('input[type=number]').first().fill(due);
      await page.locator('.modal').getByRole('button', { name: /record payment|confirm|pay/i }).last().click();
      await page.waitForTimeout(2200);
      const status = (await page.locator('table').last().innerText())
        .match(/partially_paid|paid/)?.[0] ?? '(none)';
      check('payment recorded and invoice marked paid', status === 'paid', status);
      await shot(page, 'billing-payment-recorded');
    } else {
      check('a Pay control exists on the invoice', false);
    }
  }
} else {
  check('billable patient located', false, 'E2E_BILLABLE_UHID not provided');
}

console.log(`\n${'='.repeat(70)}`);
console.log(errors.length ? `Browser errors:\n  ${errors.join('\n  ')}` : 'No console errors, page errors or 5xx responses.');
console.log(`Screenshots in ${OUT}`);

await browser.close();
