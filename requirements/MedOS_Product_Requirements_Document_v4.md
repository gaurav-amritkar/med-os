# MedOS — Product Requirements Document

**Version:** 4.0
**Status:** Current — describes the system as built and the work as planned
**Date:** 2026-10-02
**Supersedes:** `requirements/PRD.pdf` and `requirements/MedOS_Product_Requirements_Document.html` (v3.0, dated 2026-04-18)

> **Supersession notice.** The v3.0 PRD is **retained unmodified** for provenance. It specifies a
> Node.js / Express / SQLite / Knex product and a system-manifest layout (schema mappings, router
> topology, render tree, state management) that no longer describes this codebase. **Do not read
> v3.0 as current.** Tracked as [#70](https://github.com/gaurav-amritkar/med-os/issues/70).

**Companion:** [BRD v4.0](MedOS_Business_Requirements_Document_v4.md) — why the product exists and
what it must achieve. This document covers **what it is and how it behaves**.

---

## 1. Architecture as built

| Layer | Choice | Decision record |
|---|---|---|
| Runtime | Java 17, Spring Boot 3.5.16 | — |
| Persistence | PostgreSQL 16, Spring Data JPA, Hibernate 6 | [ADR-0001](../docs/adr/0001-single-host-managed-postgres.md) |
| Tenancy | Row-level `tenant_id`, enforced by SQL rewriting | [ADR-0002](../docs/adr/0002-row-level-tenancy.md) |
| Frontend | React 19, Vite 8, React Router, Zustand | — |
| Auth | In-house JWT, bcrypt, Redis-backed rate limiting | [ADR-0008](../docs/adr/0008-supabase-managed-postgres-auth-stays-inhouse.md) |
| Redis | Non-authoritative cache and rate-limit counter only | [ADR-0007](../docs/adr/0007-redis-non-authoritative.md) |
| PII | Envelope encryption, AES-256-GCM, one DEK per tenant | [ADR-0009](../docs/adr/0009-envelope-encryption-per-tenant-deks.md) |
| Secrets | Files outside the image, mode 600, never in Git | [ADR-0004](../docs/adr/0004-secret-management.md) |
| Migrations | Dedicated Flyway container; application never migrates | [ADR-0005](../docs/adr/0005-migration-ownership.md) |
| Onboarding | Gated, closed by default | [ADR-0003](../docs/adr/0003-gated-self-serve-onboarding.md) |

### Tenancy enforcement

`TenantStatementInspector` injects `<alias>.tenant_id = '<tenant>'` into the root `FROM` clause of
every statement whose table owns a `tenant_id`. Tenancy is therefore not a property of application
code that can be forgotten — a missing `where` clause is not an authorisation bug.

**Consequence worth stating plainly:** `users` has no `tenant_id` and is not tenant-owned. A user's
identity is global; their access is a row in `tenant_users`. Any endpoint reading users **must**
scope through that membership table explicitly.

---

## 2. Data model

25 tables across three migrations. V1 is frozen — once applied it is never edited, because the
checksum gate exists to prove it ([ADR-0005](../docs/adr/0005-migration-ownership.md)).

| Migration | Contents |
|---|---|
| `V1__initial_schema.sql` | 23 tables: tenants, tenant_users, users, patients, encounters, prescriptions, medicines, pharmacy transactions, lab orders, rooms, admissions, charges, invoices, payments, audit_log, notifications, appointments, diseases, … |
| `V2__pii_key_lifecycle.sql` | `tenant_keys`, `key_rotations` |
| `V3__staff_management.sql` | Adds `tenant_users.active` and `users.must_change_password` — no new tables |

### Key tables

```
users(id, username UNIQUE, password_hash, full_name, email UNIQUE,
      specialization, active, must_change_password, created_at, last_login)

tenant_users(id, user_id → users, tenant_id → tenants,
             role CHECK (admin|doctor|nurse|receptionist|pharmacist|billing),
             active, UNIQUE(user_id, tenant_id))

tenant_keys(tenant_id, wrapped_dek, dek_generation,
            wrapped_bi_key, bi_key_generation, kek_version, updated_at)

patients(id, tenant_id, uhid, name, name_index, age, gender,
         phone, email, address, blood_group,
         dpdp_consent, dpdp_consent_at, version)
```

`name`, `phone`, `email` and `address` hold AES-GCM ciphertext, not plaintext.

---

## 3. PII encryption design

The most intricate part of the product, and the part most likely to cause irreversible damage if
changed casually.

```
KEK (file, outside the image)
  └── wraps ──> DEK          (one per tenant, stable across writes)
                 ├── encrypts/decrypts ──> patient PII fields
                 └── wraps ──> blind-index key
                                  └── HMAC-SHA256 ──> patients.name_index
```

| Rule | Consequence if broken |
|---|---|
| The DEK never changes once created | Per-row DEKs would destroy search and joins |
| Only wrapped material is stored | A database-only breach yields nothing |
| A retired KEK version must still unwrap | Old data becomes permanently unreadable |
| The index key is independent of the DEK | Re-wrapping invalidates every `name_index` and search silently returns zero rows |
| Ciphertext is versioned `kv1.d<generation>:<payload>` | A future format change cannot be rolled out |

**Two decisions a reader must not "simplify":**

1. **One DEK per tenant, stable across writes.** Vendor guidance to generate a DEK per write is wrong
   for an EMR: it would make every row its own unwrap and destroy the ability to search or join.
2. **Search decrypts and filters in the application.** `name` is encrypted and `name_index` is an
   HMAC, so neither supports a SQL substring match. Name search therefore loads a bounded set of the
   most recent candidates and matches the typed fragment in memory, logging a warning if the bound
   truncates the result. This is a deliberate trade-off, not an oversight.

### Rotation status

| Capability | State |
|---|---|
| Encrypt/decrypt with per-tenant DEK | Shipped ([#86](https://github.com/gaurav-amritkar/med-os/issues/86)) |
| Index key survives a re-wrap | Shipped ([#87](https://github.com/gaurav-amritkar/med-os/issues/87)) |
| Re-wrap DEKs under a new KEK | **Missing** ([#88](https://github.com/gaurav-amritkar/med-os/issues/88)) |
| Operator-facing rotation | **Missing** ([#89](https://github.com/gaurav-amritkar/med-os/issues/89)) |

> **Rotating the KEK today makes patient PII unreadable.** The blind-index work means search will
> *recover* once a re-wrap runs, but no re-wrap mechanism exists yet. Production rotation must stay
> blocked until [#88](https://github.com/gaurav-amritkar/med-os/issues/88) and
> [#89](https://github.com/gaurav-amritkar/med-os/issues/89) ship.

---

## 4. Functional requirements

### 4.1 Identity and access

| ID | Requirement | State |
|---|---|---|
| FR-AUTH-1 | Authenticate by username or email, bcrypt-verified | Shipped |
| FR-AUTH-2 | Issue a JWT carrying user, role and tenant | Shipped |
| FR-AUTH-3 | Rate-limit and lock out failed logins | Shipped |
| FR-AUTH-4 | Refuse sign-in when every membership is inactive | Shipped ([#53](https://github.com/gaurav-amritkar/med-os/issues/53)) |
| FR-AUTH-5 | Refuse sign-in for one hospital without affecting another | Shipped (staff branch) |
| FR-USR-1 | Tenant admin creates staff with a role | Shipped, awaiting merge ([#55](https://github.com/gaurav-amritkar/med-os/issues/55)) |
| FR-USR-2 | Existing account gains a membership rather than a duplicate identity | Shipped |
| FR-USR-3 | Role change and access end/restore, scoped to one hospital | Shipped |
| FR-USR-4 | Refuse to deactivate or demote the **last active admin** | Shipped |
| FR-USR-5 | Admin-set password must be changed at first sign-in | Shipped |
| FR-USR-6 | Never return a password hash from any endpoint | Shipped ([#106](https://github.com/gaurav-amritkar/med-os/issues/106)) |
| FR-USR-7 | Cross-tenant staff operations rejected | Shipped |
| FR-USR-8 | List deactivated staff, not only active | Shipped |

### 4.2 Patients

| ID | Requirement | State |
|---|---|---|
| FR-PAT-1 | Register with validated, encrypted PII | Shipped |
| FR-PAT-2 | Generate a sequential UHID | Shipped |
| FR-PAT-3 | Search by UHID prefix | Shipped |
| FR-PAT-4 | Search by any fragment of the name, case-insensitive | Shipped |
| FR-PAT-5 | Paginate deterministically (total ordering, no repeats or skips) | Shipped |
| FR-PAT-6 | Capture DPDP consent with purpose | Shipped |
| FR-PAT-7 | Capture consent **after** registration | **Missing** ([#114](https://github.com/gaurav-amritkar/med-os/issues/114)) |
| FR-PAT-8 | Purpose-bound, representative, multilingual consent | **Missing** ([#116](https://github.com/gaurav-amritkar/med-os/issues/116)) |

### 4.3 Billing

| ID | Requirement | State |
|---|---|---|
| FR-BIL-1 | Capture charges, compute GST, generate an invoice | Shipped |
| FR-BIL-2 | Record payments against an invoice | Shipped |
| FR-BIL-3 | Apply a discount to the invoice | **Broken** — accepted and silently discarded ([#115](https://github.com/gaurav-amritkar/med-os/issues/115)) |
| FR-BIL-4 | Select an arbitrary subset of charges to invoice | **Broken** — the modal is a dead end ([#115](https://github.com/gaurav-amritkar/med-os/issues/115)) |
| FR-BIL-5 | Configurable GST rates, GSTIN, HSN | **Missing** ([#59](https://github.com/gaurav-amritkar/med-os/issues/59)) |
| FR-BIL-6 | Receivables ledger and ageing | **Missing** ([#64](https://github.com/gaurav-amritkar/med-os/issues/64)) |

### 4.4 Compliance and audit

| ID | Requirement | State |
|---|---|---|
| FR-CMP-1 | Write an audit record for every material action | Shipped |
| FR-CMP-2 | Consent ledger, revocable, time-bound | **Missing** ([#98](https://github.com/gaurav-amritkar/med-os/issues/98)) |
| FR-CMP-3 | Data-principal access, correction, erasure | **Missing** ([#101](https://github.com/gaurav-amritkar/med-os/issues/101)) |
| FR-CMP-4 | Storage limitation and retention | **Missing** ([#102](https://github.com/gaurav-amritkar/med-os/issues/102)) |
| FR-CMP-5 | Export rotation records as §8(5) evidence | **Missing** ([#103](https://github.com/gaurav-amritkar/med-os/issues/103)) |
| FR-CMP-6 | Map `audit_log` to FHIR AuditEvent | **Missing** ([#94](https://github.com/gaurav-amritkar/med-os/issues/94)) |

---

## 5. API surface

46 endpoints across 10 controllers. Authorization is declared per handler with `@PreAuthorize`; the
security chain authenticates everything else.

| Controller | Endpoints | Authorization |
|---|---|---|
| `AuthController` | 1 | permitAll |
| `OnboardingController` | 1 | permitAll — **#51](https://github.com/gaurav-amritkar/med-os/issues/51) |
| `PatientController` | 4 | mixed |
| `PatientOnboardingController` | 3 | mixed |
| `EncounterController` | 9 | doctor/nurse/admin |
| `AdmissionController` | 6 | doctor/nurse/admin |
| `PharmacyController` | 7 | pharmacist/doctor/admin |
| `BillingController` | 6 | billing/admin |
| `DashboardController` | 5 | authenticated |
| `UserController` | 4 | **admin only** |

### Error contract

One shape for every failure, so the client never has to guess:

```json
{
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "path": "/api/v1/patients",
  "validationErrors": { "phone": "must match \"^[0-9+\\-\\s()]{7,20}$\"" },
  "timestamp": "2026-10-02T03:59:01Z"
}
```

**Known gap:** `validationErrors` is produced and then discarded by the frontend
([#117](https://github.com/gaurav-amritkar/med-os/issues/117)), and its messages are developer-facing
rather than stating the rule. A 500 is also rendered as an empty list, which reports a server fault as
"no results" ([#118](https://github.com/gaurav-amritkar/med-os/issues/118)).

---

## 6. UI requirements

Ten pages plus a dev-only icon gallery, behind role-gated routes.

| Page | Route | Roles |
|---|---|---|
| Login, Onboarding | `/login`, `/onboarding` | public |
| Dashboard | `/` | all |
| Patients | `/patients` | admin, doctor, nurse, receptionist, billing |
| Encounters | `/encounters` | admin, doctor, nurse |
| Admissions | `/admissions` | admin, doctor, nurse |
| Pharmacy | `/pharmacy` | admin, pharmacist, doctor |
| Billing | `/billing` | admin, billing |
| Staff | `/staff` | admin |

### Design-system constraints, enforced in CI

These are guarded by `frontend/src/test/design-system.test.js` and failing them fails the build:

| Rule | Reason |
|---|---|
| No inline style objects in new components | Styling changes become untrackable |
| No hard-coded hex colours | Tokens only, so theming stays coherent |
| No `px` font sizes | Type scale is tokenised |
| No `var()` for an undeclared token | A typo would silently render nothing |
| Contrast gate on all token pairs | WCAG 1.4.11 |

### Accessibility

Errors are signalled by border, text and `role="alert"` — never by colour alone (WCAG 1.4.1), and the
staff form wires `aria-invalid` and `aria-describedby` to its message.

---

## 7. Verification approach

The project's quality bar is higher than "tests pass", and the reason is visible in how defects were
found in this cycle: several bugs passed a green suite and were caught only by mutation or by
reading live output.

| Layer | Tooling | Count |
|---|---|---|
| Backend unit + integration | JUnit 5, Mockito, MockMvc, H2/PostgreSQL | 219 |
| Frontend unit | Vitest, Testing Library | 39 |
| End-to-end | Playwright against the real Docker stack | 8 specs + staff flow |
| API surface probe | Node script over every endpoint | 31 checks |
| Design system | Vitest guards | included above |
| Migration integrity | `verify-migrations.sh` + self-test | 5 checks |
| Container scan | Trivy | image vulnerability scan |
| CI gates | 4 required checks | backend, frontend, container defs, vuln scan |

**Discipline applied throughout this cycle:**

1. A failing test is written and watched fail before production code exists.
2. Where a test could pass trivially, the implementation is **mutated** and the test must fail.
   Two mutants survived in this cycle; both were traced to untested behaviour and closed with new
   tests rather than accepted.
3. Live verification against the running stack, because several defects were invisible to unit tests
   — including one where a container restart silently invalidated an entire verification run.

**Known gap:** migration tests skip in CI because no PostgreSQL service is wired
([#110](https://github.com/gaurav-amritkar/med-os/issues/110)), so 10 tests are not exercising
anything on every push.

---

## 8. Delivery plan

Sequenced as in [BRD §6](MedOS_Business_Requirements_Document_v4.md#6-planned-enhancements--sequenced).

| Phase | Exit condition |
|---|---|
| **0 — Security and integrity** | [#51](https://github.com/gaurav-amritkar/med-os/issues/51), [#52](https://github.com/gaurav-amritkar/med-os/issues/52), [#118](https://github.com/gaurav-amritkar/med-os/issues/118), [#73](https://github.com/gaurav-amritkar/med-os/issues/73), [#110](https://github.com/gaurav-amritkar/med-os/issues/110), [#16](https://github.com/gaurav-amritkar/med-os/issues/16) closed |
| **1 — Key lifecycle** | [#88](https://github.com/gaurav-amritkar/med-os/issues/88)–[#91](https://github.com/gaurav-amritkar/med-os/issues/91), [#103](https://github.com/gaurav-amritkar/med-os/issues/103) closed; rotation rehearsed on a copy |
| **2 — Consent and DPDP** | [#116](https://github.com/gaurav-amritkar/med-os/issues/116), [#114](https://github.com/gaurav-amritkar/med-os/issues/114), [#98](https://github.com/gaurav-amritkar/med-os/issues/98), [#101](https://github.com/gaurav-amritkar/med-os/issues/101), [#102](https://github.com/gaurav-amritkar/med-os/issues/102), [#117](https://github.com/gaurav-amritkar/med-os/issues/117) closed |
| **3 — Clinical and revenue** | The Phase 3 backlog closed; invoice totals provably match intent |
| **4 — Interoperability** | CapabilityStatement published; ABDM sandbox exchange verified |
| **5 — Operability** | Metrics, logs, correlation IDs, contract drift gate |

### Open dependency on external parties

[#99](https://github.com/gaurav-amritkar/med-os/issues/99) (ABDM sandbox) and
[#100](https://github.com/gaurav-amritkar/med-os/issues/100) (HIE-CM gateway) require NHA sandbox
credentials and gateway registration. These are **not** engineering-effort risks and should be
requested before Phase 4 begins.

---

## 9. Known defects in the current build

Recorded because a requirements document that omits them is the same failure as the v3.0 document
this one replaces.

| Defect | Impact | Issue |
|---|---|---|
| Seed writes plaintext into encrypted columns | Whole patient list 500s | [#118](https://github.com/gaurav-amritkar/med-os/issues/118) |
| Invoice discount silently discarded | Financial | [#115](https://github.com/gaurav-amritkar/med-os/issues/115) |
| Invoice charge selection impossible | Billing blocked | [#115](https://github.com/gaurav-amritkar/med-os/issues/115) |
| Consent required to register | DPDP §6(2) breach | [#116](https://github.com/gaurav-amritkar/med-os/issues/116) |
| Field errors discarded by the UI | Every form | [#117](https://github.com/gaurav-amritkar/med-os/issues/117) |
| Anonymous tenant provisioning | Security | [#51](https://github.com/gaurav-amritkar/med-os/issues/51) |
| KEK rotation destroys access | Operational | [#88](https://github.com/gaurav-amritkar/med-os/issues/88) |

---

## 10. Related documents

| Document | Purpose |
|---|---|
| [BRD v4.0](MedOS_Business_Requirements_Document_v4.md) | Why the product exists, scope, metrics, risks |
| [ARCHITECTURE.md](../docs/ARCHITECTURE.md) | System structure |
| [requirements-traceability.md](../docs/requirements-traceability.md) | v3.0 requirements vs implementation |
| [operations.md](../docs/operations.md) | Runbook |
| `docs/adr/` | Nine accepted decisions |
| [Design spec](../docs/superpowers/specs/2026-10-01-pii-key-lifecycle-fhir-abdm-design.md) | PII key lifecycle, FHIR, ABDM |
| `requirements/PRD.pdf`, `requirements/MedOS_Product_Requirements_Document.html` | **Superseded v3.0, retained for provenance** |
