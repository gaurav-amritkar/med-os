# Screen 05 — Critical Alerts

**MedOS route:** `/alerts` (maps to lab panic values + vitals alarms + system alerts) · **Status:** ✅ mocked

## Purpose
Single queue for everything that can hurt a patient or the hospital right now,
with a hard acknowledgement SLA and an automatic escalation ladder.

## Layout regions
1. **Stat cards:** Open critical (red when unacked) · Unacknowledged
   (oldest age) · Escalated today · Closed today (avg ack time).
2. **Alert feed** — each row: severity icon tile, bold summary,
   meta line (source · subject · location · raised time · ack age),
   tag (Critical / Warning / Info), actions (Acknowledge / Escalate / View).
   Source filter chips on the header (All / Lab / Vitals / System).
3. **Escalation matrix card** — L1 floor → L2 HOD (10 min) → L3
   superintendent (25 min) → L4 immutable audit.
4. **Acknowledgement SLA chart** — avg + max ack minutes over 7 days,
   threshold dotted at target.

## Sources (mapped to MedOS)
- Laboratory critical/panic values (parity Lab module)
- Vitals threshold alarms from monitoring
- Facility/system alerts (oxygen lines, PACS lag) — informative severity
- Every ack/escalate/snooze is an auditable, identity-stamped event

## Interaction rules
- Acknowledge claims the alert visibly (who, when) — competing claims are
  serialized by the backend
- Escalate jumps the ladder by one level and records reason (required field)
- Snooze allowed only on Warning severity, max 3 times, each an audit event
- Age turns red past SLA; red rows sort first

## States
- Zero unacked: green "All clear" hero banner replaces the feed head
- Acked within SLA: green check meta (reward-cue, keep subtle)
