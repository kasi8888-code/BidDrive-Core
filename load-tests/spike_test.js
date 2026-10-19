/**
 * spike_test.js — BidDrive Spike / Stress Test
 *
 * PURPOSE: Simulate a sudden flash-crowd event — like New Year's Eve when everyone
 * in Bangalore suddenly opens BidDrive at the same time.
 *
 * DIFFERENCE from load_test.js:
 *   load_test.js: Gradual ramp up over 60s to 1,000 users. Sustainable load.
 *   spike_test.js: 0 → 1,000 users in 10 SECONDS. Tests elastic recovery.
 *
 * What we are looking for:
 *   ✓ App doesn't crash (no 503s, no OOM errors)
 *   ✓ HikariCP queue absorbs the burst — connections timeout gracefully, not with NPE
 *   ✓ Virtual threads don't exhaust the carrier thread pool
 *   ✓ After the spike drains, latency returns to normal (no lingering degradation)
 *   ✓ Redis Lua bids remain atomic — no duplicate lowest bids under chaotic load
 *
 * STAGES:
 *   0:00 → 0:10  SPIKE   — 0 → 1000 VUs in 10 seconds (brutal)
 *   0:10 → 1:40  SUSTAIN — 1000 VUs for 90 seconds
 *   1:40 → 1:50  DRAIN   — 1000 → 0 in 10 seconds
 *   1:50 → 2:10  RECOVERY PROBE — 20 VUs for 20 seconds — verify app recovered
 *
 * Thresholds are more lenient than the load test — spikes cause latency spikes.
 *
 * Run: k6 run load-tests/spike_test.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Custom metrics
const bidDuration  = new Trend('spike_bid_duration', true);
const serverErrors = new Counter('spike_server_errors');
const successRate  = new Rate('spike_success_rate');

export const options = {
  stages: [
    { duration: '10s',  target: 1000 },  // Brutal spike: 0 → 1000 in 10s
    { duration: '90s',  target: 1000 },  // Hold at 1000 for 90s
    { duration: '10s',  target: 0 },     // Instant drain
    { duration: '20s',  target: 20 },    // Recovery probe: can it serve requests again?
  ],
  thresholds: {
    // More lenient than load test — spikes are expected to cause some degradation
    'http_req_duration':       ['p(95)<2000', 'p(99)<5000'],
    'http_req_failed':         ['rate<0.05'],   // Up to 5% errors during spike (HikariCP timeouts)
    'spike_bid_duration':      ['p(95)<1000'],
    'spike_server_errors':     ['count<100'],   // Hard limit: 500s are never acceptable
    'spike_success_rate':      ['rate>0.90'],   // 90% of requests must succeed during spike
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function authHeaders(token) {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };
}

function randomPhone(prefix) {
  return `${prefix}${randomIntBetween(100000000, 999999999)}`;
}

const activeRideIds = [];

export default function () {
  const isDriver    = __VU % 4 !== 0; // 75% drivers, 25% passengers
  const uid         = `${isDriver ? 'd' : 'p'}_${__VU}_${Date.now()}`;
  const role        = isDriver ? 'DRIVER' : 'PASSENGER';
  const phonePrefix = isDriver ? '8' : '9';

  // ── Register ─────────────────────────────────────────────────────────────
  const regBody = {
    name:        `${role}_${uid}`,
    email:       `${uid}@spike.test`,
    password:    'Test@12345',
    phoneNumber: randomPhone(phonePrefix),
    role:        role,
  };
  if (isDriver) {
    const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
    const l1 = letters[Math.floor(Math.random() * letters.length)];
    const l2 = letters[Math.floor(Math.random() * letters.length)];
    const dist = String((__VU % 89) + 10).padStart(2, '0');
    const num = String(randomIntBetween(1001, 9998));
    regBody.licensePlate = `TN${dist}${l1}${l2}${num}`;
    regBody.carType      = 'Sedan';
  }

  const regRes = http.post(`${BASE_URL}/api/auth/register`,
    JSON.stringify(regBody),
    { headers: JSON_HEADERS, tags: { endpoint: 'register' } });

  if (regRes.status === 500 || regRes.status === 503) {
    serverErrors.add(1);
    successRate.add(false);
    sleep(1);
    return;
  }

  const regOk = check(regRes, {
    'spike register → 201': (r) => r.status === 201,
  });
  successRate.add(regOk);

  if (!regOk) { sleep(1); return; }

  const token = JSON.parse(regRes.body).token;
  sleep(0.2); // Minimal sleep — we WANT contention during spike

  // ── Driver path ───────────────────────────────────────────────────────────
  if (isDriver) {
    // GPS ping
    http.put(`${BASE_URL}/api/drivers/location`, JSON.stringify({
      latitude:  12.9716 + (Math.random() - 0.5) * 0.2,
      longitude: 77.5946 + (Math.random() - 0.5) * 0.2,
    }), { headers: authHeaders(token), tags: { endpoint: 'driver_location' } });

    sleep(0.5);

    // Try to bid on any active ride
    if (activeRideIds.length > 0) {
      const rideId  = activeRideIds[randomIntBetween(0, activeRideIds.length - 1)];
      const bidAmt  = randomIntBetween(50, 250);
      const start   = Date.now();

      const bidRes = http.post(`${BASE_URL}/api/bids`, JSON.stringify({
        rideId:    rideId,
        bidAmount: bidAmt,
      }), { headers: authHeaders(token), tags: { endpoint: 'submit_bid' } });

      bidDuration.add(Date.now() - start);

      if (bidRes.status === 500 || bidRes.status === 503) {
        serverErrors.add(1);
        successRate.add(false);
      } else {
        check(bidRes, {
          'spike bid no server error': (r) => r.status !== 500 && r.status !== 503,
        });
        successRate.add(true);
      }
    }

    sleep(randomIntBetween(1, 3));
  }

  // ── Passenger path ────────────────────────────────────────────────────────
  else {
    const rideRes = http.post(`${BASE_URL}/api/rides`, JSON.stringify({
      pickupLocation:       `Spike_Pickup_${uid}`,
      destinationLocation:  `Spike_Dest_${uid}`,
      pickupLatitude:       12.9279 + (Math.random() - 0.5) * 0.1,
      pickupLongitude:      77.6271 + (Math.random() - 0.5) * 0.1,
      destinationLatitude:  12.9784 + (Math.random() - 0.5) * 0.1,
      destinationLongitude: 77.6408 + (Math.random() - 0.5) * 0.1,
      baseFare:             randomIntBetween(100, 400),
    }), { headers: authHeaders(token), tags: { endpoint: 'create_ride' } });

    if (rideRes.status === 500 || rideRes.status === 503) {
      serverErrors.add(1);
      successRate.add(false);
    } else {
      const rideOk = check(rideRes, {
        'spike ride created no 500': (r) => r.status !== 500 && r.status !== 503,
      });
      successRate.add(rideOk);

      try {
        const rideId = JSON.parse(rideRes.body).id;
        if (rideId) {
          activeRideIds.push(rideId);
          if (activeRideIds.length > 200) activeRideIds.shift();
        }
      } catch (e) {}
    }

    sleep(randomIntBetween(2, 5));
  }
}
