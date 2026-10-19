/**
 * BidDrive - Mock Driver Bidding Simulation Script
 * 
 * Usage:
 *   node scripts/simulate-drivers.mjs
 *   node scripts/simulate-drivers.mjs <rideId> <startingBid>
 * 
 * Simulates real drivers competing in reverse auction bidding over REST/WebSockets.
 */

const BASE_URL = process.env.API_URL || 'http://localhost:8080';

async function registerOrLoginDriver(email, name, carType, licensePlate) {
  try {
    const res = await fetch(`${BASE_URL}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name,
        email,
        password: 'Password123!',
        phoneNumber: '+91' + Math.floor(1000000000 + Math.random() * 9000000000),
        role: 'DRIVER',
        carType,
        licensePlate,
      }),
    });
    if (res.ok) {
      const data = await res.json();
      return data.token;
    }
  } catch {}

  // If already registered, login
  const loginRes = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'Password123!' }),
  });
  if (loginRes.ok) {
    const data = await loginRes.json();
    return data.token;
  }
  throw new Error(`Failed to authenticate driver: ${email}`);
}

async function placeBid(token, rideId, bidAmount, driverName) {
  const res = await fetch(`${BASE_URL}/api/bids`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ rideId: Number(rideId), bidAmount: Number(bidAmount) }),
  });

  if (res.ok) {
    const data = await res.json();
    console.log(`✅ [${driverName}] Placed lowest bid: ₹${data.bidAmount} for Ride #${rideId}`);
    return data;
  } else {
    const err = await res.text();
    console.log(`⚠️ [${driverName}] Bid failed: ${err}`);
  }
}

async function run() {
  const rideIdArg = process.argv[2];
  let rideId = rideIdArg ? Number(rideIdArg) : null;

  console.log('🚗 BidDrive Driver Simulation Tool Initializing...');

  // Setup 2 mock drivers
  console.log('🔑 Authenticating mock drivers...');
  const token1 = await registerOrLoginDriver('driver1@demo.com', 'Rajesh Kumar', 'Sedan', 'TN01AB1234');
  const token2 = await registerOrLoginDriver('driver2@demo.com', 'Suresh Reddy', 'Mini', 'TN02CD5678');
  console.log('✅ Drivers ready: Rajesh Kumar (⭐4.9), Suresh Reddy (⭐4.8)');

  if (!rideId) {
    // Fetch latest active auction ride
    const ridesRes = await fetch(`${BASE_URL}/api/rides?status=INITIATED`, {
      headers: { 'Authorization': `Bearer ${token1}` },
    });
    const rides = await ridesRes.json();
    const auctionRide = rides.find((r) => r.rideType === 'AUCTION');
    if (!auctionRide) {
      console.log('ℹ️ No active AUCTION ride found in status INITIATED.');
      console.log('👉 Create a ride on the passenger web UI first, then re-run:');
      console.log('   node scripts/simulate-drivers.mjs <rideId>');
      return;
    }
    rideId = auctionRide.id;
    console.log(`🎯 Found active Ride #${rideId} (Base fare: ₹${auctionRide.baseFare})`);
  }

  // Get current auction lowest
  const auctionRes = await fetch(`${BASE_URL}/api/rides/${rideId}/auction`);
  const auction = await auctionRes.json();
  let lowest = Number(auction?.lowestBid || 200);

  console.log(`\n🏁 Starting competing bids for Ride #${rideId} (Current lowest: ₹${lowest})...`);

  // Driver 1 bids
  const bid1 = lowest - 5;
  await placeBid(token1, rideId, bid1, 'Rajesh Kumar');

  // Wait 3 seconds
  console.log('⏳ Waiting 3 seconds for competing driver reaction...');
  await new Promise((r) => setTimeout(r, 3000));

  // Driver 2 outbids
  const bid2 = bid1 - 5;
  await placeBid(token2, rideId, bid2, 'Suresh Reddy');

  console.log('\n🎉 Simulation completed! Check the Passenger UI to see the live bids stream in real time.');
}

run().catch(console.error);
