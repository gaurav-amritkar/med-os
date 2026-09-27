import { defineConfig, devices } from '@playwright/test';

/**
 * Wave 0 ships the harness only. The critical-flow specs and the authenticated
 * screenshot baselines belong to the Stage 0 plan, which owns auth.setup.js.
 *
 * workers: 1 because the suites share a single database; parallel workers
 * would produce false failures that mask real ones.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 1,
  reporter: [['list']],
  outputDir: 'test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.js/ },
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
