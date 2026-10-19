# ADR-0010: Product module scope — parity with the reference HMS

- **Status:** Accepted
- **Date:** 2026-10-19
- **Supersedes:** nothing. **Corrects a mis-citation in** `PRODUCT.md`.

## Context

`PRODUCT.md` carried this line:

> Out of scope per ADR-0006: native mobile apps, insurance claim adjudication,
> laboratory analysers, payroll, multi-country tax engines.

**That citation is wrong, and it is load-bearing.** ADR-0006 is titled *Explicit
non-goals for v1* and its entire content is infrastructure: Kubernetes, Helm, an API
gateway and WAF, a Redis cluster, PostgreSQL read replicas, distributed tracing, ELK,
message queues, Postgres row-level security, feature flags, and internationalization.
It does not mention payroll, insurance, laboratory analysers or tax engines.

No ADR in `docs/adr/` records a product-module exclusion. Grepping all ten ADRs for
`payroll|insurance|claim adjudication|laboratory analyser` returns nothing. So at the
moment a decision to exclude three product modules rests on a citation that does not
say what it is cited for.

Meanwhile the product is being scoped against a reference HMS — see
`docs/superpowers/specs/2026-10-19-medworks-parity-design.md` — which ships
**Insurance & TPA claims, a Laboratory module, and HR & Staff Payroll**. All three were
excluded. They are now in scope by decision.

## Decision

**MedOS will reach functional parity with the reference HMS across all 27 modules**,
built on MedOS's existing substrate: structural tenant isolation, per-tenant DEK
encryption of PII, DPDP consent, and an auditable trail.

Explicitly **in scope**:

- Insurance & TPA — policies, pre-authorisation, claims tracking, TPA payer master
- Laboratory — test master, order queue, sample accessioning, results, rejections,
  critical/panic values
- HR & Staff Payroll — employee directory, duty rosters, attendance, leave, monthly payroll
- Radiology PACS, Blood Bank, Operation Theatre, Procurement & GRN, Central Inventory,
  Accounts & Ledger (double-entry), Document Vault, Complaints & Grievances,
  Patient Feedback — all in scope per the parity spec

Explicitly **still out of scope**:

- **Native mobile apps.** Unchanged. The web application is the product.
- **Multi-country tax engines.** Unchanged. CGST/HSN only.
- **Laboratory analyser instrument drivers.** In scope is the *laboratory information
  system* — orders, samples, results, critical values. Direct instrument integration
  is out, and is a separate decision if it is ever wanted.
- **Insurance claim *adjudication automation*.** In scope is policy holding,
  pre-authorisation tracking and claim submission lifecycle. Automated adjudication
  against insurer rules engines is out.

**Parity means the module surface and workflow depth, not the reference's
assumptions.** Specifically not copied: public self-signup (ADR-0003 gates it),
plaintext PII, the absence of consent, and single-hospital identity.

### Consequences

- The exclusions in `PRODUCT.md` are replaced by the accurate list above, and the
  ADR-0006 citation is corrected.
- `PRODUCT.md` Principle 1 is preserved: every one of the **109 new tables** in the
  parity spec's §4.1 is tenant-scoped, and new PII columns join the envelope
  encryption scheme. Parity in module count does not weaken the substrate claim.
- Payroll carries an obligation the reference does not surface: Indian statutory
  deductions (PF, ESI, TDS). Those requirements must be specified before the payroll
  wave is implemented — a scope decision does not settle them.
- The size of the change means the pilot milestone in `PRODUCT.md` and the parity
  programme now compete for the same capacity. `NEXT_STEPS.md` records P0 release
  readiness as blocking any production deploy; those gates stay ahead of parity work
  and are not traded against it.
- Scope is stated per module in the parity spec's §4.1. Adding a module beyond that
  list is a new decision requiring a new ADR.