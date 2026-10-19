import { test, expect, devices } from '@playwright/test';

test.use({
  ...devices['iPhone 14'],
  browserName: 'chromium',
  channel: 'chrome',
  geolocation: { latitude: 12.8406, longitude: 80.1534 },
  permissions: ['geolocation'],
});

test('Test RideMap on iPhone', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[type="email"]', 'passenger.test@demo.com');
  await page.fill('input[type="password"]', 'Password123!');
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/passenger/);

  // Navigate to /passenger/track or book
  await page.goto('/passenger/track');
  await page.waitForLoadState('networkidle');

  // Capture screenshot of track page with RideMap
  await page.screenshot({
    path: 'C:/Users/Naveen kasi/.gemini/antigravity-ide/brain/1ed1ef64-59f7-472b-807f-c65b43c0f8e8/iphone_ridemap_track.png',
  });
  console.log('Track screenshot captured!');
});
