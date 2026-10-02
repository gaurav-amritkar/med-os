import { test, expect } from '@playwright/test';

/**
 * Login suite: the sign-in form's own contract, kept separate from the smoke suite's
 * wider "can a user get through and reach every page" walk.
 *
 * Requires the stack to be up and the app published on E2E_BASE_URL (default
 * http://localhost:8080, the only published port — the backend is reachable only
 * through nginx).
 *
 * Credentials come from the environment, never from source. This file used to hardcode
 * `http://localhost:80/`, a port nothing serves, and an admin password in plaintext.
 * Both are exactly the leak that #107 removed from full.spec.js.
 *   E2E_PASSWORD=... npm run test:e2e
 */

const PASSWORD = process.env.E2E_PASSWORD;

test.skip(!PASSWORD, 'E2E_PASSWORD is not set');

test('the sign-in form accepts valid credentials and lands on the dashboard', async ({ page }) => {
  await page.goto('/login');

  await expect(page.locator('input#username')).toBeVisible({ timeout: 10000 });
  await expect(page.locator('input#password')).toBeVisible();

  await page.fill('#username', 'admin');
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');

  await page.waitForURL('**/dashboard**', { timeout: 15000 });
  await expect(page).toHaveTitle(/MedOS/i);
});
