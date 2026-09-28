# MedOS HMS — Feature Releases: Roadmap & Improvements

Backlog of requested product features, as distinct from the infrastructure and
release-readiness work tracked in [`NEXT_STEPS.md`](../NEXT_STEPS.md).

- **Source:** feature requests from the product owner.
- **Verified against:** `main` @ `417cf0b` (28 Sep 2026).
- **Status of every "current state" note below:** read from the code, not
  assumed. Where a claim was cheap to test at runtime, it was tested — that is
  called out explicitly.

---

## Summary

| # | Requested feature | Verified state | Release |
|---|---|---|---|
| 1 | Landing page, alongside login | **Missing** | R2 |
| 2 | Show tenant name after login | **Missing** (data exists, never sent) | R1 |
| 3 | Add staff accounts (doctor, nurse, pharmacist…) | **Missing** (no create path at all) | R1 |
| 4 | Printable receipt with tenant, unique id, patient details | **Missing** (ids exist, no print) | R2 |
| 5 | Configurable GST, gov-rule-proof | **Partial** (hardcoded rates) | R3 |
| 6 | Add wards and rooms | **Partial** (rooms read-only, ward is a string) | R1 |
| 7 | Tenant admin should have all access | **Already true** (confirm intent) | R1 |
| 8 | Payment gateway in billing | **Missing** (manual ledger only) | R4 |
| 9 | Super admin: list / edit / deboard / lock tenants | **Missing** (service code exists, unreachable) | R1 |
| 10 | Tenant admin must not onboard tenants | **Missing — open P0 vulnerability** | R1 |

Two security defects were found while verifying this list. They are not feature
work and are not scheduled behind anything else.

---

## Security findings — fix before the feature work

### S1. Any anonymous caller can create a tenant with full admin rights (P0)

Requested as item 10, and it is worse than "a tenant admin should not be able to
onboard". The endpoint is **public**.

- `SecurityConfig.java:52` lists the path as permitted:
  ```java
  .requestMatchers("/api/v1/auth/**", "/api/v1/onboarding/register").permitAll()
  ```
- `OnboardingController.java:27` is a bare `@PostMapping` — no `@PreAuthorize`
  on the method, none on the class.
- `TenantService.createTenant` takes no actor and inspects no role, so the
  service is not a second line of defence.

The endpoint creates a tenant, a user, and an `admin` membership from the request
body, so an unauthenticated caller provisions a fully-administered hospital. The
only real limits are data constraints: a unique slug and a unique username.

It is not rate limited either — `LoginRateLimiter` is wired into
`AuthService.login` only. Tenant creation is also unaudited; `AuditLogger` is
used by billing, payment and admissions but not here.

**This is already known and specified.** ADR-0003 (`docs/adr/0003-gated-self-serve-onboarding.md:8`)
describes exactly this state and prescribes the fix: a `PENDING → ACTIVE`
lifecycle, email verification before any token is issued, captcha, Redis
rate limits, and a `medos.onboarding.public-signup` flag defaulting to `false`.
The ADR is Accepted; **none of it is implemented.** This roadmap does not
supersede it — it sequences the same work.

**Dependency:** the ADR's answer is to gate signup behind a super-admin role,
which does not exist yet (item 9). Fixing the hole without shipping a
replacement first means turning self-serve onboarding off entirely. Sequence
item 9 before closing S1, or ship the `public-signup=false` flag as an
interim measure.

### S2. `GET /api/v1/users` returns users from every tenant (P0)

Found while verifying item 3, not requested. **Confirmed at runtime**, not just
by reading:

```
GET /api/v1/users as the "MedOS Hospital" admin  ->  2 users
    admin     | admin@medos.local
    myclinic  | myclinic@email.com     <-- belongs to the "My Clinic" tenant
```

`DashboardController.java:80-84` returns `userRepository.findByActiveTrue()`.
`User` is not `TenantOwned` and has no `tenant_id`, and read scoping is applied
by `TenantStatementInspector` only to tenant-owned tables, so no predicate is
added. A tenant admin sees every other tenant's usernames and emails.

This blocks item 3: a staff-management screen built on this endpoint would
inherit the leak. Fix it first, then build on it.

### S3. `Tenant.active` is never enforced (P1, and it makes item 9 a trap)

`AuthService.login` checks `user.getActive()` (`AuthService.java:48`) and then
goes straight to the tenant lookup at line 62. It **never checks the tenant's
own `active` flag**, and neither does `JwtAuthenticationFilter`.

So today, deactivating a tenant is a cosmetic database flag with **no effect on
access** — its staff can still sign in and work. Item 9 asks for the ability to
deboard a tenant. Shipping that button without the login check would produce an
admin console that reports a tenant as disabled while that tenant keeps working.
Enforce the flag in the same change.

---

## R1 — Foundations: identity, tenancy, beds

Unblocks the most downstream work and carries both security fixes. Items 2, 3,
6, 9, 10 plus S1–S3.

Sequencing inside R1 matters: **9 before 10** (the role must exist to guard the
endpoint), and **S2 before 3** (don't build a staff screen on a leaking query).

### Item 9 — Super admin: list, edit, deboard, lock tenants

Requested: a super admin who can see all tenants, edit them, deboard them, and
lock their access.

There is **no super-admin role**. `TenantUser.UserRole` has six values — `admin,
doctor, nurse, receptionist, pharmacist, billing` (`TenantUser.java:31-33`) —
and the DB `CHECK` constraint agrees (`V1__initial_schema.sql:53`). What the
codebase calls "super admin" today is the *absence of a tenant claim in the
JWT*, which is a state, not a role.

There is **no `TenantController`**. The capability largely exists as unreachable
service code:

| Capability | `TenantService` | HTTP endpoint | Callers |
|---|---|---|---|
| list all tenants | `getAllTenants()` :40 | none | **none** |
| get one | `getTenant()` :44 | internal | internal |
| activate | `activateTenant()` :48 | none | **none** |
| deactivate | `deactivateTenant()` :54 | none | **none** |
| tenant config | `getConfig`/`setConfig` :60/:67 | none | onboarding only |
| assign a role | `assignUserToTenant()` :75 | none | onboarding only |
| a user's tenants | `getUserTenants()` :85 | none | **none** |

Supported today: **create only**, and only because of S1. List, edit, deactivate
and lock are all absent. `deactivateTenant` is a one-liner that works at the
database level but has no access effect (S3). `Tenant` has a single
`active BOOLEAN` — nullable, no default — and no `suspended`, `locked` or
`deleted_at`.

**Scope:** add `super_admin` to the role enum **and** the DB `CHECK` constraint
in a new `V3__` migration (V1 is frozen — `V1__initial_schema.sql:16-17`);
add a `TenantController` guarded by `@PreAuthorize("hasRole('SUPER_ADMIN')")`;
add the missing `updateTenant`; replace the `active` boolean with a real status
(`PENDING → ACTIVE → SUSPENDED`) exactly as ADR-0003 already specifies; enforce
it in `AuthService.login`; add a `TenantList.jsx` page, a sidebar section, and
an admin-only route.

**Decision needed:** ADR-0003 reserves `SUSPENDED` but does not define
reactivation or data retention on permanent deletion. Hard delete is
destructive for a system holding patient records — recommend deactivate plus a
`SUSPENDED` state, and treat hard delete as out of scope pending a data-retention
decision.

### Item 10 — Tenant admin must not onboard tenants

Requested: a tenant admin may add doctors, nurses and others, but must not
create tenants.

Blocked behind item 9, because the correct guard is a `super_admin` role that
does not exist yet. Interim option if the hole must close sooner: drop
`/api/v1/onboarding/register` from `permitAll` and default
`medos.onboarding.public-signup` to `false`, accepting that self-serve
onboarding is off until super admin exists.

**Scope beyond the guard:** once staff management (item 3) ships, remove
`/onboarding` from the admin nav (`Sidebar.jsx:13`) and its route
(`App.jsx:33`), and add the endpoint to the RBAC matrix test, which currently
has **no row for users, tenants, or onboarding** (`RbacMatrixTest.java:32-56`).

### Item 3 — Add staff accounts (doctor, nurse, pharmacist, …)

Requested: after onboarding creates the admin, there must be a way to add the
rest of the team.

**There is no way to create a user except at onboarding.** There is no
`UserController` in the project at all. The only user endpoints are in
`DashboardController`: `GET /users/me` and an admin-only, read-only
`GET /users` — which is the S2 leak, and which the frontend does not even call.
`UserRepository` has no write method beyond the inherited `save`.

The only non-test creator is onboarding, hardcoded to the `admin` role
(`OnboardingController.java:38-48`). `AdminBootstrapRunner` is a first-boot
seeder, also always `admin`.

All six roles already exist in the enum and the DB constraint, so the data model
needs no change — the gap is purely the API and the UI.

**Scope:** new `UserController`; a `UserService` that owns password encoding
(today only the onboarding controller and the bootstrap runner hold a
`PasswordEncoder`); `CreateUserRequest` / `UserDTO`; list, create, deactivate
and change-role endpoints, all tenant-scoped; a `Users.jsx` page with a route
and sidebar entry gated to `['ADMIN']`.

**Decide:** invite-and-set-password versus admin-set-initial-password versus
email verification. For clinical staff, admin-set-initial-password with a forced
change on first login is the least friction; email verification is the safer
default but needs a mailer that the project does not have yet.

### Item 2 — Show the tenant name after login

Requested: a signed-in user should see which tenant they are in.

The data exists and is never sent. `Tenant.name` is on the entity
(`Tenant.java:21`) and in the schema (`V1__initial_schema.sql:31`), and
`AuthService` already has the `Tenant` object in hand at
`AuthService.java:65` — it reads `.getId()` and discards `.getName()`.
`LoginResponse` carries `tenantId` only (`LoginResponse.java:10-19`), the auth
store persists only the id (`authStore.js:84`), and `Header.jsx:21-26` renders
just the user's name and role.

**Scope:** add `tenantName` to `LoginResponse`, populate it, persist it in the
store, render it in the header. Small and self-contained.

**Note:** this is a prerequisite for item 4 — a receipt must carry the tenant
name, and the billing page has no access to it today.

### Item 6 — Add wards and rooms

Requested: a way to add wards and rooms.

Rooms exist and are **read-only**. `AdmissionController` has exactly two
room endpoints, both `GET` (`/rooms`, `/rooms/available`); there is no POST, PUT
or DELETE. `AdmissionService` only ever *reads* rooms, plus flipping
`occupied` on admit and discharge. So bed inventory cannot be created through
the API at all — it must be inserted by hand.

The `Room` entity is rich enough to seed already: `roomNumber` (unique), `ward`,
`roomType`, `dailyRate`, `capacity`, `occupied`, `floor`, `notes`
(`Room.java:31-53`).

**"Ward" is not a concept.** `ward` is a free-text `VARCHAR(64) NOT NULL` with
no foreign key, and the only lookup is `findByWard(String)`
(`RoomRepository.java:18`). So `ICU`, `icu` and `I.C.U.` are three different
wards. The `roomType` enum (`general, semi_private, private_room, icu, nicu,
operation`) is the axis that actually carries meaning.

`Admissions.jsx` is titled "IPD / Wards" and renders a read-only occupancy grid
with an admit modal — no add, no edit, no grouping.

**Scope:** room create/update/delete endpoints guarded to `ADMIN` (nobody else
should create beds; the RBAC test at `RbacMatrixTest.java:326-332` currently
treats rooms as readable by every role), plus a manage-rooms UI.

**Decision needed:** a real `Ward` entity with a `wards` table and an FK from
`rooms.ward` (new `V3__` migration), or keep `ward` as free text and add a
normalised lookup list. A `Ward` entity is the right call if wards will ever
carry their own attributes — tariff, staffing, occupancy limits — which for an
IPD module is likely.

**Drive-by fix:** `Admissions.jsx:78` keys the colour map on `private`, but the
Java enum value is `private_room` (`Room.java:67`), so private rooms silently
render with the default colour.

---

## R2 — Public surface and patient-facing documents

Items 1 and 4. Both are visible to patients and reception, and both need
item 2 first.

### Item 1 — Landing page, alongside login

Requested: a landing page together with the login screen.

Today there is no landing page. Only two routes are public: `/login` and
`/onboarding` (`App.jsx:31-52`); everything else is nested under
`<ProtectedRoute>`, and an unauthenticated hit on `/` redirects straight to
`/login` (`ProtectedRoute.jsx:8`, `App.jsx:16-20`).

**Scope:** a public route and page, with `Root()` redirecting there instead of
to `/login`, and a signed-in user still going to `/dashboard`. Design it against
the Wave 0 token layer (`docs/superpowers/specs/2026-09-27-medos-ui-ux-redesign-design.md`)
rather than inventing new tokens. Keep it honest about what the product is: a
clinical HMS, and the security posture is worth stating plainly given S1.

### Item 4 — Printable receipt

Requested: something printable in billing, carrying the tenant name, a unique id
per receipt, and the patient details.

There is no print capability of any kind: no `window.print()`, no `@media print`
rule anywhere in the stylesheets, no PDF library in `package.json`
(`react`, `react-dom`, `react-router-dom`, `recharts`, `zustand`, `axios`, two
IBM Plex font packages — that is all), and no receipt endpoint.

The good news: **the unique identifiers already exist and are already exposed.**
`Invoice.invoiceNumber` and `Payment.paymentNumber` are `UNIQUE` in the schema
and reach the client today. So does `Invoice.gstTotal` and per-charge
`gstPercent` / `gstAmount`.

What is missing for a compliant receipt: patient name on the document, tenant
name, tenant address, tenant GSTIN, the line items, and a CGST/SGST split.
`InvoiceDTO` has none of these, and the billing endpoints currently return raw
entities rather than DTOs.

Also worth fixing while in there: `billingApi.getInvoiceCharges` exists in the
client and is never called by `Billing.jsx`, so the line items are already
fetchable but never displayed.

**Decision needed:** browser print against a print stylesheet (no new
dependency, works offline, output quality depends on the user's printer) versus
a server-rendered PDF (an endpoint plus a library, consistent output, and it
works for emailed copies). For a hospital that hands receipts to patients at the
counter, browser print is usually enough to start; PDF becomes necessary the
moment receipts are emailed or archived.

---

## R3 — Tax compliance

### Item 5 — Configurable GST

Requested: GST should be present in billing, configurable, and able to follow
government rule changes.

GST is **partly** in place and **entirely hardcoded**. The rates live as
`static final` constants in `MoneyUtil.java:36-39` — 5% medicines, 12% rooms,
18% services, 0% exempt — selected by a switch in
`MoneyUtil.getGstRateForChargeType` (`MoneyUtil.java:188-196`).

Three separate problems:

1. **That switch has no callers.** The only rate actually applied to a
   persisted charge is pharmacy (`DispenseService.java:105`, `:120`). Room
   charges are created with explicit **zero** GST (`AdmissionService.java:112-114`),
   so the 12% room rate is dead code. Consultation, lab, procedure and misc
   charges have no producer at all.
2. **Nothing is configurable.** A `static final` cannot be injected or
   overridden, and there is no `gst` key in `application.yml`. Per-tenant
   key-value storage already exists (`TenantService.getConfig`/`setConfig` over
   `tenant_configs`), but **nothing reads a config key anywhere** — the only
   caller writes whatever keys the onboarding body contains. The plumbing exists;
   billing does not use it.
3. **No GSTIN, no HSN, no intra-state split.** There is no `gstin` or
   `hsn_code` field on `Tenant`, `Charge` or `Invoice` anywhere in the schema or
   the code. `invoices` has a single `gst_total` aggregate, with no per-line tax
   breakup and no CGST/SGST columns.

The UI is partial too: `Billing.jsx` shows a per-charge `GST%` column, but the
invoice list has no GST column, while the page subtitle claims "GST invoices"
and the modal promises a "GST-compliant invoice".

**Scope:** move rates into injected configuration seeded from
`tenant_configs` so a rate change needs no redeploy; wire
`getGstRateForChargeType` into every charge producer; add `gstin` to `tenants`
and `hsn_code` to `medicine_catalog` in a new `V3__` migration; add a
CGST/SGST split and per-line tax to invoices; surface GST on the invoice screen.

**Note on the "if govt changes the rules" requirement:** with rates in
`tenant_configs` and a per-line breakdown, a rule change becomes a data edit
rather than a release. That is the actual goal — worth stating so the schema is
not designed around today's three constants.

---

## R4 — Payments

### Item 8 — Payment gateway

Requested: a payment gateway integrated into billing.

There is **no integration of any kind** — no gateway SDK, no config key, no
webhook endpoint, no callback route, in either backend or frontend. The only
mentions anywhere are aspirational lines in `docs/production-readiness-plan.md`.

What exists is a **manual ledger**. `Payment.PaymentMethod` allows `cash, card,
upi, netbanking, insurance, cheque`, but `card` and `upi` are labels, not
integrations: `PaymentService.java:77` parses the string and records it. Nothing
verifies that a card was swiped, and `transactionRef` is free text an operator
types in. `LoginRateLimiter`-class controls do not apply.

The foundation is genuinely good, which matters here: the payment path takes a
pessimistic row lock preventing double-spend (`PaymentService.java:49`), rejects
overpayment (`:66-69`), recalculates invoice status (`:87-102`), and
`Payment.Status` already has `pending` and `failed` values even though
`processPayment` only ever sets `success`.

**Scope:** a `PaymentGateway` interface with one provider implementation, a
webhook controller, a pending → confirmed payment state using the status values
that already exist, and reconciliation on webhook receipt.

**Two things to settle before building:**

- **The webhook has no tenant.** It would arrive unauthenticated, while
  `TenantStatementInspector` and `IdempotencyFilter` both assume a JWT tenant
  claim. A `permitAll` matcher for the webhook path has to be added, and tenant
  resolution for that request worked out explicitly, or writes will be unscoped.
- **Provider choice** (Razorpay, Cashfree, PayU, Stripe) is a business
  decision, not a technical one, and it constrains settlement, refund and
  reconciliation design.

---

## Item 7 — Tenant admin should have all access

Requested: the admin account should have every permission.

**This is already true**, and the audit confirms it rather than assuming it.
`admin` appears in every `@PreAuthorize` clause across patients, encounters,
pharmacy, admissions and billing, and additionally reaches `GET /users`,
actuator `/manage/**`, and the Swagger UI. `App.jsx:39-44` gates routes by role
and `Sidebar.jsx:6-14` gives admin every nav item. The RBAC matrix test
documents admin as the only role with a checkmark in every row
(`RbacMatrixTest.java:32-56`).

So this item needs no implementation. It does need **one correction and two
caveats**:

- **Correct the premise slightly:** admin having all access is precisely what
  makes items 9 and 10 necessary. The gap is not that admin is too weak, it is
  that there is no role *above* admin. Item 9 introduces it.
- **The matrix is silent where it matters.** It covers 7 clinical modules and has
  no row for `/users`, tenants, or onboarding — the exact endpoints items 3, 9
  and 10 turn on. Its helper is also lenient: denied roles may return 403 **or
  400**, on the theory that validation runs before authorization
  (`RbacMatrixTest.java:418`). A 400 does not prove denial.
- **There is no permission model.** RBAC is role strings in annotations, with no
  authorities or permission constants. Fine at six roles; worth revisiting if
  item 7 grows into custom roles later.

**Recommendation:** treat item 7 as satisfied. Spend the effort on extending the
RBAC matrix to cover the endpoints items 3, 9 and 10 introduce.

---

## Decisions needed

| # | Decision | Blocks | Why it cannot be assumed |
|---|---|---|---|
| D1 | Gateway provider, and how tenant is resolved on a webhook | item 8 | Business choice; also a security design question |
| D2 | Staff provisioning: admin-set password vs email verification | item 3 | Needs a mailer the project does not have |
| D3 | Wards as a real entity vs a normalised lookup list | item 6 | Depends on whether wards gain their own attributes |
| D4 | Receipt: browser print vs server-rendered PDF | item 4 | Depends on whether receipts are emailed or archived |
| D5 | Tenant lifecycle: hard delete vs deactivate + SUSPENDED | item 9 | Hard delete is destructive for patient records |
| D6 | Is self-serve onboarding retained at all, or super-admin-only? | items 9, 10, S1 | Changes the public sign-up posture of the product |

---

## Verification method

Every "verified state" above was read from the code at `417cf0b`. Two claims
were additionally executed at runtime against the local stack rather than
inferred from structure:

- **S2** — `GET /api/v1/users` was called as the "MedOS Hospital" tenant admin
  and returned a user belonging to the "My Clinic" tenant, with its email.
- **Item 5** — GST was traced from `MoneyUtil`'s constants through each charge
  producer to confirm which rates are actually applied, rather than assuming the
  switch is wired up.

One related defect was noted but not confirmed at runtime: `Admissions.jsx:78`
keys its colour map on `private` while the Java enum value is `private_room`
(`Room.java:67`). The mismatch is visible in the source; the visual effect was
not checked in a browser.
