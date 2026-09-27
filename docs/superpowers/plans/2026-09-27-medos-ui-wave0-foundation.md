# Wave 0 Implementation Plan — UI Foundation

- **Date:** 2026-09-27
- **Spec:** `docs/superpowers/specs/2026-09-27-medos-ui-ux-redesign-design.md` §4, §5, §6, §7 (Wave 0)
- **Goal:** Replace the token layer, rebuild the shell, and install the two machine checks — so that Waves 1–5 are mechanical.
- **Gate:** White-on-white eliminated repo-wide, all 24 token pairs script-verified, no inline styles outside `index.css`, existing 15 tests green, shell reviewed in a browser.

## Scope

In: `index.css` rewrite, `Layout`, `Header`, `Sidebar`, `ToastContainer`, `icons.jsx`, `patientBannerStore`, `PatientBanner`, `index.html`, `main.jsx`, `package.json`, plus four new files — `contrast-check.mjs`, `design-system.test.js`, `playwright.config.js`, and a dev-only icon gallery.

Out: all six page components. They keep working against the new tokens (same class names) and are restyled in Waves 1–4. **If a page breaks visually during Wave 0, that is a Wave 0 bug, not a page bug.**

## Why these two checks go in first

The existing theme failed because unverified values sat in a stylesheet nobody could check. Both checks below are written *before* the tokens they protect, and both fail the build rather than warn.

---

## Task 0.1 — Install dependencies

```bash
cd frontend
npm install --save-dev @playwright/test
npm install @fontsource/ibm-plex-sans @fontsource/ibm-plex-mono
npx playwright install chromium
```

`@fontsource` bundles the font files into the build. No third-party request is made at runtime — a requirement for a clinical application, not a preference.

**Verify:** `grep -E "fontsource|playwright" package.json` shows both; `ls node_modules/@fontsource` lists both packages.

---

## Task 0.2 — `scripts/contrast-check.mjs` (before the tokens)

Single source of truth is `src/index.css`. The script parses `--token: #hex;` out of it, so a token changed in one place cannot drift from its verified ratio.

```js
#!/usr/bin/env node
// Fails the build if any token pair falls below its WCAG 2.2 threshold.
// Text: 4.5:1 (1.4.3). Non-text: 3:1 (1.4.11).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const css = readFileSync(resolve(here, '../src/index.css'), 'utf8');

const tokens = Object.fromEntries(
  [...css.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{3,8})\s*;/g)]
    .map(([, name, hex]) => [name, hex.length === 4
      ? '#' + [...hex.slice(1)].map((c) => c + c).join('')
      : hex])
);

// [foreground, background, minimum, criterion]
const PAIRS = [
  ['ink', 'surface', 4.5, '1.4.3'],
  ['ink', 'canvas', 4.5, '1.4.3'],
  ['ink', 'sunken', 4.5, '1.4.3'],
  ['ink-muted', 'surface', 4.5, '1.4.3'],
  ['ink-muted', 'sunken', 4.5, '1.4.3'],
  ['ink-faint', 'surface', 4.5, '1.4.3'],
  ['ink-faint', 'sunken', 4.5, '1.4.3'],
  ['ink-faint', 'canvas', 4.5, '1.4.3'],
  ['critical', 'surface', 4.5, '1.4.3'],
  ['critical', 'critical-tint', 4.5, '1.4.3'],
  ['urgent', 'surface', 4.5, '1.4.3'],
  ['urgent', 'urgent-tint', 4.5, '1.4.3'],
  ['normal', 'surface', 4.5, '1.4.3'],
  ['normal', 'normal-tint', 4.5, '1.4.3'],
  ['info', 'surface', 4.5, '1.4.3'],
  ['info', 'info-tint', 4.5, '1.4.3'],
  ['action', 'surface', 4.5, '1.4.3'],
  ['surface', 'action', 4.5, '1.4.3'],
  ['frame-ink', 'frame', 4.5, '1.4.3'],
  ['frame-muted', 'frame', 4.5, '1.4.3'],
  ['line', 'surface', 3, '1.4.11'],
  ['line', 'sunken', 3, '1.4.11'],
  ['line', 'canvas', 3, '1.4.11'],
  ['action', 'canvas', 3, '1.4.11 focus ring'],
  ['action', 'sunken', 3, '1.4.11 focus ring'],
];

const srgb = (hex) => {
  const h = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
};
const luminance = (hex) => {
  const [r, g, b] = srgb(hex).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

let failures = 0;
for (const [fg, bg, min, sc] of PAIRS) {
  if (!tokens[fg]) { console.error(`MISSING TOKEN --${fg} (needed for ${sc})`); failures++; continue; }
  if (!tokens[bg]) { console.error(`MISSING TOKEN --${bg} (needed for ${sc})`); failures++; continue; }
  const value = ratio(tokens[fg], tokens[bg]);
  const ok = value >= min;
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${value.toFixed(2)}:1  (min ${min}, ${sc})  --${fg} on --${bg}`);
}
console.log(failures === 0 ? '\nAll token pairs pass.' : `\n${failures} failure(s).`);
process.exit(failures === 0 ? 0 : 1);
```

Add to `package.json` scripts:

```json
"check:contrast": "node scripts/contrast-check.mjs"
```

**Verify it actually fails:** temporarily set `--line: #D6DFDF` in the CSS, run the script, confirm three `1.4.11` failures and a non-zero exit, then restore. A check that cannot fail is not a check.

---

## Task 0.3 — Rewrite `src/index.css`

Full replacement, in this order. Nothing from the current file is carried over except the responsive breakpoints and the `prefers-reduced-motion` block, both of which are correct.

### 0.3a Token block (verbatim — this is the contract)

```css
:root {
  color-scheme: light;

  --canvas: #F6F8F8;
  --surface: #FFFFFF;
  --sunken: #EDF1F1;

  --ink: #17242C;
  --ink-muted: #4E5F68;
  --ink-faint: #5C6E77;

  --line: #74868D;
  --rule: #D6DFDF;

  --frame: #101F26;
  --frame-hover: #1B313A;
  --frame-ink: #DCE6E6;
  --frame-muted: #92A6A8;

  --action: #0A5C6B;
  --action-hover: #084A56;

  --critical: #A4262C;
  --urgent: #9A4E00;
  --normal: #1B6B3F;
  --info: #1B5FA8;

  --critical-tint: #FBF0F0;
  --urgent-tint: #FDF4E8;
  --normal-tint: #EFF6F1;
  --info-tint: #EEF3FA;

  --critical-line: #EACBCC;
  --urgent-line: #EBD9BE;
  --normal-line: #C8DED0;
  --info-line: #C6D6EC;

  --shadow-1: 0 1px 2px rgba(23, 36, 44, .08), 0 4px 12px rgba(23, 36, 44, .10);
  --shadow-2: 0 12px 32px rgba(23, 36, 44, .18);

  --r-sm: 3px;
  --r-md: 6px;

  --rail-width: 232px;
  --topbar-height: 56px;

  --s1: 0.25rem; --s2: 0.5rem;  --s3: 0.75rem; --s4: 1rem;
  --s5: 1.25rem; --s6: 1.5rem;  --s8: 2rem;     --s10: 2.5rem;

  --t-micro: 0.75rem; --t-dense: 0.8125rem; --t-label: 0.875rem;
  --t-body: 1rem; --t-h3: 1.125rem; --t-h2: 1.375rem; --t-stat: 2.5rem;

  --target: 44px;
  --dur-fast: 120ms;
  --dur-base: 160ms;
  --dur-drawer: 240ms;
  --ease: cubic-bezier(.4, 0, .2, 1);
}
```

### 0.3b Layer order

1. Reset — `box-sizing`, margin/padding zero, `html { font-size: 100% }` (**never** `15px` or a media-query reduction)
2. Base elements — `body` background `--canvas`, colour `--ink`, font stack, `line-height: 1.55`; `a`; headings `line-height: 1.35`
3. Focus — `:focus-visible { outline: 2px solid var(--action); outline-offset: 2px }` and **no rule anywhere containing `outline: none`**
4. Shell — `.app-shell`, `.rail`, `.rail-item`, `.topbar`, `.page`, `.skip-link`
5. Clinical — `.banner` and modifiers
6. Data — `.table`, `.uid`, `.num`, `.tag`, `.empty`, `.pager`
7. Forms — `.form`, `.field*`, `.consent`, `.form-actions`
8. Feedback — `.summary-error`, `.toast`, `.dialog`
9. Controls — `.btn*`
10. Utilities — `.visually-hidden`, `.grid-*`
11. Responsive — `max-width: 1024px`, `max-width: 768px`, `max-width: 480px`
12. Preference modes — `prefers-reduced-motion`, `prefers-contrast: more`, `forced-colors: active`

### 0.3c Rules that must not appear

- Any `backdrop-filter` or `-webkit-backdrop-filter` (9 exist today)
- Any `filter: brightness()` on hover (the current `.btn` hover lightens light controls)
- Any `text-transform: uppercase` on a text node (a 10.5px table header uses small caps *sizing* and `--ink-faint`, not case transformation)
- Any `→` or `←` or `▾` glyph inside button text
- Any hard-coded hex outside the token block
- Any `font-size` in `px`
- Any `--mobile-breakpoint` (it was unusable)

**Note on the responsive block:** the current file contains attribute-selector hacks (`.page-header[style*="flex"]`, `.pharmacy-layout > div[style*="width"]`, `.ai-suggestion-item`, `.rx-manual-row`, `.stats-row`, `.profile-header`) that exist purely to fight inline styles on pages. **Delete all of them.** Waves 1–4 replace the inline styles with real classes; the hacks go with them. Do not port them forward.

**Verify:**
```bash
npm run check:contrast          # 25 pairs, all pass
npx oxlint src                  # clean
npm run build                   # succeeds
```

---

## Task 0.4 — `src/components/icons.jsx` (new)

Replaces the geometric Unicode glyphs. 1.5px stroke, 24px viewBox, `aria-hidden="true"` and `focusable="false"` on every icon — they are decorative, because the adjacent text label carries the meaning.

```jsx
const ICONS = {
  dashboard: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  patients: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 7a4 4 0 1 1 0-8 4 4 0 0 1 0 8',
  encounters: 'M9 4h6v3H9zM15 5h3a2 2 0 0 1 2 2v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a2 2 0 0 1 2-2h3M8 12h8M8 16h5',
  admissions: 'M2 17v-5h20v5M2 17v3M22 17v3M6 12V8h5a4 4 0 0 1 4 4',
  pharmacy: 'M3 8l9-5 9 5v8l-9 5-9-5zM3 8l9 5 9-5M12 13v8',
  billing: 'M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2zM9 8h6M9 12h6',
  plus: 'M12 5v14M5 12h14',
  search: 'M11 19a8 8 0 1 1 0-16 8 8 0 0 1 0 16zM21 21l-4.3-4.3',
  menu: 'M3 6h18M3 12h18M3 18h18',
  close: 'M6 6l12 12M18 6L6 18',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  alert: 'M12 3L2 20h20zM12 9v5M12 17.5v.5',
  check: 'M4 12l5 5L20 6',
  clock: 'M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18zM12 7v5l3 2',
  chevron: 'M6 9l6 6 6-6',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 1 0-8 4 4 0 0 1 0 8',
};

export default function Icon({ name, size = 20, className = '' }) {
  const d = ICONS[name];
  if (!d) return null;
  const shapes = d.split('M').filter(Boolean);
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {shapes.map((s, i) => <path key={i} d={`M${s}`} />)}
    </svg>
  );
}

export { ICONS };
```

The single-`d`-per-subpath split is deliberate: it keeps the icon map to one string per icon while still supporting multi-part glyphs. The dev gallery in Task 0.5 exists precisely because hand-written path data cannot be trusted without looking at it.

**Verify:** every name in `ICONS` renders without a React key warning and the gallery in 0.5 shows all sixteen as recognisable shapes.

---

## Task 0.5 — Dev-only icon gallery (new)

Hand-authored SVG path data is not verifiable by reading it. This route makes it verifiable.

**`src/pages/IconGallery.jsx`** — renders every `ICONS` entry at 20px, 24px, and 48px on `--surface` and on `--frame`, with its name in `--ink-muted`.

**`src/App.jsx`** — one added line, dev-only so it never ships:

```jsx
{import.meta.env.DEV && <Route path="/icons" element={<IconGallery />} />}
```

**Verify:** `npm run dev`, open `http://localhost:5173/icons`, confirm all sixteen read as their intended object. Fix any path that does not, here, before the icons reach the rail.

---

## Task 0.6 — `store/patientBannerStore.js` (new)

Follows the `toastStore` pattern exactly — same file shape, same default export.

```js
import { create } from 'zustand';

/**
 * The patient currently in context, rendered by PatientBanner across route
 * changes. Pages set it on selection and clear it on unmount.
 */
const usePatientBannerStore = create((set) => ({
  patient: null,
  setPatient: (patient) => set({ patient }),
  clearPatient: () => set({ patient: null }),
}));

export default usePatientBannerStore;
```

---

## Task 0.7 — `components/PatientBanner.jsx` (new)

The chart-header direction, as approved. Flat ruled band, 4px status edge on the left, identity and status on one line, location and allergy on a second. Renders `null` when there is no patient, so mounting it in `Layout` is unconditional.

```jsx
import usePatientBannerStore from '../store/patientBannerStore';
import Icon from './icons';

const ACUITY = {
  critical: 'Critical',
  urgent: 'Urgent',
  normal: 'Normal',
};

export default function PatientBanner() {
  const patient = usePatientBannerStore((s) => s.patient);
  if (!patient) return null;

  const acuity = ACUITY[patient.acuity] ? patient.acuity : 'normal';

  return (
    <section className={`banner banner--${acuity}`} aria-label="Patient in context">
      <div className="banner__top">
        <h2 className="banner__name">{patient.name}</h2>
        {patient.status && (
          <span className={`tag tag--${acuity}`}>
            <span className="tag__dot" />
            {patient.status}
          </span>
        )}
        <p className="banner__meta">
          <span className="uid">{patient.uhid}</span>
          {patient.age != null && <><span className="dot-sep" />{patient.age} y</>}
          {patient.sex && <><span className="dot-sep" />{patient.sex}</>}
          {patient.bloodGroup && <><span className="dot-sep" />{patient.bloodGroup}</>}
          {patient.dpdpConsent === true && (
            <><span className="dot-sep" /><span className="tag tag--normal tag--sq">DPDP consent</span></>
          )}
          {patient.dpdpConsent === false && (
            <><span className="dot-sep" /><span className="tag tag--critical tag--sq">No consent</span></>
          )}
        </p>
      </div>
      <div className="banner__foot">
        <p className="banner__loc">
          <Icon name="admissions" size={16} />
          {patient.location || 'Not admitted'}
        </p>
        {patient.allergy && (
          <p className="banner__allergy">
            <Icon name="alert" size={16} />
            Allergy: {patient.allergy}
          </p>
        )}
      </div>
    </section>
  );
}
```

The `dot-sep` between meta items is a CSS `::before` on siblings — not a text character — so screen readers do not announce punctuation between values.

**Verify:** with no patient set, `PatientBanner` renders nothing and adds no vertical space. With one set, the band shows identity, acuity, consent, location, and allergy.

---

## Task 0.8 — Rebuild the shell

### `Layout.jsx`

Skip link first in DOM order, then rail, then topbar, then banner, then `<main id="main">`. All layout via classes — **zero** inline styles.

```jsx
<a className="skip-link" href="#main">Skip to main content</a>
```

### `Sidebar.jsx`

- `<nav aria-label="Primary">` wrapping the list
- each item `<Link className="rail-item" aria-current={active ? 'page' : undefined}>`
- icon via `Icon` + name in the existing `navItems` map (replace the `icon: '◉'` glyphs with `icon: 'dashboard'`)
- mobile drawer keeps `aria-label` on the close button and the overlay stays `aria-hidden`
- the "MED**OS**" wordmark and the `HMS v3.0` line lose their inline styles; `--text-dim` is gone, so use `--frame-muted`
- the `ClockWidget` monospace time becomes `.rail__clock` using `--ink-faint`-equivalent contrast on the frame (`--frame-muted`)

### `Header.jsx`

- remove the `←` from the exit button text; the button reads **Exit**
- `title="Logout"` becomes a proper accessible name — the visible text *is* the name, so `title` is redundant and should be dropped
- the user's name loses `color: 'var(--text-white)'` — the single worst instance in the file, white text on `--surface-solid`
- the avatar keeps `--action` as its background
- hamburger keeps `aria-label="Toggle menu"` and gains `aria-expanded` and `aria-controls`

### `ToastContainer.jsx`

```jsx
<div className="toast-container" role="status" aria-live="polite" aria-atomic="false">
```

Critical toasts additionally get `role="alert"`. The container must exist in the DOM before a toast arrives for the live region to be announced reliably, so **remove the `if (!toasts.length) return null` guard** — render the empty container and let CSS hide it when empty.

**Verify for all four:**
```bash
npx oxlint src                       # clean
npm test                             # 15 tests green
npm run build                        # succeeds
grep -rn 'style={{' src/components/  # no matches
```

---

## Task 0.9 — `index.html` and `main.jsx`

`index.html`:

```html
<meta name="theme-color" content="#F6F8F8" />
<meta name="color-scheme" content="light" />
```

`color-scheme: light` is load-bearing, not cosmetic. Without it, a user whose OS is in dark mode gets **dark native form controls** — select dropdowns, date pickers, scrollbars, autofill — rendered inside a light clinical UI. This is invisible in testing on a light-mode machine and is one of the most common "looks broken on my user's machine" defects.

Also remove `apple-mobile-web-app-status-bar-style="black-translucent"`, which forces a dark status bar over a light app.

`main.jsx` — add the font imports above `./index.css`:

```js
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-sans/700.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import './index.css';
```

**Verify:** `npm run build` and confirm `dist/assets/*.woff2` exists — proof the fonts are bundled and no Google request remains. `grep -ri "fonts.googleapis" dist/` returns nothing.

---

## Task 0.10 — `src/test/design-system.test.js` (new)

The anti-regression guard. `vite.config.js` already includes `src/**/*.test.js`, so it is picked up with no config change.

```js
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const SRC = new URL('..', import.meta.url).pathname;
const files = (dir, out = []) => {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === 'test-results') continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) files(full, out);
    else if (/\.jsx?$/.test(entry)) out.push(full);
  }
  return out;
};
const sources = files(SRC);
const read = (f) => readFileSync(f, 'utf8');
const offenders = (pattern) => sources
  .map((f) => [relative(SRC, f), pattern])
  .filter(([f, p]) => p.test(read(join(SRC, f))))
  .map(([f]) => f);

describe('design system guard', () => {
  it('has no inline style objects in components', () => {
    expect(offenders(/style=\{\{/)).toEqual([]);
  });

  it('has no hex colours outside index.css', () => {
    expect(offenders(/#[0-9a-fA-F]{3,8}\b/).filter((f) => f !== 'index.css')).toEqual([]);
  });

  it('has no px font sizes outside index.css', () => {
    const bad = sources
      .filter((f) => !f.endsWith('index.css'))
      .filter((f) => /font-size:\s*\d+px/.test(read(f)))
      .map((f) => relative(SRC, f));
    expect(bad).toEqual([]);
  });

  it('keeps a visible focus indicator', () => {
    const css = read(join(SRC, 'index.css'));
    expect(css).toMatch(/:focus-visible/);
    expect(css).not.toMatch(/outline:\s*none/);
  });
});
```

The focus test encodes spec §6.1 as a build failure: if anyone later adds `outline: none` without a replacement, the build stops.

**Verify it fails on current code:** before Wave 0 lands, the first two tests must report all 14 offending files. If they pass on the unreworked tree, the test is broken.

---

## Task 0.11 — Playwright harness

```bash
npm install --save-dev @playwright/test   # installed in 0.1
```

`playwright.config.js`:

```js
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 1,
  reporter: [['list']],
  outputDir: 'test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.js/ },
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
```

`workers: 1` because tests share one database. `baseURL` defaults to the Vite dev server so Wave 0 screenshots do not require the Docker stack.

Append to `frontend/.gitignore`: `e2e/.auth/`, `test-results/`, `playwright-report/`.

**Boundary:** Wave 0 ships the harness and screenshots the two unauthenticated routes. The eight critical-flow specs and authenticated screenshot baselines belong to the Stage 0 plan, which owns `auth.setup.js`. Do not duplicate that work here.

**Verify:** `npm run dev` in one shell, `npx playwright screenshot http://localhost:5173/login /tmp/medos-login.png` in another. Open the PNG and confirm the login page is legible — this is the first real check that white-on-white is dead.

---

## Task 0.12 — Full gate

```bash
cd frontend
npm run check:contrast     # 25 pairs pass
npx oxlint src             # 0 warnings, 0 errors
npm test                   # 15 existing + 4 new = 19 pass
npm run build              # succeeds, woff2 in dist
grep -rn "style={{" src    # no matches
grep -rn "backdrop-filter" src   # no matches
grep -c "outline: none" src/index.css  # 0
```

Then, by hand:

1. `npm run dev` — walk `/login`, `/onboarding`, `/icons`
2. Type into every field on `/login`. **The text must be visible.** This is the specific defect this wave exists to kill, so it gets a manual check, not just an automated one.
3. Tab through `/login`. Focus must be visible at every stop.
4. Trigger a toast (failed login) and confirm it is announced, not just drawn.
5. Reload `/icons` and confirm all sixteen icons read correctly.
6. Toggle the OS to dark mode and reload `/login`. Selects and inputs must stay light — this verifies `color-scheme`.
7. `npm run build && npm run preview`, then repeat 2 and 3 against the production bundle.

Record the outcome in the commit message. If any check fails, the wave is not done.

---

## What Wave 0 must not touch

- The six page components. They are expected to keep rendering; Wave 0 is shell and tokens.
- `api/`, `store/authStore.js`, `store/loadingStore.js`, `store/toastStore.js`, `App.jsx` routing beyond the one dev-only icon route.
- Any backend file.
- `docker-compose.yml`, nginx, or any deployment concern.

## Known risks

- **A page may look wrong after the token swap.** That is expected and is what the Waves 1–4 review is for. Do not start restyling pages inside Wave 0; note the issue and move on, or the wave stops being independently reviewable.
- **The `.tab`, `.grid-*`, `.empty`, `.stat-card` and `.card` classes are consumed by pages.** They must keep their existing names in Wave 0 even though their internals change, or all six pages break at once. Waves 1–4 are where they get properly renamed.
- **Hand-authored SVG paths will have at least one bad glyph.** The gallery in 0.5 exists for exactly this. Do not ship icons unreviewed.
