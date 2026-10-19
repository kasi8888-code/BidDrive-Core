import { test } from '@playwright/test';

test('debug maplibre layers', async ({ page }) => {
  await page.goto('http://localhost:3000/login');
  await page.fill('input[type="email"]', 'passenger.test@demo.com');
  await page.fill('input[type="password"]', 'Password123!');
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/passenger/);

  await page.goto('http://localhost:3000/passenger/track');
  await page.waitForTimeout(3000);

  const layerData = await page.evaluate(() => {
    // Look for maplibre map instance attached to container or window
    const container = document.querySelector('.maplibregl-map');
    // MapLibre attaches map to (container as any)._map or similar if we can find it
    const canvases = document.querySelectorAll('canvas');
    return {
      canvasesCount: canvases.length,
      html: container?.outerHTML?.slice(0, 500)
    };
  });
  console.log('layerData:', layerData);
});
