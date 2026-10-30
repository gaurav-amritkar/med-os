# Screen 08 — Help & Supports

**MedOS route:** `/help` · **Status:** ✅ mocked

## Purpose
Self-service help: searchable guides and policy explainers, plus a clear path
to the in-house helpdesk when self-service fails.

## Layout regions
1. **Search hero** — big centered prompt "How can we help?" + search input
   over guides, shortcuts, and policies.
2. **FAQ accordions** (`<details>`) — the sample covers: assigning a bed,
   forecast threshold meaning, alert acknowledgement permissions, PII
   encryption, lab panic-value escalation.
3. **Documentation card** — quick links: duty-nurse quick start, bed playbook,
   escalation SOP, keyboard shortcuts (⌘K), data-protection/DPDP guide.
4. **Contact support card** — helpdesk ext. (24×7), email with SLA,
   ticket routing to on-call engineer + "Open support request" button,
   systems-status dot.

## Content rules
- Every answer states the *why* where safety-relevant (audit trail, consent)
- FAQs are tenant-configurable (hospital can add local SOPs)
- Search covers FAQ bodies + doc titles, highlights matches

## States
- Zero results: offers "Search the documentation" + contact support CTA
- Status dot red: support banner warns of degraded response SLAs
