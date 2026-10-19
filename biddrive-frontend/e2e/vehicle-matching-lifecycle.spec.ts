import { test, expect } from '@playwright/test';

// Helper to cleanup any active rides for demo passenger before test runs
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

test.describe('End-to-End Vehicle Matching, Live Bidding & Ride Lifecycle', () => {
  test.beforeEach(async () => {
    await cleanupActiveRides('passenger.test@demo.com');
  });

  test('1. Full Lifecycle: Driver registers Scooty, Passenger requests Scooty, Driver bids, Passenger accepts, Ride completes', async ({ browser }) => {
    const timestamp = Date.now().toString().slice(-6);
    const driverEmail = `scooty.driver.${timestamp}@demo.com`;
    const driverPhone = `98${timestamp}12`;
    const driverPlate = `TN${timestamp.slice(0, 2)}AB${timestamp.slice(2, 6)}`;

    // Session 1: Driver Browser Context
    const driverContext = await browser.newContext();
    const driverPage = await driverContext.newPage();

    // 1. Driver Registration with Vehicle Type "Scooty"
    await driverPage.goto('/login');
    await driverPage.click('button:has-text("Register")');

    // Select Driver role
    await driverPage.click('button:has-text("DRIVER")');

    // Fill registration form using exact input placeholders
    await driverPage.fill('input[placeholder="John Doe"]', `Scooty Driver ${timestamp}`);
    await driverPage.fill('input[type="email"]', driverEmail);
    await driverPage.fill('input[placeholder="9876543210"]', driverPhone);
    await driverPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await driverPage.fill('input[placeholder="TN72AB1234"]', driverPlate);

    // Verify Vehicle Type label exists and select "Scooty"
    await expect(driverPage.locator('label:has-text("Vehicle Type")')).toBeVisible();
    await driverPage.selectOption('select.form-select', 'Scooty');

    // Submit registration
    await driverPage.click('button[type="submit"]:has-text("Create Account")');
    await expect(driverPage).toHaveURL(/\/driver/, { timeout: 15000 });

    // Session 2: Passenger Browser Context
    const passengerContext = await browser.newContext();
    const passengerPage = await passengerContext.newPage();

    // 2. Passenger Logs In
    await passengerPage.goto('/login');
    await passengerPage.fill('input[type="email"]', 'passenger.test@demo.com');
    await passengerPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await passengerPage.click('button[type="submit"]');
    await expect(passengerPage).toHaveURL(/\/passenger/, { timeout: 10000 });

    // 3. Passenger Books Ride specifying "Scooty"
    await passengerPage.goto('/passenger/book');
    await passengerPage.waitForLoadState('networkidle');

    // Click Destination input to open location drawer
    const destInput = passengerPage.getByText('Where are you going?').first();
    await destInput.click();

    // Select Guindy destination
    const guindyOption = passengerPage.locator('text=Guindy').first();
    await expect(guindyOption).toBeVisible({ timeout: 8000 });
    await guindyOption.click();

    // Select Scooty vehicle card
    const scootyOption = passengerPage.locator('text=Scooty').first();
    await expect(scootyOption).toBeVisible({ timeout: 8000 });
    await scootyOption.click();

    // Click "Bargain & Book" to initiate reverse auction
    const auctionBtn = passengerPage.locator('button:has-text("Bargain & Book")');
    await expect(auctionBtn).toBeVisible({ timeout: 8000 });
    await auctionBtn.click();

    // Verify Passenger lands on Live Auction screen
    await expect(passengerPage).toHaveURL(/\/passenger\/rides\/\d+/, { timeout: 15000 });
    const rideUrl = passengerPage.url();
    const rideId = rideUrl.split('/').pop();

    // 4. Driver opens Live Rides and sees the matching Scooty request
    await driverPage.goto('/driver/rides');
    await driverPage.waitForLoadState('networkidle');

    // Verify driver's vehicle is displayed in header
    await expect(driverPage.locator('text=My Vehicle: Scooty')).toBeVisible({ timeout: 8000 });

    // Locate the ride card for this specific ride
    const rideCard = driverPage.locator(`.ride-card:has-text("Ride #${rideId}")`);
    await expect(rideCard).toBeVisible({ timeout: 10000 });
    await expect(rideCard.locator('text=Scooty')).toBeVisible();

    // 5. Driver places a live bid (e.g. ₹60)
    const bidInput = rideCard.locator('input[type="number"]');
    await expect(bidInput).toBeVisible();
    await bidInput.fill('60');

    const placeBidBtn = rideCard.locator('button:has-text("Place Bid")');
    await expect(placeBidBtn).toBeEnabled();
    await placeBidBtn.click();

    // Verify bid toast confirmation
    await expect(driverPage.locator('text=Bid of ₹60 placed!')).toBeVisible({ timeout: 8000 });

    // 6. Passenger sees Driver's bid on live auction screen and accepts it
    const acceptBidBtn = passengerPage.locator('button:has-text("Accept")').first();
    await expect(acceptBidBtn).toBeVisible({ timeout: 12000 });
    await acceptBidBtn.click();

    // 7. Verify Ride matches and generates Passenger Boarding PIN
    await expect(passengerPage.locator('#boarding-pin')).toBeVisible({ timeout: 15000 });
    const otp = (await passengerPage.locator('#boarding-pin').innerText()).trim();
    expect(otp).toHaveLength(4);

    // 8. Driver navigates to ride details and completes trip through lifecycle
    await driverPage.goto(`/driver/rides/${rideId}`);
    await driverPage.waitForLoadState('networkidle');

    // Step A: Driver Heading to Pickup ("🚗 On My Way")
    const onMyWayBtn = driverPage.locator('button:has-text("On My Way")');
    await expect(onMyWayBtn).toBeVisible({ timeout: 10000 });
    await onMyWayBtn.click();
    await driverPage.waitForTimeout(800);

    // Step B: Driver Arrived at Pickup Spot ("📍 I Have Arrived")
    const arrivedBtn = driverPage.locator('button:has-text("I Have Arrived")');
    await expect(arrivedBtn).toBeVisible({ timeout: 10000 });
    await arrivedBtn.click();
    await driverPage.waitForTimeout(800);

    // Step C: Driver Enters Passenger OTP to Start Trip
    const otpInput = driverPage.locator('input[placeholder="Enter 4-digit OTP"]');
    await expect(otpInput).toBeVisible({ timeout: 10000 });
    await otpInput.fill(otp);

    const startTripBtn = driverPage.locator('button:has-text("Start")');
    await expect(startTripBtn).toBeEnabled();
    await startTripBtn.click();
    await driverPage.waitForTimeout(800);

    // Step D: Complete Trip ("🏁 Complete Trip")
    const completeBtn = driverPage.locator('button:has-text("Complete Trip")');
    await expect(completeBtn).toBeVisible({ timeout: 10000 });
    await completeBtn.click();

    // Verify Ride Completed badge appears on Driver Page
    await expect(driverPage.locator('.badge-completed')).toBeVisible({ timeout: 10000 });

    // Verify Passenger screen updates to Completed with Rating Prompt
    await expect(passengerPage.getByRole('heading', { name: 'Rate Your Driver' })).toBeVisible({ timeout: 15000 });

    // Cleanup
    await driverContext.close();
    await passengerContext.close();
  });

  test('2. Vehicle Mismatch Prevention: Auto driver cannot bid on Scooty request', async ({ browser }) => {
    // Session 1: Passenger creates a Scooty ride
    const passengerContext = await browser.newContext();
    const passengerPage = await passengerContext.newPage();

    await passengerPage.goto('/login');
    await passengerPage.fill('input[type="email"]', 'passenger.test@demo.com');
    await passengerPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await passengerPage.click('button[type="submit"]');
    await expect(passengerPage).toHaveURL(/\/passenger/, { timeout: 10000 });

    await passengerPage.goto('/passenger/book');
    await passengerPage.waitForLoadState('networkidle');

    const destInput = passengerPage.getByText('Where are you going?').first();
    await destInput.click();
    const guindyOption = passengerPage.locator('text=Guindy').first();
    await guindyOption.click();

    const scootyOption = passengerPage.locator('text=Scooty').first();
    await scootyOption.click();

    const auctionBtn = passengerPage.locator('button:has-text("Bargain & Book")');
    await auctionBtn.click();
    await expect(passengerPage).toHaveURL(/\/passenger\/rides\/\d+/, { timeout: 15000 });
    const rideId = passengerPage.url().split('/').pop();

    // Session 2: Auto Driver logs in
    const driverContext = await browser.newContext();
    const driverPage = await driverContext.newPage();

    const timestamp = Date.now().toString().slice(-6);
    const autoDriverEmail = `auto.driver.${timestamp}@demo.com`;
    const autoDriverPhone = `97${timestamp}34`;
    const autoDriverPlate = `TN${timestamp.slice(0, 2)}CD${timestamp.slice(2, 6)}`;

    await driverPage.goto('/login');
    await driverPage.click('button:has-text("Register")');
    await driverPage.click('button:has-text("DRIVER")');

    await driverPage.fill('input[placeholder="John Doe"]', `Auto Driver ${timestamp}`);
    await driverPage.fill('input[type="email"]', autoDriverEmail);
    await driverPage.fill('input[placeholder="9876543210"]', autoDriverPhone);
    await driverPage.fill('input[placeholder="Min. 8 characters"]', 'Password123!');
    await driverPage.fill('input[placeholder="TN72AB1234"]', autoDriverPlate);
    await driverPage.selectOption('select.form-select', 'Auto');
    await driverPage.click('button[type="submit"]:has-text("Create Account")');
    await expect(driverPage).toHaveURL(/\/driver/, { timeout: 15000 });

    // Open Live Rides
    await driverPage.goto('/driver/rides');
    await driverPage.waitForLoadState('networkidle');

    // Auto driver should either NOT see the Scooty ride in their filtered view,
    // or if seen, should have the Vehicle Mismatch badge and NO bid button.
    const rideCard = driverPage.locator(`.ride-card:has-text("Ride #${rideId}")`);
    if (await rideCard.isVisible()) {
      await expect(rideCard.locator('text=Vehicle Mismatch')).toBeVisible();
      await expect(rideCard.locator('button:has-text("Place Bid")')).not.toBeVisible();
    } else {
      // Backend vehicle filtering successfully hid incompatible ride
      await expect(driverPage.locator(`text=Ride #${rideId}`)).not.toBeVisible();
    }

    // Cleanup active ride
    await cleanupActiveRides('passenger.test@demo.com');
    await driverContext.close();
    await passengerContext.close();
  });
});
