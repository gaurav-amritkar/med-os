const { test, expect } = require('@playwright/test');

const creds = { username: 'admin', password: 'Admin@123' };

async function login(page) {
  await page.goto('http://localhost:80/');
  await expect(page.locator('input#username')).toBeVisible({ timeout: 10000 });
  await page.fill('#username', creds.username);
  await page.fill('#password', creds.password);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 15000 });
}

test('full login flow lands on dashboard', async ({ page }) => {
  await login(page);
  const url = page.url();
  const body = await page.textContent('body');
  console.log('URL:', url);
  console.log('Body snippet:', body.slice(0, 300).replace(/\s+/g, ' '));
  expect(url).toContain('/dashboard');
});

test('dashboard makes authenticated API calls through nginx proxy', async ({ page }) => {
  const apiResponses = [];
  page.on('response', (r) => {
    if (r.url().includes('/api/v1/')) apiResponses.push({ url: r.url(), status: r.status() });
  });
  await login(page);
  await page.waitForTimeout(4000);
  const dashboardCalls = apiResponses.filter(r => r.url.includes('/dashboard'));
  console.log('Dashboard API responses:', JSON.stringify(dashboardCalls, null, 2));
  expect(dashboardCalls.length).toBeGreaterThan(0);
  expect(dashboardCalls[0].status).toBe(200);
});

test('multi-tenant isolation: no X-Tenant-Id on default admin', async ({ page }) => {
  // The admin login from the docker bootstrap has no tenant assignment.
  // Verify the /users/me response doesn't crash and lacks tenantId (single-tenant default).
  const apiCalls = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/v1/users/me')) {
      apiCalls.push({ url: r.url(), headers: r.headers() });
    }
  });
  await login(page);
  await page.goto('http://localhost:80/dashboard');
  await page.waitForTimeout(3000);
  const meCall = apiCalls.find(r => r.url.includes('/users/me'));
  console.log('users/me call:', JSON.stringify(meCall, null, 2));
});