import { test, expect } from '@playwright/test';

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

async function ensureDriverAvailable(email: string) {
  try {
    const authRes = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: 'Password123!' }),
    });
    if (!authRes.ok) return;
    const auth = await authRes.json();
    await fetch('http://localhost:8080/api/drivers/status?status=Available', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${auth.token}` },
    });
    await fetch('http://localhost:8080/api/drivers/location', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ latitude: 13.0418, longitude: 80.2341 }),
    });
  } catch (err) {
    console.error('Failed to ensure driver available:', err);
  }
}

test.describe('BidDrive End-to-End Ride Lifecycle Tests', () => {
  test.beforeEach(async ({ page }) => {
    await cleanupActiveRides('passenger.test@demo.com');
    await ensureDriverAvailable('driver.test@demo.com');
    // Start from login page
    await page.goto('/login');
  });

  test('1. Passenger Authentication Flow', async ({ page }) => {
    // Fill credentials
    await page.fill('input[type="email"]', 'passenger.test@demo.com');
    await page.fill('input[type="password"]', 'Password123!');
    await page.click('button[type="submit"]');

    // Should redirect to passenger area
    await expect(page).toHaveURL(/\/passenger/);
    await expect(page.getByRole('heading', { name: 'Passenger Dashboard' })).toBeVisible({ timeout: 10000 });
  });

  test('2. Complete Booking & Driver Lifecycle via In-UI Dev Simulator', async ({ page }) => {
    // Login as Passenger
    await page.fill('input[type="email"]', 'passenger.test@demo.com');
    await page.fill('input[type="password"]', 'Password123!');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/passenger/);

    // Navigate to Book Ride
    await page.goto('/passenger/book');
    await page.waitForLoadState('networkidle');

    // Click Destination input to open location list
    const destInput = page.getByText('Where are you going?').first();
    await destInput.click();

    // Select Guindy from location drawer
    const hsrOption = page.locator('text=Guindy').first();
    await expect(hsrOption).toBeVisible({ timeout: 8000 });
    await hsrOption.click();

    // Select Auto vehicle
    const autoVehicle = page.locator('text=Auto').first();
    if (await autoVehicle.isVisible()) {
      await autoVehicle.click();
    }

    // Book Instant Ride
    const bookNowBtn = page.locator('button:has-text("Book now")');
    await expect(bookNowBtn).toBeVisible({ timeout: 8000 });
    await bookNowBtn.click();

    // Verify redirected to ride details
    await expect(page).toHaveURL(/\/passenger\/rides\/\d+/, { timeout: 15000 });

    // Verify Driver Card renders with Boarding PIN (OTP)
    await expect(page.locator('text=BOARDING VERIFICATION PIN')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('text=Google Maps')).toBeVisible();

    // Verify Dev Simulator Driver Controls are visible
    await expect(page.locator('text=Dev Simulator • Driver Controls')).toBeVisible();

    // Step A: Driver Heading to Pickup
    const arrivingBtn = page.locator('button:has-text("Driver Heading to Pickup")');
    if (await arrivingBtn.isVisible()) {
      await arrivingBtn.click();
      await page.waitForTimeout(1000);
    }

    // Step B: Driver Arrived at Pickup Spot
    const arrivedBtn = page.locator('button:has-text("Driver Arrived at Pickup Spot")');
    await expect(arrivedBtn).toBeVisible({ timeout: 10000 });
    await arrivedBtn.click();
    await page.waitForTimeout(1500);

    // Step C: Driver Enters OTP & Starts Trip
    const startTripBtn = page.locator('button:has-text("Driver Enters OTP")');
    if (!await startTripBtn.isVisible()) {
      if (await arrivedBtn.isVisible()) {
        await arrivedBtn.click();
        await page.waitForTimeout(1500);
      }
    }
    await expect(startTripBtn).toBeVisible({ timeout: 10000 });
    await startTripBtn.click();
    await page.waitForTimeout(1500);

    // Step D: Driver Reaches Destination & Completes Trip
    const completeBtn = page.locator('button:has-text("Complete Trip")');
    await expect(completeBtn).toBeVisible({ timeout: 10000 });
    await completeBtn.click();

    // Verify Rating Card appears
    await expect(page.getByRole('heading', { name: 'Rate Your Driver' })).toBeVisible({ timeout: 10000 });

    // Submit 5-star rating
    const starBtn = page.locator('div.card button:has-text("⭐")').last();
    if (await starBtn.isVisible()) {
      await starBtn.click();
      await expect(page.locator('text=Thanks for rating!')).toBeVisible({ timeout: 8000 });
      await page.waitForTimeout(3000);
    }
  });
});

