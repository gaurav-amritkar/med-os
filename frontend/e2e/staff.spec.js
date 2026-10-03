import { test, expect } from '@playwright/test';

/**
 * Staff management, driven through the real UI against the Docker stack.
 *
 * Requires the stack to be up and the app published on E2E_BASE_URL (default
 * http://localhost:8080, the only published port).
 *   E2E_BASE_URL=http://localhost:8080 E2E_PASSWORD=... npm run test:e2e
 *
 * The point of this file is the whole path an admin takes to get a doctor into the
 * system: add them, then prove that person can actually sign in and reach a doctor
 * screen while being refused the admin-only staff page.
 */

const PASSWORD = process.env.E2E_PASSWORD;

test.skip(!PASSWORD, 'E2E_PASSWORD is not set');

// Unique per run, so repeated runs do not collide on the unique username constraint.
const stamp = Date.now().toString().slice(-8);
const DOCTOR = { username: `dr.e2e.${stamp}`, password: 'Temp@12345' };

test('an admin adds a doctor, and that doctor can sign in and is confined to their role', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel(/username/i).fill('admin');
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL('**/dashboard**', { timeout: 15000 });

  await page.goto('/staff');
  await expect(page.getByRole('heading', { name: 'Staff' })).toBeVisible();

  await page.getByRole('button', { name: /add staff/i }).click();

  // Scoped to the dialog: every roster row also renders a "Role for <name>" select, so an
  // unscoped /role/i locator is ambiguous once the table has any staff in it.
  const dialog = page.locator('.modal');
  await dialog.getByLabel(/full name/i).fill(`E2E Doctor ${stamp}`);
  await dialog.getByLabel(/username/i).fill(DOCTOR.username);
  await dialog.getByLabel(/role/i).selectOption('doctor');
  await dialog.getByLabel(/temporary password/i).fill(DOCTOR.password);
  await dialog.getByLabel(/email/i).fill(`${DOCTOR.username}@example.test`);

  await page.getByRole('button', { name: /^add staff$/i }).click();

  // The new person appears in the roster, flagged as still owing a password change.
  const row = page.locator('tr', { hasText: DOCTOR.username });
  await expect(row).toBeVisible({ timeout: 15000 });
  await expect(row).toContainText(/must change password/i);

  // Now sign in as that doctor, with no admin token left anywhere. The auth store persists to
  // sessionStorage as well as localStorage, so clearing only the latter left the admin
  // session alive and /login never rendered a form.
  await page.context().clearCookies();
  await page.evaluate(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });
  await page.goto('/login');
  await expect(page.locator('input#username')).toBeVisible({ timeout: 15000 });
  await page.getByLabel(/username/i).fill(DOCTOR.username);
  await page.getByLabel(/password/i).fill(DOCTOR.password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL('**/dashboard**', { timeout: 15000 });

  await page.goto('/patients');
  await expect(page.getByRole('heading', { name: 'Patients' })).toBeVisible();

  // A doctor must not be able to reach the staff page.
  await page.goto('/staff');
  await expect(page.getByRole('heading', { name: 'Staff' })).toHaveCount(0);
});
