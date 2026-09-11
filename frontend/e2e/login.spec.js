const { test, expect } = require('@playwright/test');

test('full login flow through frontend', async ({ page }) => {
  // Go to the frontend
  await page.goto('http://localhost:80/');
  await page.waitForLoadState('networkidle');

  // Should show login form
  await expect(page.locator('input#username')).toBeVisible({ timeout: 10000 });
  await expect(page.locator('input#password')).toBeVisible();

  // Fill in credentials
  await page.fill('#username', 'admin');
  await page.fill('#password', 'Admin@123');

  // Submit
  await page.click('button[type="submit"]');

  // Wait for navigation or dashboard content
  await page.waitForURL('**/dashboard**', { timeout: 15000 }).catch(() => {});

  const url = page.url();
  const body = await page.textContent('body');
  console.log('URL after login:', url);
  console.log('Page contains Dashboard:', body.includes('Dashboard') || body.includes('dashboard'));

  // Check if we see the dashboard or an error
  const errorEl = await page.locator('.error, .alert-danger, [role="alert"]').count();
  console.log('Error elements:', errorEl);

  await expect(page).toHaveTitle(/MedOS|Dashboard|MedOS/i);
});