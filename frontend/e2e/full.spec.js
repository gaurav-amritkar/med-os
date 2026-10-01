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

  // /users/me must not disclose the password hash. This is asserted rather than
  // console.logged: the endpoint returned the User entity, so Jackson serialized
  // passwordHash to the client until this test existed. A regression re-introduces
  // a readable hash for any authenticated user.
  test('/users/me never returns the password hash', async ({ page }) => {
    const meBodies = [];
    page.on('response', async (r) => {
      if (r.url().includes('/api/v1/users/me')) {
        try {
          meBodies.push(await r.json());
        } catch {
          meBodies.push(null);
        }
      }
    });

    await login(page);
    await page.goto(`${BASE_URL}/dashboard`);
    await expect
      .poll(() => meBodies.length, { timeout: 10000, message: 'no /users/me call observed' })
      .toBeGreaterThan(0);

    const body = meBodies[0];
    expect(body).not.toBeNull();
    expect(body.username).toBe(creds.username);
    expect(body.passwordHash).toBeUndefined();
    expect(body).not.toHaveProperty('password');
  });
});
