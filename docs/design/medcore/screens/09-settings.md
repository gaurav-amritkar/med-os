# Screen 09 — Settings

**MedOS route:** `/settings` (reassembles existing settings + hospital config) · **Status:** ✅ mocked

## Purpose
Profile visibility, hospital baseline config, and the notification/security
posture of the logged-in role.

## Layout regions
Left sub-nav (local sections) + stacked section cards on the right:

1. **Profile** — name/role read-only (managed by admin), email + OTP-bound
   mobile editable. Save/Discard actions; dirty-field highlighting.
2. **Hospital** — name, city, bed count, time zone (`Asia/Kolkata`).
   Facility-editable only for admin role.
3. **Notifications** — switches: Critical alerts (push + SMS after 2 min
   unacked), Forecast threshold crossings (email on warning band entry),
   Daily occupancy digest (07:00 summary, off by default).
4. **Security** — 2FA (required for superintendent/admin), session timeout
   (15 min idle on shared terminals), destructive-action confirmation
   (type-the-identifier pattern per the MedOS design system).

## Data sources (mapped to MedOS)
- User profile + role claims (subjects/profiles API)
- Tenant configuration (per hospital; tenant-scoped)
- Notification preferences persisted per user

## Interaction rules
- Readonly fields explain why (hint text), not silently disabled
- Every security toggle records intent in audit trail
- Forecast thresholds section edits the thresholds consumed by Screen 06

## States
- Admin viewing a non-admin's profile sections: read-only with banner
  "You are viewing another user's profile"
