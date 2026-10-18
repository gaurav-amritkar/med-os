/**
 * UI snapshot capture.
 *
 * Drives every screen and sub-flow in the app in a real browser and writes one
 * PNG per step into docs/ui-snapshots/, plus manifest.json for the index
 * generator. The screenshots come from flows actually being executed: this
 * script creates the catalogue, encounter, prescription, admission and invoice
 * that the populated screens show, so it doubles as a smoke test of every flow.
 *
 * Writes real rows. Run it against a dev database, never production:
 *
 *   docker compose up -d --build && ./tools/seed-dev.sh
 *   cd frontend && npm run capture:ui
 *
 * Env:
 *   E2E_BASE_URL         default http://localhost:8080 (the only published port)
 *   E2E_PASSWORD         default 'password', which is what tools/seed-dev.sh sets
 *   CAPTURE_RUN_STAMP    short tag mixed into unique-by-constraint fields
 *                        (batch numbers, the new staff username). Defaults to the
 *                        pid. Set it explicitly for a reproducible capture.
 *
 * Deterministic by construction: the clock is frozen so the sidebar widget and
 * every rendered timestamp are identical run to run, and transitions, the text
 * caret and animations are switched off before the first shot.
 */

import { chromium } from '@playwright/test';
import { mkdir, rm, writeFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '..', '..');
const OUT = path.join(REPO_ROOT, 'docs', 'ui-snapshots');

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:8080';
const PASSWORD = process.env.E2E_PASSWORD ?? 'password';

const VIEWPORT = { width: 1440, height: 900 };
// Unique per run, so a second run against the same database does not collide on
// the unique username constraint and can be re-run without a reseed.
const RUN = process.env.CAPTURE_RUN_STAMP ?? String(process.pid);
// 09:30 on the freeze date. Chosen so the sidebar date reads as a working day.
const FROZEN = new Date('2026-10-18T09:30:00+05:30');

/** Areas, in the order they appear in the generated index. */
const AREAS = [
  { dir: '01-authentication', title: 'Authentication & onboarding' },
  { dir: '02-dashboard', title: 'Dashboard' },
  { dir: '03-patients', title: 'Patients' },
  { dir: '04-opd-encounters', title: 'OPD / Encounters' },
  { dir: '05-pharmacy', title: 'Pharmacy' },
  { dir: '06-ipd-wards', title: 'IPD / Wards' },
  { dir: '07-billing', title: 'Billing & invoicing' },
  { dir: '08-staff', title: 'Staff' },
  { dir: '09-role-access', title: 'Role-based navigation & access' },
  { dir: '10-states', title: 'Empty, error and boundary states' },
];

/**
 * The formulary the rest of the run prescribes from. Keywords are chosen so the
 * AI advisor has something to match against "acute viral fever" and a cough.
 */
const CATALOGUE = [
  { name: 'Paracetamol 500mg', generic: 'Paracetamol', price: '12.50', keywords: 'fever pain analgesic antipyretic', reorder: '40' },
  { name: 'Amoxicillin 500mg', generic: 'Amoxicillin', price: '48.00', keywords: 'antibiotic amoxy infection bacterial', reorder: '30' },
  { name: 'Azithromycin 250mg', generic: 'Azithromycin', price: '96.00', keywords: 'antibiotic macrolide infection', reorder: '20' },
  { name: 'Dextromethorphan Syrup', generic: 'Dextromethorphan', price: '32.00', keywords: 'cough cold suppressant', reorder: '25' },
];

/** Every captured step, in capture order. Written to manifest.json. */
const manifest = [];
const counters = new Map();

let browser;
let context;

function pad(n) {
  return String(n).padStart(2, '0');
}

/**
 * Remove a previous run's output, but only the folders this script owns.
 * Anything else living in docs/ui-snapshots is left alone.
 */
async function cleanOutput() {
  await mkdir(OUT, { recursive: true });
  for (const area of AREAS) {
    await rm(path.join(OUT, area.dir), { recursive: true, force: true });
  }
  await rm(path.join(OUT, 'manifest.json'), { force: true });
  for (const area of AREAS) {
    await mkdir(path.join(OUT, area.dir), { recursive: true });
  }
}

/**
 * Screenshot the current page and record it.
 *
 * Modal shots are viewport-only and detected rather than passed in. The overlay
 * is position:fixed, so a full-page capture leaves the backdrop stopping at the
 * fold with an unstyled strip of page below it — the dialog would appear to sit
 * on a page that has ended.
 */
async function shot(page, areaDir, slug, title, caption, opts = {}) {
  const { route = page.url().replace(BASE, '') || '/', role = '—' } = opts;

  const openDialog = await page.locator('.modal').first().isVisible().catch(() => false);
  const fullPage = opts.fullPage ?? !openDialog;

  await page.evaluate(() => document.fonts.ready);
  // One frame for any state change React queued in the same tick as the click.
  await page.evaluate(
    () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
  );
  if (opts.settle !== 0) await page.waitForTimeout(opts.settle ?? 450);

  const n = (counters.get(areaDir) ?? 0) + 1;
  counters.set(areaDir, n);
  const file = `${pad(n)}-${slug}.png`;
  await page.screenshot({ path: path.join(OUT, areaDir, file), fullPage });

  manifest.push({ area: areaDir, file, title, caption, route, role });
  console.log(`  ✓ ${areaDir}/${file}  ${title}`);
}

/** A fresh page with the clock frozen and animations off. */
async function newPage() {
  const page = await context.newPage();
  // setFixedTime (not install): Date is pinned, timers keep running. install()
  // would pause them too, and the app's toast dismissal depends on them.
  await page.clock.setFixedTime(FROZEN);
  page.on('pageerror', (err) => console.warn(`    ! page error: ${err.message}`));
  return page;
}

async function goto(page, route) {
  await page.goto(`${BASE}${route}`, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
  // Injected per navigation rather than as an init script: at document-start
  // there is no documentElement to append to, and every goto is a real reload.
  await page.addStyleTag({ content: NO_MOTION_CSS });
}

/** Sign in through the form, exactly as a user does. */
async function signIn(page, username) {
  await page.context().clearCookies();
  await goto(page, '/login');
  await page.evaluate(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });
  await goto(page, '/login');
  await page.locator('input#username').waitFor({ state: 'visible', timeout: 15000 });
  await page.fill('#username', username);
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 20000 });
  // The dashboard skeletons resolve into real numbers; wait for one.
  await page.locator('.stat-value').first().waitFor({ timeout: 20000 });
}

const modal = (page) => page.locator('.modal');

/**
 * The form-group carrying an exact label, searched inside a scope.
 *
 * Scoped, because the same label appears in more than one place on several
 * pages — every roster row also renders a role select, and the encounter form
 * repeats field names the pharmacy shows elsewhere.
 */
function groupIn(scope, label) {
  return scope.locator('.form-group', { has: scope.page().locator(`label:text-is("${label}")`) });
}

/** Fill a text-like control, or choose an option, by its label within a scope. */
async function fillIn(scope, label, value) {
  const control = groupIn(scope, label).locator('input, select, textarea').first();
  const tag = await control.evaluate((el) => el.tagName.toLowerCase());
  if (tag !== 'select') {
    await control.fill(value);
    return;
  }
  // Match on value first, then on the visible label case-insensitively, so a
  // call can say 'female' for an option labelled 'Female'.
  const options = await control.locator('option').evaluateAll((els) =>
    els.map((o) => ({ value: o.value, label: o.textContent.trim() }))
  );
  const exact = options.find((o) => o.value === value);
  if (exact) return void (await control.selectOption(exact.value));
  const loose = options.find((o) => o.label.toLowerCase() === value.toLowerCase());
  if (loose) return void (await control.selectOption(loose.value));
  const contains = options.find((o) => o.label.toLowerCase().includes(value.toLowerCase()));
  if (contains) return void (await control.selectOption(contains.value));
  throw new Error(`no option matching "${value}" under "${label}" (saw: ${options.map((o) => o.label).join(', ')})`);
}

/** Fill a checkbox inside a scope, leaving it alone if it is already there. */
async function checkIn(scope, label, checked) {
  const box = groupIn(scope, label).locator('input[type="checkbox"]').first();
  if ((await box.isChecked()) !== checked) await box.click();
}

const fillField = (page, label, value) => fillIn(modal(page), label, value);
const checkField = (page, label, checked) => checkIn(modal(page), label, checked);

// --- flows ----------------------------------------------------------------

async function captureAuthentication() {
  console.log('\n· Authentication & onboarding');
  const page = await newPage();

  await goto(page, '/login');
  await page.locator('input#username').waitFor({ timeout: 15000 });
  await shot(page, '01-authentication', 'login', 'Sign in', 'Empty sign-in form. The card also carries the local-setup hint a developer needs on a fresh database.', {
    route: '/login',
  });

  await page.fill('#username', 'doctor');
  await page.fill('#password', PASSWORD);
  await shot(page, '01-authentication', 'login-filled', 'Sign in — filled', 'A correct username and password, before submission.', {
    route: '/login',
  });

  await page.fill('#username', 'admin');
  await page.fill('#password', 'not-the-password');
  await page.click('button[type="submit"]');
  await page.locator('.toast.critical').first().waitFor({ timeout: 15000 });
  await shot(page, '01-authentication', 'login-rejected', 'Sign in — rejected', 'A failed sign-in raises a critical toast rather than an inline error. Five failures for the same user and socket peer trigger a 15-minute lockout.', {
    route: '/login',
  });

  // The form offers no way back to itself once signed in, so this state is
  // reachable only by navigating to /login deliberately. It is a real screen.
  await page.fill('#username', 'admin');
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 20000 });
  await goto(page, '/login');
  await page.locator('text=You are already signed in').waitFor({ timeout: 10000 });
  await shot(page, '01-authentication', 'login-already-signed-in', 'Already signed in', 'An authenticated visitor who navigates to /login is told so and offered a way forward, instead of being silently redirected.', {
    route: '/login',
    role: 'admin',
  });

  await goto(page, '/onboarding');
  await page.locator('text=Register Your Facility').waitFor({ timeout: 15000 });
  await shot(page, '01-authentication', 'onboarding', 'Register a facility', 'Tenant self-service. The endpoint is gated behind a platform secret or super_admin, so the form ships with the "disabled by default" notice.', {
    route: '/onboarding',
    role: 'admin',
  });

  // The submit path a real user can reach: fill every field, submit, and read
  // why the deployment refuses. The "select at least one module" validation is
  // NOT captured, because no interaction can switch a module off — see the
  // dead-checkbox note in Known gaps.
  const facility = `Aurora Care ${RUN}`;
  await page.getByLabel('Facility Name', { exact: true }).fill(facility);
  await page.getByLabel('URL Slug (lowercase, unique)').fill(`aurora-care-${RUN}`);
  await page.getByLabel('Contact Email').fill('contact@auroracare.test');
  await page.getByLabel('Contact Phone').fill('9822001100');
  await page.getByLabel('Address').fill('18 Residency Road, Bengaluru');
  await page.getByLabel('Username', { exact: true }).fill(`aurora.admin`);
  await page.getByLabel('Full Name', { exact: true }).fill('Aurora Facility Admin');
  await page.getByLabel('Admin Email').fill('admin@auroracare.test');
  await page.getByLabel('Password', { exact: true }).fill('Temp@12345');
  await shot(page, '01-authentication', 'onboarding-filled', 'Register a facility — filled', 'A facility and its first admin account, with the modules that organisation would have.', {
    route: '/onboarding',
    role: 'admin',
  });

  await page.click('button:has-text("Register Facility")');
  await page
    .locator('div[role="alert"]')
    .filter({ hasText: 'Self-serve registration is disabled' })
    .waitFor({ timeout: 20000 });
  await shot(page, '01-authentication', 'onboarding-rejected', 'Register a facility — rejected', 'Self-serve registration is off by default, so the form explains what to do instead of showing a raw "authentication required".', {
    route: '/onboarding',
    role: 'admin',
  });

  await page.close();
}

/**
 * Create the catalogue over the API, bypassing the UI.
 *
 * Not a shortcut: with an empty catalogue the "Add Medicine" modal is
 * unreachable, because Pharmacy.jsx renders it inside the branch taken only when
 * a medicine is already selected. Both call-to-action buttons on the empty
 * screen — "+ Add Medicine" and "Add your first medicine" — set the state and
 * render nothing. Seeding one medicine by API is the only way past that, and
 * after that the UI works normally.
 */
async function seedCatalogueViaApi() {
  const login = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: PASSWORD }),
  });
  const { token } = await login.json();
  if (!token) throw new Error('could not sign in as admin to seed the catalogue');

  for (const med of CATALOGUE) {
    const res = await fetch(`${BASE}/api/v1/pharmacy/medicines`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        name: med.name,
        genericName: med.generic,
        keywords: med.keywords,
        unitPrice: parseFloat(med.price),
        reorderLevel: parseInt(med.reorder, 10),
        active: true,
      }),
    });
    if (res.status === 409) {
      // Already present from an earlier run. Tolerated so the script is
      // re-runnable; a canonical capture still wants a freshly seeded database,
      // otherwise figures accumulate.
      console.log(`  · ${med.name} already in the catalogue, skipping`);
      continue;
    }
    if (!res.ok) throw new Error(`catalogue seed failed for ${med.name}: HTTP ${res.status}`);
  }
  console.log(`  seeded ${CATALOGUE.length} medicines over the API (UI dead end — see index)`);
}

async function captureAdminCatalog() {
  console.log('\n· Admin: catalogue and staff');
  const page = await newPage();
  await signIn(page, 'admin');

  await shot(page, '02-dashboard', 'dashboard-admin', 'Dashboard — admin', 'Six stat cards over quick actions and recent notifications. The numbers come from /api/v1/dashboard, which is filtered by role.', {
    route: '/dashboard',
    role: 'admin',
  });

  // Staff first: the catalogue has to start empty for that screen to mean anything.
  await goto(page, '/staff');
  await page.locator('h1:text-is("Staff")').waitFor({ timeout: 15000 });
  await page.locator('table tbody tr').first().waitFor({ timeout: 15000 });
  await shot(page, '08-staff', 'roster', 'Staff roster', 'Everyone who can sign in to this hospital, each with a per-hospital role, access toggle and password state.', {
    route: '/staff',
    role: 'admin',
  });

  await page.click('button:has-text("+ Add Staff")');
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await shot(page, '08-staff', 'add-staff', 'Add staff', 'The temporary password is a shared secret until its owner changes it, so the form says so directly.', {
    route: '/staff',
    role: 'admin',
  });

  // Client-side rules, shown on the controls they belong to. The email field is
  // left empty rather than filled with nonsense: type="email" would block
  // submission on the browser's own validation and the JS rules would never run.
  await fillField(page, 'Full Name *', 'Vikram Desai');
  await fillField(page, 'Username *', 'ab');
  await fillField(page, 'Role *', 'Doctor');
  await fillField(page, 'Temporary Password *', 'short');
  await modal(page).locator('button[type="submit"]').click();
  await page.locator('.field-error').first().waitFor({ timeout: 5000 });
  await shot(page, '08-staff', 'add-staff-validation', 'Add staff — validation', 'Errors land on the control they describe and are wired with aria-invalid, not only coloured.', {
    route: '/staff',
    role: 'admin',
  });

  await fillField(page, 'Username *', `dr.desai.${RUN}`);
  await fillField(page, 'Temporary Password *', 'Temp@12345');
  await fillField(page, 'Email', `vikram.desai.${RUN}@example.test`);
  await fillField(page, 'Specialization', 'General Medicine');
  await modal(page).locator('button[type="submit"]').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '08-staff', 'roster-with-new-staff', 'Staff roster — new joiner', 'The new account is flagged "must change password" until its owner signs in and sets their own.', {
    route: '/staff',
    role: 'admin',
  });

  // --- pharmacy catalogue ---
  await goto(page, '/pharmacy');
  await page.locator('h1:text-is("Pharmacy Vault")').waitFor({ timeout: 15000 });
  await page.locator('text=No medicines yet').waitFor({ timeout: 15000 });
  await shot(page, '05-pharmacy', 'catalogue-empty', 'Pharmacy — empty catalogue', 'A new tenant has no formulary. Note that both call-to-action buttons here are dead: the Add Medicine modal only renders once a medicine is already selected, so nothing on this screen works.', {
    route: '/pharmacy',
    role: 'admin',
  });

  await seedCatalogueViaApi();

  await goto(page, '/pharmacy');
  await page.locator('button:has-text("Paracetamol 500mg")').first().waitFor({ timeout: 20000 });
  await page.locator('button:has-text("Paracetamol 500mg")').first().click();
  await page.locator('.tab:has-text("Batches")').waitFor({ timeout: 15000 });
  await shot(page, '05-pharmacy', 'medicine-selected', 'Pharmacy — medicine selected', 'Selecting a medicine opens its batches. Nothing is received against it yet. This is also the state that makes the Add Medicine modal reachable at all.', {
    route: '/pharmacy',
    role: 'admin',
  });

  await page.locator('button:has-text("+ Stock In")').click();
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await fillField(page, 'Batch Number *', `BATCH-A-${RUN}`);
  await fillField(page, 'Expiry Date *', '2026-11-02');
  await fillField(page, 'Quantity *', '120');
  await fillField(page, 'Purchase Price', '9.40');
  await fillField(page, 'Supplier', 'MedPlus Distribution');
  await shot(page, '05-pharmacy', 'add-stock', 'Receive stock', 'A stock-in is a batch with an expiry, so FEFO has something to order on.', {
    route: '/pharmacy',
    role: 'admin',
  });
  await modal(page).locator('button[type="submit"]').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });

  // A second batch expiring far out, so FEFO has a choice to make.
  await page.locator('button:has-text("+ Stock In")').click();
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await fillField(page, 'Batch Number *', `BATCH-B-${RUN}`);
  await fillField(page, 'Expiry Date *', '2027-08-31');
  await fillField(page, 'Quantity *', '80');
  await fillField(page, 'Purchase Price', '9.10');
  await fillField(page, 'Supplier', 'Apex Pharma');
  await modal(page).locator('button[type="submit"]').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });

  await page.locator('table tbody tr').first().waitFor({ timeout: 15000 });
  await shot(page, '05-pharmacy', 'batches', 'Pharmacy — batches', 'Expiry inside 30 days is flagged in red. Dispensing always takes the earliest expiry first, so this ordering is the FEFO order.', {
    route: '/pharmacy',
    role: 'admin',
  });

  // The rest of the formulary is seeded over the API. Adding one more through the
  // UI here would put a second copy of the same medicine in the catalogue on a
  // re-run, so the form is demonstrated and dismissed rather than submitted.
  await page.locator('.card:has-text("Medicine Catalog") button:has-text("+ Add Medicine")').click();
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await fillField(page, 'Name *', 'ORS Sachet');
  await fillField(page, 'Generic name', 'Oral Rehydration Salts');
  await fillField(page, 'Unit price *', '18.00');
  await fillField(page, 'Reorder level', '60');
  await fillField(page, 'Keywords', 'dehydration diarrhoea electrolyte');
  await shot(page, '05-pharmacy', 'add-medicine', 'Add a medicine', 'Catalogue master data. Keywords are what the AI advisor later matches a diagnosis against. Note this form only renders once a medicine is already selected.', {
    route: '/pharmacy',
    role: 'admin',
  });
  await modal(page).locator('button:has-text("Cancel")').click();
  await modal(page).waitFor({ state: 'hidden', timeout: 10000 });

  await page.close();
}

async function captureReception() {
  console.log('\n· Reception: patient registration');
  const page = await newPage();
  await signIn(page, 'reception');

  await shot(page, '02-dashboard', 'dashboard-reception', 'Dashboard — receptionist', 'The same dashboard component. What changes by role is the navigation rail and the greeting, not the layout.', {
    route: '/dashboard',
    role: 'receptionist',
  });

  await goto(page, '/patients');
  await page.locator('h1:text-is("Patients")').waitFor({ timeout: 15000 });
  await page.locator('table tbody tr').first().waitFor({ timeout: 15000 });
  await shot(page, '03-patients', 'list', 'Patient list', 'Every registered patient with UHID, DPDP consent state and outstanding balance. Rows open the profile; the list is paginated.', {
    route: '/patients',
    role: 'receptionist',
  });

  await page.click('button:has-text("+ Register Patient")');
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await shot(page, '03-patients', 'register-empty', 'Register a patient', 'Name, age, phone and DPDP consent are the required minimum.', {
    route: '/patients',
    role: 'receptionist',
  });

  await fillField(page, 'Full Name *', 'Priya Raghunathan');
  await fillField(page, 'Age *', '31');
  await fillField(page, 'Gender', 'female');
  await fillField(page, 'Phone *', '9845012345');
  await fillField(page, 'Email', 'priya.r@example.test');
  await fillField(page, 'Blood Group', 'B+');
  await fillField(page, 'Address', '42 MG Road, Pune');
  await checkField(page, 'DPDP Consent Granted *', true);
  await fillField(page, 'Consent Purpose', 'Treatment and billing');
  await shot(page, '03-patients', 'register-filled', 'Register a patient — filled', 'DPDP consent reveals the purpose field, so the patient can see what they agreed to.', {
    route: '/patients',
    role: 'receptionist',
  });

  await modal(page).locator('button[type="submit"]').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '03-patients', 'register-success', 'Patient registered', 'The toast returns the UHID, which is what the patient is called at the counter from then on.', {
    route: '/patients',
    role: 'receptionist',
  });

  await page.close();
}

async function captureDoctor() {
  console.log('\n· Doctor: OPD and IPD');
  const page = await newPage();
  await signIn(page, 'doctor');

  await shot(page, '02-dashboard', 'dashboard-doctor', 'Dashboard — doctor', 'A clinician sees the same six counters. The rail is shorter: no staff page, no billing.', {
    route: '/dashboard',
    role: 'doctor',
  });

  // --- OPD ---
  await goto(page, '/encounters');
  await page.locator('h1:text-is("OPD / Encounters")').waitFor({ timeout: 15000 });
  await page.locator('text=New Encounter').or(page.locator('h3:has-text("Patients")')).first().waitFor({ timeout: 15000 });
  await shot(page, '04-opd-encounters', 'worklist', 'OPD — patient picker', 'Consultation starts by picking the patient. The list is searchable and paginated so a patient registered a moment ago is reachable.', {
    route: '/encounters',
    role: 'doctor',
  });

  // Priya Raghunathan was registered by the previous step. The encounter form is
  // an inline card, not a modal, so it is scoped explicitly.
  await page.locator('button:has-text("Priya Raghunathan")').first().click();
  const encounterForm = page.locator('.card').filter({ hasText: 'New Encounter: Priya Raghunathan' });
  await encounterForm.waitFor({ timeout: 10000 });
  await fillIn(encounterForm, 'BP (mmHg)', '124/78');
  await fillIn(encounterForm, 'Pulse (/min)', '88');
  await fillIn(encounterForm, 'Temp (°F)', '101.4');
  await fillIn(encounterForm, 'SpO2 (%)', '97');
  await fillIn(encounterForm, 'Weight (kg)', '58');
  await fillIn(encounterForm, 'Chief Complaint', 'Fever, body ache and cough for three days');
  await fillIn(encounterForm, 'Diagnosis', 'Acute viral fever');
  await shot(page, '04-opd-encounters', 'new-encounter', 'OPD — new encounter', 'Vitals and the two free-text fields that everything downstream keys off.', {
    route: '/encounters',
    role: 'doctor',
  });

  await page.click('button:has-text("Start Encounter")');
  await page.locator('text=Active Encounter').waitFor({ timeout: 20000 });
  await shot(page, '04-opd-encounters', 'active-encounter', 'OPD — active encounter', 'The encounter is open. Prescribing and the AI advisor both work against this record.', {
    route: '/encounters',
    role: 'doctor',
  });

  await page.click('button:has-text("Suggest")');
  await page.locator('.ai-suggestion-item').first().waitFor({ timeout: 20000 });
  await shot(page, '04-opd-encounters', 'ai-suggestions', 'OPD — AI medicine advisor', 'Keyword matching against the hospital formulary. Every suggestion is one click from becoming a prescription, and shows why it matched.', {
    route: '/encounters',
    role: 'doctor',
  });

  // Prescribe from the advisor, then add a second by hand.
  await page.locator('.ai-suggestion-item button:has-text("+ Prescribe")').first().click();
  await page.locator('text=Current Prescriptions').waitFor({ timeout: 20000 });
  await page.locator('#med-select').selectOption({ index: 2 });
  await page.locator('#dosage-input').fill('500mg');
  await page.locator('#freq-input').fill('thrice daily after food');
  await page.click('button:has-text("+ Add")');
  await page.locator('text=Current Prescriptions').waitFor({ timeout: 20000 });
  await page.waitForTimeout(600);
  await shot(page, '04-opd-encounters', 'prescriptions', 'OPD — prescriptions', 'Prescriptions name the medicine and its generic, not an id. A pharmacist dispenses from this list and cannot read a UUID.', {
    route: '/encounters',
    role: 'doctor',
  });

  // A second, still-open encounter populates the sign-off worklist.
  await page.locator('button:has-text("Suresh Reddy")').first().click();
  const followUpForm = page.locator('.card').filter({ hasText: 'New Encounter: Suresh Reddy' });
  await followUpForm.waitFor({ timeout: 10000 });
  await fillIn(followUpForm, 'Chief Complaint', 'Follow-up for blood pressure');
  await fillIn(followUpForm, 'Diagnosis', 'Hypertension');
  await page.click('button:has-text("Start Encounter")');
  await page.locator('text=Active Encounter').waitFor({ timeout: 20000 });

  await goto(page, '/encounters');
  await page.locator('text=Awaiting sign-off').waitFor({ timeout: 20000 });
  await shot(page, '04-opd-encounters', 'signoff-worklist', 'OPD — awaiting sign-off', 'Encounters left open are held here so nothing is lost when a clinician moves on.', {
    route: '/encounters',
    role: 'doctor',
  });

  await page.click('button:has-text("Resume and sign")');
  await page.locator('text=Active Encounter').waitFor({ timeout: 20000 });
  await page.click('button:has-text("Sign & Close")');
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '04-opd-encounters', 'signed', 'OPD — signed and closed', 'Signing closes the encounter. It stops appearing in the sign-off worklist and shows as signed in the patient profile.', {
    route: '/encounters',
    role: 'doctor',
  });

  // --- IPD ---
  await goto(page, '/admissions');
  await page.locator('h1:text-is("IPD / Wards")').waitFor({ timeout: 15000 });
  await page.locator('text=Room Occupancy Map').waitFor({ timeout: 20000 });
  await shot(page, '06-ipd-wards', 'ward-board', 'IPD — ward board', 'Every bed with its occupancy, type and daily rate. Occupancy counters sit above the map.', {
    route: '/admissions',
    role: 'doctor',
  });

  // Click the bed itself. Rooms are seeded master data with no API to create
  // them, so the capture addresses one by its ward line and clicks the parent
  // card — that is the element carrying the onClick.
  const icuBed = page.locator('div:text-is("Intensive Care • icu")').first();
  await icuBed.waitFor({ timeout: 15000 });
  await icuBed.locator('..').click();
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await fillField(page, 'Select Patient *', 'Suresh Reddy');
  await shot(page, '06-ipd-wards', 'admit', 'IPD — admit a patient', 'An available bed opens the admit dialog. Patients already admitted are not offered.', {
    route: '/admissions',
    role: 'doctor',
  });
  await modal(page).locator('button[type="submit"]').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '06-ipd-wards', 'admitted', 'IPD — bed occupied', 'The bed now carries the patient and the date they came in, with discharge in place.', {
    route: '/admissions',
    role: 'doctor',
  });

  await page.locator('button:has-text("Discharge")').first().click();
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await fillField(page, 'Discharge Diagnosis *', 'Hypertension under control. Continue antihypertensive therapy.');
  await shot(page, '06-ipd-wards', 'discharge', 'IPD — discharge', 'Room charges are calculated and posted to the billing ledger on discharge.', {
    route: '/admissions',
    role: 'doctor',
  });
  await modal(page).locator('button:has-text("Process Discharge")').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '06-ipd-wards', 'discharged', 'IPD — bed released', 'Discharge frees the bed and posts the stay to billing.', {
    route: '/admissions',
    role: 'doctor',
  });

  await page.close();
}

async function capturePharmacist() {
  console.log('\n· Pharmacist: dispensing');
  const page = await newPage();
  await signIn(page, 'pharmacy');

  await shot(page, '02-dashboard', 'dashboard-pharmacist', 'Dashboard — pharmacist', 'A pharmacist reaches pharmacy and the ledger, and nothing clinical.', {
    route: '/dashboard',
    role: 'pharmacist',
  });

  await goto(page, '/pharmacy');
  await page.locator('h1:text-is("Pharmacy Vault")').waitFor({ timeout: 20000 });
  await page.locator('text=Pending Rx').waitFor({ timeout: 20000 });
  await shot(page, '05-pharmacy', 'pending-rx', 'Pharmacy — pending prescriptions', 'Prescriptions waiting to be handed over, named by medicine rather than id.', {
    route: '/pharmacy',
    role: 'pharmacist',
  });

  await page.locator('button:has-text("Dispense")').first().click();
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await fillField(page, 'Quantity to Dispense', '10');
  await shot(page, '05-pharmacy', 'dispense', 'Pharmacy — dispense', 'Dispensing states the two side effects up front: FEFO deduction from the earliest batch, and a charge posted to the patient ledger.', {
    route: '/pharmacy',
    role: 'pharmacist',
  });
  await modal(page).locator('button:has-text("Dispense FEFO")').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '05-pharmacy', 'dispensed', 'Pharmacy — dispensed', 'The prescription leaves the pending list and the stock movement is written to the ledger.', {
    route: '/pharmacy',
    role: 'pharmacist',
  });

  // The ledger is the audit trail: in, out, and what it referenced.
  await page.locator('button:has-text("Paracetamol 500mg")').first().click();
  await page.locator('.tab:has-text("Ledger")').click();
  await page.locator('table tbody tr').first().waitFor({ timeout: 20000 });
  await shot(page, '05-pharmacy', 'ledger', 'Pharmacy — stock ledger', 'Every movement in and out, with the reference it belongs to. Outflows are the ones a discrepancy investigation starts from.', {
    route: '/pharmacy',
    role: 'pharmacist',
  });

  await page.close();
}

async function captureBilling() {
  console.log('\n· Billing: invoicing and payment');
  const page = await newPage();
  await signIn(page, 'billing');

  await shot(page, '02-dashboard', 'dashboard-billing', 'Dashboard — billing', 'Billing reaches the ledger and the patient list, and nothing clinical.', {
    route: '/dashboard',
    role: 'billing',
  });

  await goto(page, '/billing');
  await page.locator('h1:text-is("Billing & Invoicing")').waitFor({ timeout: 20000 });
  await shot(page, '07-billing', 'no-patient-selected', 'Billing — no patient selected', 'The ledger is per patient, so nothing is shown until one is chosen.', {
    route: '/billing',
    role: 'billing',
  });

  // Suresh Reddy has a completed stay charged to them. Wait for the ledger call
  // itself: the heading renders "(0)" on the first paint and the fetch resolves
  // a frame later, so waiting on the heading alone captures the empty state.
  const unbilledLoaded = page.waitForResponse(
    (r) => r.url().includes('/api/v1/billing/patients/') && r.url().includes('/unbilled') && r.status() === 200,
    { timeout: 20000 }
  );
  await page.locator('button:has-text("Suresh Reddy")').first().click();
  await unbilledLoaded;
  await page.locator('h4:has-text("Unbilled Charges")').waitFor({ timeout: 20000 });
  await page.locator('table tbody tr').first().waitFor({ timeout: 20000 });
  await shot(page, '07-billing', 'unbilled-charges', 'Billing — unbilled charges', 'Pharmacy dispenses and room charges post here automatically. Nothing is typed twice.', {
    route: '/billing',
    role: 'billing',
  });

  await page.click('button:has-text("Bill All")');
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  // "Select All" rather than per-row checkboxes: the modal overlay covers the
  // charge rows, so the checkboxes are not reachable at all. See the index.
  await page.click('button:has-text("Select All")');
  await shot(page, '07-billing', 'generate-invoice', 'Billing — generate invoice', 'Selection happens through "Select All" only — the charge rows are behind the modal overlay, so a patient with mixed charge types can only be invoiced in full.', {
    route: '/billing',
    role: 'billing',
  });
  await modal(page).locator('button:has-text("Generate Invoice")').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '07-billing', 'invoice-created', 'Billing — invoice issued', 'A GST invoice is raised and the selected charges leave the unbilled list.', {
    route: '/billing',
    role: 'billing',
  });

  await page.locator('button:has-text("Pay")').first().click();
  await modal(page).waitFor({ state: 'visible', timeout: 10000 });
  await fillField(page, 'Payment Method', 'UPI');
  await fillField(page, 'Transaction Reference', 'UPI-8845120399');
  await shot(page, '07-billing', 'record-payment', 'Billing — record payment', 'The dialog opens pre-filled with the outstanding balance; partial payments are the normal case.', {
    route: '/billing',
    role: 'billing',
  });
  await modal(page).locator('button:has-text("Record Payment")').click();
  await page.locator('.toast.success').first().waitFor({ timeout: 20000 });
  await shot(page, '07-billing', 'invoice-paid', 'Billing — settled', 'Settled invoices show a zero balance and no payment action.', {
    route: '/billing',
    role: 'billing',
  });

  await page.close();
}

async function captureProfilesAndStates() {
  console.log('\n· Patient profile, role access and boundary states');
  const page = await newPage();
  await signIn(page, 'admin');

  await goto(page, '/patients');
  await page.locator('h1:text-is("Patients")').waitFor({ timeout: 20000 });
  await page.locator('table tbody tr').first().waitFor({ timeout: 20000 });
  await shot(page, '03-patients', 'list-populated', 'Patient list — after the journeys', 'Outstanding balances are populated by the pharmacy dispense and the IPD stay above.', {
    route: '/patients',
    role: 'admin',
  });

  // Priya Raghunathan: registered, examined, prescribed.
  await page.locator('tr:has-text("Priya Raghunathan")').click();
  await page.locator('text=DPDP: Consented').waitFor({ timeout: 20000 });
  await page.locator('table tbody tr').first().waitFor({ timeout: 20000 });
  await shot(page, '03-patients', 'profile-encounters', 'Patient profile — encounters', 'One view of the patient: consent state, outstanding balance, and their visit history.', {
    route: '/patients/:id',
    role: 'admin',
  });

  await page.locator('.tab:has-text("Invoices")').click();
  await page.waitForTimeout(500);
  await shot(page, '03-patients', 'profile-invoices', 'Patient profile — invoices', 'The same invoice ledger billing works from, read-only here.', {
    route: '/patients/:id',
    role: 'admin',
  });

  await page.locator('.tab:has-text("History")').click();
  await shot(page, '03-patients', 'profile-history', 'Patient profile — history', 'A tab that says it is not built yet, rather than rendering an empty table that reads as "no history".', {
    route: '/patients/:id',
    role: 'admin',
  });

  await page.locator('button:has-text("← Back")').click();
  await page.locator('h1:text-is("Patients")').waitFor({ timeout: 10000 });

  // Search with no match: an empty result, which is a different thing from a failure.
  await page.fill('input[placeholder="Search patients by name..."]', 'zzzznomatch');
  await page.locator('.list-empty').waitFor({ timeout: 20000 });
  await shot(page, '10-states', 'search-no-results', 'Patients — no search results', 'A search that matched nothing says so plainly.', {
    route: '/patients',
    role: 'admin',
  });

  await page.click('button:has-text("Clear")');
  await page.locator('table tbody tr').first().waitFor({ timeout: 20000 });

  // A failed request must not read as an empty result.
  await page.route('**/api/v1/patients?**', (route) =>
    route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'patient index is warming up' }),
    })
  );
  await goto(page, '/patients');
  await page.locator('.list-error').waitFor({ timeout: 20000 });
  await shot(page, '10-states', 'patients-load-error', 'Patients — server failure', 'A server fault is reported as a server fault. Reporting it as "no patients found" would tell a clinician the record does not exist.', {
    route: '/patients',
    role: 'admin',
  });
  await page.unroute('**/api/v1/patients?**');

  // A route outside the role's allow-list redirects rather than rendering.
  await page.context().clearCookies();
  await page.evaluate(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });
  await signIn(page, 'doctor');
  await goto(page, '/staff');
  await page.waitForURL('**/dashboard**', { timeout: 20000 });
  await page.locator('.stat-value').first().waitFor({ timeout: 20000 });
  await shot(page, '09-role-access', 'doctor-blocked-from-staff', 'Access control — doctor opening /staff', 'A doctor who types the staff URL is sent to their dashboard. The page never renders, so nothing leaks before the redirect.', {
    route: '/staff',
    role: 'doctor',
  });

  // An unknown path falls back to the root, which lands on the dashboard.
  await goto(page, '/does-not-exist');
  await page.waitForURL('**/dashboard**', { timeout: 20000 });
  await shot(page, '10-states', 'unknown-route', 'Unknown route', 'An unrecognised path is caught by the router and sent home rather than rendering a blank page.', {
    route: '/*',
    role: 'doctor',
  });

  await page.close();
}

/** The navigation rail per role, which is where role-based access is visible. */
async function captureRoleNav() {
  console.log('\n· Navigation rail per role');
  const ROLES = [
    { user: 'admin', slug: 'admin', caption: 'Admin — every module, including staff.' },
    { user: 'doctor', slug: 'doctor', caption: 'Doctor — clinical modules only. No staff, no billing.' },
    { user: 'nurse', slug: 'nurse', caption: 'Nurse — patients, encounters and admissions.' },
    { user: 'reception', slug: 'receptionist', caption: 'Receptionist — patients and appointments.' },
    { user: 'pharmacy', slug: 'pharmacist', caption: 'Pharmacist — pharmacy and the ledger.' },
    { user: 'billing', slug: 'billing', caption: 'Billing — billing and patients.' },
  ];

  for (const role of ROLES) {
    const page = await newPage();
    await signIn(page, role.user);
    await shot(page, '09-role-access', `nav-${role.slug}`, `Navigation rail — ${role.slug}`, role.caption, {
      route: '/dashboard',
      role: role.user,
    });
    await page.close();
  }
}

// --- run ------------------------------------------------------------------

const NO_MOTION_CSS = `
  *, *::before, *::after {
    animation-duration: 0s !important;
    animation-delay: 0s !important;
    transition-duration: 0s !important;
    transition-delay: 0s !important;
  }
  *, *::before, *::after { caret-color: transparent !important; }
`;

async function main() {
  console.log(`Capturing UI snapshots from ${BASE} → ${path.relative(REPO_ROOT, OUT)}`);

  await cleanOutput();
  browser = await chromium.launch();
  context = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 1,
    reducedMotion: 'reduce',
    // The app ships one light palette defined in :root. Forcing dark would
    // leave native form controls (date pickers, checkboxes) mismatched.
    colorScheme: 'light',
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata',
  });

  await captureAuthentication();
  await captureAdminCatalog();
  await captureReception();
  await captureDoctor();
  await capturePharmacist();
  await captureBilling();
  await captureProfilesAndStates();
  await captureRoleNav();

  await context.close();
  await browser.close();

  const areas = await readdir(OUT, { withFileTypes: true });
  const areaCount = areas.filter((d) => d.isDirectory()).length;
  const doc = {
    capturedFrom: BASE,
    viewport: `${VIEWPORT.width}x${VIEWPORT.height}`,
    clockFrozenAt: FROZEN.toISOString(),
    areas: AREAS.map(({ dir, title }) => ({ dir, title })),
    shots: manifest,
  };
  await writeFile(path.join(OUT, 'manifest.json'), `${JSON.stringify(doc, null, 2)}\n`, 'utf8');

  console.log(`\nDone. ${manifest.length} snapshots across ${areaCount} areas.`);
  console.log(`Next: node scripts/generate-snapshot-index.mjs`);
}

try {
  await main();
} catch (err) {
  console.error('\nCapture failed:', err.message);
  if (browser) await browser.close().catch(() => {});
  process.exit(1);
}