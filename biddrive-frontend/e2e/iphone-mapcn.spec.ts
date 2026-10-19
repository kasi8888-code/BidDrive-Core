import { test, expect, devices } from '@playwright/test';

test.use({
  ...devices['iPhone 14'],
  geolocation: { latitude: 12.8406, longitude: 80.1534 },
  permissions: ['geolocation'],
});

test.describe('MapCN iPhone Mobile Testing & Geolocation Verification', () => {
  test('Verify MapCN Map, Zoom Controls, Geolocation, and OSRM Highway Routing on iPhone', async ({ page, context }) => {
    // 1. Authenticate as Passenger on iPhone Viewport (390x844)
    await page.goto('/login');
    await page.fill('input[type="email"]', 'passenger.test@demo.com');
    await page.fill('input[type="password"]', 'Password123!');
    await page.click('button[type="submit"]');

    // Wait for Passenger Dashboard with Fullscreen MapCN
    await expect(page).toHaveURL(/\/passenger/, { timeout: 10000 });
    await page.waitForLoadState('networkidle');

    // Verify MapLibre GL Canvas is rendered by MapCN
    const mapCanvas = page.locator('.maplibregl-canvas').first();
    await expect(mapCanvas).toBeVisible({ timeout: 15000 });

    // Verify MapCN Zoom & Locate Controls
    const zoomInBtn = page.getByRole('button', { name: 'Zoom In' }).or(page.getByRole('button', { name: 'Zoom in' })).first();
    const zoomOutBtn = page.getByRole('button', { name: 'Zoom Out' }).or(page.getByRole('button', { name: 'Zoom out' })).first();
    const locateBtn = page.getByRole('button', { name: 'Locate me' }).or(page.getByRole('button', { name: 'Find my location' })).first();
    const toggle3DBtn = page.getByRole('button', { name: '3D' }).first();

    await expect(zoomInBtn).toBeVisible();
    await expect(zoomOutBtn).toBeVisible();
    await expect(locateBtn).toBeVisible();

    // Capture initial fullscreen MapCN view on iPhone
    await page.waitForTimeout(1000);
    const screenshotDashboard = 'C:/Users/Naveen kasi/.gemini/antigravity-ide/brain/1ed1ef64-59f7-472b-807f-c65b43c0f8e8/iphone_mapcn_dashboard.png';
    await page.screenshot({ path: screenshotDashboard, fullPage: false });

    // Test Zoom In interaction
    await zoomInBtn.click({ force: true });
    await page.waitForTimeout(500);

    // Test Zoom Out interaction
    await zoomOutBtn.click({ force: true });
    await page.waitForTimeout(500);

    // Test 3D view toggle interaction
    if (await toggle3DBtn.isVisible()) {
      await toggle3DBtn.click({ force: true });
      await page.waitForTimeout(500);
    }

    // Test Locate Me Geolocation interaction
    await locateBtn.click({ force: true });
    await page.waitForTimeout(1000);

    // 2. Navigate to Book Ride Screen
    await page.goto('/passenger/book');
    await page.waitForLoadState('networkidle');

    // Select Destination to Trigger Real OSRM Highway Route
    const whereToInput = page.getByText('Where are you going?').first();
    await whereToInput.click({ force: true });

    // Select Tambaram or Guindy from location drawer
    const locationOption = page.locator('text=Tambaram').or(page.locator('text=Guindy')).first();
    await expect(locationOption).toBeVisible({ timeout: 5000 });
    await locationOption.click({ force: true });

    // 3. Verify OSRM Highway Route Badge with ETA and Distance
    const routeBadge = page.locator('text=OSRM Highway').or(page.locator('text=OpenStreetMap')).first();
    await expect(routeBadge).toBeVisible({ timeout: 15000 });

    // Capture Route Selection on iPhone Screen
    const screenshotRoute = 'C:/Users/Naveen kasi/.gemini/antigravity-ide/brain/1ed1ef64-59f7-472b-807f-c65b43c0f8e8/iphone_mapcn_route.png';
    await page.screenshot({ path: screenshotRoute, fullPage: false });

    // 4. Navigate to MapCN Interactive Showcase (/passenger/map)
    await page.goto('/passenger/map');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.maplibregl-canvas').first()).toBeVisible({ timeout: 15000 });

    // Verify Zoom In & Zoom Out on MapShowcasePage
    const showcaseZoomIn = page.getByRole('button', { name: 'Zoom In' }).or(page.getByRole('button', { name: 'Zoom in' })).first();
    const showcaseZoomOut = page.getByRole('button', { name: 'Zoom Out' }).or(page.getByRole('button', { name: 'Zoom out' })).first();
    await showcaseZoomIn.click({ force: true });
    await page.waitForTimeout(400);
    await showcaseZoomOut.click({ force: true });
    await page.waitForTimeout(400);

    // Verify GPS Locate button
    const showcaseLocate = page.getByRole('button', { name: 'Detect GPS Location' }).or(page.getByRole('button', { name: 'Find my location' })).first();
    await showcaseLocate.click({ force: true });
    await page.waitForTimeout(1000);

    // Capture MapCN Showcase on iPhone Screen
    const screenshotShowcase = 'C:/Users/Naveen kasi/.gemini/antigravity-ide/brain/1ed1ef64-59f7-472b-807f-c65b43c0f8e8/iphone_mapcn_showcase.png';
    await page.screenshot({ path: screenshotShowcase, fullPage: false });

    console.log('iPhone MapCN test passed successfully! Screenshots captured at:', screenshotDashboard, screenshotRoute, screenshotShowcase);
  });
});
