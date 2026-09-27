import { test, expect } from '@playwright/test';

/**
 * Smoke suite: the critical path a receptionist and a clinician take on a
 * working day, driven through the real UI against the Docker stack.
 *
 * Requires the stack to be up and the app published on E2E_BASE_URL (default
 * http://localhost:8080, which is the only published port — the backend is
 * reachable only through nginx).
 *
 * Credentials come from the environment, never from source. The admin password
 * is read from the deployment .env by the caller:
 *   E2E_PASSWORD=... npm run test:e2e
 */

const PASSWORD = process.env.E2E_PASSWORD;

test.skip(!PASSWORD, 'E2E_PASSWORD is not set');

/** Fails the test on any browser-side error, because "the page rendered" is
 *  not the same as "the page worked". */
const withCleanConsole = async (page, errors) => {
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`console: ${m.text()}`);
  });
  page.on('pageerror', (e) => errors.push(`pageerror: ${e.message}`));
  page.on('response', (r) => {
    if (r.status() >= 500) errors.push(`http ${r.status()} ${r.url()}`);
  });
};

test('a user can sign in and reach every page they are allowed to see', async ({ page }) => {
  const errors = [];
  await withCleanConsole(page, errors);

  await page.goto('/login');

  await page.getByLabel(/username/i).fill('admin');
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();

  await expect(page).toHaveURL(/\/dashboard/);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  // Every role-scoped page an admin may open.
  for (const path of [
    '/patients',
    '/encounters',
    '/admissions',
    '/pharmacy',
    '/billing',
  ]) {
    await page.goto(path);
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.locator('main#main')).toBeVisible();
  }

  expect(errors, 'browser reported errors').toEqual([]);
});

test('every field on the sign-in form renders legible text', async ({ page }) => {
  await page.goto('/login');

  // The original defect: inputs were white text on a near-white background.
  for (const label of [/username/i, /password/i]) {
    const field = page.getByLabel(label);
    await expect(field).toBeVisible();

    const { color, backgroundColor } = await field.evaluate((el) => {
      const s = getComputedStyle(el);
      return { color: s.color, backgroundColor: s.backgroundColor };
    });

    const parse = (rgb) => (rgb.match(/\d+/g) || []).slice(0, 3).map(Number);
    const lum = ([r, g, b]) =>
      [r, g, b]
        .map((c) => c / 255)
        .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
        .reduce((sum, c, i) => sum + c * [0.2126, 0.7152, 0.0722][i], 0);

    const [fg, bg] = [lum(parse(color)), lum(parse(backgroundColor))].sort(
      (a, b) => b - a
    );
    const ratio = (fg + 0.05) / (bg + 0.05);

    // 4.5:1 is WCAG 2.2 1.4.3 for text. The defect this guards was ~1.05:1.
    expect(ratio, `contrast for ${label} (${color} on ${backgroundColor})`).toBeGreaterThanOrEqual(4.5);
  }
});

test('a failed sign-in surfaces a visible, announced error', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel(/username/i).fill('admin');
  await page.getByLabel(/password/i).fill('definitely-not-the-password');
  await page.getByRole('button', { name: /sign in/i }).click();

  // WCAG 2.2 4.1.3: the status region exists and is announced.
  const live = page.locator('[role="status"]');
  await expect(live).toHaveCount(1);
  await expect(page.locator('[role="alert"]')).toBeVisible();

  // Still on the login page, no token issued.
  await expect(page).toHaveURL(/\/login/);
});

test('keyboard focus is visible on the sign-in form', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel(/username/i).focus();

  const outline = await page
    .getByLabel(/username/i)
    .evaluate((el) => getComputedStyle(el).outlineStyle);

  // 2.4.7 Focus Visible. The original theme had no :focus-visible rule at all.
  expect(outline).not.toBe('none');
});
