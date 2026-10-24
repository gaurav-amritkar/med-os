const { test, expect } = require('@playwright/test');

/**
 * The appointments journey, end to end against the real stack:
 * book from the board -> check in -> the day-token is issued and named -> the
 * queue board shows the waiting patient and the token -> the consultation
 * moves and the now-serving panel follows.
 *
 * Requires the stack up, the frontend on E2E_BASE_URL (default
 * http://localhost:8080) and E2E_PASSWORD in the environment — same contract
 * as login.spec.js. workers: 1 comes from the config: the suite shares one
 * seeded database, Aman Khan booked twice in parallel would be two tokens and
 * two truthes for one assertion.
 */

const PASSWORD = process.env.E2E_PASSWORD;
const PATIENT_SEARCH = 'Aman';

test.skip(!PASSWORD, 'E2E_PASSWORD is not set; see login.spec.js header');

async function login(page) {
  await page.goto('/login');
  await page.fill('#username', 'admin');
  await page.fill('#password', PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 15000 });
}

test('booking, check-in and the day-token flow readably from the board to the queue', async ({ page }) => {
  await login(page);

  // Book from the board — patient picker, doctor, type, reason. The time
  // field ships prefilled with "now + 10 minutes"; the desk would take it.
  await page.goto('/appointments');
  await page.getByRole('button', { name: /book appointment/i }).click();

  const patientSearch = page.getByLabel('Patient');
  await patientSearch.fill(PATIENT_SEARCH);
  const chosen = page.getByRole('button', { name: new RegExp(PATIENT_SEARCH) }).first();
  await chosen.waitFor({ state: 'visible', timeout: 10000 });
  const chosenLabel = await chosen.innerText();
  await chosen.click();

  await page.getByLabel('Doctor').selectOption({ index: 1 });
  await page.getByRole('radio', { name: /follow-up/i }).check();
  await page.getByLabel('Reason').fill('Journey check: book, check in, watch the token move');
  await page.getByRole('button', { name: /^Book$/ }).click();
  await expect(page.getByText(new RegExp(`Booked ${chosenLabel.split(' ·')[0]}`), { exact: false })).toBeVisible({ timeout: 10000 });

  // Check in the patient we just booked — the toast must SAY the token,
  // because a silent success teaches nobody how tokens are given.
  const bookerRow = page
    .locator('tr', { hasText: chosenLabel.split(' ·')[0] })
    .filter({ has: page.getByRole('button', { name: /check in/i }) })
    .first();
  await bookerRow.getByRole('button', { name: /check in/i }).click();
  const toast = page.locator('.toast', { hasText: /token \d+ issued/i });
  await expect(toast).toBeVisible({ timeout: 10000 });
  const tokenNo = Number((await toast.textContent()).match(/token (\d+)/i)[1]);

  // The queue board: token order, the caption, and the token we just issued.
  await page.goto('/appointments/queue');
  await expect(page.getByText(/issued at check-in — one per appointment, reset each morning/i)).toBeVisible();
  const queueRow = page.locator('tr', { hasText: `Token ${tokenNo}` }).first();
  await expect(queueRow).toBeVisible();

  // Move the consultation from the board; the now-serving panel follows.
  await queueRow.getByRole('button', { name: /^start$/i }).click();
  const nowServing = page.locator('.queue-now');
  await expect(nowServing).toContainText(`Token ${tokenNo}`, { timeout: 10000 });
  await expect(nowServing).toContainText(/now serving/i);
});
