# Documentation Drift Prevention: CI Check That README Matches Source

* **Author:** MedOS team
* **Date:** 2026-10-31

## Overview

Add a CI check that verifies README documentation (table counts, module lists, package side effects, and other claims) matches the actual source code, migrations, and configuration. The 2026-10-30 drift reports found stale table counts (25 vs 30), omitted modules (appointment, enquiry), understated side effects across nearly every package README, and incorrect statements about test behavior. This brief defines an automated gate to catch drift on every pull request.

## Analysis

**Contrarian / YAGNI** — Does this feature need to exist?
Documentation is already drifting despite manual review. The drift reports show that stale docs are a recurring problem, not a one-time oversight. Manual checks before release have failed. An automated CI check is the lowest-effort way to prevent new drift. The counter-argument is that maintaining the drift-check script is itself work, and it may produce false positives that slow development. That is manageable: the script checks concrete, countable facts (table counts, module directory names, side-effect mentions), not prose quality.

**Cost, Quota & Token Economics** — What is the footprint?
A CI job that runs a script comparing README claims against source. Execution time is seconds to minutes. No infrastructure beyond existing GitHub Actions runners. No license cost.

**UX & Developer Ergonomics** — Is it intuitive?
Developers get a CI failure when a README is out of sync with code they changed. This forces documentation updates as part of the same pull request, which is the correct place for them. The friction is real: engineers must update docs with code. That is desirable, not a bug — it keeps documentation honest.

**Migration & Backward Compatibility** — Does it break anything?
No runtime changes. Adding the CI job may fail existing PRs if drift already exists. The drift reports document the current gaps; these should be fixed before enabling the gate, or a baseline should allow known drift while blocking new drift.

**Observability & SRE Debuggability** — Is it traceable and actionable?
CI pass/fail gives immediate visibility. A failure points to the specific README and the mismatched fact, making it actionable.

**Long-Term Tech Debt & Maintenance** — Will it hurt in two years?
The script must be updated when documentation structure changes (e.g., new README sections, new tables). That is expected maintenance, not technical debt. It prevents the larger debt of documentation that no longer reflects reality.

## Goals vs. Non-Goals

**In-Scope Goals**
- CI job that parses README files and compares claims against source code, migrations, and configuration.
- Verify total table count across `database/migrations/V*.sql` matches README statements.
- Verify module directories under `backend/src/main/java/com/medos/modules/` match README module lists.
- Verify package-level README `Side Effects` sections mention the actual persistence/event effects described in the drift reports.
- Fail CI build on mismatches for tracked facts.
- Allow a baseline for pre-existing drift so the build passes while new drift is blocked.

**Out-of-Scope Non-Goals**
- Auto-generating documentation from code (docs still written by humans).
- Enforcing documentation style, tone, or completeness.
- Checking external documentation (website, Notion, Confluence).
- Verifying that every code change has a doc update (too broad, high false positive rate).

## Alternatives Weighed

**Option A: CI drift check on countable facts (chosen)**
Reason: Targets the specific, recurring failures found in drift reports. Low false-positive rate because it checks concrete numbers and lists, not subjective prose. Integrated into existing GitHub Actions CI.

**Option B: Manual documentation review before release**
Reason rejected: Already failed. Drift reports show repeated missed drift across many packages. Humans are not reliable at counting tables or remembering to update every package README.

**Option C: Auto-generate all documentation from code**
Reason rejected: Loses human explanation, design rationale, and operational context. High effort, low value for narrative docs like architecture decision records and runbooks.

## Key Decisions & Staging for Next Steps

1. Track only countable, structural facts: table counts, module directory names, side-effect mentions, package README interface sections. Avoid checking prose quality or examples.
2. Fix the drift already documented in the 2026-10-30 reports before enabling the gate, or establish a baseline listing known mismatches.
3. The drift check runs as part of the existing CI workflow on every PR, not as a separate manual step.
4. Add a `CONTRIBUTING` or `README` note that documentation changes are expected alongside code changes that affect structure.
5. Next step: write the drift-check script (Python or shell), add it to CI, run against `main` to establish a baseline, then enable the fail-on-mismatch behavior.
