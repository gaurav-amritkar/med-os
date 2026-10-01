const { test, expect } = require('@playwright/test');

// Base URL comes from the environment, like smoke.spec.js. The development
// stack publishes the frontend on FRONTEND_EXTERNAL_PORT (8080 by default);
// port 80 is only correct in production, where Caddy terminates TLS.
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8080';
const PASSWORD = process.env.E2E_PASSWORD;

test.describe('end-to-end journeys', () => {
  test.skip(!PASSWORD, 'E2E_PASSWORD is not set; see README "End-to-end tests"');

  const creds = { username: 'admin', password: PASSWORD };

  async function login(page) {
    await page.goto(`${BASE_URL}/`);
    await expect(page.locator('input#username')).toBeVisible({ timeout: 10000 });
    await page.fill('#username', creds.username);
    await page.fill('#password', creds.password);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/dashboard**', { timeout: 15000 });
  }

  test('full login flow lands on dashboard', async ({ page }) => {
    await login(page);
    expect(page.url()).toContain('/dashboard');
  });

  test('dashboard makes authenticated API calls through the frontend proxy', async ({ page }) => {
    const apiResponses = [];
    page.on('response', (r) => {
      if (r.url().includes('/api/v1/')) apiResponses.push({ url: r.url(), status: r.status() });
    });
    await login(page);
    await page.waitForTimeout(4000);
    const dashboardCalls = apiResponses.filter((r) => r.url.includes('/dashboard'));
    expect(dashboardCalls.length).toBeGreaterThan(0);
    expect(dashboardCalls[0].status).toBe(200);
  });

  // /users/me must not disclose the password hash. It previously returned the User
  // entity, so Jackson serialised passwordHash to any authenticated caller.
  //
  // The request is made directly rather than by navigating, because no page calls
  // this endpoint: the UI reads identity from the login response. An earlier
  // version of this test waited for a call triggered by page load, observed none,
  // and failed for the wrong reason. Calling the endpoint also means the
  // assertion holds regardless of what the UI happens to do.
  test('/users/me never returns the password hash', async ({ page }) => {
    await login(page);

    // The auth store keeps the token in sessionStorage under 'medos_token'.
    const token = await page.evaluate(() => window.sessionStorage.getItem('medos_token'));
    expect(token, 'a token should be in storage after login').toBeTruthy();

    const response = await page.request.get(`${BASE_URL}/api/v1/users/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();

    expect(body.username).toBe(creds.username);
    expect(body.passwordHash).toBeUndefined();
    expect(body).not.toHaveProperty('password');
    expect(body).not.toHaveProperty('password_hash');
    // Persistence-only fields must not travel either.
    expect(body).not.toHaveProperty('createdAt');
    expect(body).not.toHaveProperty('lastLogin');
    // The acting tenant should be resolved, not absent.
    expect(body.tenantId).toBeTruthy();
  });
});
