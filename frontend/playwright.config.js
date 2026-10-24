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
    // Drive the real installed Chrome instead of Playwright's bundled
    // chromium test build (~1.6G in ~/Library/Caches/ms-playwright).
    channel: 'chrome',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.js/ },
    // The channel override repeats here: project-level use replaces the
    // global use spread for the chromium project, so driving the installed
    // Chrome must be said twice.
    { name: 'chromium', use: { ...devices['Desktop Chrome'], channel: 'chrome' } },
  ],
});
