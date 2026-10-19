import { test, expect } from '@playwright/test';

test('verify MapControls compass and zoom controls render without overflow', async ({ page }) => {
  await page.goto('http://localhost:3000/login');
  await page.fill('input[type="email"]', 'passenger.test@demo.com');
  await page.fill('input[type="password"]', 'Password123!');
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/passenger/);

  await page.goto('http://localhost:3000/passenger/map');
  await page.waitForSelector('.maplibregl-canvas');
  await page.waitForTimeout(2000);

  // Check compass SVG size
  const compassSize = await page.evaluate(() => {
    const compassSvg = document.querySelector('button[aria-label="Reset bearing to north"] svg');
    if (!compassSvg) return null;
    const rect = compassSvg.getBoundingClientRect();
    return { width: rect.width, height: rect.height };
  });

  if (compassSize) {
    expect(compassSize.width).toBeLessThanOrEqual(24);
    expect(compassSize.height).toBeLessThanOrEqual(24);
  }

  // Check zoom buttons are visible and clickable
  const zoomIn = page.getByRole('button', { name: 'Zoom in' }).first();
  const zoomOut = page.getByRole('button', { name: 'Zoom out' }).first();
  await expect(zoomIn).toBeVisible();
  await expect(zoomOut).toBeVisible();
});
