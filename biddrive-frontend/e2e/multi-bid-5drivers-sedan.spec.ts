import { test, expect } from '@playwright/test';

// Helper to cleanup any existing active rides for test passenger
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

// Helper to register a test driver and set status Available
async function registerTestDriver(name: string, email: string, phone: string, plate: string, carType: string) {
  const regRes = await fetch('http://localhost:8080/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name,
      email,
      phoneNumber: phone,
      password: 'Password123!',
      licensePlate: plate,
      carType,
      role: 'DRIVER',
    }),
  });

  let authData: any;
  if (regRes.ok) {
    authData = await regRes.json();
  } else {
    // If already exists, login
    const loginRes = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: 'Password123!' }),
    });
    authData = await loginRes.json();
  }

  // Ensure Available status and location
  await fetch('http://localhost:8080/api/drivers/status?status=Available', {
    method: 'PUT',
    headers: { Authorization: `Bearer ${authData.token}` },
  });
  await fetch('http://localhost:8080/api/drivers/location', {
    method: 'PUT',
    headers: { Authorization: `Bearer ${authData.token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ latitude: 13.0418, longitude: 80.2341 }),
  });

  return authData;
}

test.describe('Multi-Bid Reverse Auction: 1 Passenger (Sedan) vs 5 Drivers (3 Sedan, 1 Hatchback, 1 Bike)', () => {
  test.beforeEach(async () => {
    await cleanupActiveRides('passenger.test@demo.com');
  });

  test('Full Verification: Sedan drivers compete with descending bids; Hatchback & Bike blocked by vehicle mismatch', async ({ browser }) => {
    const ts = Date.now().toString().slice(-6);

    // ── 1. Setup 5 Drivers ────────────────────────────────────────────────────────
    console.log('--- Registering 5 Drivers ---');
    const driverSedan1 = await registerTestDriver(
      `Sedan Driver One ${ts}`,
      `sedan1_${ts}@biddrive.test`,
      `91${ts}01`,
      `TN${ts.slice(0, 2)}AA${ts.slice(2, 6)}`,
      'Sedan'
    );
    const driverSedan2 = await registerTestDriver(
      `Sedan Driver Two ${ts}`,
      `sedan2_${ts}@biddrive.test`,
      `91${ts}02`,
      `TN${ts.slice(0, 2)}AB${ts.slice(2, 6)}`,
      'Sedan'
    );
    const driverSedan3 = await registerTestDriver(
      `Sedan Driver Three ${ts}`,
      `sedan3_${ts}@biddrive.test`,
      `91${ts}03`,
      `TN${ts.slice(0, 2)}AC${ts.slice(2, 6)}`,
      'Sedan'
    );
    const driverHatchback = await registerTestDriver(
      `Hatchback Driver ${ts}`,
      `hatch_${ts}@biddrive.test`,
      `91${ts}04`,
      `TN${ts.slice(0, 2)}AD${ts.slice(2, 6)}`,
      'Hatchback'
    );
    const driverBike = await registerTestDriver(
      `Bike Driver ${ts}`,
      `bike_${ts}@biddrive.test`,
      `91${ts}05`,
      `TN${ts.slice(0, 2)}AE${ts.slice(2, 6)}`,
      'Bike'
    );

    console.log(`Registered 3 Sedan Drivers (IDs: ${driverSedan1.userId}, ${driverSedan2.userId}, ${driverSedan3.userId})`);
    console.log(`Registered 1 Hatchback Driver (ID: ${driverHatchback.userId})`);
    console.log(`Registered 1 Bike Driver (ID: ${driverBike.userId})`);

    // ── 2. Passenger Logs In & Books a Sedan Auction Ride ─────────────────────────
    const passengerContext = await browser.newContext();
    const passengerPage = await passengerContext.newPage();

    console.log('--- Passenger booking Sedan ride ---');
    await passengerPage.goto('/login');
    await passengerPage.fill('input[type="email"]', 'passenger.test@demo.com');
    await passengerPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await passengerPage.click('button[type="submit"]');
    await expect(passengerPage).toHaveURL(/\/passenger/, { timeout: 15000 });

    await passengerPage.goto('/passenger/book');
    await passengerPage.waitForLoadState('networkidle');

    // Select destination Guindy
    const destTrigger = passengerPage.getByText('Where are you going?').first();
    await destTrigger.click();
    const guindyOption = passengerPage.locator('text=Guindy').first();
    await expect(guindyOption).toBeVisible({ timeout: 8000 });
    await guindyOption.click();

    // Select Vehicle: Sedan
    const sedanOption = passengerPage.locator('text=Sedan').first();
    await expect(sedanOption).toBeVisible({ timeout: 8000 });
    await sedanOption.click();

    // Start Reverse Auction
    const bargainBtn = passengerPage.locator('button:has-text("Bargain & Book")');
    await expect(bargainBtn).toBeVisible({ timeout: 8000 });
    await bargainBtn.click();

    // Verify redirected to Ride Details
    await expect(passengerPage).toHaveURL(/\/passenger\/rides\/\d+/, { timeout: 15000 });
    const rideIdStr = passengerPage.url().split('/').pop()!;
    const rideId = parseInt(rideIdStr, 10);
    console.log(`Passenger Ride Created: #${rideId} (Vehicle Category: Sedan)`);

    // Verify Reverse Auction Live banner is active
    await expect(passengerPage.locator('text=REVERSE AUCTION')).toBeVisible({ timeout: 10000 });

    // Fetch ride details to get exact base fare
    const rideDetailsRes = await fetch(`http://localhost:8080/api/rides/${rideId}`, {
      headers: { Authorization: `Bearer ${driverSedan1.token}` },
    });
    const rideData = await rideDetailsRes.json();
    const baseFare = Number(rideData.baseFare);
    console.log(`Base Fare ceiling for Ride #${rideId}: ₹${baseFare}`);

    // ── 3. Verify Incompatible Drivers (Hatchback & Bike) are BLOCKED ─────────────
    console.log('--- Testing Incompatible Drivers (Hatchback & Bike) ---');

    // A. Backend API Level rejection check for Hatchback driver
    const hatchBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${driverHatchback.token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        rideId,
        bidAmount: baseFare - 5,
      }),
    });
    expect(hatchBidRes.status).toBe(400);
    const hatchErr = await hatchBidRes.json();
    console.log('Hatchback Driver Bid Rejection Response:', hatchErr);
    expect(hatchErr.error).toContain('Vehicle mismatch');
    expect(hatchErr.error).toContain('Hatchback');

    // B. Backend API Level rejection check for Bike driver
    const bikeBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${driverBike.token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        rideId,
        bidAmount: baseFare - 5,
      }),
    });
    expect(bikeBidRes.status).toBe(400);
    const bikeErr = await bikeBidRes.json();
    console.log('Bike Driver Bid Rejection Response:', bikeErr);
    expect(bikeErr.error).toContain('Vehicle mismatch');
    expect(bikeErr.error).toContain('Bike');

    // C. Browser UI Level check for Hatchback driver
    const hatchContext = await browser.newContext();
    const hatchPage = await hatchContext.newPage();
    await hatchPage.goto('/login');
    await hatchPage.fill('input[type="email"]', `hatch_${ts}@biddrive.test`);
    await hatchPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await hatchPage.click('button[type="submit"]');
    await expect(hatchPage).toHaveURL(/\/driver/, { timeout: 15000 });

    await hatchPage.goto('/driver/rides');
    await hatchPage.waitForLoadState('networkidle');

    // If visible on Live Rides, it must display the Vehicle Mismatch warning & NO place bid button
    const hatchRideCard = hatchPage.locator(`.ride-card:has-text("Ride #${rideId}")`);
    if (await hatchRideCard.isVisible()) {
      await expect(hatchRideCard.locator('text=Vehicle Mismatch')).toBeVisible();
      await expect(hatchRideCard.locator('button:has-text("Place Bid")')).not.toBeVisible();
    }
    await hatchContext.close();

    // ── 4. 3 Sedan Drivers Compete in Multi-Bid Bidding War ───────────────────────
    console.log('--- 3 Sedan Drivers Entering Bidding War in Chrome ---');

    // Context for Sedan Driver 1
    const sedan1Context = await browser.newContext();
    const sedan1Page = await sedan1Context.newPage();
    await sedan1Page.goto('/login');
    await sedan1Page.fill('input[type="email"]', `sedan1_${ts}@biddrive.test`);
    await sedan1Page.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await sedan1Page.click('button[type="submit"]');
    await expect(sedan1Page).toHaveURL(/\/driver/, { timeout: 15000 });
    await sedan1Page.goto('/driver/rides');
    await sedan1Page.waitForLoadState('networkidle');

    // Context for Sedan Driver 2
    const sedan2Context = await browser.newContext();
    const sedan2Page = await sedan2Context.newPage();
    await sedan2Page.goto('/login');
    await sedan2Page.fill('input[type="email"]', `sedan2_${ts}@biddrive.test`);
    await sedan2Page.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await sedan2Page.click('button[type="submit"]');
    await expect(sedan2Page).toHaveURL(/\/driver/, { timeout: 15000 });
    await sedan2Page.goto('/driver/rides');
    await sedan2Page.waitForLoadState('networkidle');

    // Context for Sedan Driver 3
    const sedan3Context = await browser.newContext();
    const sedan3Page = await sedan3Context.newPage();
    await sedan3Page.goto('/login');
    await sedan3Page.fill('input[type="email"]', `sedan3_${ts}@biddrive.test`);
    await sedan3Page.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await sedan3Page.click('button[type="submit"]');
    await expect(sedan3Page).toHaveURL(/\/driver/, { timeout: 15000 });
    await sedan3Page.goto('/driver/rides');
    await sedan3Page.waitForLoadState('networkidle');

    // Bid 1: Driver 1 bids (baseFare - 5)
    const bid1Amount = Math.max(10, baseFare - 5);
    const card1 = sedan1Page.locator(`.ride-card:has-text("Ride #${rideId}")`);
    await expect(card1).toBeVisible({ timeout: 10000 });
    await card1.locator('input[type="number"]').fill(bid1Amount.toString());
    await card1.locator('button:has-text("Place Bid")').click();
    console.log(`Sedan Driver 1 placed bid: ₹${bid1Amount}`);

    // Verify Passenger screen updates with Bid 1
    await expect(passengerPage.locator(`.bid-item:has-text("₹${bid1Amount}")`)).toBeVisible({ timeout: 10000 });

    // Bid 2: Driver 2 outbids with lower bid (baseFare - 10)
    const bid2Amount = Math.max(5, baseFare - 10);
    const card2 = sedan2Page.locator(`.ride-card:has-text("Ride #${rideId}")`);
    await expect(card2).toBeVisible({ timeout: 10000 });
    await card2.locator('input[type="number"]').fill(bid2Amount.toString());
    await card2.locator('button:has-text("Place Bid")').click();
    console.log(`Sedan Driver 2 placed lower bid: ₹${bid2Amount}`);

    // Verify Passenger screen updates with Bid 2 as new lowest
    await expect(passengerPage.locator(`.bid-item:has-text("₹${bid2Amount}")`)).toBeVisible({ timeout: 10000 });

    // Bid 3: Driver 3 outbids with even lower bid (baseFare - 15)
    const bid3Amount = Math.max(1, baseFare - 15);
    const card3 = sedan3Page.locator(`.ride-card:has-text("Ride #${rideId}")`);
    await expect(card3).toBeVisible({ timeout: 10000 });
    await card3.locator('input[type="number"]').fill(bid3Amount.toString());
    await card3.locator('button:has-text("Place Bid")').click();
    console.log(`Sedan Driver 3 placed winning lowest bid: ₹${bid3Amount}`);

    // ── 5. Passenger Accepts Winning Bid (Driver 3) ───────────────────────────────
    console.log('--- Passenger Accepting Winning Bid ---');
    // Verify 3 competing bids visible in the live bids list
    await expect(passengerPage.locator(`.bid-item:has-text("₹${bid3Amount}")`)).toBeVisible({ timeout: 10000 });
    await expect(passengerPage.locator('text=3 bids')).toBeVisible({ timeout: 10000 });

    // Find the Accept button for the lowest bid (Driver 3, ranked #1)
    const winningBidRow = passengerPage.locator(`.bid-item:has-text("₹${bid3Amount}")`);
    await expect(winningBidRow).toBeVisible();
    const acceptBtn = winningBidRow.locator('button:has-text("Accept")');
    await expect(acceptBtn).toBeVisible();
    await acceptBtn.click();

    // Verify Passenger Screen transitions to MATCHED status
    await expect(passengerPage.locator('text=BOARDING VERIFICATION PIN')).toBeVisible({ timeout: 15000 });
    await expect(passengerPage.locator('text=Sedan Driver Three')).toBeVisible({ timeout: 10000 });

    // Verify Driver 3 Screen reflects ride matched
    console.log('--- Verifying Driver 3 Matched State ---');
    await sedan3Page.goto(`/driver/rides/${rideId}`);
    await sedan3Page.waitForLoadState('networkidle');
    await expect(sedan3Page.locator('.badge-matched')).toBeVisible({ timeout: 10000 });
    await expect(sedan3Page.locator('button:has-text("On My Way")')).toBeVisible({ timeout: 10000 });

    console.log('✅ TEST PASSED: 5 Drivers, 1 Passenger, Vehicle Filtering + Multi-bid Auction completed successfully!');

    // Cleanup
    await cleanupActiveRides('passenger.test@demo.com');
    await passengerContext.close();
    await sedan1Context.close();
    await sedan2Context.close();
    await sedan3Context.close();
  });
});
