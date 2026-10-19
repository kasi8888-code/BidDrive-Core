import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright Demo Configuration for Live Mentor Showcases
 * - Runs headed (real visible Chrome browser)
 * - Sets slowMo to 900ms so humans can easily follow each click, type, and transition
 * - Viewport sized cleanly for laptop screen presentations
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:3000',
    headless: false,
    launchOptions: {
      slowMo: 900, // 900ms delay between actions so mentors can watch every step
    },
    viewport: { width: 1280, height: 820 },
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    {
      name: 'chrome',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
      },
    },
  ],
});
