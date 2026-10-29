# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

The product serves seven roles, but the interface is optimised for the **receptionist** first — confirmed as the role that wins design tradeoffs. The front desk is the entry point for every patient and the highest-volume, most time-pressured seat in the hospital, so its throughput and error-resistance outrank other roles' convenience when they conflict.

The full set, as the BRD records it:

- **Receptionist** — register patients, find them fast, book appointments. *Primary.*
- **Doctor** — outpatient encounters, prescriptions, admissions.
- **Nurse** — wards, admissions, vitals.
- **Pharmacist** — dispensing, stock ledger.
- **Billing clerk** — charges, GST invoices, payment collection.
- **Hospital admin** — staff, configuration, tenant settings. The buyer and operator.
- **Data principal (patient)** — consent, and (once built) access, correction, erasure.
- **Regulator / auditor** — evidence of consent and safeguards.

## Product Purpose

MedOS is a multi-tenant Hospital Management System for Indian hospitals: one shared deployment serves many hospitals, and no hospital can read another's data. It digitises the end-to-end hospital workflow — patient registration, outpatient encounters, admissions, pharmacy, billing, and consent.

Success means a hospital can run its daily operations on it, and pass a DPDP (and GST) audit on the evidence it produces.

The **next milestone is a real hospital pilot, then commercial per-hospital subscription**. That sets the bar: this is pre-launch product work, so compliance and correctness gaps are launch gates, not roadmap items.

## Positioning

The tenant boundary is enforced at the persistence layer, not in application code. `TenantStatementInspector` rewrites the root `FROM` of every statement against a tenant-owned table to include `tenant_id`, so a forgotten `where` clause is not an authorisation bug. Patient PII is encrypted at rest with a **per-tenant DEK** (wrapped by a KEK held outside the image), and name search uses an independently keyed blind index — so a database-only breach yields nothing, and one hospital's key cannot decrypt another's records.

A neighbouring single-tenant HMS can copy the screens; it cannot truthfully copy "isolation is structural rather than remembered."

## Operating Context

- **Setting:** Indian hospitals — OPD, IPD/wards, pharmacy, billing counter, front desk. English-only UI today.
- **Regulatory frame:** DPDP Act (consent, data-principal rights, retention, security safeguards), CGST (GST invoicing, GSTIN, HSN), and ABDM/ABHA + FHIR R4 as the interoperability obligation.
- **Deployment:** dev runs the full stack via `docker compose` (Docker is the only hard requirement); production uses a separate compose file, managed PostgreSQL (Supabase-compatible), Caddy as sole TLS terminator, and secrets as files outside the image. Single region, single database.
- **Data model:** 25 tables, Flyway-owned; migrations are forward-only and never edited once applied.
- **Working discipline:** defects are typically found by mutation testing and by reading live output, not by a green suite alone — the project treats "tests pass" as necessary but not sufficient.

## Capabilities and Constraints

Confirmed and shipped:

- In-house JWT auth, bcrypt, Redis-backed login rate limiting with lockout; seven roles (platform `super_admin` plus six per-tenant roles: admin, doctor, nurse, receptionist, pharmacist, billing); a user's identity is global and their access is a `tenant_users` membership row.
- Patients (registration, sequential UHID, fragment name search, versioned name index), encounters/OPD, admissions/IPD, pharmacy (catalog, FEFO dispensing, stock ledger), billing (charges, GST, invoices, payments), dashboard, notifications, audit log.
- Idempotency-key protection on dispense and payment/invoice endpoints, scoped per tenant and user.
- Envelope encryption with versioned ciphertext `kv1.d<generation>:<payload>`; KEK rotation is implemented and audited.

Constraints and known state (recorded as facts, not to be papered over):

- **DR: DEK retirement remains conservative.** Per-tenant DEK rotation re-encrypts and verifies that tenant's rows. The previous generation remains archived so a transaction that commits after verification stays readable; automated safe retirement is not implemented.
- **Consent is currently a condition of registration** — declining blocks care. This is a P0 compliance defect against DPDP §6(2).
- Tenant row scoping covers `SELECT` only; writes rely on services setting `tenant_id` explicitly.
- No observability (no metrics, structured logs, or correlation IDs); CI scans images but not dependencies; no SAST/CodeQL/secret scanning.
- FHIR R4 and ABDM/ABHA are not implemented. ABDM sandbox credentials and HIE-CM gateway registration are external dependencies that gate that phase regardless of engineering capacity.
- Out of scope per [ADR-0010](adr/0010-product-module-scope.md): native mobile apps, multi-country tax engines, laboratory analyser instrument drivers, and automated insurance claim adjudication. Payroll, laboratory *information* systems, and insurance claim *lifecycle tracking* are now in scope, having previously been listed as excluded under a citation to ADR-0006 that does not cover product modules.
- Undecided / not yet specified: the mechanics of the software-subscription billing model, and safe retirement of archived DEK generations.

## Brand Commitments

- **Name:** MedOS (an "HMS"). Confirmed **MedOS-branded for all tenants** — hospitals are tenants sharing one product identity; the hospital name appears as tenant context, not as the product's identity. White-labeling is *not* the model.
- **Voice/positioning language in use:** "ultra-premium, role-based Hospital Management System". This is the incumbent positioning and should be preserved unless the user changes it.
- No committed logo or brand asset beyond a `favicon.svg` and an icon sprite; do not invent testimonial, customer, or press marks.

## Evidence on Hand

Real, in-repo material future work may draw on:

- `requirements/MedOS_Product_Requirements_Document_v4.md` and `..._Business_Requirements_Document_v4.md` — authoritative, code-verified product and business truth (v3.0 PDF/HTML retained for provenance only; describes a superseded stack).
- `README.md` — accurate current system description, credentials, operations, security posture, known gaps.
- `docs/adr/` — nine accepted architecture decisions; `docs/ARCHITECTURE.md`, `docs/operations.md`, `docs/requirements-traceability.md`.
- Automated evidence: backend JUnit/MockMvc suite, frontend Vitest suite (including design-system guardrails), Playwright e2e specs, an endpoint flow probe, `tools/verify-*.sh` gates.
- `docs/MEDOS.pptx`, `docs/diagrams/` — presentation material.

Absent, and must not be fabricated: customer names or logos, testimonials, press, benchmarks, pricing, or real product screenshots.

## Product Principles

1. **The tenant boundary is the product.** Isolation is structural and provable, not a setting someone remembers to apply. Any feature that weakens it is wrong regardless of convenience.
2. **Optimise the front desk.** When roles conflict, the receptionist's throughput and error-resistance win.
3. **Compliance is a launch gate.** DPDP consent, data-principal rights, and GST correctness block the pilot; they are not post-launch enhancements.
4. **A failure must never masquerade as a normal answer.** A 500 must not render as "no results"; a discarded discount must not look like a successful invoice.
5. **Evidence over assertion.** Claims are backed by tests, mutation checks, and live verification — and by documents checked against the code, since docs drift is itself a defect.

## Accessibility & Inclusion

Target is **WCAG 2.1 AA**, enforced in CI: errors are signalled by border, text, and `role="alert"` rather than colour alone (WCAG 1.4.1), staff forms wire `aria-invalid`/`aria-describedby`, and a contrast gate covers all design-token pairs (WCAG 1.4.11). DPDP §7(9) will eventually require consent notice in English or one of the 22 scheduled languages — English-only is the current, acknowledged limit.
