import { test, expect } from '@playwright/test';

// Helper to cleanup any active rides for passenger
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

// Helper to register / login driver
async function getDriverAuth(name: string, email: string, phone: string, plate: string, carType: string) {
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

// Helper to create auction ride directly
async function createSedanAuctionRide(passengerToken: string, passengerId: number, baseFare: number = 250) {
  const res = await fetch('http://localhost:8080/api/rides', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${passengerToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      passengerId,
      pickupLocation: 'T. Nagar, Chennai',
      destinationLocation: 'Guindy, Chennai',
      pickupLatitude: 13.0418,
      pickupLongitude: 80.2341,
      destinationLatitude: 13.0067,
      destinationLongitude: 80.2026,
      baseFare,
      rideType: 'AUCTION',
      vehicleType: 'Sedan',
    }),
  });
  return res.json();
}

test.describe('Exhaustive Multi-Bid & Reverse Auction Edge Cases in Chrome', () => {
  let passengerAuth: any;
  const ts = Date.now().toString().slice(-6);

  test.beforeAll(async () => {
    // Authenticate passenger
    const pLogin = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'passenger.test@demo.com', password: 'Password123!' }),
    });
    passengerAuth = await pLogin.json();
  });

  test.beforeEach(async () => {
    await cleanupActiveRides('passenger.test@demo.com');
  });

  test('Case 1: Price Rules (Above Base Fare, Equal Lowest, Above Lowest, Zero/Negative Bids)', async () => {
    const ride = await createSedanAuctionRide(passengerAuth.token, passengerAuth.userId, 250);
    const rideId = ride.id;

    const sedanDriver = await getDriverAuth(`Sedan 1 ${ts}`, `s1_${ts}@test.com`, `90${ts}01`, `TN${ts.slice(0, 2)}AA${ts.slice(2, 6)}`, 'Sedan');
    const sedanDriver2 = await getDriverAuth(`Sedan 2 ${ts}`, `s2_${ts}@test.com`, `90${ts}02`, `TN${ts.slice(0, 2)}AB${ts.slice(2, 6)}`, 'Sedan');

    // 1A. Initial bid > Base Fare (250) -> Must be rejected
    const highBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${sedanDriver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 260 }),
    });
    expect(highBidRes.status).toBe(400);
    const highErr = await highBidRes.json();
    console.log('✓ Rejected bid above base fare:', highErr.error);
    expect(highErr.error).toContain('cannot exceed the base fare');

    // 1B. Bid <= 0 (Zero & Negative) -> Must be rejected
    const zeroBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${sedanDriver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 0 }),
    });
    expect(zeroBidRes.status).toBe(400);
    const zeroErr = await zeroBidRes.json();
    console.log('✓ Rejected 0 bid:', zeroErr.error);
    expect(zeroErr.error).toContain('greater than 0');

    // 1C. Valid First Bid equal to base fare (250) -> Accepted
    const validFirstRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${sedanDriver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 240 }),
    });
    expect(validFirstRes.status).toBe(200);
    console.log('✓ Accepted first valid bid at ₹240');

    // 1D. Second Driver tries EQUAL to current lowest (240) -> Must be rejected
    const equalBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${sedanDriver2.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 240 }),
    });
    expect(equalBidRes.status).toBe(400);
    const equalErr = await equalBidRes.json();
    console.log('✓ Rejected equal bid (240):', equalErr.error);
    expect(equalErr.error).toContain('must be lower than the current lowest bid');

    // 1E. Second Driver tries HIGHER than current lowest (245) -> Must be rejected
    const outbidHigherRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${sedanDriver2.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 245 }),
    });
    expect(outbidHigherRes.status).toBe(400);
    const outbidHigherErr = await outbidHigherRes.json();
    console.log('✓ Rejected higher counter-bid (245):', outbidHigherErr.error);
    expect(outbidHigherErr.error).toContain('must be lower than the current lowest bid');

    // 1F. Second Driver bids strictly lower (230) -> Accepted!
    const validSecondRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${sedanDriver2.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 230 }),
    });
    expect(validSecondRes.status).toBe(200);
    console.log('✓ Accepted lower counter-bid at ₹230');

    // 1G. Same Driver 1 self-underbids to win (lowers own bid from 240 to 220) -> Accepted!
    const selfUnderbidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${sedanDriver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 220 }),
    });
    expect(selfUnderbidRes.status).toBe(200);
    console.log('✓ Accepted self-underbid from Driver 1 at ₹220');
  });

  test('Case 2: Vehicle Compatibility Matrix against Sedan Request (Hatchback, Bike, Auto, SUV blocked)', async () => {
    const ride = await createSedanAuctionRide(passengerAuth.token, passengerAuth.userId, 250);
    const rideId = ride.id;

    const hatchDriver = await getDriverAuth(`Hatch ${ts}`, `h_${ts}@test.com`, `90${ts}11`, `TN${ts.slice(0, 2)}BA${ts.slice(2, 6)}`, 'Hatchback');
    const bikeDriver = await getDriverAuth(`Bike ${ts}`, `b_${ts}@test.com`, `90${ts}12`, `TN${ts.slice(0, 2)}BB${ts.slice(2, 6)}`, 'Bike');
    const autoDriver = await getDriverAuth(`Auto ${ts}`, `a_${ts}@test.com`, `90${ts}13`, `TN${ts.slice(0, 2)}BC${ts.slice(2, 6)}`, 'Auto');
    const suvDriver = await getDriverAuth(`SUV ${ts}`, `suv_${ts}@test.com`, `90${ts}14`, `TN${ts.slice(0, 2)}BD${ts.slice(2, 6)}`, 'SUV');

    // Test each non-Sedan vehicle type
    for (const [driver, type] of [[hatchDriver, 'Hatchback'], [bikeDriver, 'Bike'], [autoDriver, 'Auto'], [suvDriver, 'SUV']] as const) {
      const bidRes = await fetch('http://localhost:8080/api/bids', {
        method: 'POST',
        headers: { Authorization: `Bearer ${driver.token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ rideId, bidAmount: 200 }),
      });
      expect(bidRes.status).toBe(400);
      const err = await bidRes.json();
      console.log(`✓ Blocked ${type} from bidding on Sedan:`, err.error);
      expect(err.error).toContain('Vehicle mismatch');
      expect(err.error).toContain(type);
    }
  });

  test('Case 3: Driver Duty Status Enforcement (Offline and BUSY Blocked)', async () => {
    const ride = await createSedanAuctionRide(passengerAuth.token, passengerAuth.userId, 250);
    const rideId = ride.id;

    const driver = await getDriverAuth(`Status Driver ${ts}`, `stat_${ts}@test.com`, `90${ts}21`, `TN${ts.slice(0, 2)}CA${ts.slice(2, 6)}`, 'Sedan');

    // Set Driver to Offline
    await fetch('http://localhost:8080/api/drivers/status?status=Offline', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${driver.token}` },
    });

    const offlineBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${driver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 230 }),
    });
    expect(offlineBidRes.status).toBe(400);
    const offErr = await offlineBidRes.json();
    console.log('✓ Offline driver blocked:', offErr.error);
    expect(offErr.error).toContain('Driver is not available to place bids');

    // Set Driver to BUSY
    await fetch('http://localhost:8080/api/drivers/status?status=BUSY', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${driver.token}` },
    });

    const busyBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${driver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 230 }),
    });
    expect(busyBidRes.status).toBe(400);
    const busyErr = await busyBidRes.json();
    console.log('✓ BUSY driver blocked:', busyErr.error);
    expect(busyErr.error).toContain('Driver is not available to place bids');

    // Revert to Available
    await fetch('http://localhost:8080/api/drivers/status?status=Available', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${driver.token}` },
    });

    const availBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${driver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 230 }),
    });
    expect(availBidRes.status).toBe(200);
    console.log('✓ Available driver bid accepted at ₹230');
  });

  test('Case 4: Post-Acceptance / Closed Auction Bidding Blocked', async () => {
    const ride = await createSedanAuctionRide(passengerAuth.token, passengerAuth.userId, 250);
    const rideId = ride.id;

    const winnerDriver = await getDriverAuth(`Winner ${ts}`, `win_${ts}@test.com`, `90${ts}31`, `TN${ts.slice(0, 2)}DA${ts.slice(2, 6)}`, 'Sedan');
    const lateDriver = await getDriverAuth(`Late ${ts}`, `late_${ts}@test.com`, `90${ts}32`, `TN${ts.slice(0, 2)}DB${ts.slice(2, 6)}`, 'Sedan');

    // Winner places bid of 220
    const bidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${winnerDriver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 220 }),
    });
    const bidData = await bidRes.json();
    const bidId = bidData.id;

    // Passenger accepts winner's bid -> closes auction in Redis and sets ride to MATCHED
    const acceptRes = await fetch(`http://localhost:8080/api/bids/${bidId}/accept`, {
      method: 'PUT',
      headers: { Authorization: `Bearer ${passengerAuth.token}` },
    });
    expect(acceptRes.status).toBe(200);
    console.log('✓ Passenger accepted bid #', bidId);

    // Late driver tries to bid on the closed auction -> Must be rejected
    const lateBidRes = await fetch('http://localhost:8080/api/bids', {
      method: 'POST',
      headers: { Authorization: `Bearer ${lateDriver.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ rideId, bidAmount: 200 }),
    });
    expect(lateBidRes.status).toBe(400);
    const lateErr = await lateBidRes.json();
    console.log('✓ Late bid on closed auction rejected:', lateErr.error);
    expect(lateErr.error).toContain('Auction has expired or is not active');
  });

  test('Case 5: End-to-End Real Chrome UI Live Auction with 5 Drivers & Acceptance', async ({ browser }) => {
    // 1. Setup 5 drivers
    const sedan1 = await getDriverAuth(`Sedan Chrome 1 ${ts}`, `sc1_${ts}@test.com`, `90${ts}41`, `TN${ts.slice(0, 2)}EA${ts.slice(2, 6)}`, 'Sedan');
    const sedan2 = await getDriverAuth(`Sedan Chrome 2 ${ts}`, `sc2_${ts}@test.com`, `90${ts}42`, `TN${ts.slice(0, 2)}EB${ts.slice(2, 6)}`, 'Sedan');
    const sedan3 = await getDriverAuth(`Sedan Chrome 3 ${ts}`, `sc3_${ts}@test.com`, `90${ts}43`, `TN${ts.slice(0, 2)}EC${ts.slice(2, 6)}`, 'Sedan');
    const hatch = await getDriverAuth(`Hatch Chrome ${ts}`, `hc_${ts}@test.com`, `90${ts}44`, `TN${ts.slice(0, 2)}ED${ts.slice(2, 6)}`, 'Hatchback');
    const bike = await getDriverAuth(`Bike Chrome ${ts}`, `bc_${ts}@test.com`, `90${ts}45`, `TN${ts.slice(0, 2)}EE${ts.slice(2, 6)}`, 'Bike');

    // 2. Passenger UI Context in Chrome
    const pContext = await browser.newContext();
    const pPage = await pContext.newPage();
    await pPage.goto('/login');
    await pPage.fill('input[type="email"]', 'passenger.test@demo.com');
    await pPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await pPage.click('button[type="submit"]');
    await expect(pPage).toHaveURL(/\/passenger/, { timeout: 15000 });

    // Passenger books Sedan auction
    await pPage.goto('/passenger/book');
    await pPage.waitForLoadState('networkidle');
    const destTrigger = pPage.getByText('Where are you going?').first();
    await destTrigger.click();
    await pPage.locator('text=Guindy').first().click();
    await pPage.locator('text=Sedan').first().click();
    await pPage.locator('button:has-text("Bargain & Book")').click();

    await expect(pPage).toHaveURL(/\/passenger\/rides\/\d+/, { timeout: 15000 });
    const rideId = parseInt(pPage.url().split('/').pop()!, 10);
    console.log(`✓ Passenger created live Sedan auction #${rideId}`);

    // Verify Reverse Auction Live banner
    await expect(pPage.locator('text=REVERSE AUCTION')).toBeVisible({ timeout: 10000 });

    // 3. Hatchback Driver UI Context in Chrome
    const hContext = await browser.newContext();
    const hPage = await hContext.newPage();
    await hPage.goto('/login');
    await hPage.fill('input[type="email"]', `hc_${ts}@test.com`);
    await hPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await hPage.click('button[type="submit"]');
    await expect(hPage).toHaveURL(/\/driver/, { timeout: 15000 });
    await hPage.goto('/driver/rides');
    await hPage.waitForLoadState('networkidle');

    // Incompatible card must show Vehicle Mismatch
    const hCard = hPage.locator(`.ride-card:has-text("Ride #${rideId}")`);
    if (await hCard.isVisible()) {
      await expect(hCard.locator('text=Vehicle Mismatch')).toBeVisible();
      await expect(hCard.locator('button:has-text("Place Bid")')).not.toBeVisible();
      console.log('✓ Hatchback driver UI displays Vehicle Mismatch and hides bid input');
    }

    // 4. Sedan Driver 1 UI Context in Chrome (Bids ₹210)
    const s1Context = await browser.newContext();
    const s1Page = await s1Context.newPage();
    await s1Page.goto('/login');
    await s1Page.fill('input[type="email"]', `sc1_${ts}@test.com`);
    await s1Page.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await s1Page.click('button[type="submit"]');
    await expect(s1Page).toHaveURL(/\/driver/, { timeout: 15000 });
    await s1Page.goto('/driver/rides');
    await s1Page.waitForLoadState('networkidle');

    const s1Card = s1Page.locator(`.ride-card:has-text("Ride #${rideId}")`);
    await expect(s1Card).toBeVisible({ timeout: 10000 });
    await s1Card.locator('input[type="number"]').fill('210');
    await s1Card.locator('button:has-text("Place Bid")').click();
    console.log('✓ Sedan Driver 1 placed bid ₹210 in Chrome');

    // Passenger UI updates via WebSocket
    await expect(pPage.locator('.bid-item:has-text("₹210")')).toBeVisible({ timeout: 10000 });

    // 5. Sedan Driver 2 UI Context in Chrome (Bids lower: ₹195)
    const s2Context = await browser.newContext();
    const s2Page = await s2Context.newPage();
    await s2Page.goto('/login');
    await s2Page.fill('input[type="email"]', `sc2_${ts}@test.com`);
    await s2Page.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await s2Page.click('button[type="submit"]');
    await expect(s2Page).toHaveURL(/\/driver/, { timeout: 15000 });
    await s2Page.goto('/driver/rides');
    await s2Page.waitForLoadState('networkidle');

    const s2Card = s2Page.locator(`.ride-card:has-text("Ride #${rideId}")`);
    await expect(s2Card).toBeVisible({ timeout: 10000 });
    await s2Card.locator('input[type="number"]').fill('195');
    await s2Card.locator('button:has-text("Place Bid")').click();
    console.log('✓ Sedan Driver 2 placed lower bid ₹195 in Chrome');

    // Passenger UI updates via WebSocket
    await expect(pPage.locator('.bid-item:has-text("₹195")')).toBeVisible({ timeout: 10000 });

    // 6. Sedan Driver 3 UI Context in Chrome (Bids winning lowest: ₹180)
    const s3Context = await browser.newContext();
    const s3Page = await s3Context.newPage();
    await s3Page.goto('/login');
    await s3Page.fill('input[type="email"]', `sc3_${ts}@test.com`);
    await s3Page.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await s3Page.click('button[type="submit"]');
    await expect(s3Page).toHaveURL(/\/driver/, { timeout: 15000 });
    await s3Page.goto('/driver/rides');
    await s3Page.waitForLoadState('networkidle');

    const s3Card = s3Page.locator(`.ride-card:has-text("Ride #${rideId}")`);
    await expect(s3Card).toBeVisible({ timeout: 10000 });
    await s3Card.locator('input[type="number"]').fill('180');
    await s3Card.locator('button:has-text("Place Bid")').click();
    console.log('✓ Sedan Driver 3 placed winning lowest bid ₹180 in Chrome');

    // 7. Passenger accepts winning bid (₹180) in Chrome
    await expect(pPage.locator('.bid-item:has-text("₹180")')).toBeVisible({ timeout: 10000 });
    await expect(pPage.locator('text=3 bids')).toBeVisible({ timeout: 10000 });

    const winningRow = pPage.locator('.bid-item:has-text("₹180")');
    await winningRow.locator('button:has-text("Accept")').click();
    console.log('✓ Passenger clicked Accept on winning bid');

    // Verify Passenger Matched Screen with PIN
    await expect(pPage.locator('text=BOARDING VERIFICATION PIN')).toBeVisible({ timeout: 15000 });
    await expect(pPage.locator('text=Sedan Chrome 3')).toBeVisible({ timeout: 10000 });

    // Verify Winning Driver 3 Matched Screen
    await s3Page.goto(`/driver/rides/${rideId}`);
    await s3Page.waitForLoadState('networkidle');
    await expect(s3Page.locator('.badge-matched')).toBeVisible({ timeout: 10000 });
    await expect(s3Page.locator('button:has-text("On My Way")')).toBeVisible({ timeout: 10000 });
    console.log('✓ Driver 3 confirmed ride is MATCHED with OTP progression controls');

    // Cleanup contexts
    await pContext.close();
    await hContext.close();
    await s1Context.close();
    await s2Context.close();
    await s3Context.close();
  });
});
