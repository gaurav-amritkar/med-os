# CI Security Scanning: Dependency, SAST, and Secret Scanning

* **Author:** MedOS team
* **Date:** 2026-10-31

## Overview

Add dependency scanning, static application security testing (SAST), and committed-secret scanning to CI, in addition to the existing Trivy container image vulnerability scan. The goal is to catch vulnerable libraries, insecure code patterns, and accidentally committed credentials before they reach production.

## Analysis

**Contrarian / YAGNI** — Does this feature need to exist?
CI already scans container images with Trivy, but that covers OS packages and runtime dependencies only. It does not scan application source code for SQL injection, insecure cryptography, or hardcoded keys. It does not flag a vulnerable Maven dependency (e.g., a vulnerable Spring Boot version) or a React npm package with a known CVE. It does not prevent someone from committing `DB_PASSWORD` or `PII_ENCRYPTION_KEY` to the repository. For a healthcare system handling patient data, these gaps are unacceptable. The counter-argument is developer friction: scanning can produce false positives that block PRs. That is manageable with tuning and baselines.

**Cost, Quota & Token Economics** — What is the footprint?
GitHub Actions provides Dependabot, CodeQL (SAST), secret scanning, and dependency review at no additional cost for public repositories and at low cost for private ones. Scanning adds minutes to CI runtime, not hours. No per-scan fees for these native tools.

**UX & Developer Ergonomics** — Is it intuitive?
Developers see scanning results as PR comments or CI failures. Dependabot creates pull requests to upgrade vulnerable dependencies. Secret scanning alerts on committed secrets before they are merged. The friction point is triage: someone must review findings and decide which are real issues and which are false positives. Without an owner, scanning becomes noise that gets ignored.

**Migration & Backward Compatibility** — Does it break anything?
No runtime changes. Adding scanning workflows may fail existing PRs if secrets or vulnerable dependencies are already present. A baseline may be needed to ignore pre-existing findings so the build can pass while new issues are blocked.

**Observability & SRE Debuggability** — Is it traceable and actionable?
CI job pass/fail status and scanning reports provide visibility. Secret scanning alerts go to configured repository administrators or security contacts. Dependabot alerts appear in the repository's Security tab.

**Long-Term Tech Debt & Maintenance** — Will it hurt in two years?
Scanning rules and baselines need occasional tuning as dependencies and code change. Low maintenance if using GitHub's native tools, which update their rule sets automatically. The risk is alert fatigue if findings are not triaged.

## Goals vs. Non-Goals

**In-Scope Goals**
- Enable Dependabot for Maven (backend) and npm (frontend) dependency updates.
- Enable CodeQL or equivalent SAST for Java and JavaScript/TypeScript.
- Enable secret scanning for committed credentials (keys, passwords, tokens).
- Add dependency review to PRs to block vulnerable package versions from being merged.
- Fail CI build on critical or high-severity findings from any scanner.
- Establish a baseline for pre-existing findings so new code is held to a higher standard than legacy code.

**Out-of-Scope Non-Goals**
- DAST (dynamic application security testing) against a running instance.
- Penetration testing or red-team exercises.
- Software composition analysis for non-code assets (documentation, designs).
- Runtime application self-protection (RASP).

## Alternatives Weighed

**Option A: GitHub native scanning tools (chosen)**
Reason: MedOS already uses GitHub Actions for CI. Dependabot, CodeQL, and secret scanning are native, well-integrated, and free or low-cost. No new vendor or infrastructure.

**Option B: Third-party tools (Snyk, SonarCloud, Snyk Code)**
Reason rejected: Similar capability to GitHub native tools but with added cost, separate dashboards, and another integration to maintain. Could be considered later if GitHub's tools prove insufficient.

**Option C: Do nothing (status quo)**
Reason rejected: CI scans images but not dependencies, source code, or secrets. Already identified as a gap in the README.

## Key Decisions & Staging for Next Steps

1. Use GitHub Advanced Security features (Dependabot, CodeQL, secret scanning) since the repository is already on GitHub.
2. Configure CodeQL for Java (backend) and JavaScript (frontend) with the default query suites.
3. Enable secret scanning with push protection to block commits containing secrets.
4. Set up Dependabot version updates weekly and security updates immediately.
5. Create a baseline for any pre-existing high-severity findings so the build passes while new findings fail it.
6. Next step: add a `.github/workflows/security.yml` file and enable repository security settings; verify on a feature branch before merging to main.
