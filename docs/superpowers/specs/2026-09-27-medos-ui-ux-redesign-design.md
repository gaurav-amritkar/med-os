# MedOS UI/UX Redesign — Design

- **Date:** 2026-09-27
- **Status:** Draft for review
- **Scope:** Theme and interface redesign of the MedOS frontend against an industry accessibility and clinical-safety standard
- **Standard applied:** WCAG 2.2 AA (measurable floor) + NHS/GOV.UK interaction patterns (domain layer) + a bespoke visual identity
- **Related:** `docs/superpowers/specs/2026-09-26-medos-production-release-design.md` (release program), `docs/superpowers/plans/2026-09-26-medos-stage0-baseline-validation.md` (Stage 0)

---

## 1. Purpose

Replace a half-finished dark-mode migration that renders white text on white surfaces, and establish a real design system underneath it, so the interface is legible, accessible, and recognisably a clinical tool.

This is **not** a beautification pass. The current theme is partly broken, and the redesign is a prerequisite for shipping a hospital product to anyone who has to use it for eight hours.

## 2. What is wrong today

Verified by reading `frontend/src/index.css` and the component tree on 2026-09-27.

### 2.1 The theme is two themes at once, and they collide

`:root` declares a light palette — `--bg-deep: #f8fafc`, `--surface-solid: #ffffff`, `--text: #1e293b` — while the shell is hardcoded dark: `.sidebar` is `rgba(12, 12, 20, 0.98)` (`index.css:608`) and `index.html:7` sets `theme-color` to `#06060b`.

The collision produces **invisible text in 28 places** — 21 of them inline in JSX across 8 files, 7 in the stylesheet. `--text-white: #ffffff` is used for:

| Element | Rule | Background | Contrast |
|---|---|---|---|
| Every `input`, `textarea`, `select` | `index.css:76` | `--surface-2` `rgba(241,245,249,.9)` | **~1.05:1** |
| `.card-header h2/h3` | `index.css:190` | `--surface` on canvas | **~1.04:1** |
| `.stat-card .stat-value` | `index.css:229` | card surface | **~1.04:1** |
| `.page-header h1` | `index.css:289` | canvas | **~1.05:1** |
| `.modal-header h2` | `index.css:330` | `--surface-solid` | **1.00:1** |
| Active sidebar nav item | `Sidebar.jsx:108` | `--primary-glow` over near-black | 1.00:1 on the glow |

The form controls are the serious case: a receptionist cannot type a phone number into a field whose text is white on near-white.

Additional light-mode casualties from the same migration: `.badge-default` (`index.css:279`) and `tbody tr:hover` (`index.css:262`) both use white-alpha, which is invisible on a light surface. `button:hover { filter: brightness(1.1) }` (`index.css:120`) lightens already-light controls.

### 2.2 There is no design system

- **202 inline `style={{…}}` objects across 14 files** — `Billing.jsx` 33, `Pharmacy.jsx` 30, `Encounters.jsx` 29, `Admissions.jsx` 26, `Patients.jsx` 21.
- This is why the responsive block resorts to attribute-selector hacks against inline styles: `.page-header[style*="flex"]`, `.pharmacy-layout > div[style*="width"]`, `.ai-suggestion-item`, `.rx-manual-row`, `.stats-row`, `.profile-header` (`index.css:505-576`).
- Consequently the visual system cannot be changed in one place, and the theme drift went unnoticed.

### 2.3 Typography is fictional

`body { font-family: 'Inter', … }` (`index.css:45`) with no `@font-face` rule and no `<link>` in `index.html`. Inter is never loaded; every screen silently renders in the system fallback.

### 2.4 Accessibility blockers

- **3 `aria-*` attributes and 0 `role=` in the entire app.** Toasts render as plain `div`s (`ToastContainer.jsx:7`) → fails **WCAG 2.2 4.1.3 Status Messages (AA)**.
- **No `:focus-visible` rule anywhere** → fails **2.4.7 Focus Visible (AA)**.
- Six dashboard states distinguished by colour alone (`Dashboard.jsx:31-38` passes `color:` per card) → fails **1.4.1 Use of Color (AA)**.
- No `aria-current` on the active nav item, no skip link, no `<th scope>`, no `<caption>`.
- Navigation icons are geometric Unicode glyphs (`◉ ◈ ◎ ▣ ⬡ ₿`) with no `aria-hidden` (`Sidebar.jsx:6-40`) — announced by screen readers as arbitrary punctuation.
- Base font size **shrinks** 15px → 14px below 768px (`index.css:460`), which is backwards for clinical readability.
- No `forced-colors` (Windows High Contrast) support, and **9** `backdrop-filter` declarations that collapse in that mode.

### 2.5 Dead and contradictory code

- `.tab:hover` declared twice (`index.css:428-434` and `443-446`); the second block resets `transform`/`filter` with `!important`-free overrides.
- `--mobile-breakpoint: 768px` (`index.css:31`) is unusable — CSS custom properties cannot be used in media queries.
- `--primary-glow`, `--info`, `--info-bg` are defined and inconsistently used.
- `text-transform: uppercase` + `letter-spacing` on table headers and stat labels (2 occurrences), which flattens hierarchy rather than encoding it.

## 3. Standard applied, and why

### 3.1 The measurable floor: WCAG 2.2 AA

WCAG 2.2 became a W3C Recommendation on 2023-10-05 (errata 2024-12-12) and is the standard NHS treats as a requirement rather than an aspiration. Criteria this redesign is held to:

| SC | Level | Requirement | How it is met |
|---|---|---|---|
| 1.4.1 Use of Color | A | Colour is not the only visual means | Every status is a word plus a colour; dot = acuity, square = workflow state |
| 1.4.3 Contrast (Minimum) | AA | 4.5:1 body text | All ink tokens ≥ 4.8:1 (§4.2) |
| 1.4.11 Non-text Contrast | AA | 3:1 on control boundaries, focus rings, status indicators | `--line` at 3.8:1 (§4.1) |
| 1.4.4 Resize Text | AA | 200% without loss | Base 16px, `rem`-based, never reduced |
| 2.4.7 Focus Visible | AA | Visible keyboard focus | `:focus-visible` 2px `--action` + 2px offset |
| 2.4.11 Focus Not Obscured | AA | Focus not hidden by author content | Sticky header offset accounted for in scroll padding |
| 2.5.8 Target Size (Minimum) | AA | 24×24 CSS px | 44×44 used throughout |
| 3.3.1 / 3.3.3 Error Identification & Suggestion | A | Errors identified, with correction | Error summary + per-field message naming the fix |
| 4.1.3 Status Messages | AA | Status programmatically determinable | `role="status" aria-live="polite"` on toasts, `role="alert"` on errors |

### 3.2 The domain layer: NHS and GOV.UK patterns

Adopted deliberately, from a system built under clinical-safety governance:

- **Patient banner** as a persistent identity block. Sourced from the NHS Common User Interface standards (ISB 1505, *Patient Banner*). Those standards are formally **deprecated** — the NHS Standards Directory marks ISB 1500–1508 as out of date — so they are cited as the origin of the pattern, not as a compliance target. The pattern itself remains correct: never act on a patient you cannot identify.
- **Summary list** field presentation, **error summary with links to fields**, **notification banner** pattern, **tag** component, **plain-language microcopy**, and the rule to keep text as text rather than in images.
- **Make unsafe actions difficult.** Destructive operations require the record's identifier to be typed, not a Yes/No confirmation.

Not adopted: the NHS visual identity. A commercial product sold to Indian hospitals — locale already `en-IN`, with GST invoices and DPDP consent — should not look like the NHS.

### 3.3 Consequence for the process

A UI change of this scope is a change with clinical-safety implications, and UK practice (DCB0160 for deployment, DCB0129 for manufacture) expects that to be assessed rather than assumed. This spec is that assessment's design input; the sign-off is recorded in §11.

## 4. The system

### 4.1 Line colours, split by purpose

| Token | Value | Contrast | Permitted use |
|---|---|---|---|
| `--line` | `#74868D` | 3.80:1 on white, 3.34:1 on `--sunken` | **Load-bearing only** — input/select/button borders, panel edges, table outer rule, anything whose visibility is required to operate the UI |
| `--rule` | `#D6DFDF` | 1.36:1 | **Decorative only** — separators between table rows, internal dividers |

This split exists because the mockups shown during design used `#B9C6C8` for control borders at 1.76:1, which fails **1.4.11**. Any boundary a user must perceive in order to operate the interface gets `--line`.

### 4.2 Colour tokens

| Token | Value | Contrast | Role |
|---|---|---|---|
| `--canvas` | `#F6F8F8` | — | Page background |
| `--surface` | `#FFFFFF` | — | Panel, table, banner |
| `--sunken` | `#EDF1F1` | — | Table header band, inset blocks |
| `--ink` | `#17242C` | 15.1:1 | Headings, primary text |
| `--ink-muted` | `#4E5F68` | 6.64:1 | Secondary text, labels |
| `--ink-faint` | `#5C6E77` | 5.31:1 white, 4.67:1 sunken, 4.99:1 canvas | Table headers, hints — passes 1.4.3 on **every** background it is used on |
| `--line` | `#74868D` | 3.80:1 | Load-bearing borders |
| `--rule` | `#D6DFDF` | — | Decorative rules |
| `--frame` | `#101F26` | — | Navigation rail |
| `--frame-hover` | `#1B313A` | — | Rail item hover |
| `--frame-ink` | `#DCE6E6` | 13.33:1 | Rail text |
| `--frame-muted` | `#92A6A8` | 6.66:1 | Rail inactive text |
| `--action` | `#0A5C6B` | 7.62:1 with white | Primary action, focus ring |
| `--action-hover` | `#084A56` | — | Primary hover |
| `--critical` | `#A4262C` | 7.30:1 | Critical acuity, overdue, destructive |
| `--urgent` | `#9A4E00` | 6.05:1 | Urgent acuity |
| `--normal` | `#1B6B3F` | 6.53:1 | Normal acuity, paid, consent recorded |
| `--info` | `#1B5FA8` | 6.44:1 | Informational |
| `--critical-tint` | `#FBF0F0` | — | Tag and field-error background |
| `--urgent-tint` | `#FDF4E8` | — | |
| `--normal-tint` | `#EFF6F1` | — | |
| `--info-tint` | `#EEF3FA` | — | |

Each status also has a matching border tone (`#EACBCC`, `#EBD9BE`, `#C8DED0`, `#C6D6EC`) so a tag is identifiable by its border in forced-colors mode.

**Every ratio in the tables above is script-verified, not estimated.** `frontend/scripts/contrast-check.mjs` computes each pair from the token values and fails the build on any pair below its threshold — 4.5:1 for text, 3:1 for non-text. Two values were corrected by that script during design and are recorded here so the history is not lost:

- `--ink-faint` was `#86959D` in the design mockups at 3.09:1, failing 1.4.3. Corrected first to `#64757E` (4.79:1 on white) — which then failed at **4.21:1 on the `--sunken` table-header band**, the one place it is actually used. Final value `#5C6E77` passes on white, sunken, and canvas.
- The mockups used `#B9C6C8` for control borders at 1.75:1, failing 1.4.11. Replaced by `--line` `#74868D` at 3.79:1.

### 4.3 Typography

- **IBM Plex Sans** 400/500/600/700 — all interface text. Chosen for its legibility at small sizes and its enterprise-clinical pedigree (IBM Carbon), and because it is not Inter.
- **IBM Plex Mono** 400/500 — UHIDs, vitals, dosages, quantities, money, invoice and payment numbers, timestamps.
- **Both self-hosted** via `@fontsource/ibm-plex-sans` and `@fontsource/ibm-plex-mono`. A clinical application must not make a third-party font request: it leaks an access event to an external party and creates a dependency on someone else's uptime.
- Monospace here is **functional, not decorative**: `0`/`O` and `1`/`l`/`I` must not be confusable in a drug code or a UHID. It is not applied to small labels.
- `font-variant-numeric: tabular-nums` on all numeric contexts, so digits form a true vertical column.
- Base `1rem` (16px), `rem`-based throughout, **never reduced at any breakpoint** (fixes §2.4).
- Scale: 12 / 13 / 14 / 16 / 18 / 22 / 40 px. Line height 1.55 body, 1.35 headings. Prose and summary blocks capped at 70ch.

### 4.4 Space, radius, elevation

- **Space:** 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 (`0.25`–`2.5rem`).
- **Radius:** 3px controls and fields, 6px panels, modals and dialogs, `999px` for tags only. Three values, no others.
- **Elevation — three levels, and only overlays are raised:**
  - `0` flat: no shadow, 1px `--line` border. All panels, tables, banners, the rail.
  - `1` raised: dropdowns and popovers, `0 1px 2px rgba(23,36,44,.08), 0 4px 12px rgba(23,36,44,.10)`.
  - `2` modal: dialogs, `0 12px 32px rgba(23,36,44,.18)`.
- **All `backdrop-filter` and all gradient decoration is removed.** Blur is the generic-glass tell, it costs paint performance on a scrolling clinical table, and it collapses in `forced-colors` mode.
- The rail is dark, the canvas is light. That contrast is the shell's only ornament, and it is what orients a user in a screen full of white panels.

### 4.5 Motion

| Interaction | Duration | Easing |
|---|---|---|
| Hover, focus | 120ms | `ease-out` |
| Panel / dialog entry | 160ms | opacity + 4px translate |
| Rail drawer (mobile) | 240ms | `cubic-bezier(.4,0,.2,1)` |
| Toast entry | 200ms | slide + fade |

One orchestrated moment only: the toast, because it answers an action the user just took. No page-load choreography, no scroll reveals, no animated counters. `prefers-reduced-motion` honoured globally and extended to the drawer.

### 4.6 Microcopy

- Active voice; a button says what happens ("Register patient", "Record payment").
- An action keeps its name through the whole flow: press "Record payment", the toast reads "Payment recorded".
- Errors never apologise and are never vague: "Enter a 10-digit mobile number", not "Invalid input".
- An empty state is an invitation to act, never a shrug: "No patients registered yet — Register the first patient".
- Sentence case throughout. The `→` glyph is removed from button text.

## 5. Components

New and rewritten in `index.css`; the 202 inline styles are extracted into these and deleted.

**Shell** — `.app-shell`, `.rail`, `.rail-item` (+ `[aria-current="page"]`), `.topbar`, `.page`, `.page-head`, `.page-title`, `.page-sub`, `.skip-link`

**Clinical** — `.banner` (flat ruled band, 4px status edge, name + inline meta run, footer row for location and allergy), `.banner__name`, `.banner__meta`, `.banner__foot`, `.banner--critical|urgent|normal`

**Data** — `.table` (ledger: hairline `--rule` rows, no zebra, sticky `--sunken` header, 44px rows), `.table th` with `scope="col"`, `.uid` (mono), `.num` (mono, right-aligned, tabular), `.tag` + `--critical|urgent|normal|info|neutral` with `.tag__dot` (acuity) and `.tag__square` (workflow state), `.empty`, `.pager`

**Forms** — `.form`, `.field`, `.field__label`, `.field__control`, `.field__hint`, `.field__error`, `.field--invalid` (3px left rule), `.consent` (guarded block, not a bare checkbox), `.form-actions`

**Feedback** — `.summary-error` (links to fields), `.toast` (+ `role="status"`), `.dialog`, `.dialog__confirm` (typed identifier)

**Controls** — `.btn`, `.btn--primary|secondary|danger|ghost`, `.btn--sm`, all ≥44px

**Icons** — `components/icons.jsx`: inline SVG, 1.5px stroke, 20px box, `aria-hidden="true"`, `focusable="false"`. Replaces every geometric glyph.

**Status vocabulary — closed set of eight:** Critical, Urgent, Normal (acuity, dot); Draft, Unbilled, Paid, Overdue (workflow, square); DPDP recorded (neutral). Reused everywhere; no page may invent a ninth.

## 6. Accessibility floor

Enforced per component, not aspirational:

1. `:focus-visible` → 2px solid `--action`, 2px offset, ≥3:1 against adjacent colours. Never `outline: none` without a replacement.
2. Every interactive target ≥44×44 CSS px.
3. Toasts: `role="status" aria-live="polite"`. Errors: `role="alert"`.
4. Every input has a real `<label for>`; invalid fields carry `aria-invalid="true"` and `aria-describedby` pointing at the message.
5. Active nav item carries `aria-current="page"`.
6. Skip link to `#main`, visually hidden until focused.
7. Landmarks: `<header>`, `<nav aria-label="Primary">`, `<main id="main">`.
8. `prefers-reduced-motion` honoured.
9. `@media (forced-colors: active)` — borders and focus indicators restored via `CanvasText`/`Highlight`, `backdrop-filter` neutralised.
10. No status conveyed by colour alone; no icon-only control without an accessible name.
11. Modals trap focus, restore it on close, and close on `Escape`.
12. `prefers-contrast: more` raises border weight from 1px to 2px.

## 7. Rollout

Six waves, each independently shippable and independently reviewable.

| Wave | Scope | Rationale |
|---|---|---|
| **0 — Foundation** | `index.css` token layer rewritten; `Layout`, `Header`, `Sidebar`, `ToastContainer`; `components/icons.jsx`; `store/patientBannerStore.js`; `.banner` component; Playwright install + screenshot capability; anti-inline-style vitest guard | All pages already consume these class names, so the white-on-white failure is fixed once here rather than nine times |
| **1 — Entry** | `Login`, `Onboarding` | No data tables; validates the shell in a real browser early |
| **2 — Identity** | `Patients`, `Dashboard` | The banner gains a patient in context here |
| **3 — Clinical** | `Encounters`, `Admissions` | Vitals, guarded forms, consent blocks, typed-confirmation dialogs |
| **4 — Money** | `Pharmacy`, `Billing` | Ledger tables, right-aligned money columns, idempotency-replay messaging |
| **5 — Sweep** | `ErrorBoundary`, empty states | Remove duplicate `.tab:hover`, `--mobile-breakpoint`, orphaned tokens, residual inline styles |

**One architectural addition.** The banner persists across route changes, which inline styles cannot express, so it reads from a new Zustand store (`store/patientBannerStore.js`) that pages set on selection and clear on unmount. This follows the existing `store/` pattern; no new mechanism is introduced.

## 8. Contracts

### 8.1 With the Stage 0 plan

**Preserved exactly:** route paths, element structure, accessible names, ARIA roles, and the selectors the Stage 0 Playwright specs depend on — `getByLabel(/username/i)`, `getByLabel(/password/i)`, `getByRole('button', { name: /sign in/i })`, and the post-login URL assertion.

**Changed:** class names, inline styles, icon glyphs. The `→` is removed from "Sign In →"; the Stage 0 regex `/sign in/i` still matches.

**Added:** `data-testid` hooks on the elements Stage 0 and future specs target, so a later restyle cannot break a test.

**Added tooling:** `frontend/scripts/contrast-check.mjs` and its `npm run check:contrast` script. This is pulled forward from §9 because it is the check that caught the two token errors in §4.2 — it must exist before the tokens are written, not after.

Net effect: the Stage 0 plan remains valid as written and needs no selector rework.

### 8.2 The anti-regression guard

Wave 0 adds `frontend/src/test/design-system.test.js`, which fails the build when:

- any `style={{` appears in `src/**/*.jsx`;
- any hex literal (`#rgb`, `#rrggbb`) appears in `src/` outside `index.css`;
- any hard-coded `px` value appears in a `font-size` declaration outside `index.css`.

This converts "keep it tokenised" from a convention into a machine check. The 202 inline styles were not a discipline failure that more care would have fixed; they are what happens when nothing enforces the rule.

## 9. Verification

Per wave, in order:

1. `npm run lint` — oxlint, currently 0 warnings / 0 errors on 29 files
2. `npm test` — vitest, 15 tests currently passing; all must stay green
3. `npm run build` — Vite production build
4. `npm run test:e2e` — Playwright, once installed in Wave 0
5. `npm run check:contrast` — `frontend/scripts/contrast-check.mjs`, recomputes every §4.2 pair from the token values and fails on any threshold breach. Added in Wave 0, run in CI, and re-run whenever a token changes.
6. Visual diff — Playwright screenshots before and after, per wave
7. Keyboard pass — tab order, focus visibility, dialog focus trap, on each page touched

Manual, once, at the end: print one page in greyscale and confirm no status or tag becomes unreadable. That single test validates the 1.4.1 and 1.4.11 commitments end to end.

## 10. Out of scope

- **Tailwind CSS** — a fourth concept to learn across nine pages, to solve a problem the 681 lines being replaced already solve in plain CSS. Plain CSS with tokens is retained.
- **Any component library** (MUI, Ant, PrimeNG, Chakra) — each would impose its own visual identity over the clinical palette, and its own accessibility defaults to audit.
- **Dark mode** — a light clinical canvas was chosen deliberately. Dark mode would double the contrast QA surface, and a dark mode nobody has tested for clinical legibility is a liability, not a feature.
- **Icon library dependency** — a purpose-built 12-icon SVG set is smaller and fully controlled.
- **Layout restructuring beyond the banner** — master-detail and list layouts stay as they are; this is a theme and component-system change, not an information-architecture change.
- **Backend or API changes.**

## 11. Clinical safety sign-off

Required before release, and not satisfiable by this document alone:

- [ ] Design reviewed against §3.1 criteria with a real screen reader (NVDA or VoiceOver), not a linter alone
- [ ] Reviewed at 200% browser zoom with no loss of content or function (1.4.4)
- [ ] Reviewed in Windows High Contrast mode (forced-colors)
- [ ] Greyscale print test passed (§9)
- [ ] Reviewed by at least one clinical user — a nurse or registrar — against a real task
- [ ] Keyboard-only pass completed on Login, Patients, Encounters, Pharmacy, Billing
- [ ] Contrast audit script output attached

## 12. Glossary additions

Extending the glossary in the release design spec:

- **Patient banner** — a persistent block at the top of any patient-context screen carrying identity, status, consent and allergies, so no action is ever taken against an unidentified patient. From NHS CUI ISB 1505, now deprecated as a standard but correct as a pattern.
- **Guarded field** — a form control whose validation failure is shown in three places at once: an error summary, a rule on the field, and a message naming the correction.
- **Typed confirmation** — a destructive-action dialog requiring the record's identifier to be typed. Prevents a Yes/No dialog being answered by muscle memory.
- **Acuity vs workflow state** — the two meanings of status in a clinical system, distinguished by marker shape: dot for clinical acuity, square for workflow state. Prevents "Critical" and "Overdue" being conflated.
- **Token** — a named design decision expressed as a CSS custom property, so the system changes in one place.
- **Load-bearing line** — a border a user must perceive in order to operate the interface; requires 3:1 under WCAG 1.4.11. Distinct from a decorative rule.
- **Greyscale test** — rendering a screen without colour to confirm status survives. The cheapest honest test of 1.4.1.
