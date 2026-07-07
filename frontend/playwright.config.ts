import { defineConfig, devices } from '@playwright/test';

/**
 * Requires the backend + Postgres to already be running (see e2e/README.md).
 * The Vite dev server is started automatically and proxies /api and /ws to
 * http://localhost:8080, matching the project's normal dev-mode setup.
 */
export default defineConfig({
  testDir: './e2e',
  // All tests share one backend and one matchmaking queue (not isolated
  // per-worker infra), and fresh accounts all start at the same ELO — so
  // two tests queuing "Human" opponents at once could get cross-matched
  // with each other. Run serially to keep that race off the table.
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run dev -- --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
