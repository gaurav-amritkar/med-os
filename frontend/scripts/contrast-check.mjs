#!/usr/bin/env node
/**
 * Contrast guard for the MedOS token layer.
 *
 * Reads the token values out of src/index.css — the single source of truth —
 * and fails the build if any pair we actually render falls below its WCAG 2.2
 * threshold: 4.5:1 for text (1.4.3), 3:1 for non-text and focus indicators
 * (1.4.11).
 *
 * This exists because the previous theme shipped white text on a near-white
 * background and no check noticed. A value that is only ever verified by eye
 * is a value that will drift.
 *
 * Run: npm run check:contrast
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const cssPath = resolve(here, '../src/index.css');
const css = readFileSync(cssPath, 'utf8');

/** Expand #abc to #aabbcc so the parser handles both forms. */
const normalise = (hex) => {
  const h = hex.replace('#', '');
  return h.length === 3 ? `#${[...h].map((c) => c + c).join('')}` : `#${h}`;
};

/**
 * Collect token values from the :root block. A token may be redefined later in
 * a preference block (prefers-contrast, forced-colors); the first definition
 * wins, because that is the value rendered by default. Taking the last would
 * silently validate the high-contrast override instead of the default.
 */
const tokens = {};
for (const [, name, hex] of css.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{3,8})\s*;/g)) {
  if (!(name in tokens)) tokens[name] = normalise(hex);
}

/** [foreground, background, minimum ratio, success criterion] */
const PAIRS = [
  ['ink', 'surface', 4.5, '1.4.3'],
  ['ink', 'canvas', 4.5, '1.4.3'],
  ['ink', 'sunken', 4.5, '1.4.3'],
  ['ink-muted', 'surface', 4.5, '1.4.3'],
  ['ink-muted', 'canvas', 4.5, '1.4.3'],
  ['ink-muted', 'sunken', 4.5, '1.4.3'],
  ['ink-faint', 'surface', 4.5, '1.4.3'],
  ['ink-faint', 'canvas', 4.5, '1.4.3'],
  ['ink-faint', 'sunken', 4.5, '1.4.3'],
  ['critical', 'surface', 4.5, '1.4.3'],
  ['critical', 'critical-tint', 4.5, '1.4.3'],
  ['critical', 'surface-solid', 4.5, '1.4.3'],
  ['urgent', 'surface', 4.5, '1.4.3'],
  ['urgent', 'urgent-tint', 4.5, '1.4.3'],
  ['normal', 'surface', 4.5, '1.4.3'],
  ['normal', 'normal-tint', 4.5, '1.4.3'],
  ['info', 'surface', 4.5, '1.4.3'],
  ['info', 'info-tint', 4.5, '1.4.3'],
  ['action', 'surface', 4.5, '1.4.3'],
  ['action', 'surface-solid', 4.5, '1.4.3'],
  ['surface', 'action', 4.5, '1.4.3'],
  ['frame-ink', 'frame', 4.5, '1.4.3'],
  ['frame-muted', 'frame', 4.5, '1.4.3'],
  ['frame-ink', 'frame-hover', 4.5, '1.4.3'],
  ['line', 'surface', 3, '1.4.11'],
  ['line', 'canvas', 3, '1.4.11'],
  ['line', 'sunken', 3, '1.4.11'],
  ['action', 'canvas', 3, '1.4.11 focus'],
  ['action', 'sunken', 3, '1.4.11 focus'],
];

const channel = (hex) => {
  const h = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
};

const luminance = (hex) => {
  const [r, g, b] = channel(hex).map((c) =>
    c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  );
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

let failures = 0;
const missing = new Set();

for (const [fg, bg, min, sc] of PAIRS) {
  for (const name of [fg, bg]) {
    if (!tokens[name]) missing.add(name);
  }
  if (!tokens[fg] || !tokens[bg]) {
    failures++;
    console.error(`FAIL  --  missing token --${tokens[fg] ? bg : fg}  (needed for ${sc})`);
    continue;
  }
  const value = ratio(tokens[fg], tokens[bg]);
  const ok = value >= min;
  if (!ok) failures++;
  console.log(
    `${ok ? 'PASS' : 'FAIL'}  ${value.toFixed(2)}:1  (min ${min}, ${sc})  --${fg} on --${bg}`
  );
}

if (missing.size) {
  console.error(`\nTokens declared in :root but never used in a checked pair: ${[...missing].join(', ')}`);
}

console.log(
  failures === 0
    ? `\nAll ${PAIRS.length} token pairs pass.`
    : `\n${failures} failure(s).`
);
process.exit(failures === 0 ? 0 : 1);
