# MedOS UI snapshots

Every screen in the application, captured by walking the flow that produces
it rather than by loading each route in isolation. That matters here: the
pharmacy, billing, IPD and OPD screens are empty on a fresh database, so a
route-by-route capture would document empty tables and miss how the data
gets there.

- **Captured from** `http://localhost:8080` (local dev stack)
- **Viewport** 1440x900, light theme, `en-IN`, Asia/Kolkata
- **Clock** frozen at `2026-10-18T04:00:00.000Z` so timestamps are stable between runs
- **Snapshots** 61, across 10 areas

## Regenerating

Against a **freshly seeded** database, so the figures in the populated
screens are this run's and not an accumulation of earlier ones:

```bash
docker compose down -v           # only to discard a previous capture's rows
docker compose up -d --build && ./tools/seed-dev.sh
cd frontend
CAPTURE_RUN_STAMP=01 npm run capture:ui        # writes the PNGs and manifest.json
npm run capture:ui:index                       # rebuilds this file from the manifest
```

The capture writes real rows to the database it runs against: it creates the
catalogue, encounters, prescriptions, an admission and an invoice. Point it at
a throwaway dev database, never production.

---

## Flow tour

The nine journeys a hospital actually walks, in the order they happen to a
single patient.

### Sign in

A new user starts here. There is no sign-up: a hospital is registered by a platform administrator and its first admin account is created with it.

![Sign in](01-authentication/01-login.png)

**Sign in** — Empty sign-in form. The card also carries the local-setup hint a developer needs on a fresh database. _(`/login`, —)_

![Sign in — filled](01-authentication/02-login-filled.png)

**Sign in — filled** — A correct username and password, before submission. _(`/login`, —)_

![Sign in — rejected](01-authentication/03-login-rejected.png)

**Sign in — rejected** — A failed sign-in raises a critical toast rather than an inline error. Five failures for the same user and socket peer trigger a 15-minute lockout. _(`/login`, —)_

### Register a facility

Platform-side tenant provisioning. Self-serve is off by default, so the reachable outcome on a default deployment is the explanation, not a registered organisation.

![Register a facility](01-authentication/05-onboarding.png)

**Register a facility** — Tenant self-service. The endpoint is gated behind a platform secret or super_admin, so the form ships with the "disabled by default" notice. _(`/onboarding`, admin)_

![Register a facility — filled](01-authentication/06-onboarding-filled.png)

**Register a facility — filled** — A facility and its first admin account, with the modules that organisation would have. _(`/onboarding`, admin)_

![Register a facility — rejected](01-authentication/07-onboarding-rejected.png)

**Register a facility — rejected** — Self-serve registration is off by default, so the form explains what to do instead of showing a raw "authentication required". _(`/onboarding`, admin)_

### Patient registration

Reception registers the patient and takes DPDP consent. The UHID returned here is the identifier used everywhere after this.

![Register a patient](03-patients/02-register-empty.png)

**Register a patient** — Name, age, phone and DPDP consent are the required minimum. _(`/patients`, receptionist)_

![Register a patient — filled](03-patients/03-register-filled.png)

**Register a patient — filled** — DPDP consent reveals the purpose field, so the patient can see what they agreed to. _(`/patients`, receptionist)_

![Patient registered](03-patients/04-register-success.png)

**Patient registered** — The toast returns the UHID, which is what the patient is called at the counter from then on. _(`/patients`, receptionist)_

### Build the formulary and receive stock

Nothing can be prescribed or dispensed until the catalogue exists and a batch has an expiry, because FEFO orders on expiry.

![Pharmacy — empty catalogue](05-pharmacy/01-catalogue-empty.png)

**Pharmacy — empty catalogue** — A new tenant has no formulary. Note that both call-to-action buttons here are dead: the Add Medicine modal only renders once a medicine is already selected, so nothing on this screen works. _(`/pharmacy`, admin)_

![Pharmacy — medicine selected](05-pharmacy/02-medicine-selected.png)

**Pharmacy — medicine selected** — Selecting a medicine opens its batches. Nothing is received against it yet. This is also the state that makes the Add Medicine modal reachable at all. _(`/pharmacy`, admin)_

![Receive stock](05-pharmacy/03-add-stock.png)

**Receive stock** — A stock-in is a batch with an expiry, so FEFO has something to order on. _(`/pharmacy`, admin)_

![Pharmacy — batches](05-pharmacy/04-batches.png)

**Pharmacy — batches** — Expiry inside 30 days is flagged in red. Dispensing always takes the earliest expiry first, so this ordering is the FEFO order. _(`/pharmacy`, admin)_

![Add a medicine](05-pharmacy/05-add-medicine.png)

**Add a medicine** — Catalogue master data. Keywords are what the AI advisor later matches a diagnosis against. Note this form only renders once a medicine is already selected. _(`/pharmacy`, admin)_

### OPD consultation

A doctor opens an encounter, records vitals, and prescribes. The AI advisor only suggests from the hospital formulary, so it returns nothing until the catalogue is populated.

![OPD — patient picker](04-opd-encounters/01-worklist.png)

**OPD — patient picker** — Consultation starts by picking the patient. The list is searchable and paginated so a patient registered a moment ago is reachable. _(`/encounters`, doctor)_

![OPD — new encounter](04-opd-encounters/02-new-encounter.png)

**OPD — new encounter** — Vitals and the two free-text fields that everything downstream keys off. _(`/encounters`, doctor)_

![OPD — active encounter](04-opd-encounters/03-active-encounter.png)

**OPD — active encounter** — The encounter is open. Prescribing and the AI advisor both work against this record. _(`/encounters`, doctor)_

![OPD — AI medicine advisor](04-opd-encounters/04-ai-suggestions.png)

**OPD — AI medicine advisor** — Keyword matching against the hospital formulary. Every suggestion is one click from becoming a prescription, and shows why it matched. _(`/encounters`, doctor)_

![OPD — prescriptions](04-opd-encounters/05-prescriptions.png)

**OPD — prescriptions** — Prescriptions name the medicine and its generic, not an id. A pharmacist dispenses from this list and cannot read a UUID. _(`/encounters`, doctor)_

### Dispense

The pharmacist hands the medicine over. Dispensing deducts from the earliest-expiring batch and posts the charge to the patient ledger in the same step.

![Pharmacy — pending prescriptions](05-pharmacy/06-pending-rx.png)

**Pharmacy — pending prescriptions** — Prescriptions waiting to be handed over, named by medicine rather than id. _(`/pharmacy`, pharmacist)_

![Pharmacy — dispense](05-pharmacy/07-dispense.png)

**Pharmacy — dispense** — Dispensing states the two side effects up front: FEFO deduction from the earliest batch, and a charge posted to the patient ledger. _(`/pharmacy`, pharmacist)_

![Pharmacy — dispensed](05-pharmacy/08-dispensed.png)

**Pharmacy — dispensed** — The prescription leaves the pending list and the stock movement is written to the ledger. _(`/pharmacy`, pharmacist)_

![Pharmacy — stock ledger](05-pharmacy/09-ledger.png)

**Pharmacy — stock ledger** — Every movement in and out, with the reference it belongs to. Outflows are the ones a discrepancy investigation starts from. _(`/pharmacy`, pharmacist)_

### Inpatient stay

An available bed becomes an admission, and discharge posts the room charge to billing. There is no room creation in the UI; rooms are master data.

![IPD — ward board](06-ipd-wards/01-ward-board.png)

**IPD — ward board** — Every bed with its occupancy, type and daily rate. Occupancy counters sit above the map. _(`/admissions`, doctor)_

![IPD — admit a patient](06-ipd-wards/02-admit.png)

**IPD — admit a patient** — An available bed opens the admit dialog. Patients already admitted are not offered. _(`/admissions`, doctor)_

![IPD — bed occupied](06-ipd-wards/03-admitted.png)

**IPD — bed occupied** — The bed now carries the patient and the date they came in, with discharge in place. _(`/admissions`, doctor)_

![IPD — discharge](06-ipd-wards/04-discharge.png)

**IPD — discharge** — Room charges are calculated and posted to the billing ledger on discharge. _(`/admissions`, doctor)_

![IPD — bed released](06-ipd-wards/05-discharged.png)

**IPD — bed released** — Discharge frees the bed and posts the stay to billing. _(`/admissions`, doctor)_

### Invoice and payment

Billing never types a charge: pharmacy dispenses and room charges post themselves. Billing selects, invoices and settles.

![Billing — no patient selected](07-billing/01-no-patient-selected.png)

**Billing — no patient selected** — The ledger is per patient, so nothing is shown until one is chosen. _(`/billing`, billing)_

![Billing — unbilled charges](07-billing/02-unbilled-charges.png)

**Billing — unbilled charges** — Pharmacy dispenses and room charges post here automatically. Nothing is typed twice. _(`/billing`, billing)_

![Billing — generate invoice](07-billing/03-generate-invoice.png)

**Billing — generate invoice** — Selection happens through "Select All" only — the charge rows are behind the modal overlay, so a patient with mixed charge types can only be invoiced in full. _(`/billing`, billing)_

![Billing — invoice issued](07-billing/04-invoice-created.png)

**Billing — invoice issued** — A GST invoice is raised and the selected charges leave the unbilled list. _(`/billing`, billing)_

![Billing — record payment](07-billing/05-record-payment.png)

**Billing — record payment** — The dialog opens pre-filled with the outstanding balance; partial payments are the normal case. _(`/billing`, billing)_

![Billing — settled](07-billing/06-invoice-paid.png)

**Billing — settled** — Settled invoices show a zero balance and no payment action. _(`/billing`, billing)_

### Onboarding staff

A new hospital has exactly one account. The admin adds everyone else and hands out a temporary password.

![Staff roster](08-staff/01-roster.png)

**Staff roster** — Everyone who can sign in to this hospital, each with a per-hospital role, access toggle and password state. _(`/staff`, admin)_

![Add staff](08-staff/02-add-staff.png)

**Add staff** — The temporary password is a shared secret until its owner changes it, so the form says so directly. _(`/staff`, admin)_

![Add staff — validation](08-staff/03-add-staff-validation.png)

**Add staff — validation** — Errors land on the control they describe and are wired with aria-invalid, not only coloured. _(`/staff`, admin)_

![Staff roster — new joiner](08-staff/04-roster-with-new-staff.png)

**Staff roster — new joiner** — The new account is flagged "must change password" until its owner signs in and sets their own. _(`/staff`, admin)_

---

## Every snapshot

### Authentication & onboarding

`01-authentication/` · 7 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Sign in](01-authentication/01-login.png) | **Sign in** | Empty sign-in form. The card also carries the local-setup hint a developer needs on a fresh database. | `/login` | — |
| ![Sign in — filled](01-authentication/02-login-filled.png) | **Sign in — filled** | A correct username and password, before submission. | `/login` | — |
| ![Sign in — rejected](01-authentication/03-login-rejected.png) | **Sign in — rejected** | A failed sign-in raises a critical toast rather than an inline error. Five failures for the same user and socket peer trigger a 15-minute lockout. | `/login` | — |
| ![Already signed in](01-authentication/04-login-already-signed-in.png) | **Already signed in** | An authenticated visitor who navigates to /login is told so and offered a way forward, instead of being silently redirected. | `/login` | admin |
| ![Register a facility](01-authentication/05-onboarding.png) | **Register a facility** | Tenant self-service. The endpoint is gated behind a platform secret or super_admin, so the form ships with the "disabled by default" notice. | `/onboarding` | admin |
| ![Register a facility — filled](01-authentication/06-onboarding-filled.png) | **Register a facility — filled** | A facility and its first admin account, with the modules that organisation would have. | `/onboarding` | admin |
| ![Register a facility — rejected](01-authentication/07-onboarding-rejected.png) | **Register a facility — rejected** | Self-serve registration is off by default, so the form explains what to do instead of showing a raw "authentication required". | `/onboarding` | admin |

### Dashboard

`02-dashboard/` · 5 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Dashboard — admin](02-dashboard/01-dashboard-admin.png) | **Dashboard — admin** | Six stat cards over quick actions and recent notifications. The numbers come from /api/v1/dashboard, which is filtered by role. | `/dashboard` | admin |
| ![Dashboard — receptionist](02-dashboard/02-dashboard-reception.png) | **Dashboard — receptionist** | The same dashboard component. What changes by role is the navigation rail and the greeting, not the layout. | `/dashboard` | receptionist |
| ![Dashboard — doctor](02-dashboard/03-dashboard-doctor.png) | **Dashboard — doctor** | A clinician sees the same six counters. The rail is shorter: no staff page, no billing. | `/dashboard` | doctor |
| ![Dashboard — pharmacist](02-dashboard/04-dashboard-pharmacist.png) | **Dashboard — pharmacist** | A pharmacist reaches pharmacy and the ledger, and nothing clinical. | `/dashboard` | pharmacist |
| ![Dashboard — billing](02-dashboard/05-dashboard-billing.png) | **Dashboard — billing** | Billing reaches the ledger and the patient list, and nothing clinical. | `/dashboard` | billing |

### Patients

`03-patients/` · 8 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Patient list](03-patients/01-list.png) | **Patient list** | Every registered patient with UHID, DPDP consent state and outstanding balance. Rows open the profile; the list is paginated. | `/patients` | receptionist |
| ![Register a patient](03-patients/02-register-empty.png) | **Register a patient** | Name, age, phone and DPDP consent are the required minimum. | `/patients` | receptionist |
| ![Register a patient — filled](03-patients/03-register-filled.png) | **Register a patient — filled** | DPDP consent reveals the purpose field, so the patient can see what they agreed to. | `/patients` | receptionist |
| ![Patient registered](03-patients/04-register-success.png) | **Patient registered** | The toast returns the UHID, which is what the patient is called at the counter from then on. | `/patients` | receptionist |
| ![Patient list — after the journeys](03-patients/05-list-populated.png) | **Patient list — after the journeys** | Outstanding balances are populated by the pharmacy dispense and the IPD stay above. | `/patients` | admin |
| ![Patient profile — encounters](03-patients/06-profile-encounters.png) | **Patient profile — encounters** | One view of the patient: consent state, outstanding balance, and their visit history. | `/patients/:id` | admin |
| ![Patient profile — invoices](03-patients/07-profile-invoices.png) | **Patient profile — invoices** | The same invoice ledger billing works from, read-only here. | `/patients/:id` | admin |
| ![Patient profile — history](03-patients/08-profile-history.png) | **Patient profile — history** | A tab that says it is not built yet, rather than rendering an empty table that reads as "no history". | `/patients/:id` | admin |

### OPD / Encounters

`04-opd-encounters/` · 7 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![OPD — patient picker](04-opd-encounters/01-worklist.png) | **OPD — patient picker** | Consultation starts by picking the patient. The list is searchable and paginated so a patient registered a moment ago is reachable. | `/encounters` | doctor |
| ![OPD — new encounter](04-opd-encounters/02-new-encounter.png) | **OPD — new encounter** | Vitals and the two free-text fields that everything downstream keys off. | `/encounters` | doctor |
| ![OPD — active encounter](04-opd-encounters/03-active-encounter.png) | **OPD — active encounter** | The encounter is open. Prescribing and the AI advisor both work against this record. | `/encounters` | doctor |
| ![OPD — AI medicine advisor](04-opd-encounters/04-ai-suggestions.png) | **OPD — AI medicine advisor** | Keyword matching against the hospital formulary. Every suggestion is one click from becoming a prescription, and shows why it matched. | `/encounters` | doctor |
| ![OPD — prescriptions](04-opd-encounters/05-prescriptions.png) | **OPD — prescriptions** | Prescriptions name the medicine and its generic, not an id. A pharmacist dispenses from this list and cannot read a UUID. | `/encounters` | doctor |
| ![OPD — awaiting sign-off](04-opd-encounters/06-signoff-worklist.png) | **OPD — awaiting sign-off** | Encounters left open are held here so nothing is lost when a clinician moves on. | `/encounters` | doctor |
| ![OPD — signed and closed](04-opd-encounters/07-signed.png) | **OPD — signed and closed** | Signing closes the encounter. It stops appearing in the sign-off worklist and shows as signed in the patient profile. | `/encounters` | doctor |

### Pharmacy

`05-pharmacy/` · 9 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Pharmacy — empty catalogue](05-pharmacy/01-catalogue-empty.png) | **Pharmacy — empty catalogue** | A new tenant has no formulary. Note that both call-to-action buttons here are dead: the Add Medicine modal only renders once a medicine is already selected, so nothing on this screen works. | `/pharmacy` | admin |
| ![Pharmacy — medicine selected](05-pharmacy/02-medicine-selected.png) | **Pharmacy — medicine selected** | Selecting a medicine opens its batches. Nothing is received against it yet. This is also the state that makes the Add Medicine modal reachable at all. | `/pharmacy` | admin |
| ![Receive stock](05-pharmacy/03-add-stock.png) | **Receive stock** | A stock-in is a batch with an expiry, so FEFO has something to order on. | `/pharmacy` | admin |
| ![Pharmacy — batches](05-pharmacy/04-batches.png) | **Pharmacy — batches** | Expiry inside 30 days is flagged in red. Dispensing always takes the earliest expiry first, so this ordering is the FEFO order. | `/pharmacy` | admin |
| ![Add a medicine](05-pharmacy/05-add-medicine.png) | **Add a medicine** | Catalogue master data. Keywords are what the AI advisor later matches a diagnosis against. Note this form only renders once a medicine is already selected. | `/pharmacy` | admin |
| ![Pharmacy — pending prescriptions](05-pharmacy/06-pending-rx.png) | **Pharmacy — pending prescriptions** | Prescriptions waiting to be handed over, named by medicine rather than id. | `/pharmacy` | pharmacist |
| ![Pharmacy — dispense](05-pharmacy/07-dispense.png) | **Pharmacy — dispense** | Dispensing states the two side effects up front: FEFO deduction from the earliest batch, and a charge posted to the patient ledger. | `/pharmacy` | pharmacist |
| ![Pharmacy — dispensed](05-pharmacy/08-dispensed.png) | **Pharmacy — dispensed** | The prescription leaves the pending list and the stock movement is written to the ledger. | `/pharmacy` | pharmacist |
| ![Pharmacy — stock ledger](05-pharmacy/09-ledger.png) | **Pharmacy — stock ledger** | Every movement in and out, with the reference it belongs to. Outflows are the ones a discrepancy investigation starts from. | `/pharmacy` | pharmacist |

### IPD / Wards

`06-ipd-wards/` · 5 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![IPD — ward board](06-ipd-wards/01-ward-board.png) | **IPD — ward board** | Every bed with its occupancy, type and daily rate. Occupancy counters sit above the map. | `/admissions` | doctor |
| ![IPD — admit a patient](06-ipd-wards/02-admit.png) | **IPD — admit a patient** | An available bed opens the admit dialog. Patients already admitted are not offered. | `/admissions` | doctor |
| ![IPD — bed occupied](06-ipd-wards/03-admitted.png) | **IPD — bed occupied** | The bed now carries the patient and the date they came in, with discharge in place. | `/admissions` | doctor |
| ![IPD — discharge](06-ipd-wards/04-discharge.png) | **IPD — discharge** | Room charges are calculated and posted to the billing ledger on discharge. | `/admissions` | doctor |
| ![IPD — bed released](06-ipd-wards/05-discharged.png) | **IPD — bed released** | Discharge frees the bed and posts the stay to billing. | `/admissions` | doctor |

### Billing & invoicing

`07-billing/` · 6 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Billing — no patient selected](07-billing/01-no-patient-selected.png) | **Billing — no patient selected** | The ledger is per patient, so nothing is shown until one is chosen. | `/billing` | billing |
| ![Billing — unbilled charges](07-billing/02-unbilled-charges.png) | **Billing — unbilled charges** | Pharmacy dispenses and room charges post here automatically. Nothing is typed twice. | `/billing` | billing |
| ![Billing — generate invoice](07-billing/03-generate-invoice.png) | **Billing — generate invoice** | Selection happens through "Select All" only — the charge rows are behind the modal overlay, so a patient with mixed charge types can only be invoiced in full. | `/billing` | billing |
| ![Billing — invoice issued](07-billing/04-invoice-created.png) | **Billing — invoice issued** | A GST invoice is raised and the selected charges leave the unbilled list. | `/billing` | billing |
| ![Billing — record payment](07-billing/05-record-payment.png) | **Billing — record payment** | The dialog opens pre-filled with the outstanding balance; partial payments are the normal case. | `/billing` | billing |
| ![Billing — settled](07-billing/06-invoice-paid.png) | **Billing — settled** | Settled invoices show a zero balance and no payment action. | `/billing` | billing |

### Staff

`08-staff/` · 4 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Staff roster](08-staff/01-roster.png) | **Staff roster** | Everyone who can sign in to this hospital, each with a per-hospital role, access toggle and password state. | `/staff` | admin |
| ![Add staff](08-staff/02-add-staff.png) | **Add staff** | The temporary password is a shared secret until its owner changes it, so the form says so directly. | `/staff` | admin |
| ![Add staff — validation](08-staff/03-add-staff-validation.png) | **Add staff — validation** | Errors land on the control they describe and are wired with aria-invalid, not only coloured. | `/staff` | admin |
| ![Staff roster — new joiner](08-staff/04-roster-with-new-staff.png) | **Staff roster — new joiner** | The new account is flagged "must change password" until its owner signs in and sets their own. | `/staff` | admin |

### Role-based navigation & access

`09-role-access/` · 7 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Access control — doctor opening /staff](09-role-access/01-doctor-blocked-from-staff.png) | **Access control — doctor opening /staff** | A doctor who types the staff URL is sent to their dashboard. The page never renders, so nothing leaks before the redirect. | `/staff` | doctor |
| ![Navigation rail — admin](09-role-access/02-nav-admin.png) | **Navigation rail — admin** | Admin — every module, including staff. | `/dashboard` | admin |
| ![Navigation rail — doctor](09-role-access/03-nav-doctor.png) | **Navigation rail — doctor** | Doctor — clinical modules only. No staff, no billing. | `/dashboard` | doctor |
| ![Navigation rail — nurse](09-role-access/04-nav-nurse.png) | **Navigation rail — nurse** | Nurse — patients, encounters and admissions. | `/dashboard` | nurse |
| ![Navigation rail — receptionist](09-role-access/05-nav-receptionist.png) | **Navigation rail — receptionist** | Receptionist — patients and appointments. | `/dashboard` | reception |
| ![Navigation rail — pharmacist](09-role-access/06-nav-pharmacist.png) | **Navigation rail — pharmacist** | Pharmacist — pharmacy and the ledger. | `/dashboard` | pharmacy |
| ![Navigation rail — billing](09-role-access/07-nav-billing.png) | **Navigation rail — billing** | Billing — billing and patients. | `/dashboard` | billing |

### Empty, error and boundary states

`10-states/` · 3 snapshots

| | Snapshot | What it shows | Route | Role |
|---|---|---|---|---|
| ![Patients — no search results](10-states/01-search-no-results.png) | **Patients — no search results** | A search that matched nothing says so plainly. | `/patients` | admin |
| ![Patients — server failure](10-states/02-patients-load-error.png) | **Patients — server failure** | A server fault is reported as a server fault. Reporting it as "no patients found" would tell a clinician the record does not exist. | `/patients` | admin |
| ![Unknown route](10-states/03-unknown-route.png) | **Unknown route** | An unrecognised path is caught by the router and sent home rather than rendering a blank page. | `/*` | doctor |

---

## What is deliberately not here

- **Mobile and tablet.** Desktop 1440x900 only. The CSS has breakpoints at
  1100px, 900px and 560px, where the navigation rail becomes a drawer — a
  separate capture pass.
- **The dev-only icon gallery** (`/icons`). Stripped from production builds
  by an `import.meta.env.DEV` guard, so it is not part of the product surface.
- **A successful facility registration.** `/onboarding` is gated behind a
  platform secret or a `super_admin` token, and self-serve registration is
  off by default. The disabled state is captured instead, which is what a
  tenant admin actually sees.
- **Lockout.** Reaching the five-failed-attempt lockout would leave `admin`
  locked out for 15 minutes and break every later capture in the run.

## Defects found while capturing

These were found by driving the screens, not by reading the code. They are
recorded here rather than fixed, because this pass was for documentation.

- **The "Enabled Modules" checkboxes on `/onboarding` cannot be toggled at
  all.** Each one is a real `<input type="checkbox">` nested inside a
  `<label htmlFor={id}>` that points at that same input. Every activation —
  clicking the box, clicking its label, pressing Space — bubbles to the label
  and is dispatched to the input a second time, so the two change events
  cancel out. The consequence is not cosmetic: a tenant can never submit
  fewer than all four modules, and the "Select at least one module."
  validation below the list is unreachable by any user. Dropping `htmlFor`
  (the pattern the patient registration modal uses, which works) or removing
  the nesting fixes it.
- **Individual charge selection on `/billing` is impossible.** "Bill All" opens
  the "Generate Invoice" dialog, which says "Select charges to bill" — but
  the charge rows and their checkboxes sit *behind* the modal overlay, which
  covers the viewport. Playwright reports the overlay as intercepting pointer
  events on the checkbox, so a click never reaches it. The only working
  control is the "Select All" button, which means a patient with a mix of
  charge types can only ever be invoiced for all of them. There is a second
  latent bug on those same rows: the checkbox carries its own `onChange`
  *and* the `<tr>` carries an `onClick` that toggles the same charge, so even
  if the overlay were lifted the click would toggle twice and cancel.
- **The dashboard is not role-specific.** `Dashboard.jsx` renders the same six
  stat cards for every role. Role filtering happens in the
  `/api/v1/dashboard` response, so the numbers differ but the screen does not.
- **The encounter tabs do nothing.** The Clinical / AI Assistant /
  Prescriptions tabs in an active encounter all render as `active` with no
  click handler, so they are one panel, not three.
- **The history tab is a placeholder** — "coming soon" rather than data.
- **The dispense dialog reverts to a UUID.** The pending-prescription card
  names the medicine and its generic, and the prescription table does too —
  then "Dispense Prescription" opens with the raw prescription id in a
  disabled field, which is the one identifier on that screen a pharmacist
  cannot use to confirm they have the right one.

## Worth knowing, not a defect

- **"Outstanding" is 0 until a charge is invoiced.** `PatientBalanceService`
  sums only charges in status `billed` or `paid`, so a patient with a
  dispensed medicine or a completed stay still reads ₹0 until billing raises
  the invoice. It is also recalculated asynchronously after commit, so it
  lags by a moment — visible in the invoice-issued snapshot, where the
  invoice shows a ₹12,000 balance while the header beside it still says ₹0.

