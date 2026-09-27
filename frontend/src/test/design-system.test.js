import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

// Vitest runs with cwd at the project root; import.meta.url is an http URL
// under the jsdom environment, so it cannot be converted with fileURLToPath.
const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const SKIP = new Set(['node_modules', 'test-results', 'playwright-report', 'dist']);

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    if (SKIP.has(entry)) continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.jsx?$/.test(entry)) out.push(full);
  }
  return out;
}

const files = walk(SRC);
const read = (file) => readFileSync(file, 'utf8');
const rel = (file) => relative(SRC, file).split(sep).join('/');
const isStylesheet = (file) => rel(file) === 'index.css';
const exists = (name) => files.some((f) => rel(f) === name);

const matching = (pattern, { excludeCss = false } = {}) =>
  files
    .filter((f) => !(excludeCss && isStylesheet(f)))
    .filter((f) => pattern.test(read(f)))
    .map(rel)
    .sort();

/**
 * Ratchet for the eight page components.
 *
 * Wave 0 replaces the token layer and the shell. Pages keep working against the
 * same class names, so their inline styles cannot be removed until their own
 * wave restyles them. This list is the explicit record of that debt, tagged with
 * the wave that clears it.
 *
 * Three properties make it a ratchet rather than a loophole:
 *   - it must never gain an entry, so a NEW file with an inline style fails;
 *   - every entry must still exist, so a rename fails instead of silently
 *     exempting a file;
 *   - every entry must still contain inline styles, so fixing a file forces its
 *     removal from the list and the diff shows the debt shrinking.
 */
const INLINE_STYLE_BASELINE = {
  'pages/Admissions.jsx': 'wave 3',
  'pages/Billing.jsx': 'wave 4',
  'pages/Dashboard.jsx': 'wave 2',
  'pages/Encounters.jsx': 'wave 3',
  'pages/Login.jsx': 'wave 1',
  'pages/Onboarding.jsx': 'wave 1',
  'pages/Patients.jsx': 'wave 2',
  'pages/Pharmacy.jsx': 'wave 4',
};

describe('design system guard', () => {
  it('finds source files to check', () => {
    // Guards against the walk silently returning nothing, which would make
    // every assertion below pass vacuously.
    expect(files.length).toBeGreaterThan(10);
  });

  it('has no inline style objects outside the documented baseline', () => {
    const offenders = matching(/style=\{\{/);
    expect(offenders.filter((f) => !(f in INLINE_STYLE_BASELINE))).toEqual([]);
  });

  it('has no hard-coded hex colours outside the stylesheet', () => {
    expect(matching(/#[0-9a-fA-F]{3,8}\b/, { excludeCss: true })).toEqual([]);
  });

  it('has no px font sizes outside the stylesheet', () => {
    expect(matching(/font-size:\s*\d+px/, { excludeCss: true })).toEqual([]);
  });

  it('has no baseline entry for a file that no longer exists', () => {
    const stale = Object.keys(INLINE_STYLE_BASELINE).filter((f) => !exists(f));
    expect(stale).toEqual([]);
  });

  it('has no stale baseline entry for a file that is already clean', () => {
    const stale = Object.keys(INLINE_STYLE_BASELINE).filter(
      (f) => exists(f) && !/style=\{\{/.test(read(join(SRC, f)))
    );
    expect(stale).toEqual([]);
  });

  it('keeps a visible focus indicator in the stylesheet', () => {
    expect(read(join(SRC, 'index.css'))).toMatch(/:focus-visible/);
  });

  it('never removes a focus outline without a replacement', () => {
    expect(read(join(SRC, 'index.css'))).not.toMatch(/outline:\s*none/);
  });

  it('does not reintroduce backdrop-filter', () => {
    expect(read(join(SRC, 'index.css'))).not.toMatch(/backdrop-filter/);
  });

  it('locks the colour scheme to light', () => {
    // Without this, a user whose OS is in dark mode gets dark native form
    // controls inside a light clinical interface.
    expect(read(join(ROOT, 'index.html'))).toMatch(/name="color-scheme" content="light"/);
  });

  it('has no var() reference to an undeclared token', () => {
    // A var() pointing at a deleted token does not throw — it silently falls
    // back to inherited or initial. That is how 20 dangling --text-white
    // references survived a token rename, so it is checked mechanically.
    const css = read(join(SRC, 'index.css'));
    const declared = new Set(
      [...css.matchAll(/^\s*(--[a-z0-9-]+)\s*:/gm)].map(([, name]) => name)
    );

    const referenced = new Map();
    for (const file of files) {
      for (const [, name] of read(file).matchAll(/var\((--[a-z0-9-]+)/g)) {
        if (!referenced.has(name)) referenced.set(name, new Set());
        referenced.get(name).add(rel(file));
      }
    }
    for (const [, name] of css.matchAll(/var\((--[a-z0-9-]+)/g)) {
      if (!referenced.has(name)) referenced.set(name, new Set());
      referenced.get(name).add('index.css');
    }

    const dangling = [...referenced]
      .filter(([name]) => !declared.has(name))
      .map(([name, where]) => `${name} in ${[...where].join(', ')}`)
      .sort();

    expect(dangling).toEqual([]);
  });
});
