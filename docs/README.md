# MedOS — Documentation Index

**`docs/` is the single home for all project documentation.** The only
exception is the root `README.md` (runbook/quickstart — convention requires it
at the repo root, GitHub renders it, and every tool expects it there).

> Status values are **verified against the code on 2026-10-22**, not against the
> documents themselves — several older docs had drifted; anything stale is
> marked here or carries a correction banner inside the doc.

## Start here

| Doc | Purpose | Status |
|---|---|---|
| [../README.md](../README.md) | Architecture, runbook, API surface, security posture | ✅ Current (updated 2026-10-22) |
| [roadmap.md](roadmap.md) | **What is built vs pending** — milestones, testing gates, design-programme statuses, suggested working order | ✅ Current (updated 2026-10-22) |
| [product.md](product.md) | Product definition: users, principles, capabilities, constraints, brand | ✅ Current (ADR-0010 correction applied) |

## Requirements

| Doc | Purpose | Status |
|---|---|---|
| [requirements/MedOS_Product_Requirements_Document_v4.md](requirements/MedOS_Product_Requirements_Document_v4.md) | PRD v4 — code-verified system truth | ✅ Current (2026-10-02) |
| [requirements/MedOS_Business_Requirements_Document_v4.md](requirements/MedOS_Business_Requirements_Document_v4.md) | BRD v4 | ✅ Current (2026-10-02) |
| [requirements/BRD.pdf](requirements/BRD.pdf) · [requirements/PRD.pdf](requirements/PRD.pdf) + HTML pair | Superseded v3.0 sources — **retained unmodified for provenance only**; describe a Node/Express/SQLite single-tenant product that was never this codebase | 🗄 Provenance |
| [requirements-traceability.md](requirements-traceability.md) | v3.0 requirements vs implementation, row by row | 🟡 References superseded v3.0 as primary — read alongside v4 |

## Architecture

| Doc | Purpose | Status |
|---|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Target production architecture (C4-ish narrative) | 🟡 Older — reflects the pre-hardening stack; check against README |
| [adr/](adr/) | Architecture decision records | ✅ 0001–0010 accepted; **note:** two files are numbered `0001-` — `0001-tenant-encryption-seam-deepening.md` is really a deepening proposal and should have its own number |
| [architecture-review/](architecture-review/) | Module-deepening proposals (TenantKeyHolder, blind index, ciphertext seam, key orchestration) + HTML deck | 🟡 Proposed, none started |
| [diagrams/](diagrams/) | Tenant key-resolution sequence diagram | ✅ Current |
| [glossary.md](glossary.md) | Shared codebase-design vocabulary used in ADRs and reviews | ✅ Current |
| [MEDOS.pptx](MEDOS.pptx) | Presentation material | 🗄 As-of Oct 2026 |

## Operations & process

| Doc | Purpose | Status |
|---|---|---|
| [operations.md](operations.md) | Runbook — backups, key rotation (§7d), etc. | ✅ Current |
| [branch-protection.md](branch-protection.md) | Branch protection setup | ✅ Enforced |
| [feature-releases.md](feature-releases.md) | Product-owner feature backlog (landing page, receipts, GST, wards, payment gateway…) | 🟡 Partially stale — three security findings closed; items still open are valid |

## Programme reviews (dated, mostly historical)

Read these as point-in-time analyses; the live state is always [roadmap.md](roadmap.md).

| Doc | Date | Status |
|---|---|---|
| [production-readiness.md](production-readiness.md) | 2026-09-22, corrected 12 Oct | 🟡 History with corrections |
| [production-readiness-review-2026-10-12.md](production-readiness-review-2026-10-12.md) | 2026-10-12 | 🟡 History; three rows closed by #133 |
| [production-readiness-plan.md](production-readiness-plan.md) | 2026-09-27 | 🗄 Superseded plan (Hermes-targeted); its Phase 1 issues are all closed |
| [gap-analysis-2026-10-13.md](gap-analysis-2026-10-13.md) | 2026-10-13 | 🟡 G01–G05 fixed same-day; rest re-verify before acting |

## Design specs & implementation plans (`superpowers/`)

| Doc | Date | Status |
|---|---|---|
| [superpowers/specs/2026-10-19-medworks-parity-design.md](superpowers/specs/2026-10-19-medworks-parity-design.md) | 2026-10-19 | 🟢 **Active programme** — parity Wave 0 is the next implementation target. See [roadmap.md §Design programmes](roadmap.md) |
| [superpowers/specs/2026-09-27-medos-ui-ux-redesign-design.md](superpowers/specs/2026-09-27-medos-ui-ux-redesign-design.md) | 2026-09-27 | ✅ Foundation shipped (#170); page waves continue opportunistically |
| [superpowers/specs/2026-09-26-medos-production-release-design.md](superpowers/specs/2026-09-26-medos-production-release-design.md) | 2026-09-26 | 🗄 Superseded by the hardening work that shipped |
| [superpowers/specs/2026-10-01-pii-key-lifecycle-fhir-abdm-design.md](superpowers/specs/2026-10-01-pii-key-lifecycle-fhir-abdm-design.md) | 2026-10-01 | 🟡 PII lifecycle shipped; FHIR/ABDM not started (#92+) |
| [superpowers/specs/2026-10-04-tenant-key-resolver-design.md](superpowers/specs/2026-10-04-tenant-key-resolver-design.md) | 2026-10-04 | 🟡 Proposed — pair with DEK-rotation work |
| [superpowers/plans/…stage0…](superpowers/plans/2026-09-26-medos-stage0-baseline-validation.md) · […ui-wave0…](superpowers/plans/2026-09-27-medos-ui-wave0-foundation.md) | 2026-09 | ✅ Executed; assets merged |

## Evidence artefacts

| Doc | Purpose | Status |
|---|---|---|
| [ui-snapshots/INDEX.md](ui-snapshots/INDEX.md) | 61 screenshots across 10 areas, captured by walking flows (18 Oct 2026) | ✅ Current |
| [archive/](archive/) | Historical readiness verdicts, deploy steps, refactoring reports, old Hermes plans, the 2026-09-27 API test report | 🗄 History — read-only |
