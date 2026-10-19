# BidDrive Load Tests (k6)

Simulates **1,000 concurrent users** (750 drivers + 250 passengers) against a live BidDrive-Core instance.

## Prerequisites

1. **k6 installed** — `winget install k6` (Windows) or `brew install k6` (macOS)
2. **BidDrive-Core running** — `mvnw spring-boot:run` in `core/core/`
3. **PostgreSQL + Redis running** — either locally or via `docker compose up`

---

## Test Files

| File | What it tests | Scale |
|:---|:---|:---|
| `smoke.js` | Single user happy path — sanity check before load test | 1 VU, 30s |
| `load_test.js` | **Main 1,000-user auction stress test** | 1,000 VUs, 3 min |
| `spike_test.js` | Sudden burst from 0 to 1,000 to 0 users to test elasticity | 0→1000→0 in 90s |

---

## Running Tests

### 1. Smoke Test First (Always)
```bash
k6 run load-tests/smoke.js
```
Expected: All checks pass, no errors.

### 2. Full 1,000-User Load Test
```bash
k6 run load-tests/load_test.js
```

### 3. Spike Test (Stress to failure)
```bash
k6 run load-tests/spike_test.js
```

---

## Performance Thresholds (What "Pass" Means)

| Metric | Target | Meaning |
|:---|:---|:---|
| `http_req_duration p(95)` | < 500ms | 95% of requests finish within 500ms |
| `http_req_duration p(99)` | < 1,500ms | Extreme tail latency under 1.5 seconds |
| `http_req_failed` | < 1% | Error rate under 1% |
| `checks` | > 99% | All response shape assertions pass |
| `auction_bid_duration p(95)` | < 300ms | Redis Lua bid is fast |

---

## What the Test Simulates

```
Phase 1 - RAMP UP (0 to 1000 users, 60s)
  All 1000 users register (500 passengers, 500 drivers)
  All 1000 users log in and get JWT tokens

Phase 2 - SUSTAINED LOAD (1000 users, 120s)
  Passengers: POST /api/rides  (create auctions with realistic coords)
  Drivers:    PUT  /api/drivers/location  (send GPS pings every 2s)
  Drivers:    POST /api/bids  (submit bids atomically via Redis Lua)
  Passengers: GET  /api/rides/{id}/auction  (poll live auction state)

Phase 3 - RAMP DOWN (1000 to 0 users, 30s)
```

---

## Understanding k6 Output

```
check register successful      POST /api/auth/register returned 201
check login successful         POST /api/auth/login returned 200 with token
check ride created             POST /api/rides returned 201
check bid accepted or rejected POST /api/bids returned 201 or 400 (both valid)
check auction state readable   GET /api/rides/{id}/auction returned 200

http_req_duration.............: avg=120ms  p(95)=380ms  p(99)=890ms
http_reqs.....................: 48230  267/s
checks........................: 99.3%
```
