import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

// Check the actual shipped output, not just the rendered DOM: hiding the
// guidance with CSS or a runtime flag would still publish it to visitors.
const dist = fileURLToPath(new URL('../dist/', import.meta.url));
const files = readdirSync(dist, { recursive: true })
  .filter((file) => /\.(?:js|html|map)$/.test(file));
assert(files.includes('index.html'), 'Build first: dist/index.html is missing');
assert(files.some((file) => file.endsWith('.js')), 'No built JavaScript found');

const guidance = [
  'BOOTSTRAP_ADMIN_PASSWORD',
  'seed-dev.sh',
  'admin/doctor/nurse/reception/pharmacy/billing',
  'Demo users:',
];
for (const file of files) {
  const contents = readFileSync(join(dist, file), 'utf8');
  for (const text of guidance) {
    assert(!contents.includes(text), `${file} exposes local login guidance: ${text}`);
  }
}
console.log('Production build contains no local login credential guidance.');
