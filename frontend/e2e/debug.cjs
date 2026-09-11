const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  // Capture console + failed requests
  const logs = [];
  page.on('console', (m) => logs.push(`[console.${m.type()}] ${m.text()}`));
  page.on('pageerror', (e) => logs.push(`[pageerror] ${e.message}`));
  page.on('requestfailed', (r) => logs.push(`[reqfail] ${r.url()} ${r.failure()?.errorText}`));
  const api = [];
  page.on('response', (r) => { if (r.url().includes('/api/')) api.push(`${r.status()} ${r.url()}`); });

  await page.goto('http://localhost:80/');
  await page.waitForSelector('#username', { timeout: 10000 });
  await page.fill('#username', 'admin');
  await page.fill('#password', 'Admin@123');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 15000 });
  await page.waitForTimeout(4000);

  const url = page.url();
  const body = (await page.textContent('body')) || '';
  const token = await page.evaluate(() => localStorage.getItem('medos_token') || localStorage.getItem('token') || 'NO_TOKEN');
  const stored = await page.evaluate(() => JSON.stringify(localStorage));

  console.log('URL:', url);
  console.log('Token present:', token !== 'NO_TOKEN');
  console.log('localStorage:', stored.slice(0, 500));
  console.log('Body (2000):', body.replace(/\s+/g, ' ').slice(0, 2000));
  console.log('--- API calls ---');
  api.forEach((a) => console.log(a));
  console.log('--- LOGS ---');
  logs.forEach((l) => console.log(l));

  await browser.close();
})();