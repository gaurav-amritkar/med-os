# MedOS — Business Requirements Document

**Version:** 4.0
**Status:** Current — describes the system as built and the work as planned
**Date:** 2026-10-02
**Supersedes:** `requirements/BRD.pdf` and `requirements/MedOS_Business_Requirements_Document.html` (v3.0, dated 2026-04-18)

> **Supersession notice.** The v3.0 BRD is **retained unmodified** for provenance. It describes a
> Node.js / Express / SQLite / Knex / bcrypt product deployed as a single-tenant instance and is
> stamped `STATUS: VERIFIED_DEPLOYMENT`. The implementation is Java / Spring Boot / PostgreSQL /
> AES-GCM / multi-tenant. **Do not read v3.0 as current.** It is kept only as a record of what was
> originally specified. Tracked as [#70](https://github.com/gaurav-amritkar/med-os/issues/70).

**Authority.** Where this document and the code disagree, the code is right and this document is a
defect. Every claim in §5 and §6 below was checked against the schema, the code, or the running
application.

---

## 1. Executive summary

MedOS is a multi-tenant Hospital Management System for Indian hospitals, built so that one
deployment serves many hospitals without one hospital being able to read another's data. It covers
registration, outpatient encounters, admissions, pharmacy, billing, and consent.

This version exists because the v3.0 BRD stopped describing the product roughly two stack changes
ago, and every reader who opened it was misled. Rather than patch a document whose premise had
changed, v4 states plainly what the system **is**, what is **planned**, and which legal obligations
are **unmet**.

Two facts frame everything below:

1. The clinical core works and is verified. Registration, OPD, admissions, pharmacy and billing are
   implemented, tenant-isolated, and covered by an automated test suite.
2. The compliance and interoperability obligations that make an Indian HMS sellable — DPDP consent
   lifecycle, FHIR R4, ABDM/ABHA — are **mostly absent**. These are the largest gap between the
   product and what a hospital actually needs to buy it.

---

## 2. Product scope

### In scope

| Area | Capability |
|---|---|
| Tenancy | Shared deployment, row-level tenant isolation, per-tenant encryption keys |
| Identity | In-house JWT auth, six roles, tenant-scoped RBAC, staff management |
| Patients | Registration, versioned name index, consent capture, encryption-at-rest for all PII |
| Clinical | Outpatient encounters, admissions and wards, discharge |
| Pharmacy | Catalog, stock, dispensing, ledger |
| Billing | Charge capture, GST, invoicing, payments |
| Compliance | DPDP consent capture, audit log |
| Interoperability | FHIR R4 (planned), ABDM/ABHA (planned) |

### Explicitly out of scope

Per [ADR-0006](../docs/adr/0006-v1-non-goals.md): native mobile apps, insurance claim adjudication,
laboratory analysers, payroll, and multi-country tax engines.

### V1 non-goals are still non-goals

The v3.0 BRD's §14 "Hidden Assumptions" was genuinely useful and is preserved here in §11 with its
invalidation triggers now recorded — the failure mode of the old document was not that it had bad
sections, but that nothing recorded when a trigger had fired.

---

## 3. Stakeholders and users

| Role | Daily need | Primary gaps today |
|---|---|---|
| **Receptionist** | Register patients, find them fast, book appointments | Name search only matched whole names ([#118](https://github.com/gaurav-amritkar/med-os/issues/118) partially fixed) |
| **Doctor** | OPD encounters, prescriptions, admissions | No discharge summary ([#69](https://github.com/gaurav-amritkar/med-os/issues/69)); no lab orders ([#63](https://github.com/gaurav-amritkar/med-os/issues/63)) |
| **Nurse** | Wards, admissions | Ward and room management is read-only ([#56](https://github.com/gaurav-amritkar/med-os/issues/56)) |
| **Pharmacist** | Dispense, stock ledger | No medicine-level drilldown ([#66](https://github.com/gaurav-amritkar/med-os/issues/66)) |
| **Billing clerk** | Invoice, collect payment | Invoice generation is a dead end and the discount is silently discarded ([#115](https://github.com/gaurav-amritkar/med-os/issues/115)); no receivables ledger ([#64](https://github.com/gaurav-amritkar/med-os/issues/64)) |
| **Hospital admin** | Manage staff, tenants, configuration | Staff management built but unmerged ([#55](https://github.com/gaurav-amritkar/med-os/issues/55)); no tenant management ([#54](https://github.com/gaurav-amritkar/med-os/issues/54)) |
| **Data principal** | Consent, access, correction, erasure | Consent cannot be withdrawn or purpose-bound ([#98](https://github.com/gaurav-amritkar/med-os/issues/98), [#114](https://github.com/gaurav-amritkar/med-os/issues/114), [#116](https://github.com/gaurav-amritkar/med-os/issues/116)) |
| **Regulator / auditor** | Evidence of consent and safeguards | Not satisfiable today ([#101](https://github.com/gaurav-amritkar/med-os/issues/101), [#103](https://github.com/gaurav-amritkar/med-os/issues/103)) |

---

## 4. Business problems and drivers

### 4.1 Regulatory (the binding constraint)

| Obligation | Source | Status |
|---|---|---|
| Consent must be free, specific, informed, purpose-bound | DPDP §6(2)–(6) | **Unmet.** A single boolean and a free-text purpose ([#116](https://github.com/gaurav-amritkar/med-os/issues/116)) |
| Refusal must carry no detrimental consequence | DPDP §6(2) | **Unmet.** Consent is a precondition of registration — declining means no care ([#116](https://github.com/gaurav-amritkar/med-os/issues/116)) |
| Notice must be plain-language, in English or 22 scheduled languages | DPDP §5(2), §7(9) | **Unmet.** No notice version, no language |
| Data-principal access, correction, erasure | DPDP §11–12 | **Unmet** ([#101](https://github.com/gaurav-amritkar/med-os/issues/101)) |
| Withdrawal must stop processing | DPDP §6(4)–(6) | **Unmet** ([#98](https://github.com/gaurav-amritkar/med-os/issues/98)) |
| Reasonable security safeguards, evidenced | DPDP §8(5) | **Partly met.** Per-tenant DEKs and versioned ciphertext; rotation audit not yet exportable ([#103](https://github.com/gaurav-amritkar/med-os/issues/103)) |
| Storage limitation and retention | DPDP §8(7) | **Unmet** ([#102](https://github.com/gaurav-amritkar/med-os/issues/102)) |
| Consent-based sharing as a HIP | ABDM Health Data Management Policy | **Unmet.** No ABHA, no HIE-CM gateway ([#97](https://github.com/gaurav-amritkar/med-os/issues/97), [#100](https://github.com/gaurav-amritkar/med-os/issues/100)) |
| GST-compliant invoicing | CGST Act | **Partial.** Rates hardcoded, no GSTIN/HSN, discount discarded ([#59](https://github.com/gaurav-amritkar/med-os/issues/59), [#115](https://github.com/gaurav-amritkar/med-os/issues/115)) |

> The single most serious item above is that **consent is currently a condition of receiving care**.
> A patient who declines cannot be registered. That is coercion in the one setting where coercion is
> least defensible, and it should be treated as a P0 compliance defect regardless of its issue
> priority label.

### 4.2 Operational

- **Staffing a new hospital is impossible.** A tenant can create exactly one user — the admin made
  at onboarding — so a new hospital has nobody to log in as but its owner.
  ([#55](https://github.com/gaurav-amritkar/med-os/issues/55))
- **Billing loses money silently.** An invoice discount is accepted by the API and discarded; no
  error is raised. ([#115](https://github.com/gaurav-amritkar/med-os/issues/115))
- **Clinical records are incomplete.** IPD captures one free-text diagnosis and no discharge summary.
  ([#69](https://github.com/gaurav-amritkar/med-os/issues/69))
- **No operational visibility.** No metrics, structured logs or correlation IDs.
  ([#9](https://github.com/gaurav-amritkar/med-os/issues/9))

---

## 5. Implemented capability — verified baseline

Verified against `main` and the working tree on 2026-10-02. 219 backend tests, 39 frontend unit
tests, 8 Playwright specs and a 31-endpoint flow probe were green at the time of writing.

### Platform

| Property | Value | Source |
|---|---|---|
| Language / framework | Java 17, Spring Boot 3.5.16 | `backend/pom.xml` |
| Frontend | React 19, Vite 8 | `frontend/package.json` |
| Database | PostgreSQL 16 (Supabase-compatible), 25 tables | `database/migrations/` |
| Tenancy | Row-level, injected at the SQL layer | [ADR-0002](../docs/adr/0002-row-level-tenancy.md) |
| PII at rest | Envelope encryption, one DEK per tenant | [ADR-0009](../docs/adr/0009-envelope-encryption-per-tenant-deks.md) |
| Migrations | Dedicated container, Flyway, checksum-gated | [ADR-0005](../docs/adr/0005-migration-ownership.md) |
| Secrets | Files outside the image, mode 600 | [ADR-0004](../docs/adr/0004-secret-management.md) |
| Auth | In-house JWT, six roles | [ADR-0008](../docs/adr/0008-supabase-managed-postgres-auth-stays-inhouse.md) |
| ADRs | 9 accepted | `docs/adr/` |

### Modules

| Module | Endpoints | State |
|---|---|---|
| Authentication | 1 | Complete |
| Onboarding | 1 | Complete, gated per [ADR-0003](../docs/adr/0003-gated-self-serve-onboarding.md) |
| Patients | 7 | Complete; fragment name search fixed on branch |
| Encounters / OPD | 9 | Complete |
| Admissions / IPD | 6 | Complete; no discharge summary |
| Pharmacy | 7 | Complete; no drilldown |
| Billing | 6 | Complete; invoice flow and discount defective |
| Dashboard | 5 | Complete |
| Staff management | 4 | Built and verified, **awaiting merge** ([#55](https://github.com/gaurav-amritkar/med-os/issues/55)) |

46 endpoints across 10 controllers.

### PII key lifecycle

Delivered as a chain of issues, and the reason a KEK rotation no longer breaks the system:

- [ADR-0009](../docs/adr/0009-envelope-encryption-per-tenant-deks.md) — envelope encryption,
  one DEK per tenant ([#83](https://github.com/gaurav-amritkar/med-os/issues/83))
- `tenant_keys` / `key_rotations` schema ([#84](https://github.com/gaurav-amritkar/med-os/issues/84))
- Versioned ciphertext, `kv1.d<generation>:<payload>` ([#85](https://github.com/gaurav-amritkar/med-os/issues/85))
- Per-tenant DEK resolution on every read and write ([#86](https://github.com/gaurav-amritkar/med-os/issues/86))
- Independent blind-index key, so search survives rotation ([#87](https://github.com/gaurav-amritkar/med-os/issues/87))

### Security posture delivered

Tenant isolation enforced by SQL rewriting rather than application filters; cross-tenant reads
rejected in tests; bcrypt password hashes never returned by an API ([#106](https://github.com/gaurav-amritkar/med-os/issues/106));
login rate limiting with lockout; login lockouts that actually take effect
([#53](https://github.com/gaurav-amritkar/med-os/issues/53) resolved by the same work); image
vulnerability scanning and a migration-integrity gate in CI.

---

## 6. Planned enhancements — sequenced

Ordered by dependency, not by priority label alone. **Phase 0 must clear before any hospital is
live**, because the items in it are defects, not enhancements.

### Phase 0 — Security and data integrity (before first production hospital)

| Issue | Requirement |
|---|---|
| [#51](https://github.com/gaurav-amritkar/med-os/issues/51) | Anonymous callers can provision admin tenants. `permitAll` must go |
| [#52](https://github.com/gaurav-amritkar/med-os/issues/52) | User list leaks across tenants. Resolved on the staff branch; needs verification and merge |
| [#118](https://github.com/gaurav-amritkar/med-os/issues/118) | Seed writes plaintext into ciphertext columns, 500-ing the patient list |
| [#73](https://github.com/gaurav-amritkar/med-os/issues/73) | Credentials were committed to a public repository. Rotated and purged; needs post-merge history audit |
| [#110](https://github.com/gaurav-amritkar/med-os/issues/110) | Migration tests skip in CI because no PostgreSQL service is wired |
| [#16](https://github.com/gaurav-amritkar/med-os/issues/16) | TLS termination, HSTS, Docker secrets for production |

### Phase 1 — PII key lifecycle completion

| Issue | Requirement |
|---|---|
| [#88](https://github.com/gaurav-amritkar/med-os/issues/88) | `KeyRewrapService`: dry run, resumable, idempotent KEK re-wrap |
| [#89](https://github.com/gaurav-amritkar/med-os/issues/89) | Operator runner with `--apply` and a runbook |
| [#90](https://github.com/gaurav-amritkar/med-os/issues/90) | Migrate existing ciphertext to the versioned format |
| [#91](https://github.com/gaurav-amritkar/med-os/issues/91) | Tenant-scoped DEK rotation for compromise response |
| [#103](https://github.com/gaurav-amritkar/med-os/issues/103) | Export rotation records as DPDP §8(5) evidence |

> Until [#88](https://github.com/gaurav-amritkar/med-os/issues/88) ships, rotating the KEK **breaks
> patient search**: material is unwrapable and search returns zero rows. The blind-index key from
> [#87](https://github.com/gaurav-amritkar/med-os/issues/87) makes search *survive* a re-wrap, but no
> re-wrap can be performed yet. This is the top technical risk in the product.

### Phase 2 — Consent and DPDP

| Issue | Requirement |
|---|---|
| [#116](https://github.com/gaurav-amritkar/med-os/issues/116) | Purpose-bound, representative and multilingual consent. **Declining must not block care** |
| [#114](https://github.com/gaurav-amritkar/med-os/issues/114) | Capture consent after registration; withdrawal as the mirror case |
| [#98](https://github.com/gaurav-amritkar/med-os/issues/98) | Consent ledger: purpose-, scope- and duration-bound, revocable |
| [#101](https://github.com/gaurav-amritkar/med-os/issues/101) | Data-principal access, correction, erasure |
| [#102](https://github.com/gaurav-amritkar/med-os/issues/102) | Storage limitation and retention |
| [#117](https://github.com/gaurav-amritkar/med-os/issues/117) | Field-level validation surfaced on the control, with the rule stated |

### Phase 3 — Clinical and revenue completeness

[#63](https://github.com/gaurav-amritkar/med-os/issues/63) lab orders ·
[#69](https://github.com/gaurav-amritkar/med-os/issues/69) discharge summary ·
[#56](https://github.com/gaurav-amritkar/med-os/issues/56) wards and rooms ·
[#64](https://github.com/gaurav-amritkar/med-os/issues/64) receivables and ageing ·
[#115](https://github.com/gaurav-amritkar/med-os/issues/115) invoice flow and discount ·
[#59](https://github.com/gaurav-amritkar/med-os/issues/59) configurable GST, GSTIN, HSN ·
[#66](https://github.com/gaurav-amritkar/med-os/issues/66) pharmacy drilldown ·
[#50](https://github.com/gaurav-amritkar/med-os/issues/50) generic provider scheduling ·
[#54](https://github.com/gaurav-amritkar/med-os/issues/54) tenant management ·
[#58](https://github.com/gaurav-amritkar/med-os/issues/58) printable receipt ·
[#65](https://github.com/gaurav-amritkar/med-os/issues/65) AI note mutability until signed ·
[#61](https://github.com/gaurav-amritkar/med-os/issues/61) real AI advisor rather than keyword matching

### Phase 4 — Interoperability

[#92](https://github.com/gaurav-amritkar/med-os/issues/92) FHIR R4 foundation and CapabilityStatement ·
[#93](https://github.com/gaurav-amritkar/med-os/issues/93) FHIR Patient with ABHA-ready identifier ·
[#94](https://github.com/gaurav-amritkar/med-os/issues/94) `audit_log` → FHIR AuditEvent ·
[#95](https://github.com/gaurav-amritkar/med-os/issues/95) ABDM Composition profiles ·
[#96](https://github.com/gaurav-amritkar/med-os/issues/96) SMART on FHIR authorization ·
[#97](https://github.com/gaurav-amritkar/med-os/issues/97) ABHA and consent-gated linking ·
[#100](https://github.com/gaurav-amritkar/med-os/issues/100) HIE-CM gateway as HIP/HIU

> ABDM sandbox credentials and HIE-CM gateway registration are **external dependencies** and will
> gate [#99](https://github.com/gaurav-amritkar/med-os/issues/99) and
> [#100](https://github.com/gaurav-amritkar/med-os/issues/100) regardless of engineering capacity.

### Phase 5 — Operability and product surface

[#9](https://github.com/gaurav-amritkar/med-os/issues/9) observability ·
[#11](https://github.com/gaurav-amritkar/med-os/issues/11) API versioning and contract drift ·
[#62](https://github.com/gaurav-amritkar/med-os/issues/62) reports and analytics ·
[#67](https://github.com/gaurav-amritkar/med-os/issues/67) dashboard trends ·
[#57](https://github.com/gaurav-amritkar/med-os/issues/57) tenant name in header ·
[#60](https://github.com/gaurav-amritkar/med-os/issues/60) public landing page ·
[#49](https://github.com/gaurav-amritkar/med-os/issues/49) payment gateway abstraction

---

## 7. Success metrics

| Metric | Target | Measured today |
|---|---|---|
| Cross-tenant data leaks | 0 | 0 in tests; 2 open issues ([#52](https://github.com/gaurav-amritkar/med-os/issues/52), [#51](https://github.com/gaurav-amritkar/med-os/issues/51)) |
| Patient search success rate | 100% of known patients | Degraded by [#118](https://github.com/gaurav-amritkar/med-os/issues/118) |
| Consent demonstrable to an auditor | Yes | No ([#98](https://github.com/gaurav-amritkar/med-os/issues/98), [#103](https://github.com/gaurav-amritkar/med-os/issues/103)) |
| KEK rotation without downtime | Yes | No ([#88](https://github.com/gaurav-amritkar/med-os/issues/88)) |
| FHIR conformance | R4 + ABDM IG | Not started |
| Invoice amount matches what staff intended | 100% | No ([#115](https://github.com/gaurav-amritkar/med-os/issues/115)) |
| Undisclosed PII at rest | 0 | 0 except dev seed ([#118](https://github.com/gaurav-amritkar/med-os/issues/118)) |
| Migration tests actually executing in CI | 100% | 0% ([#110](https://github.com/gaurav-amritkar/med-os/issues/110)) |

---

## 8. Non-functional requirements

| Attribute | Requirement | State |
|---|---|---|
| Isolation | No tenant can read another's data, at any layer | Enforced and tested |
| Encryption | All PII encrypted at rest, per-tenant keys | Met, rotation incomplete |
| Availability | Rotation and maintenance online, no downtime | Blocked on [#88](https://github.com/gaurav-amritkar/med-os/issues/88) |
| Recoverability | Backup and restore rehearsed | **Gap** — not yet written down |
| Performance | Search and list responsive at hospital scale | Decrypt-and-filter search is bounded at 5000 rows |
| Observability | Metrics, structured logs, correlation IDs | **Missing** ([#9](https://github.com/gaurav-amritkar/med-os/issues/9)) |
| Accessibility | WCAG 2.1 AA, not colour-only errors | Enforced by CI gates |
| Auditability | Every consent and access decision evidenced | **Partial** — rotation only |
| Contract stability | Published API contract with drift detection | **Missing** ([#11](https://github.com/gaurav-amritkar/med-os/issues/11)) |

---

## 9. Risk analysis

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | KEK rotation destroys unreadable patient data | **High** until [#88](https://github.com/gaurav-amritkar/med-os/issues/88) | Catastrophic — irreversible PII loss | Block production rotation until the re-wrap service and runbook ship |
| R2 | Consent flow fails a DPDP audit | **High** | Fines to ₹250 crore (§8(5)) | Phase 2 treated as pre-launch, not post-launch |
| R3 | No external FHIR/ABDM conformance | High | Loses every ABDM-integrated hospital | Phase 4 started before sales push |
| R4 | Silent failures reach users as wrong answers | Medium | Clinical and financial harm | Every fix in this cycle pairs a test with a mutation check |
| R5 | Seed/scripts write plaintext into encrypted columns | **Recurred** | Patient list unusable | [#118](https://github.com/gaurav-amritkar/med-os/issues/118); audit all SQL against column format |
| R6 | External ABDM dependencies stall delivery | High | Phase 4 slips | Request sandbox credentials early |
| R7 | Single region, single database, no restore rehearsal | Medium | Total loss | Backup/restore runbook before first live hospital |

---

## 10. Monetization model

Unchanged in intent from v3.0 and not yet implemented: per-hospital subscription on the shared
multi-tenant deployment, with the tenant boundary as both the isolation mechanism and the billing
unit. Payment collection is in scope
([#49](https://github.com/gaurav-amritkar/med-os/issues/49)); software subscription billing is not
yet specified beyond this paragraph.

---

## 11. Assumptions and invalidation triggers

Preserved from v3.0 §13–§14, with fired triggers recorded — the omission that made the old document
untrustworthy.

| Assumption | Invalidation trigger | Fired? |
|---|---|---|
| Hospitals will accept an English-only interface | A hospital requires a scheduled-language UI | **No.** But DPDP §7(9) obliges it for consent; tracked in [#116](https://github.com/gaurav-amritkar/med-os/issues/116) |
| Bright-clinic screens mean dark mode is optional | A deployment ships to a dark-reading environment | **Yes** — dark mode shipped; recorded here so it does not read as an unexplained regression |
| Clinical staff will accept free-text fields | Audit finds free text used as structured data | **Yes** — ward is free text ([#56](https://github.com/gaurav-amritkar/med-os/issues/56)) |
| Consent can be captured once at registration | A patient declines, or consents later | **Yes** — [#114](https://github.com/gaurav-amritkar/med-os/issues/114), [#116](https://github.com/gaurav-amritkar/med-os/issues/116) |
| A single database serves every hospital | A hospital requires data residency in its own region | **No**, but it constrains R7 |
| Staff passwords can be set by an admin and handed over | A hospital requires per-user SSO | **No.** Current design forces a password change at first login |
| Legacy ciphertext can be migrated in place | A pre-versioned row cannot be re-wrapped | **Unknown** — [#90](https://github.com/gaurav-amritkar/med-os/issues/90) |

---

## 12. Related documents

| Document | Purpose |
|---|---|
| [PRD v4.0](MedOS_Product_Requirements_Document_v4.md) | What the system is: architecture, data, API, UI, verification |
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md) | How the system is built |
| [requirements-traceability.md](../docs/requirements-traceability.md) | v3.0 requirements vs implementation |
| [production-readiness-plan.md](../docs/production-readiness-plan.md) | Path to first live hospital |
| [operations.md](../docs/operations.md) | Runbook |
| `docs/adr/` | Nine accepted architecture decisions |
| [Design spec](../docs/superpowers/specs/2026-10-01-pii-key-lifecycle-fhir-abdm-design.md) | PII key lifecycle, FHIR and ABDM |
| `requirements/BRD.pdf`, `requirements/MedOS_Business_Requirements_Document.html` | **Superseded v3.0, retained for provenance** |
