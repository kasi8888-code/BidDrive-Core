/**
 * BidDrive - Autonomous Live Driver Fleet Daemon
 * 
 * Simulates a realistic fleet of 5 active drivers in Tamil Nadu:
 * - Listens for new ride bookings over STOMP WebSocket (/topic/rides)
 * - Automatically evaluates vehicle compatibility
 * - Submits competitive reverse-auction counter-bids with realistic human delays (1.5s - 5s)
 * - Directly exercises Spring Boot REST API, PostgreSQL database, and Redis distributed locks
 * - Pushes live bid updates to Android & Web passengers over /topic/auction/{rideId}
 * 
 * Run with: node scripts/simulate_live_drivers.mjs
 */

const BASE_URL = process.env.API_URL || 'http://localhost:8080';
const WS_URL = process.env.WS_URL || 'ws://localhost:8080/ws';

const FLEET_CONFIG = [
  { name: 'Rajesh Kumar (Auto)', email: 'fleet.rajesh@biddrive.com', carType: 'Auto', plate: 'TN-09-BD-1001', phone: '+919840112233' },
  { name: 'Suresh Reddy (Auto)', email: 'fleet.suresh@biddrive.com', carType: 'Auto', plate: 'TN-01-AX-2045', phone: '+919840223344' },
  { name: 'Meena Sundaram (Auto)', email: 'fleet.meena@biddrive.com', carType: 'Auto', plate: 'TN-07-JK-3092', phone: '+919840334455' },
  { name: 'Karthik Raman (Sedan)', email: 'fleet.karthik@biddrive.com', carType: 'Sedan', plate: 'TN-02-ZZ-4112', phone: '+919840445566' },
  { name: 'Vignesh V (Hatchback)', email: 'fleet.vignesh@biddrive.com', carType: 'Hatchback', plate: 'TN-10-XY-5521', phone: '+919840556677' },
];

const activeFleet = [];

function log(emoji, msg) {
  const ts = new Date().toLocaleTimeString();
  console.log(`[${ts}] ${emoji} ${msg}`);
}

async function api(method, endpoint, body, token) {
  const headers = {};
  if (body) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${endpoint}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await res.text();
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    data = text;
  }
  return { ok: res.ok, status: res.status, data };
}

async function registerOrLogin(driverConfig) {
  const reg = await api('POST', '/api/auth/register', {
    email: driverConfig.email,
    password: 'Password123!',
    name: driverConfig.name,
    role: 'DRIVER',
    phoneNumber: driverConfig.phone,
    carType: driverConfig.carType,
    licensePlate: driverConfig.plate,
  });

  let token = null;
  let userId = null;

  if (reg.ok && reg.data?.token) {
    token = reg.data.token;
    userId = reg.data.userId;
  } else if (reg.ok && reg.data?.status === 'PENDING_VERIFICATION' && reg.data?.testOtp) {
    const verify = await api('POST', '/api/auth/verify-otp', {
      email: driverConfig.email,
      otp: reg.data.testOtp,
    });
    if (verify.ok && verify.data?.token) {
      token = verify.data.token;
      userId = verify.data.userId;
    }
  }

  if (!token) {
    const login = await api('POST', '/api/auth/login', {
      email: driverConfig.email,
      password: 'Password123!',
    });
    if (login.ok && login.data?.token) {
      token = login.data.token;
      userId = login.data.userId;
    } else {
      throw new Error(`Failed to login ${driverConfig.email}: ${JSON.stringify(login.data)}`);
    }
  }

  // Set driver status to Available and broadcast GPS location
  await api('PUT', '/api/drivers/status?status=Available', null, token);
  await api('PUT', '/api/drivers/location', {
    latitude: 12.8406 + (Math.random() - 0.5) * 0.02,
    longitude: 80.1534 + (Math.random() - 0.5) * 0.02,
  }, token);

  return { ...driverConfig, token, userId };
}

function isCompatible(requestedType, driverCarType) {
  if (!requestedType || !driverCarType) return true;
  const req = requestedType.toLowerCase().trim();
  const car = driverCarType.toLowerCase().trim();
  if (req === car) return true;
  if (req.includes('auto') && car.includes('auto')) return true;
  if ((req === 'sedan' || req === 'hatchback') && (car === 'sedan' || car === 'hatchback')) return true;
  return false;
}

function connectDriverStomp(driver) {
  return new Promise((resolve) => {
    const ws = new WebSocket(WS_URL);
    driver.ws = ws;

    ws.addEventListener('open', () => {
      const connectFrame = `CONNECT\naccept-version:1.2,1.1,1.0\nAuthorization:Bearer ${driver.token}\n\n\u0000`;
      ws.send(connectFrame);
    });

    ws.addEventListener('message', (event) => {
      const text = event.data;
      if (text.startsWith('CONNECTED')) {
        // Subscribe to /topic/rides (New rides broadcast)
        ws.send(`SUBSCRIBE\nid:sub-rides-${driver.userId}\ndestination:/topic/rides\n\n\u0000`);
        // Subscribe to driver personal queue
        ws.send(`SUBSCRIBE\nid:sub-queue-${driver.userId}\ndestination:/user/queue/notifications\n\n\u0000`);
        resolve();
      } else if (text.startsWith('MESSAGE')) {
        handleIncomingMessage(driver, text);
      }
    });

    ws.addEventListener('error', (err) => {
      log('⚠️', `WebSocket error for ${driver.name}: ${err.message || 'connection issue'}`);
    });
  });
}

const activeAuctions = new Map();

async function handleIncomingMessage(driver, frame) {
  const bodyIndex = frame.indexOf('\n\n');
  if (bodyIndex === -1) return;
  const bodyText = frame.substring(bodyIndex + 2).replace(/\u0000/g, '').trim();

  let payload;
  try {
    payload = JSON.parse(bodyText);
  } catch {
    return;
  }

  // 1. New Ride Announced
  if (payload.eventType === 'RIDE_CREATED' || payload.rideId) {
    const rideId = payload.rideId || payload.id;
    const vehicleType = payload.vehicleType || payload.carType;
    const baseFare = payload.baseFare || 75.0;

    if (!isCompatible(vehicleType, driver.carType)) {
      return; // Not this driver's vehicle class
    }

    if (!activeAuctions.has(rideId)) {
      activeAuctions.set(rideId, {
        rideId,
        baseFare,
        vehicleType,
        lowestBid: baseFare,
        bidsCount: 0,
      });
      log('📢', `NEW AUCTION DETECTED: Ride #${rideId} (${vehicleType || 'Any'}) • Base Fare: ₹${baseFare}`);
    }

    const auction = activeAuctions.get(rideId);

    // Staggered counter-bidding by fleet drivers
    const delayMs = 1500 + Math.random() * 2500;
    setTimeout(async () => {
      try {
        const stepUnderbid = 5 + Math.floor(Math.random() * 8);
        const myBidAmount = Math.max(35, Math.round((auction.lowestBid - stepUnderbid) * 10) / 10);

        if (myBidAmount < auction.lowestBid) {
          const res = await api('POST', '/api/bids', { rideId, bidAmount: myBidAmount }, driver.token);
          if (res.ok) {
            auction.lowestBid = myBidAmount;
            auction.bidsCount++;
            log('⚡', `[${driver.name}] Underbid placed: ₹${myBidAmount} on Ride #${rideId} (HTTP 200 via Redis lock)`);
          }
        }
      } catch (err) {
        // Silently skip if auction closed or accepted
      }
    }, delayMs);
  }

  // 2. Personal Ride Award Notification
  if (payload.eventType === 'RIDE_AWARDED' || payload.eventType === 'AUCTION_MATCHED') {
    if (payload.driverId === driver.userId) {
      log('🏆', `[${driver.name}] WON RIDE #${payload.rideId}! Final Fare: ₹${payload.lowestBid}`);
    }
  }
}

async function startFleet() {
  console.log('\n=============================================================');
  console.log('🚕 BIDDRIVE LIVE DRIVER FLEET DAEMON (5 COMPETITIVE DRIVERS)');
  console.log('=============================================================\n');
  log('🚀', `Connecting to Spring Boot backend at ${BASE_URL}...`);

  for (const config of FLEET_CONFIG) {
    try {
      const driver = await registerOrLogin(config);
      await connectDriverStomp(driver);
      activeFleet.push(driver);
      log('✅', `Driver Online & Connected: ${driver.name} [Plate: ${driver.plate}]`);
    } catch (err) {
      log('❌', `Failed initializing ${config.name}: ${err.message}`);
    }
  }

  console.log('\n-------------------------------------------------------------');
  log('🟢', `Fleet Daemon is ACTIVE! (${activeFleet.length}/5 drivers ready)`);
  log('💡', 'Any ride created in the Android App or Web Dashboard will now');
  log('💡', 'receive live STOMP counter-bids automatically every 2-3 seconds.');
  console.log('-------------------------------------------------------------\n');
}

startFleet().catch(err => {
  console.error('Fleet startup error:', err);
});
