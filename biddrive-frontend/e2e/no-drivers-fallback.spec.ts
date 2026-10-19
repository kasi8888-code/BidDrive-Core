import { test, expect } from '@playwright/test';

// Helper to set driver status and location in Redis
async function setDriverStatus(email: string, status: string) {
  try {
    const authRes = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: 'Password123!' }),
    });
    if (!authRes.ok) return;
    const auth = await authRes.json();
    await fetch(`http://localhost:8080/api/drivers/status?status=${status}`, {
      method: 'PUT',
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    if (status === 'Available') {
      await fetch('http://localhost:8080/api/drivers/location', {
        method: 'PUT',
        headers: { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ latitude: 13.0418, longitude: 80.2341 }),
      });
    }
  } catch (err) {
    console.error('Failed to set driver status:', err);
  }
}

// Helper to auto-heal active ride state before test runs
async function cleanupActiveRides(email: string) {
  try {
    const authRes = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: 'Password123!' }),
    });
    if (!authRes.ok) return;
    const auth = await authRes.json();
    const ridesRes = await fetch('http://localhost:8080/api/rides', {
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    if (!ridesRes.ok) return;
    const rides = await ridesRes.json();
    const activeRides = rides.filter((r: any) =>
      r.passengerId === auth.userId && !['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(r.status)
    );
    for (const r of activeRides) {
      if (r.status === 'INITIATED') {
        await fetch(`http://localhost:8080/api/rides/${r.id}/cancel`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ cancellationReason: 'Test cleanup' }),
        });
      } else {
        for (let i = 0; i < 4; i++) {
          await fetch(`http://localhost:8080/api/rides/${r.id}/simulate-driver-step`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${auth.token}` },
          });
        }
      }
    }
  } catch (err) {
    console.error('Cleanup error:', err);
  }
}

test.describe('Edge Case: No Nearby Drivers Found Flow', () => {
  test.beforeEach(async () => {
    await cleanupActiveRides('passenger.test@demo.com');
  });

  test.afterEach(async () => {
    // Restore driver availability for subsequent tests
    await setDriverStatus('driver.test@demo.com', 'Available');
  });

  test('Instant ride searches nearby drivers, fails gracefully when offline, and allows switching to Reverse Auction', async ({ page }) => {
    // 1. Mark driver Offline
    await setDriverStatus('driver.test@demo.com', 'Offline');

    // 2. Login as Passenger
    await page.goto('/login');
    await page.fill('input[type="email"]', 'passenger.test@demo.com');
    await page.fill('input[type="password"]', 'Password123!');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/passenger/);

    // 3. Go to Book Ride page
    await page.goto('/passenger/book');
    await page.waitForLoadState('networkidle');

    // 4. Select destination (Guindy)
    const destInput = page.getByText('Where are you going?').first();
    await destInput.click();
    const destOption = page.locator('text=Guindy').first();
    await expect(destOption).toBeVisible({ timeout: 8000 });
    await destOption.click();

    // 5. Select vehicle category (SUV has no registered drivers in the area)
    const suvOption = page.locator('text=SUV').first();
    if (await suvOption.isVisible()) {
      await suvOption.click();
    }

    // 6. Click Instant Ride booking button
    const bookNowBtn = page.locator('button:has-text("Book now")');
    await expect(bookNowBtn).toBeVisible({ timeout: 8000 });
    await bookNowBtn.click();

    // 7. Verify Radar Searching Overlay appears with progressive scan steps
    const radarOverlay = page.locator('[data-testid="radar-searching-overlay"]');
    await expect(radarOverlay).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=Ola / Rapido Smart Dispatch')).toBeVisible();

    // 8. Verify No Drivers Modal appears after dispatch search fails
    const noDriversModal = page.locator('[data-testid="no-drivers-modal"]');
    await expect(noDriversModal).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'No Nearby Drivers Found' })).toBeVisible();

    // 9. Switch to Reverse Auction from the fallback dialog
    const switchAuctionBtn = page.getByRole('button', { name: /Switch to Reverse Auction/i });
    await expect(switchAuctionBtn).toBeVisible();
    await switchAuctionBtn.click();

    // 10. Verify redirected to live Reverse Auction room
    await expect(page).toHaveURL(/\/passenger\/rides\/\d+/, { timeout: 15000 });
    await expect(page.getByRole('heading', { name: 'Live Bids' })).toBeVisible({ timeout: 10000 });
  });
});
