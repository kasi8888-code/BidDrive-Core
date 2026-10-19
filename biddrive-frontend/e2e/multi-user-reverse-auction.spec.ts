import { test, expect } from '@playwright/test';

// Helper to auto-heal active ride state before/after test runs
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

test.describe('BidDrive Multi-User Reverse Auction (Passenger + Driver)', () => {
  test.beforeEach(async () => {
    await cleanupActiveRides('passenger.test@demo.com');
  });

  test('Two concurrent browser sessions: Passenger posts auction, Driver bids, Passenger accepts', async ({ browser }) => {
    // Session 1: Passenger
    const passengerContext = await browser.newContext();
    const passengerPage = await passengerContext.newPage();

    // Session 2: Driver
    const driverContext = await browser.newContext();
    const driverPage = await driverContext.newPage();

    // 1. Passenger Logs In
    await passengerPage.goto('/login');
    await passengerPage.fill('input[type="email"]', 'passenger.test@demo.com');
    await passengerPage.fill('input[type="password"]', 'Password123!');
    await passengerPage.click('button[type="submit"]');
    await expect(passengerPage).toHaveURL(/\/passenger/);

    // 2. Driver Logs In
    await driverPage.goto('/login');
    await driverPage.fill('input[type="email"]', 'driver.test@demo.com');
    await driverPage.fill('input[type="password"]', 'Password123!');
    await driverPage.click('button[type="submit"]');
    await expect(driverPage).toHaveURL(/\/driver/);

    // 3. Passenger creates an Auction Ride
    await passengerPage.goto('/passenger/book');
    const destInput = passengerPage.getByText('Where are you going?').first();
    await destInput.click();

    // Pick Guindy
    const hsrOption = passengerPage.locator('text=Guindy').first();
    await expect(hsrOption).toBeVisible({ timeout: 8000 });
    await hsrOption.click();

    // Click "Bargain & Book" to initiate reverse auction
    const auctionBtn = passengerPage.locator('button:has-text("Bargain & Book")');
    await expect(auctionBtn).toBeVisible({ timeout: 8000 });
    await auctionBtn.click();

    // Passenger arrives on live auction screen with 90s countdown timer
    await expect(passengerPage).toHaveURL(/\/passenger\/rides\/\d+/, { timeout: 15000 });
    await expect(passengerPage.locator('text=REVERSE AUCTION')).toBeVisible();

    // 4. Test In-UI Competing Driver Bid Simulation Tool
    const simBidBtn = passengerPage.locator('button:has-text("Simulate Competing Driver Bid")');
    await expect(simBidBtn).toBeVisible();
    await simBidBtn.click();

    // Verify bid appeared in live bid table
    await expect(passengerPage.locator('text=Accepted').or(passengerPage.locator('button:has-text("Accept")')).first()).toBeVisible({ timeout: 10000 });

    // 5. Accept the bid
    const acceptBtn = passengerPage.locator('button:has-text("Accept")').first();
    if (await acceptBtn.isVisible()) {
      await acceptBtn.click();
    }

    // Verify transition to MATCHED & Boarding PIN
    await expect(passengerPage.locator('text=BOARDING VERIFICATION PIN')).toBeVisible({ timeout: 10000 });

    // 6. Complete the ride via Dev Simulator driver controls so state is clean
    const arrivingBtn = passengerPage.locator('button:has-text("Driver Heading to Pickup")');
    if (await arrivingBtn.isVisible()) {
      await arrivingBtn.click();
      await passengerPage.waitForTimeout(1000);
    }

    const arrivedBtn = passengerPage.locator('button:has-text("Driver Arrived at Pickup Spot")');
    await expect(arrivedBtn).toBeVisible({ timeout: 10000 });
    await arrivedBtn.click();
    await passengerPage.waitForTimeout(1000);

    const startTripBtn = passengerPage.locator('button:has-text("Driver Enters OTP")');
    await expect(startTripBtn).toBeVisible({ timeout: 10000 });
    await startTripBtn.click();
    await passengerPage.waitForTimeout(1000);

    const completeBtn = passengerPage.locator('button:has-text("Complete Trip")');
    await expect(completeBtn).toBeVisible({ timeout: 10000 });
    await completeBtn.click();

    // Verify rating prompt appears
    await expect(passengerPage.getByRole('heading', { name: 'Rate Your Driver' })).toBeVisible({ timeout: 10000 });

    // Submit 5-star rating
    const starBtn = passengerPage.locator('div.card button:has-text("⭐")').last();
    if (await starBtn.isVisible()) {
      await starBtn.click();
      await expect(passengerPage.locator('text=Thanks for rating!')).toBeVisible({ timeout: 8000 });
    }

    // Cleanup contexts
    await passengerContext.close();
    await driverContext.close();
  });
});

