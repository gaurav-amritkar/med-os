# MedWorks parity: full module surface on the MedOS tenant substrate

- **Date:** 2026-10-19
- **Status:** Draft for review
- **Author:** agent, from video evidence at `youtube.com/watch?v=P3yfXZACLM0`
- **Related:** `PRODUCT.md`, `docs/adr/0006-v1-non-goals.md`, proposed `docs/adr/0010-product-module-scope.md`

---

## 1. Goal

Bring MedOS to the **functional depth of MedWorks v2.5** — all 27 modules, all tabs —
while keeping MedOS's substrate, which MedWorks does not have: structural tenant
isolation, encrypted PII, DPDP consent, and an audit trail that is provable rather
than asserted.

The reference product is treated as a **scope and information-architecture source**,
not a design source. Its screens are copied; its assumptions about tenancy, consent
and privacy are not.

### 1.1 Non-goals

Copying the following from MedWorks, deliberately:

| MedWorks behaviour | Why not |
|---|---|
| Public "Sign up" on the login screen | ADR-0003 gates self-serve onboarding behind a platform secret or `super_admin`. Public signup cannot be enabled: the tenant-provisioning prerequisites do not exist. |
| Plaintext patient names, phones, addresses in every list | MedOS encrypts PII at rest under a per-tenant DEK (ADR-0009). |
| No consent concept anywhere in 27 modules | DPDP consent is a launch gate for the pilot (PRODUCT.md Principle 3), and consent-as-precondition-of-registration is itself a P0 defect to fix. |
| Single-hospital identity, no tenant switch | The tenant boundary is the product (Principle 1). Every new module is multi-tenant. |
| Email-as-username login | MedOS uses usernames with a global identity plus a `tenant_users` membership. |

---

## 2. Evidence and its limits

### 2.1 How it was gathered

YouTube's caption endpoint is bot-blocked (HTTP 429), so **no transcript exists for
this analysis**. Instead:

1. Metadata pulled via `yt-dlp` (title, channel, duration, upload date).
2. Video stream downloaded at 1280x720.
3. Frames extracted and de-duplicated by mean-absolute-difference on a
   160x90 greyscale reduction.
4. First pass at 1 frame / 6 s → 48 frames → 29 distinct scenes, all read.
5. Second pass at 1 frame / 1 s → 288 frames → 29 clusters.

**The 1 s pass found one scene the 6 s pass had missed** (Patient Feedback) and
proved that **no screen was on screen for fewer than 3 seconds**. Every visible state
in the video was read.

| Property | Value |
|---|---|
| Title | MedWorks Hospital Management System - Product Walkthrough |
| Channel | Prashant K (`@PrashantKay`) |
| Uploaded | 2026-10-16 |
| Duration | 4:48 |
| Views at time of analysis | 89 |
| Description | "A 5-minute tour of MedWorks: the complete hospital management system, from first enquiry to final invoice." |

### 2.2 Confidence tiers

Every claim in Part 2 carries one of these. Nothing else may be presented as fact.

| Tier | Meaning |
|---|---|
| **A — Observed** | Seen on screen. Module name, tab names, page title, subtitle, counter labels, column headers, filter labels, button labels, status values. |
| **B — Named only** | Appeared in a header bar or tab strip but its contents were never shown. Name is exact; layout is unknown. |
| **C — Designed** | Not in the video at all. Every dialog, form, validation rule, write path, error state, loading state, and responsive behaviour. |

### 2.3 The observed/designed boundary — read this before estimating

A 5-minute tour of a 27-module product shows **every module at rest**. It contains
**no create dialog, no edit dialog, no validation, no save, no confirmation, no
error state, no loading state, no empty-vs-error distinction, no mobile layout, and
no auth flow beyond the login screen.**

So:

- **Information architecture and list-page grammar: Tier A.** Reliable. This is
  where MedWorks is genuinely well made, and where most of the reusable value is.
- **Dialog and CRUD behaviour: Tier C.** Not one dialog is observable. This is the
  majority of the engineering work and none of it is copied.
- **Tabs marked B:** their names are exact, their contents are invented.

Estimating this as "copy 27 screens" would understate the work by roughly an order of
magnitude. Section 9 gives the wave plan instead.

### 2.4 Reference data is not to be copied

The video's own seed data is internally inconsistent, and copying it would import the
inconsistency:

- Risk analytics report 12 + 25 + 78 = **115 patients** while the KPI card says
  **Total Patients 3**.
- Patient phone `+44 7723 01913 (Metro City)` and addresses in Rajasthan/UK form
  alongside **₹ (INR)** billing.
- The audit log contains `root@hospital.internal` with a failed login — an artefact
  of the demo environment, not a designed state.

Take the structure. Do not take the data.

---

## 3. Target architecture

### 3.1 Application shell (Tier A)

```
┌────────────────────────────────────────────────────────────────┐
│ ▦ MedWorks   [module name] │ [tabs…]      🔍 Search  ⌘K   (A) ▾│
├────────────────┬─────────────────────────────────────────────────┤
│ CLINICAL      │                                                 │
│  Dashboard    │   page title + one-line purpose                 │
│  Enquiry      │   [ primary action ]                             │
│  Patients     │                                                 │
│  …            │   [counter chips]                                │
│ DIAGNOSTICS & │   [filters]                                      │
│  PHARMA       │   ┌ table ──────────────────────────────────┐    │
│  …            │   │ footer: Total results X–Y of Z |        │    │
│ FINANCE &     │   │         Display [20] GO ‹ 1 ›           │    │
│  SUPPLY       │   └──────────────────────────────────────────┘    │
│ v2.5        ‹ │                                                 │
└──────────────┴─────────────────────────────────────────────────┘
```

Observed shell facts:

- Left rail, three visible groups — **CLINICAL**, **DIAGNOSTICS & PHARMA**,
  **FINANCE & SUPPLY** — independently scrollable, with a scrollbar visible and more
  items below the fold than fit.
- Rail footer: version string and a collapse chevron.
- Global search in the top bar with a `⌘K` affordance.
- Active module highlighted solid green; inactive rows outlined.
- Top bar carries the **module name** and its **tab strip**, not just a page title.
- Green is the single primary colour. Amber, red, blue, violet and pink are semantic
  only (severity, status, positive delta).

### 3.2 The page grammar (Tier A) — the reusable contract

Every list page in MedWorks uses the same eight-part structure. This is the single
most valuable thing to adopt, because it is what makes a 27-module product
navigable:

1. **Module title + one-line purpose subtitle.** Never a bare noun. Observed
   examples: "Front-desk & call-center enquiries — follow up and convert into
   appointments"; "Real-time inpatient stay tracking, bed allocations, attending
   consultants & length of stay".
2. **Tab strip in the header bar**, module-scoped. The active tab is a filled green
   pill.
3. **Counter chip row.** 3–6 chips, each a label + value, values colour-coded by
   semantic state (0 is usually neutral; danger states are red).
4. **Primary action button**, top-right, green, `+`-prefixed.
5. **Filter row.** Search input, then 1–4 selects/radio groups, then a Filter
   button. Radio groups for mutually exclusive sets (Patient Type, Queue Status,
   Blood Group component).
6. **Data table.** Dense, uppercase headers, sticky, first column often a monospace
   identifier. Row actions as icon buttons or small pill buttons.
7. **Table footer**, identical everywhere: `Total results: X - Y of Z`, `Display
   [20]`, `GO`, `‹ Prev  [1]  Next ›`.
8. **Empty state**, specific to the table — never "No data". Observed: "No
   appointments scheduled for the selected filters."; "No active emergency cases.
   Triage queue is clear."; "No complaints logged. Click 'Log Complaint' to record
   the first one."

Two conventions worth carrying deliberately:

- **A status vocabulary that reads as a sentence.** `OPD Registrant`, `Must change
  password`, `Outpatient / Discharged`, `Cold Stored`, `Universal Donor`.
- **Specific unit-bearing counters.** `Available Beds 0/1`, `Active Staff 5 / 5`,
  `Present Today 6 Checked-in`, `Bed Occupancy 0%`.

### 3.3 Module inventory

27 modules. Tier A = landing tab seen. Tier B = tab named but not shown.

| # | Module | Tabs (A = shown, B = named only) |
|---|---|---|
| **CLINICAL** | | |
| 1 | Executive Dashboard | **A** Executive Overview · B Patient Analytics · B Clinical Operations · B Revenue & Billing · B Bed Census |
| 2 | Enquiry Desk | A (single page) |
| 3 | Patients Hub | A (single page) |
| 4 | Appointments | **A** Appointments · B Appointments Calendar · B Doctor Shift Schedules |
| 5 | OPD Desk | **A** Today's Queue · B Consultation Desk · B E-Prescriptions · B Diagnostics Orders · B Clinical History |
| 6 | IPD & Beds | **A** Live Bed Matrix · B Admit Patient · B Ward Rounds · B Nursing Station · B Bed Reservations · B Ward Bed T… *(truncated in video)* |
| 7 | Emergency | **A** Triage Queue · B Register Casualty · B Trauma Bays & Resus · B Escalate to OT/ICU |
| 8 | Nursing & MAR | **A** Ward Nursing Station · B Medication Admin (MAR) · B Shift Handover Log · B Incident Reports |
| 9 | Operation Theatre | **A** Live OT Whiteboard · B Book Surgery · B PACU Recovery · B Safety Checklist · B Implant Registry · B Surger… *(truncated)* |
| 10 | Doctors Desk | **A** Doctor Directory · B Duty Rosters & Shifts · B Consultation Fees |
| 11 | EMR Records | **A** Patient Directory · B Clinical Timeline · B Clinical Notes & SOAP · B Allergies & Alerts |
| **DIAGNOSTICS & PHARMA** | | |
| 12 | Laboratory | **A** Test Orders Queue · B Sample Accessioning · B Result Entry · B Sample Rejections · B Critical Panic Values |
| 13 | Radiology PACS | **A** Radiology Orders · B Modality Schedule · B Radiologist Reports · B Modality Scanners · B Critical Imaging AI |
| 14 | Pharmacy & POS | **A** Pharmacy Dashboard · B Drug Formulary · B Prescription POS · B Reorder & Low Stock · B Returns & Refun… |
| 15 | Blood Bank | **A** Component Inventory · B Crossmatching Requests · B Donor Registration · B Transfusions & Reactions · B Wasta… |
| **FINANCE & SUPPLY** | | |
| 16 | Billing & Cashier | **A** Cashier POS Counter · B Daily Closing Ledger · B Deposits & Advances |
| 17 | Insurance & TPA | **A** Insurance Desk · B Pre-Auth Requests · B Claims Tracking · B TPA Payers Master |
| 18 | Central Inventory | **A** Master Stock Level · B Stock & Batches · B Issue Items · B Stock Transfers · B Stock Adjustments |
| 19 | Procurement & GRN | **A** Procurement Desk · B Purchase Orders (PO) · B Goods Received (GRN) · B Approved Vendors |
| 20 | Accounts & Ledger | **A** Financial Overview · B Chart of Accounts · B Journal Entries · B Expense Vouchers · B Reconciliation |
| 21 | HR & Staff Payroll | **A** Employee Directory · B Duty Rosters · B Attendance Logs · B Leave Approvals · B Monthly Payroll |
| **GOVERNANCE** *(group header not captured — inferred)* | | |
| 22 | Reports & Analytics | **A** Executive BI Hub · B Clinical Indices · B Financial Intelligence · B Operational Metrics · B Report Schedule |
| 23 | Document Vault | **A** Clinical Consents · B Archive Vault · B Expiring Documents · B Patient Documents · B Templates Library |
| 24 | Complaints & Grievances | A (single page) |
| 25 | Patient Feedback | A (single page) |
| 26 | Roles & Security | **A** User Accounts · B Privilege Matrix · B Permissions · B Policies · B Active Sessions |
| 27 | Hospital Settings | **A** Overview · B Hospital Profile · B Branches · B Departments · B Services & Tariffs · B Doctors & Schedules |

Plus the **login screen** (Tier A): split marketing panel + form. Email and password,
password visibility toggle, Forgot password, Sign up, Contact IT Support. MedOS keeps
its own login and adds no Sign up.

Two observations to record as **inferences, not observations**:

- Modules 22–27 sat under the "FINANCE & SUPPLY" heading in the video, but three of
  them (Reports, Document Vault, Complaints) are not finance. The sidebar was
  scrolled and its grouping below the fold is **not visible**. MedOS should group
  them as it sees fit; do not assume the reference's grouping is deliberate.
- "Patient Feedback" was reached from the Executive Dashboard tab strip, so it may
  be a dashboard sub-page rather than a top-level module.

### 3.4 Per-module detail observed (Tier A)

Recorded so the rebuild matches rather than approximates. Counter labels, columns
and filters are verbatim.

| Module | Counters | Table columns | Filters / extras |
|---|---|---|---|
| Executive Overview | Appointments (Today, New, Year Total); Total Patients; Overall Rooms (All Time, General/Private split); Doctors on Duty (Total, On Leave); Treatments (Upcoming, Operations/General) | — | Risk donut (High/Moderate/Low + deltas + AI insight bullets); Patients Statistics 5-column chart (Emergency, Routine check-up, Appointment, Physical therapy, Therapy session); Appointment Overview (Scheduled/Completed/Checked-in/Cancelled); Appointment Calendar month grid; Date Legends |
| Enquiry Desk | Total / New / Follow-up / Converted / Cancelled | Enquiry #, Contact, Reason/Department, Status, Actions | All Statuses, All Types; `+ Log Enquiry` |
| Patients Hub | Active OPD / Inpatient (IPD) / Emergency / New Today / Total Records | S.No., UHID/Reg No., Patient Name, Age/Gender, Blood Group, Phone & City, Active Encounter/Bed, Status, Actions | Blood Group select; Patient Type radios: All/Active/Admitted (IPD)/Emergency/Discharged; `+ Register Patient` |
| Appointments | Today's Total / Scheduled / Checked-in / In Consultation / Completed / Cancelled | Token, Appt No., Patient Information, Doctor & Department, Time & Type, Status, Actions | Doctor select, Date, Queue Status radios; `+ Book Appointment` |
| OPD Desk | Today's Visits / Waiting-Queue / In Consultation / Completed / Prescriptions (Rx) / Lab Orders | Token #, Patient Details, Doctor & Department, Type & Reason, Queue Status, Action | Search, consulting-doctor select, department select, queue-status select, Filter; `Open Active Consultation Desk`, Refresh |
| IPD & Beds | Admitted Inpatients / Occupancy Rate / Available Beds (`0/1`) / ICU-Critical Care / Under Cleaning / Hospital Wards | IPD Number, Patient Details, Ward & Bed, Attending Doctor, Admission Date & Stay, Admitting Reason/Diagnosis, Actions | Ward & unit select, attending-doctor select, Filter Inpatients; `+ New IPD Admission` |
| Emergency | 4 triage tier cards — **LEVEL 1: RED** (Immediate 0 min, Critical Resus), **LEVEL 2: YELLOW** (Urgent <15 min, Observation), **LEVEL 3: GREEN** (Non-Urgent, Fast Track), **LEVEL 4: BLACK** (Expectant, Logged); then Total Today / Red Critical / Yellow Urgent / Green Non-Urgent / In Observation | Triage/ER #, Patient Demographics, Chief Complaint & Vitals, Bay/Attending, Status, Actions | `24/7 LIVE` badge; `+ Emergency Intake` (red) |
| Nursing & MAR | — (form + feed) | Vitals charting (BP, Pulse, Temp, SpO2), Medication Given/Administered, Intake & Output, Nursing Observations, Recording Nurse; right panel = Inpatient Vitals & Care Audit Feed (chronological) | Shift label; `Save & Chart Nursing Care Log` |
| Operation Theatre | Scheduled Today / In-Progress / PACU Recovery / Emergency OT / Completed Today | Suite cards: OT-1..OT-4 with suite name, specialty, floor, READY state, queue depth | `LIVE SUITES`; `+ Schedule Surgery`; suite queue depth |
| Doctors Desk | Total Doctors / Departments / Showing | Emp ID, Doctor Name, Department, Specialization, Room/Shift, Fee, Status | Department select; `+ Add Doctor` |
| EMR Records | Indexed Patients / Active IPD / Allergy Alerts / Clinical Notes | UHID/Patient, Demographics, Active Care Status, Medical Alerts/Allergies, Last Encounter, EMR Actions (Timeline, Notes) | Search; Filter EMR; `+ New SOAP Note` |
| Laboratory | Total Today / Sample Pending / Processing / Verified / Critical | Order queue, filter tabs: All / Ordered / Sample Collected / Processing / Verified | `LIVE LIS`; `+ Test Master` (purple) |
| Radiology PACS | Total Scans Today / Requested & Unscheduled / Scheduled on Modality / Reported by Radiologist | Search; modality tabs All / X-Ray / CT / MRI / Ultrasound | `DICOM / PACS WORKLIST`; Modality Calendar; Refresh |
| Pharmacy & POS | Total Formulations / Pending Dispensing / Dispensed Today / Low Stock Reorders / Total Sales Revenue | RX # & Date, Patient Demographics, Prescribed Medications & Dosages, Prescribing Doctor, Status, Dispensing Action | `LIVE DISPENSARY`; Counter POS Sale; `+ Refresh` |
| Blood Bank | Total Units in Stock / Urgent-STAT Requests / Low Stock Alerts / Registered Donors | Blood-group cards (O+, O-, A+, A-, B+, B-, AB+, AB-) × component rows (FFP, PRBC, Platelets, Whole Blood) with reorder level | `24/7 COLD CHAIN`; component tabs All/Whole Blood/PRBC/FFP/Platelets; `+ Register Donor / Bleed` |
| Billing & Cashier | Total Invoices Raised / Total Collected Revenue / Pending Receivables | Invoice #, Patient (UHID), Type, Total Amount, Paid Amount, Balance, Status, Payment Action (Collect Payment / PAID IN FULL) | `+ Generate New Invoice` |
| Insurance & TPA | Active Patient Policies / Pre-Auth Desk Queue / Claims Submitted / Settled Claim Value | Policy #/Member ID, Patient Details, Carrier/TPA, Sum Insured, Remaining Balance, Room Eligibility, Valid Until, Status | `+ Attach Patient Policy`; Manage Blood Requests link |
| Central Inventory | Catalog SKUs / Active Warehouses / Low Stock Triggers / Stock Valuation | Item Code, Item Name & Generic, Category, UOM, Current Stock, Reorder Point, Standard Price, Status | Category select; `+ Add Item SKU` |
| Procurement & GRN | Pending PRs / Approved Vendors / Active POs / GRN Receipts | PR Number, Department & Store, Priority, Justification/Reason, Est. Budget, Requested By/Date, Status, Workflow Actions (Approve/Reject/Convert to PO) | Status/priority selects; `+ New Purchase Requisition (PR)` |
| Accounts & Ledger | Total Revenue / Operating Expenses / Net Surplus / Active Accounts | Revenue streams (credits) by account code; Cost heads (debits); Hospital Assets & Liquid Reserves; Liabilities & Payables | Double-entry account codes shown (4010-OPD-REV, 5020-SALARIES) |
| HR & Staff Payroll | Active Staff (`5 / 5`) / Present Today / Pending Leave Requests / Monthly Payroll | Emp ID, Staff Name & Gender, Department & Designation, Qualifications/Exp, Contact, Basic Monthly Pay, Type, Status | Department + status selects; `+ Onboard New Employee` |
| Reports & Analytics | OPD+IPD Patients / Bed Occupancy / Net Surplus Margin / Diagnostics Volume | Clinical Activity & Diagnostic Throughput; Financial Operations & Cost Breakdown | `Current Month` scoping |
| Document Vault | Total Documents / Verified / Pending Review / Archived | Document Details (name, doc no, version, tags), Category & Module, Associated UHID, Uploaded/Verified By, Date & Size, Status, Actions (view, download, delete) | Category + Status selects; `+ Upload Document` |
| Complaints & Grievances | Total / Open / In Progress / Resolved / Escalated | Complaint #, Subject, Raised By, Priority, Status, Actions | All Statuses, All Priorities; `+ Log Complaint` (red) |
| Patient Feedback | Total / New / Needs Follow-up / Avg Rating (`—/5`) | Feedback #, From, Rating, Comments, Status, Actions | All Statuses, All Categories; `+ Log Feedback` (amber) |
| Roles & Security | Total Users / Active Accounts / Suspended / Locked | User Identity (name, @username, email), Role & Scope, Department, Last Login, Status, Actions (suspend/activate) | Role filter; `+ Provision New User` |
| Audit Trail | Total System Events / Critical & High-Risk / Routine Operations | Timestamp & ID, Actor (User & Role), Module & Action, Description & Affected Record, IP & Severity, Inspect | Module + Severity selects; `Export Audit Log` |
| Hospital Settings | Total Departments / Total Doctors / Total Staff / Total Services / Total Wards / Total Rooms / Total Beds (`28 Available \| 6 Occupied`) / Active Users / Active Branches | Master Setup Checklist (9 items, all "Configured"); Recent Administrative Activity | `+ Add Doctor`, `+ Add Service` |

---

## 4. Data model

All new tables follow existing MedOS conventions: UUID primary key, `tenant_id` on
every tenant-owned table, `created_at`/`updated_at`, `version` for optimistic locking
where the entity is concurrently edited (Admissions already does this).

### 4.1 New tables by module

Names are indicative; the migration is written in the implementation plan.

| Module | Tables |
|---|---|
| Enquiry | `enquiries`, `enquiry_status_history` |
| Appointments | `appointment_slots`, `appointments`, `appointment_queue_tokens`, `doctor_shifts`, `doctor_shift_slots` |
| EMR | `clinical_notes` (SOAP), `clinical_timeline_events`, `patient_allergies`, `patient_alerts` |
| OPD | `opd_queue_entries`, `encounter_vitals`, `e_prescriptions`, `diagnostic_orders` |
| IPD | `wards`, `beds`, `bed_reservations`, `bed_assignments`, `admission_vitals`, `ward_rounds`, `length_of_stay_daily` |
| Emergency | `emergency_triages`, `trauma_bays`, `escalations` |
| Nursing | `nursing_shift_logs`, `medication_administration_records`, `intake_output_records`, `shift_handovers`, `nursing_incidents` |
| OT | `operation_theatres`, `surgeries`, `surgery_safety_checklists`, `pacu_records`, `surgical_implants` |
| Doctors | `departments`, `doctor_profiles`, `doctor_consultation_fees` |
| Laboratory | `lab_tests`, `lab_test_orders`, `lab_samples`, `lab_results`, `lab_rejections`, `critical_value_alerts` |
| Radiology | `imaging_modalities`, `imaging_orders`, `imaging_studies`, `imaging_reports`, `imaging_scanners`, `imaging_critical_findings` |
| Pharmacy | extend existing `medicine_catalog`/`medicine_batches`; add `pos_sales`, `pos_sale_lines`, `stock_returns` |
| Blood Bank | `blood_groups`, `blood_inventory`, `blood_donors`, `blood_donations`, `crossmatch_requests`, `transfusions`, `transfusion_reactions`, `blood_wastage` |
| Billing | extend existing `invoices`/`payments`; add `deposits`, `advances`, `daily_closing_ledger` |
| Insurance | `tpa_payers`, `insurance_policies`, `policy_members`, `preauth_requests`, `claims`, `claim_items`, `claim_status_history` |
| Inventory | `item_catalog`, `warehouses`, `stock_ledger`, `stock_batches`, `stock_transfers`, `stock_adjustments` |
| Procurement | `vendors`, `purchase_requisitions`, `purchase_orders`, `purchase_order_lines`, `goods_receipt_notes`, `grn_lines` |
| Accounts | `chart_of_accounts`, `journal_entries`, `journal_lines`, `expense_vouchers`, `bank_reconciliations` |
| HR | `employees`, `employee_attendance`, `leave_requests`, `leave_types`, `payroll_runs`, `payslips`, `salary_structures` |
| Documents | `documents`, `document_versions`, `document_links`, `retention_policies` |
| Complaints | `complaints`, `complaint_comments`, `complaint_escalations` |
| Feedback | `feedback_entries`, `feedback_responses` |
| Reports | `report_schedules`, `report_runs` |
| Audit | extend existing `audit_log` |
| Settings | `hospital_profiles`, `branches`, `service_catalog` (extends `tenants`) |

### 4.2 PII classification — the encryption surface grows sharply

Today 9 columns are encrypted (5 on `Patient`, 4 on `Encounter`). Parity adds
substantially more, and each is a compliance obligation, not a preference:

| Table | Columns | Note |
|---|---|---|
| `blood_donors` | name, phone, email, address, dob | New PII class, high sensitivity |
| `employees` | name, phone, email, address, dob, bank_account, pan, aadhaar | Employee PII; bank/PAN are financial PII |
| `policy_members` | name, dob, phone, email | Insurance member PII |
| `complaints` | complainant_name, contact | Complaint subjects can be staff or patients |
| `emergency_triages` | contact, next_of_kin_name, next_of_kin_phone | |
| `feedback_entries` | respondent_name, email | |
| `clinical_notes` | free-text body | Clinical free text is the highest-sensitivity category; **must not** be indexed or blind-searched |
| `journal_entries` / `expense_vouchers` | payee_name, payee_account | Vendor bank details |

Blind (keyed) index required, for fragment name search: `blood_donors`,
`employees`, `policy_members`. **Not** for `clinical_notes` — free text is searched
by the owning clinician through the record, not by blind index.

---

## 5. Tenancy and compliance rules

These are the constraints that make "keeps our tenant logic" testable. Each is
verifiable, and each belongs in CI.

1. **Every new tenant-owned table carries `tenant_id`**, declared `NOT NULL`, and is
   listed in the tenant-scoped set read by `TenantStatementInspector`.
2. **Every new read path goes through the inspector.** No repository query is written
   that reads a tenant-owned table without it. A test asserts that each new table
   appears in the scoping set.
3. **Every new write sets `tenantId` explicitly** from the acting-tenant context.
   Known weakness today: scoping covers `SELECT` only. Parity adds **109 new tables**
   to that exposure, so Wave 0 must either extend scoping to writes or add a guard
   test that fails when a write omits it.
4. **Every PII column in §4.2 is AES-256-GCM under the tenant's DEK**, with the
   versioned `kv1.d<generation>:<base64>` format, and is decryptable only by that
   tenant's key.

   **Wave-0 design note — per-tenant DEK rotation (closed 2026-10-22).** The
   versioned format above exists precisely to make a DEK rotation readable-through:
   rotation tells the tenant's current generation N → N+1 with *new key bytes*, and
   the generation prefix means ciphertext written before the rotation keeps its
   identity. Three decisions define the mechanism:

   - **Per-generation retention, not re-encryption.** A superseded generation is
     archived (wrapped) into `tenant_dek_history` (V6) *before* the row's
     generation advances. Rotation is an online, per-tenant operation with no
     re-encryption window; converged old rows onto the current generation via a
     backfill runner is an optional efficiency follow-up, not a correctness need.
   - **The (current DEK, current generation) pair is one fact, read together.**
     Every step of a rotation leaves the store in a state where the label a writer
     chooses and the key it encrypts with stay consistent — including the
     interrupted states (archive done, advance not; advance done, replace not),
     which are all decodable by rerunning the rotation. This is why an in-flight
     request across a rotation either labels with the old generation and the old
     key, or the new pair — never a mismatch, and why historical reads resolve the
     exact generation named by a value's prefix rather than whatever key is
     current. A format whose resolver ignored the generation (the seated bug the
     tenant-key-holder deepening flagged) fails the pinned test suite:
     `TenantDekRotationTest`.
   - **Rotation is deliberate, named, and audited.** No bulk wildcard; the runner
     refuses to run without `tenant-id` and `initiated-by`, and the
     `key_rotations.operation='dek_rotation'` row carries both (DPDP §8(5)).
     Runbook: `docs/operations.md` §7e.

5. **New PII columns are covered by the existing encryption tests**, extended rather
   than bypassed.
6. **DPDP consent is captured and enforced per module** wherever patient data is
   first written: registration, emergency intake, admission, lab/imaging order. The
   `consents` table already exists; these are additional consent events, not a new
   concept.
7. **Every mutation writes an `audit_log` row** with actor, role, tenant, affected
   record, IP and severity — matching the four-field shape the reference's audit
   screen displays.
8. **A failure never renders as a normal answer.** PRODUCT.md Principle 4. The new
   modules adopt the pattern already established in `Patients.jsx`: a failed fetch and
   an empty result are separate states, and a failure renders `role="alert"`.
9. **Every list page implements the §3.2 grammar**, including the footer pagination
   control and a specific empty state. A shared `<DataTable>` component owns it, so
   27 modules cannot drift.
10. **Accessibility holds at WCAG 2.1 AA**, which CI already enforces: errors by
    border + text + `role="alert"`, `aria-invalid`/`aria-describedby` on staff forms,
    contrast gate across all token pairs.

---

## 6. API surface

Conventions, matching the existing `/api/v1` contract: JWT bearer, RFC-7807-style
error bodies, page metadata (`content`, `meta: {page, size, totalElements,
totalPages, first, last}`), idempotency keys on the three endpoints that already
require them plus any new POST that mutates a ledger.

| Module | Representative endpoints |
|---|---|
| Enquiry | `GET/POST /api/v1/enquiries`, `PATCH /enquiries/{id}/status`, `POST /enquiries/{id}/convert` |
| Appointments | `GET/POST /appointments`, `GET /appointments/calendar`, `POST /appointments/{id}/check-in`, `GET/POST /doctors/{id}/shifts` |
| EMR | `GET /patients/{id}/timeline`, `POST /patients/{id}/soap-notes`, `GET/POST /patients/{id}/allergies` |
| OPD | `GET /opd/queue`, `POST /opd/queue/{token}/call`, `POST /encounters/{id}/e-prescriptions`, `POST /encounters/{id}/diagnostic-orders` |
| IPD | `GET /ipd/bed-matrix`, `POST /ipd/admissions`, `GET /ipd/active`, `PUT /ipd/admissions/{id}/discharge`, `POST /ipd/wards/rounds` |
| Emergency | `POST /emergency/intake`, `GET /emergency/triage-board`, `POST /emergency/{id}/escalate` |
| Nursing | `POST /nursing/vitals`, `POST /nursing/mar`, `POST /nursing/shift-handover`, `GET /nursing/audit-feed` |
| OT | `GET /ot/whiteboard`, `POST /ot/surgeries`, `POST /ot/surgeries/{id}/checklist`, `POST /ot/pacu` |
| Laboratory | `GET /lab/orders`, `POST /lab/samples/accession`, `POST /lab/results`, `POST /lab/orders/{id}/reject`, `GET /lab/critical-values` |
| Radiology | `GET /radiology/orders`, `GET /radiology/modality-calendar`, `POST /radiology/reports`, `POST /radiology/findings/critical` |
| Pharmacy | existing `/pharmacy/*` + `POST /pharmacy/pos/sales`, `POST /pharmacy/returns` |
| Blood Bank | `GET /blood-bank/inventory`, `POST /blood-bank/donors`, `POST /blood-bank/crossmatch`, `POST /blood-bank/transfusions`, `POST /blood-bank/wastage` |
| Billing | existing `/billing/*` + `GET /billing/daily-closing`, `POST /billing/deposits` |
| Insurance | `GET/POST /insurance/policies`, `POST /insurance/preauth`, `POST /insurance/claims`, `GET /insurance/tpa-payers` |
| Inventory | `GET /inventory/stock`, `POST /inventory/transfers`, `POST /inventory/adjustments` |
| Procurement | `GET/POST /procurement/requisitions`, `POST /procurement/requisitions/{id}/approve`, `POST /procurement/purchase-orders`, `POST /procurement/grn` |
| Accounts | `GET /accounts/chart-of-accounts`, `POST /accounts/journal-entries`, `POST /accounts/expense-vouchers`, `POST /accounts/reconciliations` |
| HR | `GET/POST /hr/employees`, `GET /hr/attendance`, `POST /hr/leave-requests`, `POST /hr/payroll/runs` |
| Documents | `GET/POST /documents`, `GET /documents/{id}/versions`, `POST /documents/{id}/archive` |
| Complaints | `GET/POST /complaints`, `PATCH /complaints/{id}`, `POST /complaints/{id}/escalate` |
| Feedback | `GET/POST /feedback` |
| Reports | `GET /reports/indices`, `GET/POST /reports/schedules` |
| Audit | `GET /audit`, `GET /audit/export` |
| Settings | `GET/PUT /settings/profile`, `GET/POST /settings/branches`, `GET/POST /settings/departments`, `GET/POST /settings/services` |
| Roles | `GET/POST /users`, `PUT /users/{id}/role`, `GET /users/privilege-matrix`, `GET /users/sessions` |

---

## 7. Delivery waves

Ordered so that each wave's work depends only on what is already built, and so the
highest-value front-desk surface lands early. **Wave 0 is a gate, not a phase of
parity work.**

| Wave | Content | Gate to exit |
|---|---|---|
| **0 — Substrate** | The four known P0s: consent decoupled from registration; `/users/me` stops returning the password hash; per-tenant DEK rotation; observability (metrics, structured logs, correlation IDs). Plus a shared `<DataTable>`, `<CounterRow>`, `<FilterBar>` component set so 27 modules cannot each invent their own grammar. | All four closed with tests. Tenant-scoping test covers every table added from Wave 1 on. |

**Wave-0 design note — DEK rotation (closed 2026-10-22, PR #180).** See §5.4 #4
below for the mechanism: per-generation archive (V6 `tenant_dek_history`), the
(current DEK, current generation) pair read as one fact, rotation deliberate,
named and audited (`key_rotations.op='dek_rotation'`, runbook §7e).

**Wave-0 design note — observability closure (closed 2026-10-22).** The wave
had two halves: the in-process slice (correlation IDs, scrape endpoint,
`json-logs` logback profiles) and the *deployment* half — which is what made it
a wave-0 item rather than a config tidy-up:

- **Wiring is a contract test, not a convention.** `SPRING_PROFILES_ACTIVE:
  prod,json-logs` in `docker-compose.prod.yml`, pinned by
  `ObservabilityProdComposeGuardTest`; the JSON format itself is pinned by
  booting with the profile and parsing a real log line
  (`ObservabilityJsonLogsProfileTest`). #9's failure mode was never "no
  dependency" — it was "wired in one file, dropped in another" (see next).
- **Profile overrides replace, not extend.** `application-prod.yml`'s exposure
  list silently omitted `prometheus`, so no prod-profile container scraped.
  Found only by booting a real container; the fix is pinned prose + guard test.
- **Thresholds are defined, not implied.** Seven alerts (5xx ratio, p95 latency,
  DB pool pressure, heap, GC pause, login-failure spike, scrape liveness) over
  the exact metric families this backend exports, promtool-validated, drop-in
  `tools/observability/prometheus-rules-medos.yml`; definitions and severity
  contract in `docs/operations.md` "Alert thresholds".
**Wave-1 opening note — the inspector writes now (closed 2026-10-22, PR #182).**
Before any front-desk table lands, `TenantStatementInspector` (G16) scoping
moves past SELECT: UPDATE/DELETE root-table writes fold the tenant predicate
into their WHERE (or acquire one), and tenanted-table INSERTs that omit
`tenant_id` are refused loudly rather than silently orphaned. Combined with the
Wave-0 entity↔table guard, every table added in this wave is scoped at the SQL
boundary the moment it is added — the guard test (`TenantWriteScopingGuardTest`)
is what makes adjacency to `TENANT_TABLES` the only step to register a new table
for all four DML shapes.

| **1 — Front desk** | Enquiry Desk, Appointments (+ calendar, doctor shifts, token queue), Patients Hub deepened | Receptionist can run a full day: enquiry → appointment → token → check-in → register |

**Wave-1 progress tracker.** In-flight, module by module, each with its own
front-end/compose-verified evidence before the gate can close:

- **Enquiry Desk** — backend landed (PR #183): V7 tables (`enquiries`,
  `enquiry_status_history`), per-tenant numbering, audited transitions with the
  trail as record, `/api/v1/enquiries` (RBAC: writes = receptionist/admin;
  board readable by clinical roles), migration test on real Postgres, seed.
  Front-end page next (uses the shared `DataTable`/`FilterBar` set).
| **2 — Clinical core** | EMR 360 (Timeline, SOAP notes, Allergies & Alerts), OPD Desk tabs (Consultation Desk, E-Prescriptions, Diagnostics Orders, Clinical History) | A consultation runs end to end and the record is readable from the EMR |
| **3 — Inpatient** | IPD & Beds (Bed Matrix, Admit, Ward Rounds, Nursing Station, Reservations), Nursing & MAR | A patient can be admitted, nursed, and discharged with a charting trail |
| **4 — Diagnostics** | Laboratory, Radiology PACS, Blood Bank | An order placed in OPD is resulted, and a unit can be crossmatched and transfused |
| **5 — Theatre + workforce** | Operation Theatre, Doctors Desk, HR & Staff Payroll | A surgery is scheduled, run, and closed out; payroll runs from the staff directory |
| **6 — Supply chain** | Central Inventory, Procurement & GRN | A requisition becomes a PO, a GRN, and stock |
| **7 — Revenue** | Billing & Cashier deepened, Accounts & Ledger (double-entry), Insurance & TPA | Charges from every upstream module land in one ledger; an invoice settles; a claim is submitted |
| **8 — Governance** | Reports & Analytics, Document Vault, Audit trail, Roles & Security, Hospital Settings, Complaints, Feedback | An auditor can answer who-did-what-to-which-patient from the product alone |

**Wave 7 is the largest risk.** Double-entry accounting (chart of accounts, journal
entries, cost heads, bank reconciliation) is a different discipline from the rest of
this product and is not implied anywhere by the current codebase. It deserves its
own design pass before implementation, not a slot in a wave.

---

## 8. How to consume this document

This is a **programme spec, not an implementation plan.** It scopes 27 modules, 109
new tables and 9 waves; no single plan can execute it.

- **The next implementation plan covers Wave 0 only.** Everything after Wave 0 depends
  on decisions made inside it — principally the shared component set and the
  write-scoping guard — and planning against components that do not exist yet would
  be guesswork.
- Each subsequent wave gets its own spec and plan when its predecessor is done.
- §2, §3, §5 and §6 are stable and are the contract every wave is held to. §3.4 and
  §4 are expected to grow as earlier waves reveal real needs.

Wave order is a recommendation, not a dependency graph. If the hospital pilot
constrains delivery, Waves 5–8 are the ones to defer; Waves 0–3 are what a pilot
actually exercises.

---

## 9. Acceptance criteria

Per wave, all must hold:

1. `mvn clean test` and `npm test` green; `npm run lint` clean.
2. Every new table carries `tenant_id` and appears in the tenant-scoping test.
3. Every new PII column in §4.2 is encrypted, with a test asserting ciphertext at
   rest and decryptability by the owning tenant **and failure under another tenant's
   key**.
4. Every new list page uses the shared grammar components; no page hand-rolls its own
   table footer or pagination.
5. Every new page distinguishes empty from failed, with `role="alert"` on the failure
   state.
6. Every mutation writes an audit row, asserted by test.
7. Frontend accessibility suite passes; no colour-only status signalling.
8. A Playwright spec walks the wave's primary flow against the real stack.

Global: `docs/requirements-traceability.md` updated to map each new module to its
requirement, and the UI snapshot capture (`npm run capture:ui`) re-run so
`docs/ui-snapshots/` reflects the new screens.

---

## 10. Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| Write-path tenant scoping is unenforced (`SELECT`-only today). 109 new tables multiplies the exposure from a latent gap to a probable data breach. | **Critical** | Wave 0 must close this, not defer it. A tenant-isolation test per table. |
| Double-entry accounting is unproven territory for this codebase | High | Separate design pass in Wave 7 |
| Blood bank and banking are cold-chain and regulated chains of custody with their own audit obligations | High | Model as append-only transfer records; no destructive updates |
| Scope: 27 modules is a multi-quarter programme competing with an imminent hospital pilot | High | Waves exist so pilot value lands in Waves 1–3; Waves 5–8 are post-pilot if the pilot stalls |
| PII surface grows ~4× (donors, employees, insurance members, clinical free text) | High | §4.2 is a checklist, not a suggestion; encryption test extension is a gate |
| Payroll introduces statutory Indian compliance (PF, ESI, TDS) not visible in the reference | Medium | The reference shows a basic monthly payroll only. Statutory requirements must be specified separately |
| The video shows no write flows, so Wave 1+ dialogs are designed blind | Medium | §3.4 and §3.2 are the fidelity anchor; dialog design is a design task, reviewed before implementation |
| Reference data inconsistencies (§2.4) leak into fixtures | Low | Seed fixtures written fresh, not transcribed from frames |

---

## 11. Document changes accompanying this spec

1. **`docs/adr/0010-product-module-scope.md`** — new ADR. Records all 27 modules as
   in scope, superseding the exclusions in `PRODUCT.md`.
2. **`PRODUCT.md` line 62** — currently reads "Out of scope per ADR-0006: native mobile
   apps, insurance claim adjudication, laboratory analysers, payroll, multi-country
   tax engines." ADR-0006 is about *infrastructure* (Kubernetes, ELK, message
   queues, read replicas) and says nothing about product modules; no ADR in
   `docs/adr/` covers product scope at all. The citation is wrong. Correct it to cite
   ADR-0010, and state the surviving exclusions accurately.
3. **`PRODUCT.md` line 36** — "A neighbouring single-tenant HMS can copy the screens…"
   remains uncorroborated: nothing in the repo names it. This spec's evidence is
   consistent with that description, but the claim should either name its referent or
   be marked as a general positioning statement.