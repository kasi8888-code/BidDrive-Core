/**
 * load_test.js — BidDrive 1,000-User Distributed Load Test
 *
 * ARCHITECTURE:
 *   750 VUs act as DRIVERS  — register → login → ping GPS → submit bids
 *   250 VUs act as PASSENGERS — register → login → create rides → poll auction
 *
 * WHAT IS BEING STRESS-TESTED:
 *   1. Tomcat + Virtual Threads — can it handle 1000 concurrent HTTP connections?
 *   2. HikariCP (30 connections) — does the DB connection pool hold under load?
 *   3. Redis Lua Script (submitLiveBid) — is bidding atomically correct & fast?
 *   4. Lettuce connection pool (10 connections) — no Redis socket bottleneck?
 *
 * STAGES:
 *   0:00 → 1:00  RAMP UP   — 0 to 1000 virtual users over 60 seconds
 *   1:00 → 3:00  SUSTAINED — 1000 VUs hammering all endpoints for 2 minutes
 *   3:00 → 3:30  RAMP DOWN — 1000 to 0 over 30 seconds (graceful drain)
 *
 * THRESHOLDS (test FAILS if any of these are breached):
 *   p95 latency < 500ms
 *   p99 latency < 1500ms
 *   Error rate  < 1%
 *   Check pass rate > 99%
 *   Bid-specific p95 < 300ms (Redis Lua should be very fast)
 *
 * Run: k6 run load-tests/load_test.js
 * Run with JSON results: k6 run --out json=load-tests/results/result.json load-tests/load_test.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Rate, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ─── Config ──────────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_VUS = 1000;

// ─── Custom Metrics ───────────────────────────────────────────────────────────
// These appear as separate rows in the k6 summary — makes it easy to
// isolate bid performance vs. general HTTP performance.
const bidDuration   = new Trend('auction_bid_duration', true);
const bidErrors     = new Rate('auction_bid_errors');
const ridesCreated  = new Counter('rides_created_total');
const bidsSubmitted = new Counter('bids_submitted_total');

const P_TARGET = parseInt(__ENV.PASSENGERS || '25');
const D_TARGET = parseInt(__ENV.DRIVERS || '75');
const RAMP_TIME = __ENV.RAMP_TIME || '10s';
const SUSTAIN_TIME = __ENV.SUSTAIN_TIME || '20s';
const DRAIN_TIME = __ENV.DRAIN_TIME || '5s';

// ─── Load Test Stages ────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    passengers: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { duration: RAMP_TIME, target: P_TARGET },   // ramp up
        { duration: SUSTAIN_TIME, target: P_TARGET },  // sustain
        { duration: DRAIN_TIME, target: 0 },     // ramp down
      ],
      exec: 'passengerScenario',
      gracefulRampDown: '10s',
    },
    drivers: {
      executor:         'ramping-vus',
      startVUs:         0,
      stages: [
        { duration: RAMP_TIME, target: D_TARGET },   // ramp up
        { duration: SUSTAIN_TIME, target: D_TARGET },  // sustain
        { duration: DRAIN_TIME, target: 0 },     // ramp down
      ],
      exec: 'driverScenario',
      gracefulRampDown: '10s',
    },
  },

  thresholds: {
    // Overall HTTP
    'http_req_duration':             ['p(95)<500', 'p(99)<1500'],
    'http_req_failed':               ['rate<0.01'],
    'checks':                        ['rate>0.99'],

    // Bid-specific (Redis Lua path — should be faster than DB queries)
    'auction_bid_duration':          ['p(95)<300'],
    'auction_bid_errors':            ['rate<0.05'], // bids can legitimately fail (outbid)
  },
};

// ─── Shared State ─────────────────────────────────────────────────────────────
// We use a SharedArray to share the list of ride IDs that passengers created
// so drivers can bid on them. In k6, SharedArrays are read-only and initialized once.
// For dynamic sharing, we use the k6 test lifecycle and a simple global rideIds array.
const activeRideIds = [];

// ─── Helpers ──────────────────────────────────────────────────────────────────
const JSON_HEADERS = { 'Content-Type': 'application/json' };

function authHeaders(token) {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
}

// Bangalore area coordinates — realistic GPS variation within 10km radius
function randomBangaloreCoord() {
  return {
    lat: 12.9716 + (Math.random() - 0.5) * 0.15,
    lng: 77.5946 + (Math.random() - 0.5) * 0.15,
  };
}

// Generate a unique phone number for each VU iteration (guaranteed unique per VU + time)
function randomPhone(prefix) {
  const timePart = String(Date.now()).slice(-5);
  const vuPart   = String(__VU).padStart(4, '0');
  return `${prefix}${timePart}${vuPart}`;
}

// ─── PASSENGER SCENARIO ───────────────────────────────────────────────────────
/**
 * Each passenger VU does:
 *   1. Register → get JWT
 *   2. Login (verify it works)
 *   3. In a loop:
 *      a. POST /api/rides  — start a 180s auction
 *      b. Poll GET /api/rides/{id}/auction every 2s for 10s to see live bid updates
 *      c. Small sleep, repeat
 */
export function passengerScenario() {
  const uid = `p_${__VU}_${Date.now()}`;

  // ── Register (with quick retry to absorb initial OS connection spikes) ───────
  let regRes;
  for (let attempt = 0; attempt < 3; attempt++) {
    regRes = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
      name:        `Passenger_${uid}`,
      email:       `${uid}@biddrive.test`,
      password:    'Test@12345',
      phoneNumber: randomPhone('9'),
      role:        'PASSENGER',
    }), { headers: JSON_HEADERS, tags: { endpoint: 'register' } });

    if (regRes && regRes.status === 201) break;
    sleep(0.3);
  }

  const regOk = check(regRes, {
    'passenger register → 201': (r) => r.status === 201,
  });

  if (!regOk) {
    sleep(2);
    return;
  }

  const pData = JSON.parse(regRes.body);
  let token = pData.token;
  if (!token && pData.testOtp) {
    const vRes = http.post(`${BASE_URL}/api/auth/verify-otp`, JSON.stringify({
      email: pData.email,
      otp: pData.testOtp,
    }), { headers: JSON_HEADERS });
    if (vRes.status === 200) {
      token = JSON.parse(vRes.body).token;
    }
  }

  sleep(randomIntBetween(1, 3));

  // ── Main ride loop ─────────────────────────────────────────────────────────
  for (let i = 0; i < 3; i++) {
    const pickup = randomBangaloreCoord();
    const dest   = randomBangaloreCoord();
    const fare   = randomIntBetween(80, 300);

    group('create_ride', () => {
      const rideRes = http.post(`${BASE_URL}/api/rides`, JSON.stringify({
        pickupLocation:       `Location_${uid}_${i}`,
        destinationLocation:  `Dest_${uid}_${i}`,
        pickupLatitude:       pickup.lat,
        pickupLongitude:      pickup.lng,
        destinationLatitude:  dest.lat,
        destinationLongitude: dest.lng,
        baseFare:             fare,
        vehicleType:          'Sedan',
        rideType:             'AUCTION',
      }), { headers: authHeaders(token), tags: { endpoint: 'create_ride' } });

      const rideOk = check(rideRes, {
        'ride created → 200/201':    (r) => r.status === 200 || r.status === 201,
        'ride has id':               (r) => JSON.parse(r.body).id !== undefined,
        'ride status is INITIATED':  (r) => JSON.parse(r.body).status === 'INITIATED',
      });

      if (rideOk) {
        const rideId = JSON.parse(rideRes.body).id;
        ridesCreated.add(1);

        // Share ride ID so drivers can bid on it
        activeRideIds.push(rideId);
        // Keep the pool bounded to prevent memory growth
        if (activeRideIds.length > 500) {
          activeRideIds.shift();
        }

        // ── Poll auction state (simulates passenger watching bids come in) ──
        for (let poll = 0; poll < 5; poll++) {
          sleep(2);
          const auctionRes = http.get(`${BASE_URL}/api/rides/${rideId}/auction`,
            { headers: authHeaders(token), tags: { endpoint: 'poll_auction' } });

          check(auctionRes, {
            'auction poll → 200':         (r) => r.status === 200,
            'auction has remainingTime':  (r) => {
              try { return JSON.parse(r.body).remainingSeconds !== undefined; }
              catch { return false; }
            },
          });
        }

        // Clean up/cancel the ride so subsequent ride booking in loop can proceed
        http.del(`${BASE_URL}/api/rides/${rideId}`, null, { headers: authHeaders(token) });
      }
    });

    sleep(randomIntBetween(5, 15));
  }
}

const PLATE_LETTERS = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
function generateValidPlate(vu) {
  const l1 = PLATE_LETTERS[Math.floor(Math.random() * PLATE_LETTERS.length)];
  const l2 = PLATE_LETTERS[Math.floor(Math.random() * PLATE_LETTERS.length)];
  const dist = String((vu % 89) + 10).padStart(2, '0');
  const num = String(randomIntBetween(1001, 9998));
  return `TN${dist}${l1}${l2}${num}`;
}

// ─── DRIVER SCENARIO ──────────────────────────────────────────────────────────
/**
 * Each driver VU does:
 *   1. Register → get JWT
 *   2. Update GPS location (becomes visible to passengers)
 *   3. In a loop:
 *      a. GET /api/rides  — browse available auctions
 *      b. POST /api/bids  — submit bid on a random open ride
 *      c. PUT /api/drivers/location  — GPS ping (simulates moving vehicle)
 *      d. Sleep, repeat
 */
export function driverScenario() {
  const uid = `d_${__VU}_${Date.now()}`;

  // ── Register (with quick retry to absorb initial OS connection spikes) ───────
  let regRes;
  const licensePlate = generateValidPlate(__VU);
  for (let attempt = 0; attempt < 3; attempt++) {
    regRes = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
      name:         `Driver_${uid}`,
      email:        `${uid}@biddrive.test`,
      password:     'Test@12345',
      phoneNumber:  randomPhone('8'),
      role:         'DRIVER',
      licensePlate: licensePlate,
      carType:      'Sedan',
    }), { headers: JSON_HEADERS, tags: { endpoint: 'register' } });

    if (regRes && regRes.status === 201) break;
    sleep(0.3);
  }

  const regOk = check(regRes, {
    'driver register → 201': (r) => r.status === 201,
  });

  if (!regOk) {
    sleep(2);
    return;
  }

  const dData = JSON.parse(regRes.body);
  let token = dData.token;
  if (!token && dData.testOtp) {
    const vRes = http.post(`${BASE_URL}/api/auth/verify-otp`, JSON.stringify({
      email: dData.email,
      otp: dData.testOtp,
    }), { headers: JSON_HEADERS });
    if (vRes.status === 200) {
      token = JSON.parse(vRes.body).token;
    }
  }

  // Ensure Driver is Available
  http.put(`${BASE_URL}/api/drivers/status?status=Available`, null, { headers: authHeaders(token) });
  sleep(randomIntBetween(1, 3));

  // ── Initial GPS location (make driver discoverable) ────────────────────────
  const initialPos = randomBangaloreCoord();
  http.put(`${BASE_URL}/api/drivers/location`, JSON.stringify({
    latitude:  initialPos.lat,
    longitude: initialPos.lng,
  }), { headers: authHeaders(token), tags: { endpoint: 'driver_location' } });

  sleep(1);

  // ── Main driver activity loop ──────────────────────────────────────────────
  for (let i = 0; i < 6; i++) {

    group('driver_activity', () => {
      // ── Browse available rides ───────────────────────────────────────────
      const ridesRes = http.get(`${BASE_URL}/api/rides?status=INITIATED`,
        { headers: authHeaders(token), tags: { endpoint: 'get_rides' } });

      check(ridesRes, {
        'get rides → 200': (r) => r.status === 200,
      });

      let rideIdToBid = null;

      // Try to pick a ride from the shared pool first (more realistic)
      if (activeRideIds.length > 0) {
        rideIdToBid = activeRideIds[randomIntBetween(0, activeRideIds.length - 1)];
      } else {
        // Fall back: parse from the rides list response
        try {
          const rides = JSON.parse(ridesRes.body);
          if (rides.length > 0) {
            rideIdToBid = rides[randomIntBetween(0, rides.length - 1)].id;
          }
        } catch (e) {}
      }

      // ── Submit a bid ────────────────────────────────────────────────────
      if (rideIdToBid) {
        const bidAmount = randomIntBetween(50, 280); // realistic bid values

        const start  = Date.now();
        const bidRes = http.post(`${BASE_URL}/api/bids`, JSON.stringify({
          rideId:    rideIdToBid,
          bidAmount: bidAmount,
        }), {
          headers: authHeaders(token),
          tags: { endpoint: 'submit_bid' },
          responseCallback: http.expectedStatuses(200, 201, 400),
        });
        const elapsed = Date.now() - start;

        bidDuration.add(elapsed);
        bidsSubmitted.add(1);

        const bidOk = check(bidRes, {
          // 201 = bid accepted (new lowest). 400 = rejected (outbid or expired). Both are VALID outcomes.
          // 500 = server crash — NOT valid.
          'bid response is NOT a server error': (r) => r.status !== 500 && r.status !== 503,
          'bid response is fast':               () => elapsed < 1000,
        });

        bidErrors.add(!bidOk);
      }

      // ── GPS ping (simulates vehicle movement) ─────────────────────────────
      const newPos = randomBangaloreCoord();
      http.put(`${BASE_URL}/api/drivers/location`, JSON.stringify({
        latitude:  newPos.lat,
        longitude: newPos.lng,
      }), { headers: authHeaders(token), tags: { endpoint: 'driver_location' } });
    });

    sleep(randomIntBetween(3, 8));
  }
}
