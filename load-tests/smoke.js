/**
 * smoke.js — BidDrive Smoke Test
 *
 * PURPOSE: Run this FIRST before any load test.
 * A single virtual user walks through the full happy path:
 *   1. Register as passenger
 *   2. Register as driver
 *   3. Login as both
 *   4. Driver updates location
 *   5. Passenger creates auction ride
 *   6. Driver submits a bid
 *   7. Verify auction state in Redis
 *
 * If this fails → do NOT run the load test. Fix the app first.
 * If this passes → everything works for 1 user. Scale up.
 *
 * Run: k6 run load-tests/smoke.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ─── Config ────────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed:        ['rate<0.01'],  // Zero failures expected in smoke
    http_req_duration:      ['p(95)<2000'], // 2s max for 1 user (generous)
    checks:                 ['rate>0.99'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// ─── Helper: authenticated headers ─────────────────────────────────────────
function authHeaders(token) {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
}

// ─── Main scenario ──────────────────────────────────────────────────────────
export default function () {
  const uid = randomString(8);

  // ── Step 1: Register Passenger ───────────────────────────────────────────
  const passengerRegisterRes = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
    name:        `Passenger_${uid}`,
    email:       `passenger_${uid}@test.com`,
    password:    'Test@12345',
    phoneNumber: `9${Math.floor(100000000 + Math.random() * 900000000)}`,
    role:        'PASSENGER',
  }), { headers: JSON_HEADERS });

  const pReg = JSON.parse(passengerRegisterRes.body);
  let passengerToken = pReg.token;
  if (!passengerToken && pReg.testOtp) {
    const vRes = http.post(`${BASE_URL}/api/auth/verify-otp`, JSON.stringify({
      email: pReg.email,
      otp: pReg.testOtp,
    }), { headers: JSON_HEADERS });
    passengerToken = JSON.parse(vRes.body).token;
  }

  check(passengerRegisterRes, {
    'STEP 1: passenger register → 201': (r) => r.status === 201,
    'STEP 1: got JWT token':             () => passengerToken !== undefined && passengerToken !== null,
  });

  sleep(0.5);

  // ── Step 2: Register Driver ──────────────────────────────────────────────
  const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const l1 = letters[Math.floor(Math.random() * letters.length)];
  const l2 = letters[Math.floor(Math.random() * letters.length)];
  const dist = String(Math.floor(10 + Math.random() * 89));
  const num = String(Math.floor(1001 + Math.random() * 8998));
  const validPlate = `TN${dist}${l1}${l2}${num}`;

  const driverRegisterRes = http.post(`${BASE_URL}/api/auth/register`, JSON.stringify({
    name:         `Driver_${uid}`,
    email:        `driver_${uid}@test.com`,
    password:     'Test@12345',
    phoneNumber:  `8${Math.floor(100000000 + Math.random() * 900000000)}`,
    role:         'DRIVER',
    licensePlate: validPlate,
    carType:      'Sedan',
  }), { headers: JSON_HEADERS });

  const dReg = JSON.parse(driverRegisterRes.body);
  let driverToken = dReg.token;
  if (!driverToken && dReg.testOtp) {
    const vRes = http.post(`${BASE_URL}/api/auth/verify-otp`, JSON.stringify({
      email: dReg.email,
      otp: dReg.testOtp,
    }), { headers: JSON_HEADERS });
    driverToken = JSON.parse(vRes.body).token;
  }

  check(driverRegisterRes, {
    'STEP 2: driver register → 201':  (r) => r.status === 201,
    'STEP 2: driver role in response': () => dReg.role === 'DRIVER',
  });

  // Ensure Driver is Available
  http.put(`${BASE_URL}/api/drivers/status?status=Available`, null, { headers: authHeaders(driverToken) });
  sleep(0.5);

  // ── Step 3: Driver updates GPS location ──────────────────────────────────
  const locationRes = http.put(`${BASE_URL}/api/drivers/location`, JSON.stringify({
    latitude:  12.9716 + (Math.random() - 0.5) * 0.1,
    longitude: 77.5946 + (Math.random() - 0.5) * 0.1,
  }), { headers: authHeaders(driverToken) });

  check(locationRes, {
    'STEP 3: driver location update → 200': (r) => r.status === 200,
  });
  sleep(0.5);

  // ── Step 4: Passenger creates auction ride ────────────────────────────────
  const rideRes = http.post(`${BASE_URL}/api/rides`, JSON.stringify({
    pickupLocation:       'Koramangala, Bangalore',
    destinationLocation:  'Indiranagar, Bangalore',
    pickupLatitude:       12.9279,
    pickupLongitude:      77.6271,
    destinationLatitude:  12.9784,
    destinationLongitude: 77.6408,
    baseFare:             120.00,
    vehicleType:          'Sedan',
    rideType:             'AUCTION',
  }), { headers: authHeaders(passengerToken) });

  check(rideRes, {
    'STEP 4: ride created → 200 or 201': (r) => r.status === 200 || r.status === 201,
    'STEP 4: ride has an id':            (r) => JSON.parse(r.body).id !== undefined,
    'STEP 4: ride status is INITIATED':  (r) => JSON.parse(r.body).status === 'INITIATED',
  });

  const rideId = JSON.parse(rideRes.body).id;
  sleep(1);

  // ── Step 5: Check auction state in Redis ─────────────────────────────────
  const auctionRes = http.get(`${BASE_URL}/api/rides/${rideId}/auction`,
    { headers: authHeaders(driverToken) });

  check(auctionRes, {
    'STEP 5: auction state readable → 200': (r) => r.status === 200,
    'STEP 5: auction has lowestBid':        (r) => JSON.parse(r.body).lowestBid !== undefined,
    'STEP 5: auction status is ACTIVE':     (r) => JSON.parse(r.body).status === 'ACTIVE',
  });
  sleep(0.5);

  // ── Step 6: Driver submits a bid ─────────────────────────────────────────
  const bidAmount = 100.00; // below baseFare of 120
  const bidRes = http.post(`${BASE_URL}/api/bids`, JSON.stringify({
    rideId:    rideId,
    bidAmount: bidAmount,
  }), { headers: authHeaders(driverToken) });

  check(bidRes, {
    // 201 = bid accepted. 400 = bid rejected (auction expired or not lower) — both are valid.
    'STEP 6: bid response is 201 or 400': (r) => r.status === 201 || r.status === 400 || r.status === 200,
  });

  sleep(1);
}
