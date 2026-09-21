# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: full.spec.js >> dashboard makes authenticated API calls through nginx proxy
- Location: full.spec.js:23:1

# Error details

```
TimeoutError: page.waitForURL: Timeout 15000ms exceeded.
=========================== logs ===========================
waiting for navigation to "**/dashboard**" until "load"
  navigated to "http://localhost/login"
============================================================
```

# Page snapshot

```yaml
- generic [ref=f1e4]:
  - generic [ref=f1e5]:
    - generic [ref=f1e6]: MEDOS
    - generic [ref=f1e7]: Hospital Management System v3.0
  - generic [ref=f1e8]:
    - generic [ref=f1e9]:
      - generic [ref=f1e10]: Username
      - textbox "Username" [active] [ref=f1e11]:
        - /placeholder: Enter username
    - generic [ref=f1e12]:
      - generic [ref=f1e13]: Password
      - textbox "Password" [ref=f1e14]:
        - /placeholder: Enter password
    - button "Sign In →" [ref=f1e15] [cursor=pointer]
  - generic [ref=f1e16]:
    - strong [ref=f1e17]: "Local Login Setup:"
    - text: "Fresh database: sign in as"
    - strong [ref=f1e18]: admin
    - text: with your
    - strong [ref=f1e19]: BOOTSTRAP_ADMIN_PASSWORD
    - text: ".Demo users: run"
    - code [ref=f1e20]: ./tools/seed-dev.sh
    - text: ", then use admin/doctor/nurse/reception/pharmacy/billing with password"
    - strong [ref=f1e21]: password
    - text: .
```

# Test source

```ts
  1  | const { test, expect } = require('@playwright/test');
  2  | 
  3  | const creds = { username: 'admin', password: 'Admin@123' };
  4  | 
  5  | async function login(page) {
  6  |   await page.goto('http://localhost:80/');
  7  |   await expect(page.locator('input#username')).toBeVisible({ timeout: 10000 });
  8  |   await page.fill('#username', creds.username);
  9  |   await page.fill('#password', creds.password);
  10 |   await page.click('button[type="submit"]');
> 11 |   await page.waitForURL('**/dashboard**', { timeout: 15000 });
     |              ^ TimeoutError: page.waitForURL: Timeout 15000ms exceeded.
  12 | }
  13 | 
  14 | test('full login flow lands on dashboard', async ({ page }) => {
  15 |   await login(page);
  16 |   const url = page.url();
  17 |   const body = await page.textContent('body');
  18 |   console.log('URL:', url);
  19 |   console.log('Body snippet:', body.slice(0, 300).replace(/\s+/g, ' '));
  20 |   expect(url).toContain('/dashboard');
  21 | });
  22 | 
  23 | test('dashboard makes authenticated API calls through nginx proxy', async ({ page }) => {
  24 |   const apiResponses = [];
  25 |   page.on('response', (r) => {
  26 |     if (r.url().includes('/api/v1/')) apiResponses.push({ url: r.url(), status: r.status() });
  27 |   });
  28 |   await login(page);
  29 |   await page.waitForTimeout(4000);
  30 |   const dashboardCalls = apiResponses.filter(r => r.url.includes('/dashboard'));
  31 |   console.log('Dashboard API responses:', JSON.stringify(dashboardCalls, null, 2));
  32 |   expect(dashboardCalls.length).toBeGreaterThan(0);
  33 |   expect(dashboardCalls[0].status).toBe(200);
  34 | });
  35 | 
  36 | test('multi-tenant isolation: no X-Tenant-Id on default admin', async ({ page }) => {
  37 |   // The admin login from the docker bootstrap has no tenant assignment.
  38 |   // Verify the /users/me response doesn't crash and lacks tenantId (single-tenant default).
  39 |   const apiCalls = [];
  40 |   page.on('request', (r) => {
  41 |     if (r.url().includes('/api/v1/users/me')) {
  42 |       apiCalls.push({ url: r.url(), headers: r.headers() });
  43 |     }
  44 |   });
  45 |   await login(page);
  46 |   await page.goto('http://localhost:80/dashboard');
  47 |   await page.waitForTimeout(3000);
  48 |   const meCall = apiCalls.find(r => r.url.includes('/users/me'));
  49 |   console.log('users/me call:', JSON.stringify(meCall, null, 2));
  50 | });
```