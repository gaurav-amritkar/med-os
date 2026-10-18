/**
 * Builds docs/ui-snapshots/INDEX.md from the manifest the capture script wrote.
 *
 *   node scripts/capture-ui-snapshots.mjs   # writes the PNGs and manifest.json
 *   node scripts/generate-snapshot-index.mjs # writes this index
 *
 * The manifest is the single source of truth: area order, titles and captions
 * all come from it, so a shot cannot drift from the description beside it.
 */

import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '..', '..');
const OUT = path.join(REPO_ROOT, 'docs', 'ui-snapshots');

/** Flow groups, in the order they appear in the "Tour" section. */
const TOUR = [
  {
    title: 'Sign in',
    note: 'A new user starts here. There is no sign-up: a hospital is registered by a platform administrator and its first admin account is created with it.',
    shots: ['01-authentication/01-login', '01-authentication/02-login-filled', '01-authentication/03-login-rejected'],
  },
  {
    title: 'Register a facility',
    note: 'Platform-side tenant provisioning. Self-serve is off by default, so the reachable outcome on a default deployment is the explanation, not a registered organisation.',
    shots: [
      '01-authentication/05-onboarding',
      '01-authentication/06-onboarding-filled',
      '01-authentication/07-onboarding-rejected',
    ],
  },
  {
    title: 'Patient registration',
    note: 'Reception registers the patient and takes DPDP consent. The UHID returned here is the identifier used everywhere after this.',
    shots: ['03-patients/02-register-empty', '03-patients/03-register-filled', '03-patients/04-register-success'],
  },
  {
    title: 'Build the formulary and receive stock',
    note: 'Nothing can be prescribed or dispensed until the catalogue exists and a batch has an expiry, because FEFO orders on expiry.',
    shots: [
      '05-pharmacy/01-catalogue-empty',
      '05-pharmacy/02-medicine-selected',
      '05-pharmacy/03-add-stock',
      '05-pharmacy/04-batches',
      '05-pharmacy/05-add-medicine',
    ],
  },
  {
    title: 'OPD consultation',
    note: 'A doctor opens an encounter, records vitals, and prescribes. The AI advisor only suggests from the hospital formulary, so it returns nothing until the catalogue is populated.',
    shots: [
      '04-opd-encounters/01-worklist',
      '04-opd-encounters/02-new-encounter',
      '04-opd-encounters/03-active-encounter',
      '04-opd-encounters/04-ai-suggestions',
      '04-opd-encounters/05-prescriptions',
    ],
  },
  {
    title: 'Dispense',
    note: 'The pharmacist hands the medicine over. Dispensing deducts from the earliest-expiring batch and posts the charge to the patient ledger in the same step.',
    shots: [
      '05-pharmacy/06-pending-rx',
      '05-pharmacy/07-dispense',
      '05-pharmacy/08-dispensed',
      '05-pharmacy/09-ledger',
    ],
  },
  {
    title: 'Inpatient stay',
    note: 'An available bed becomes an admission, and discharge posts the room charge to billing. There is no room creation in the UI; rooms are master data.',
    shots: [
      '06-ipd-wards/01-ward-board',
      '06-ipd-wards/02-admit',
      '06-ipd-wards/03-admitted',
      '06-ipd-wards/04-discharge',
      '06-ipd-wards/05-discharged',
    ],
  },
  {
    title: 'Invoice and payment',
    note: 'Billing never types a charge: pharmacy dispenses and room charges post themselves. Billing selects, invoices and settles.',
    shots: [
      '07-billing/01-no-patient-selected',
      '07-billing/02-unbilled-charges',
      '07-billing/03-generate-invoice',
      '07-billing/04-invoice-created',
      '07-billing/05-record-payment',
      '07-billing/06-invoice-paid',
    ],
  },
  {
    title: 'Onboarding staff',
    note: 'A new hospital has exactly one account. The admin adds everyone else and hands out a temporary password.',
    shots: ['08-staff/01-roster', '08-staff/02-add-staff', '08-staff/03-add-staff-validation', '08-staff/04-roster-with-new-staff'],
  },
];

function image(shot) {
  return `![${shot.title}](${shot.area}/${shot.file})`;
}

function shotTable(shots) {
  const rows = shots.map((s) => `| ![${s.title}](${s.area}/${s.file}) | **${s.title}** | ${s.caption} | \`${s.route}\` | ${s.role} |`);
  return ['| | Snapshot | What it shows | Route | Role |', '|---|---|---|---|---|', ...rows].join('\n');
}

async function main() {
  const manifest = JSON.parse(await readFile(path.join(OUT, 'manifest.json'), 'utf8'));
  const byId = new Map(manifest.shots.map((s) => [`${s.area}/${s.file.replace(/\.png$/, '')}`, s]));
  const missing = [];

  const lines = [
    '# MedOS UI snapshots',
    '',
    'Every screen in the application, captured by walking the flow that produces',
    'it rather than by loading each route in isolation. That matters here: the',
    'pharmacy, billing, IPD and OPD screens are empty on a fresh database, so a',
    'route-by-route capture would document empty tables and miss how the data',
    'gets there.',
    '',
    `- **Captured from** \`${manifest.capturedFrom}\` (local dev stack)`,
    `- **Viewport** ${manifest.viewport}, light theme, \`en-IN\`, Asia/Kolkata`,
    `- **Clock** frozen at \`${manifest.clockFrozenAt}\` so timestamps are stable between runs`,
    `- **Snapshots** ${manifest.shots.length}, across ${manifest.areas.length} areas`,
    '',
    '## Regenerating',
    '',
    'Against a **freshly seeded** database, so the figures in the populated',
    'screens are this run\'s and not an accumulation of earlier ones:',
    '',
    '```bash',
    'docker compose down -v           # only to discard a previous capture\'s rows',
    'docker compose up -d --build && ./tools/seed-dev.sh',
    'cd frontend',
    'CAPTURE_RUN_STAMP=01 npm run capture:ui        # writes the PNGs and manifest.json',
    'npm run capture:ui:index                       # rebuilds this file from the manifest',
    '```',
    '',
    'The capture writes real rows to the database it runs against: it creates the',
    'catalogue, encounters, prescriptions, an admission and an invoice. Point it at',
    'a throwaway dev database, never production.',
    '',
    '---',
    '',
    '## Flow tour',
    '',
    'The nine journeys a hospital actually walks, in the order they happen to a',
    'single patient.',
    '',
  ];

  for (const group of TOUR) {
    const shots = group.shots.map((id) => byId.get(id));
    group.shots.forEach((id, i) => {
      if (!shots[i]) missing.push(id);
    });
    if (shots.some((s) => !s)) continue;
    lines.push(`### ${group.title}`, '', group.note, '');
    for (const s of shots) {
      lines.push(image(s), '', `**${s.title}** — ${s.caption} _(\`${s.route}\`, ${s.role})_`, '');
    }
  }

  lines.push('---', '', '## Every snapshot', '');

  for (const area of manifest.areas) {
    const shots = manifest.shots.filter((s) => s.area === area.dir);
    if (!shots.length) continue;
    lines.push(
      `### ${area.title}`,
      '',
      `\`${area.dir}/\` · ${shots.length} snapshot${shots.length === 1 ? '' : 's'}`,
      '',
      shotTable(shots),
      ''
    );
  }

  lines.push(
    '---',
    '',
    '## What is deliberately not here',
    '',
    '- **Mobile and tablet.** Desktop 1440x900 only. The CSS has breakpoints at',
    '  1100px, 900px and 560px, where the navigation rail becomes a drawer — a',
    '  separate capture pass.',
    '- **The dev-only icon gallery** (`/icons`). Stripped from production builds',
    '  by an `import.meta.env.DEV` guard, so it is not part of the product surface.',
    '- **A successful facility registration.** `/onboarding` is gated behind a',
    '  platform secret or a `super_admin` token, and self-serve registration is',
    '  off by default. The disabled state is captured instead, which is what a',
    '  tenant admin actually sees.',
    '- **Lockout.** Reaching the five-failed-attempt lockout would leave `admin`',
    '  locked out for 15 minutes and break every later capture in the run.',
    '',
    '## Defects found while capturing',
    '',
    'These were found by driving the screens, not by reading the code. They are',
    'recorded here rather than fixed, because this pass was for documentation.',
    '',
    '- **The "Enabled Modules" checkboxes on `/onboarding` cannot be toggled at',
    '  all.** Each one is a real `<input type="checkbox">` nested inside a',
    '  `<label htmlFor={id}>` that points at that same input. Every activation —',
    '  clicking the box, clicking its label, pressing Space — bubbles to the label',
    '  and is dispatched to the input a second time, so the two change events',
    '  cancel out. The consequence is not cosmetic: a tenant can never submit',
    '  fewer than all four modules, and the "Select at least one module."',
    '  validation below the list is unreachable by any user. Dropping `htmlFor`',
    '  (the pattern the patient registration modal uses, which works) or removing',
    '  the nesting fixes it.',
    '- **Individual charge selection on `/billing` is impossible.** "Bill All" opens',
    '  the "Generate Invoice" dialog, which says "Select charges to bill" — but',
    '  the charge rows and their checkboxes sit *behind* the modal overlay, which',
    '  covers the viewport. Playwright reports the overlay as intercepting pointer',
    '  events on the checkbox, so a click never reaches it. The only working',
    '  control is the "Select All" button, which means a patient with a mix of',
    '  charge types can only ever be invoiced for all of them. There is a second',
    '  latent bug on those same rows: the checkbox carries its own `onChange`',
    '  *and* the `<tr>` carries an `onClick` that toggles the same charge, so even',
    '  if the overlay were lifted the click would toggle twice and cancel.',
    '- **The dashboard is not role-specific.** `Dashboard.jsx` renders the same six',
    '  stat cards for every role. Role filtering happens in the',
    '  `/api/v1/dashboard` response, so the numbers differ but the screen does not.',
    '- **The encounter tabs do nothing.** The Clinical / AI Assistant /',
    '  Prescriptions tabs in an active encounter all render as `active` with no',
    '  click handler, so they are one panel, not three.',
    '- **The history tab is a placeholder** — "coming soon" rather than data.',
    '- **The dispense dialog reverts to a UUID.** The pending-prescription card',
    '  names the medicine and its generic, and the prescription table does too —',
    '  then "Dispense Prescription" opens with the raw prescription id in a',
    '  disabled field, which is the one identifier on that screen a pharmacist',
    '  cannot use to confirm they have the right one.',
    '',
    '## Worth knowing, not a defect',
    '',
    '- **"Outstanding" is 0 until a charge is invoiced.** `PatientBalanceService`',
    '  sums only charges in status `billed` or `paid`, so a patient with a',
    '  dispensed medicine or a completed stay still reads ₹0 until billing raises',
    '  the invoice. It is also recalculated asynchronously after commit, so it',
    '  lags by a moment — visible in the invoice-issued snapshot, where the',
    '  invoice shows a ₹12,000 balance while the header beside it still says ₹0.',
    ''
  );

  if (missing.length) {
    console.warn(`\nWarning: ${missing.length} tour entries had no snapshot: ${missing.join(', ')}`);
  }

  await writeFile(path.join(OUT, 'INDEX.md'), `${lines.join('\n')}\n`, 'utf8');
  console.log(`Wrote ${path.relative(REPO_ROOT, path.join(OUT, 'INDEX.md'))} — ${manifest.shots.length} snapshots indexed.`);
  if (missing.length) process.exitCode = 1;
}

await main();