# MedCore sample screens (docs/design/medcore)

Static design mockups for the hospital management application, based on the
Dribbble shot
[**Medcore Control Center Dashboard** by Sayem](https://dribbble.com/shots/27345309-Medcore-Control-Center-Dashboard).

The shot contains one full screen (Capacity Forecasts) plus a tablet mockup of
the same screen; its sidebar defines the rest of the application map. This
folder completes that map: **nine sample screens**, each a standalone HTML
mockup with realistic sample data, plus a short companion spec.

## How to view

- Open [`index.html`](./index.html) — it links every mockup.
- Mockups are plain HTML/CSS/SVG with no network dependencies; they work
  offline from `file://`.
- Reference screenshots from the Dribbble shot:
  [main screen](./reference/medcore-dribbble-1.jpg) · [tablet mockup](./reference/medcore-dribbble-2.jpg)

## Screens

| # | Screen | Mockup | Spec | Notes |
|---|---|---|---|---|
| 01 | Dashboard | [mockup](./mockups/01-dashboard.html) | [spec](./screens/01-dashboard.md) | KPIs, trends, department load, activity feed |
| 02 | Bed & Wards | [mockup](./mockups/02-bed-and-wards.html) | [spec](./screens/02-bed-and-wards.md) | bed map, housekeeping, transfers |
| 03 | Emergency Flow | [mockup](./mockups/03-emergency-flow.html) | [spec](./screens/03-emergency-flow.md) | triage funnel, timers, ambulance bay |
| 04 | Staff Management | [mockup](./mockups/04-staff-management.html) | [spec](./screens/04-staff-management.md) | roster, 7-day coverage, leave gaps |
| 05 | Critical Alerts | [mockup](./mockups/05-critical-alerts.html) | [spec](./screens/05-critical-alerts.md) | ack/escalate feed, SLA trend |
| 06 | Capacity Forecasts | [mockup](./mockups/06-capacity-forecasts.html) | [spec](./screens/06-capacity-forecasts.md) | faithful recreation of the shot |
| 07 | Operational Reports | [mockup](./mockups/07-operational-reports.html) | [spec](./screens/07-operational-reports.md) | report library, run history |
| 08 | Help & Supports | [mockup](./mockups/08-help-supports.html) | [spec](./screens/08-help-supports.md) | FAQ, docs, support contact |
| 09 | Settings | [mockup](./mockups/09-settings.html) | [spec](./screens/09-settings.md) | profile, hospital, notifications, security |

## Design language (captured where referenced)

- Light canvas `#f2f2f3`, white content card, hairline borders, 16–20px radii
- Brand orange `#f5a623` (logo, forecast series, accents); blue `#4a9f5`-family
  for data bars; status colors: red `#ef4444`, orange `#f59e0b`, green `#10b981`
- Sidebar with grouped nav: *Main Menu / Monitoring & Control / Analytics & Reports*,
  `⌘K` search pill, bell + avatar in the page header
- Charts drawn as inline SVG (`assets/charts.js`) — forecast lines with dotted
  Critical/Warning/Normal threshold rails, hatched declining bar plans, smooth
  multi-series lines

## Status

Mockups and specs are **design artefacts only** — no product code has changed.
Status per screen: `planned → mocked → implemented`.

| Status | Meaning |
|---|---|
| ✅ mocked | HTML mockup + spec committed (all nine screens) |
| ⬜ implemented | React screen wired to live MedOS backend |

## Implementation rules (agreed)

When these screens are implemented one by one in `frontend/`:

1. Map this visual language onto the existing MedOS token system
   (see `docs/superpowers/specs/2026-09-27-medos-ui-ux-redesign-design.md`,
   IBM Plex fonts, WCAG 2.2 AA).
2. The brand orange is decorative/brand — it may not carry meaning alone
   (WCAG 1.4.1) and never renders body-size text on white.
3. All status indicators pair a word with a colour, as in these mockups.
4. Sample data shown here is illustrative; real data comes from the backend
   with tenant-scoped, PII-envelope-encrypted fields.
