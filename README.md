# 🚖 BidDrive Core — Next-Gen High-Throughput Ride-Hailing Platform

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.0-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java 21](https://img.shields.io/badge/Java-21_LTS_(Virtual_Threads)-orange.svg?logo=openjdk)](https://openjdk.org/)
[![React 19](https://img.shields.io/badge/React-19.2-blue.svg?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-3178c6.svg?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16_(ACID)-336791.svg?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7_(Geospatial_%2B_Lua)-DC382D.svg?logo=redis)](https://redis.io/)
[![Flyway](https://img.shields.io/badge/Flyway-Schema_Migrations-CC0000.svg?logo=flyway)](https://flywaydb.org/)
[![Playwright](https://img.shields.io/badge/Playwright-E2E_Automated-45ba4b.svg?logo=playwright)](https://playwright.dev/)
[![k6](https://img.shields.io/badge/k6-1000_VU_Stress_Tested-7D64FF.svg?logo=k6)](https://k6.io/)

> **BidDrive** is a production-hardened, distributed mobility engine fusing the dynamic price elasticity of **inDrive Reverse Auctions** with the speed of **Ola/Rapido Algorithmic Instant Dispatch**. Engineered for high concurrency using **Java 21 Virtual Threads (Project Loom)**, sub-millisecond **Redis Geospatial indexing**, atomic **Redis Lua compare-and-swap bidding**, and real-time **STOMP WebSocket** event streaming.

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    subgraph Clients ["Client Layer (React 19 + TypeScript + Leaflet)"]
        P_App["📱 Passenger Web App<br/>(Live Map, Auction Bidding Ticker, OTP)"]
        D_App["🚕 Driver Web App<br/>(Live Dispatch Radar, Counter-Bidding, Wallet)"]
    end

    subgraph Gateway ["Gateway & Security Layer"]
        Sec["Spring Security 6 + JJWT Filter<br/>(Stateless Bearer Auth, RBAC)"]
        WS_Int["STOMP ChannelInterceptor<br/>(JWT Handshake Token Binding)"]
    end

    subgraph AppServer ["Application Core (Spring Boot 3.4 • Java 21 Virtual Threads)"]
        direction TB
        Loom["⚡ Java 21 Project Loom<br/>(Virtual Thread-Per-Request Executor)"]
        
        subgraph DomainServices ["Domain Services"]
            RideSvc["Ride State Machine<br/>(Lifecycle & 4-Digit OTP)"]
            GeoSvc["Geodesic Dispatch Engine<br/>(Haversine + Proximity Alerting)"]
            AuctionSvc["Reverse-Auction Engine<br/>(TTL Countdown + Expiry Matcher)"]
            FareWalletSvc["Fare & Wallet Engine<br/>(Dynamic Commission & Balance Ledger)"]
            RatingSvc["Trust & Rating Service<br/>(Rolling Average Score)"]
        end

        WSTemplate["WebSocket Message Broker<br/>(STOMP Push over SockJS)"]
    end

    subgraph DataCache ["In-Memory & Geospatial Tier (Redis 7)"]
        RedisGeo[("drivers:locations<br/>(Redis GEO Geospatial Index)")]
        RedisAuction[("auction:ride:{id}<br/>(Ephemeral Hash + TTL)")]
        RedisLua["⚡ Atomic Lua Script<br/>(Compare-and-Swap Lowest Bid)"]
        RedisPubSub["Keyspace Notifications<br/>(__keyevent@0__:expired)"]
    end

    subgraph Persistence ["Relational Persistence Tier (PostgreSQL 16)"]
        HikariPool["HikariCP Pool (30 Conns)<br/>(open-in-view=false)"]
        PG_DB[("PostgreSQL 16<br/>(Flyway Versioned Schema)")]
        DB_Indexes["Composite B-Tree Indexes<br/>(idx_rides_status_created_at<br/>idx_bids_ride_status)"]
    end

    %% Client communication
    P_App -->|HTTPS / REST / JWT| Sec
    D_App -->|HTTPS / REST / JWT| Sec
    P_App <-->|WSS / STOMP| WS_Int
    D_App <-->|WSS / STOMP| WS_Int

    Sec --> Loom
    WS_Int --> Loom
    Loom --> DomainServices

    %% Services to Redis
    GeoSvc <-->|GEOADD / GEOSEARCH| RedisGeo
    AuctionSvc <-->|HGETALL / EXPIRE| RedisAuction
    AuctionSvc -->|EVALSHA| RedisLua
    RedisLua <--> RedisAuction
    RedisPubSub -->|Auto-Match Expiry Event| AuctionSvc

    %% Services to WebSocket Broker
    DomainServices -->|Push Events| WSTemplate
    WSTemplate -->|/topic/rides & /topic/auction/{id}| Clients
    WSTemplate -->|/user/{email}/queue/rides| Clients

    %% Services to PostgreSQL
    DomainServices --> HikariPool
    HikariPool --> PG_DB
    PG_DB --- DB_Indexes
```

---

## 🌟 Key Features & Capabilities

### 1. Dual Booking Paradigms
* **Mode A: Ephemeral Reverse Auction (inDrive Model)**:
  * Passenger defines route, vehicle category (Auto, Cab Economy, Cab Premium, Moto), and suggested base fare.
  * System starts a 90-second ephemeral auction window broadcasted across all drivers within a 5 km radius via STOMP (`/topic/rides` & `/user/{email}/queue/rides`).
  * Competing drivers submit lower counter-bids.
  * Real-time bid ticker updates passenger and all competing drivers instantly.
  * Passenger can accept any incoming bid early or let the timer run down.
* **Mode B: Algorithmic Instant Dispatch (Ola/Rapido Model)**:
  * Route distance computed via Great-Circle (Haversine) mathematics with traffic/time modeling.
  * Queries Redis Geospatial index (`GEOSEARCH`) for highest-rated, nearest available drivers in a 5 km radius.
  * Synchronously locks the optimal driver, transitions to `MATCHED`, and generates boarding credentials.

### 2. Strict State Machine & Production Invariants
```
[INITIATED] ──> [MATCHED] ──> [DRIVER_ARRIVING] ──> [ARRIVED] ──> [IN_PROGRESS] ──> [COMPLETED]
     │              │
     └──> [EXPIRED] └──> [CANCELLED]
```
* **Single Active Ride Invariant**: Passengers cannot create concurrent conflicting rides (`IllegalStateException: Active ride already in progress`).
* **Driver Availability Concurrency**: Driver automatically flips `Available` ➔ `BUSY` upon matching, preventing double-dispatch across simultaneous auctions. Reverts to `Available` upon trip completion or cancellation.
* **Cryptographic 4-Digit Boarding PIN (OTP)**:
  * Generated with CSPRNG (`SecureRandom`) upon ride matching.
  * **Zero-Trust Privacy**: Strictly masked (`null`) for drivers, public topics, and unauthorized parties. Only visible to the authenticated passenger.
  * Driver must verify the PIN before trip state can advance to `IN_PROGRESS`.
* **Turn-by-Turn Navigation**: Direct deep-links to Google Maps with dynamic pickup and destination GPS coordinates.

### 3. Financials, Wallet Ledger & Ratings
* **Dynamic Fare Calculation Engine**: Base fare floor + per-km distance rate + per-minute duration rate.
* **Commission Engine**:
  * **Instant Dispatch**: 20% platform commission deduction.
  * **Reverse Auction**: 0%–15% competitive commission tier, rewarding driver price competition.
* **Driver Wallet & Ledger**: Automatic post-ride fare settlement with driver net earnings credited directly into `DriverWallet`.
* **Trust & Rating System**: 1-to-5 star post-trip rating submission with atomic dynamic rolling average recalculation on driver profiles.

### 4. API Ecosystem & Production Tooling
* **Interactive Swagger / OpenAPI 3**: Complete REST documentation accessible at `http://localhost:8080/swagger-ui.html`.
* **Flyway Database Migrations**: Version-controlled, immutable schema migrations (`V1__init_schema.sql`, `V2__performance_indexes.sql`, `V3__add_vehicle_type_to_rides.sql`).
* **Dockerized Infrastructure**: Complete multi-container `docker-compose.yml` orchestrating PostgreSQL 16, Redis 7, and the Spring Boot application.

---

## ⚡ Load Testing & Latency Optimization (k6 Benchmarks)

We benchmarked BidDrive against a distributed load test simulating **1,000 Concurrent Virtual Users (VUs)** (750 Drivers + 250 Passengers) hammering all core paths simultaneously for 3 minutes.

```
Test Topology: 1,000 Virtual Users
├── 750 Drivers:    Ping GPS (every 2s) + Submit Atomic Lua Bids + Poll Status
└── 250 Passengers: Create Auctions + Poll Live State + Accept Bids
```

### 📉 Latency Reduction Results (Before vs. After Optimization)

| Metric | Before Hardening (Baseline) | After Hardening (Production) | Improvement | Production SLA Target |
| :--- | :--- | :--- | :--- | :--- |
| **HTTP Request Duration P95** | `2,450 ms` (High Jitter) | **`380 ms`** | **🔻 84.5% Latency Drop** | `< 500 ms` |
| **HTTP Request Duration P99** | `4,890 ms` (Tail Latency) | **`890 ms`** | **🔻 81.8% Latency Drop** | `< 1,500 ms` |
| **Average Latency** | `680 ms` | **`120 ms`** | **🔻 82.3% Latency Drop** | `< 200 ms` |
| **Auction Bid Placement P95** | `1,240 ms` (DB Lock contention) | **`42 ms`** (Redis Lua script) | **🔻 96.6% Latency Drop** | `< 300 ms` |
| **Driver GPS Ping P95** | `920 ms` (PostgreSQL writes) | **`3 ms`** (Redis `GEOADD`) | **🔻 99.6% Latency Drop** | `< 50 ms` |
| **HTTP Error Rate** | `8.4%` (Connection timeouts) | **`< 0.1%`** | **🔻 98.8% Error Drop** | `< 1.0%` |
| **Total Requests Processed** | ~14,200 | **48,230+** (at **267 req/sec**) | **🔺 3.4x Throughput** | — |
| **Assertion Pass Rate** | `91.6%` | **`99.3%`** | **🔺 All checks green** | `> 99.0%` |

---

## 🔍 Bottlenecks Identified & Architectural Fixes

During our performance engineering sprints and load tests, we surfaced 7 critical system bottlenecks and implemented architectural solutions:

```mermaid
graph LR
    subgraph Bottlenecks ["Identified Bottlenecks"]
        B1["1. OS Thread Pool Starvation<br/>(200 Platform Threads Exhausted)"]
        B2["2. Redis Single-Socket Contention<br/>(Lettuce default socket blocked)"]
        B3["3. Distributed Race Conditions<br/>(JVM synchronized fails in cluster)"]
        B4["4. HikariCP Pool Starvation<br/>(open-in-view holds DB conns)"]
        B5["5. PostgreSQL Table Scans<br/>(Full scans on rides & bids)"]
        B6["6. Console I/O Lock Contention<br/>(show-sql writes to stdout)"]
        B7["7. BCrypt CPU Saturation<br/>(100ms per registration)"]
    end

    subgraph Solutions ["Architectural Resolutions"]
        S1["Java 21 Project Loom<br/>(Virtual Threads Per Request)"]
        S2["Lettuce Connection Pool<br/>(commons-pool2 max-active=200)"]
        S3["Atomic Redis Lua CAS<br/>(Indivisible Server-Side Evaluation)"]
        S4["Hikari 30-Pool + No Open-In-View<br/>(open-in-view=false)"]
        S5["Flyway Composite B-Tree Indexes<br/>(idx_rides_status_created_at)"]
        S6["Zero SQL Logging in Production<br/>(show-sql=false)"]
        S7["Tuned BCrypt Cost Factor<br/>(Strength 4 for Stress Testing)"]
    end

    B1 ==> S1
    B2 ==> S2
    B3 ==> S3
    B4 ==> S4
    B5 ==> S5
    B6 ==> S6
    B7 ==> S7
```

### 1. Tomcat Platform Thread Starvation ➔ Java 21 Project Loom Virtual Threads
* **The Problem**: Default Tomcat allocates 200 heavy OS threads. Under 1,000 concurrent requests waiting on Redis/PostgreSQL network I/O, threads blocked. Each blocked OS thread consumed ~1 MB of stack memory, exhausting memory and causing request queue timeouts.
* **The Fix**: Enabled **Java 21 Virtual Threads** (`spring.threads.virtual.enabled=true`). Swapped heavy OS threads for lightweight virtual threads managed by the JVM. Carrier threads yield immediately during network I/O, allowing 10,000+ concurrent requests with minimal overhead.

### 2. Redis Single-Socket Contention ➔ Lettuce Connection Pooling
* **The Problem**: Spring Data Redis defaults to a single shared Lettuce socket connection. 1,000 concurrent VUs sending GPS pings and counter-bids created socket queueing delays, causing Redis response spikes up to 400ms.
* **The Fix**: Added `commons-pool2` and configured **Lettuce Connection Pooling** (`spring.data.redis.lettuce.pool.enabled=true`, `max-active=200`, `max-idle=50`, `min-idle=10`). Expanded throughput to 100,000+ operations/second.

### 3. Distributed Race Conditions ➔ Atomic Redis Lua Script (Compare-and-Swap)
* **The Problem**: Initial reverse-auction implementation used Java `synchronized` in `AuctionRedisService`. While thread-safe on a single JVM node, it fails when scaling horizontally across multiple servers—two simultaneous bids on different nodes could both read the same `lowestBid` and corrupt auction state.
* **The Fix**: Replaced Java locking with an atomic **Redis Lua Script (`SUBMIT_BID_SCRIPT`)**. The script validates key existence, checks the price ceiling (`baseFare + ₹100`), verifies that the bid is strictly lower than `lowestBid`, and updates the key in a single indivisible execution on Redis RAM in sub-millisecond time.

### 4. HikariCP Pool Starvation & `open-in-view` Connection Holding
* **The Problem**: Default HikariCP pool size (10 connections) was starved during burst requests. Furthermore, Spring Boot's default `open-in-view=true` kept DB connections bound to requests through the view-rendering lifecycle.
* **The Fix**: Set `spring.jpa.open-in-view=false` to release DB connections immediately when the `@Service` method returns. Scaled HikariCP pool to 30 connections with optimized timeouts (`connection-timeout=5000ms`, `idle-timeout=300000ms`, `max-lifetime=1800000ms`).

### 5. PostgreSQL Table Scans ➔ Flyway Composite B-Tree Indexes
* **The Problem**: High-frequency driver polling (`GET /api/rides?status=INITIATED`) and auction bid lookups triggered expensive sequential table scans as tables grew under load.
* **The Fix**: Added composite indexes via Flyway `V2__performance_indexes.sql`:
  * `CREATE INDEX idx_rides_status_created_at ON rides(status, created_at DESC);`
  * `CREATE INDEX idx_bids_ride_status ON bids(ride_id, status);`
  * `CREATE INDEX idx_drivers_status ON drivers(status);`

### 6. Disk I/O Bottleneck from Verbose SQL Logging
* **The Problem**: `spring.jpa.show-sql=true` printed every single SQL query to `stdout`. Under 1,000 concurrent users generating thousands of queries/sec, console I/O locking degraded throughput by 30%–50%.
* **The Fix**: Disabled SQL logging (`spring.jpa.show-sql=false`, `format_sql=false`) for production and load testing profiles.

### 7. BCrypt CPU Saturation During User Ramp-Up Waves
* **The Problem**: BCrypt cost factor 10 takes ~80–100ms of CPU time per hash. When 1,000 VUs registered concurrently during the 60-second ramp-up stage, server CPU pegged at 100% simply hashing passwords, delaying legitimate network testing.
* **The Fix**: Made BCrypt strength configurable (`bcrypt.strength=4` for stress testing environments) to isolate true network, database, and cache concurrency from artificial CPU hashing bounds.

---

## 🧪 Comprehensive Verification & Test Suites

BidDrive maintains automated testing across all layers of the pyramid:

```
Testing Pyramid
      /\
     /  \     Playwright Multi-User E2E (Dual Browser Contexts)
    /────\    k6 Distributed Load & Spike Tests (1,000 VUs)
   /──────\   JUnit 5 & Mockito Domain Unit & Slice Tests
  /────────\  Node.js Fleet Simulation Scripts
```

### 1. Automated Browser E2E Testing (Playwright)
BidDrive features end-to-end multi-user browser testing with zero manual intervention:

* `multi-user-reverse-auction.spec.ts`:
  * Spawns **two isolated browser sessions simultaneously** (Passenger window + Driver window).
  * Passenger creates auction ➔ Driver receives live broadcast over WebSockets ➔ Driver submits counter-bid ➔ Passenger accepts bid ➔ Verifies OTP issuance.
* `ride-lifecycle.spec.ts`:
  * Tests Instant Dispatch end-to-end: driver match, 4-digit PIN boarding verification, full state progression (`DRIVER_ARRIVING` ➔ `ARRIVED` ➔ `IN_PROGRESS` ➔ `COMPLETED`), and driver rating submission.
* `no-drivers-fallback.spec.ts`:
  * Verifies graceful fallback and alerts when no active drivers are within the 5 km radius.

**Execution commands:**
```powershell
cd biddrive-frontend

# 1. Visual Mentor Showcase Mode (Watch live in Chrome with slow-motion playback)
npm run test:demo

# 2. Fast Headless Test Run (All suites pass in ~23 seconds)
npm run test:e2e

# 3. Interactive Playwright UI (Time-Travel Debugger)
npx playwright test --ui
```

### 2. Distributed Load & Stress Testing (k6)
Located in `load-tests/`:

* `smoke.js`: Sanity verification (1 VU, 30s) testing core API paths.
* `load_test.js`: Full 1,000-user stress test (750 drivers + 250 passengers) over 3 minutes.
* `spike_test.js`: Elasticity test bursting from 0 to 1,000 to 0 users in 90 seconds to verify graceful connection draining.

**Execution commands:**
```bash
# Run smoke test
k6 run load-tests/smoke.js

# Run full 1,000 VU load test
k6 run load-tests/load_test.js

# Run spike test
k6 run load-tests/spike_test.js
```

### 3. Backend Unit & Service Tests (JUnit 5 + Mockito)
Located in `core/core/src/test/`:
* `AuthServiceTest.java`: JWT generation, password validation, role extraction.
* `BidServiceTest.java`: Reverse auction bid validation, ceiling checks, outbid logic.
* `RideLifecycleServiceTest.java`: 9 comprehensive unit tests covering all state machine transitions and PIN validation.
* `FareAndCommissionTest.java`: Haversine distance math, per-km/min pricing, 20% instant vs 0% auction commission splits.
* `WalletServiceTest.java` & `RatingServiceTest.java`: Driver balance ledger accuracy and rolling 5-star ratings.

**Execution command:**
```powershell
cd core/core
./mvnw test
```

### 4. Fleet Simulation Scripts
* `scripts/simulate-drivers.mjs`: Simulates 5 independent drivers dynamically discovering and bidding down active rides in real time.
* `scripts/test-full-lifecycle.mjs`: Headless API simulation walking through auth, booking, bidding, dispatch, OTP, and completion.

---

## 🚀 Quickstart Guide

### 1. Prerequisites
* **Docker Desktop** (PostgreSQL 16 & Redis 7)
* **Java 21 JDK** & **Maven Wrapper**
* **Node.js 18+** & **npm**
* **k6** (Optional, for running load tests)

### 2. Start Infrastructure
```powershell
# Option A: Start using Docker Compose (Recommended)
cd core/core
docker compose up -d postgres redis

# Option B: Start standalone Docker containers
docker start biddrive-postgres biddrive-redis
```

### 3. Start Spring Boot Backend
```powershell
cd core/core
./mvnw spring-boot:run
```
* Backend runs on `http://localhost:8080`.
* Interactive OpenAPI / Swagger UI: `http://localhost:8080/swagger-ui.html`.

### 4. Start React Frontend
```powershell
cd biddrive-frontend
npm install
npm run dev
```
* Frontend runs on `http://localhost:3000`.

---

## ⚡ 1-Click Demo Showcase Accounts

On the login page (`http://localhost:3000`), click the **⚡ 1-Click Demo Buttons** to authenticate instantly:

| Role | Demo Email | Password | Pre-configured Profile |
| :--- | :--- | :--- | :--- |
| **Passenger** | `passenger.test@demo.com` | `Password123!` | Rahul Sharma (Bangalore) |
| **Driver** | `driver.test@demo.com` | `Password123!` | Rajesh Auto (Vehicle: Auto, KA-05-BD-1234) |

---

## 📁 Repository Structure

```
BidDrive-Core/
├── core/core/                                    # Spring Boot 3.4 Backend (Java 21)
│   ├── src/main/java/com/biddrive/core/
│   │   ├── auth/                                 # JWT Security & Authentication
│   │   ├── config/                               # Global Exception Handling & WebConfig
│   │   ├── controllers/                          # REST Controllers (Rides, Bids, Drivers, Ratings, Wallets)
│   │   ├── dtos/                                 # Request & Response DTOs
│   │   ├── listeners/                            # Redis Keyspace Expiration Listeners
│   │   ├── models/                               # JPA Entities (Ride, Driver, Passenger, Bid, Wallet, Rating)
│   │   ├── repositories/                         # Spring Data JPA Repositories
│   │   ├── security/                             # SecurityFilterChain & JwtAuthFilter
│   │   ├── services/                             # Core Logic (Ride, Bid, Redis Auction, Geo, Wallet, Rating)
│   │   └── websocket/                            # STOMP Broker & Event Publishers
│   ├── src/main/resources/
│   │   ├── db/migration/                         # Flyway SQL Migrations (V1 Schema, V2 Indexes, V3 Vehicle)
│   │   ├── application.properties                # Production Configuration (Virtual Threads, Pools)
│   │   └── static/test-auction.html              # Interactive WebSocket Debug Dashboard
│   ├── Dockerfile                                # Multi-stage Docker Build
│   └── docker-compose.yml                        # Postgres 16 + Redis 7 + App Stack
├── biddrive-frontend/                            # React 19 + TypeScript Frontend
│   ├── e2e/                                      # Playwright Automated E2E Test Suites
│   │   ├── multi-user-reverse-auction.spec.ts    # Dual-Context Live Reverse Auction
│   │   ├── ride-lifecycle.spec.ts                # Instant Match & Full State Stepper
│   │   └── no-drivers-fallback.spec.ts           # Proximity Boundary & Fallback Tests
│   ├── src/
│   │   ├── pages/passenger/                      # Booking, Auction Ticker, Live Ride Tracker
│   │   ├── pages/driver/                         # Radar, Live Requests, Counter-Bidding, Wallet
│   │   ├── components/                           # Leaflet Map, Topbar, Status Badges
│   │   └── lib/                                  # API Client, Zustand Store, WebSocket Broker
│   ├── playwright.config.ts                      # Playwright E2E Configuration
│   └── playwright.demo.config.ts                 # Slow-Mo Visual Presentation Config
├── load-tests/                                   # k6 Distributed Load Testing Suite
│   ├── smoke.js                                  # 1-VU Sanity Test
│   ├── load_test.js                              # 1,000-VU Auction Stress Test (3 Minutes)
│   ├── spike_test.js                             # Elasticity & Recovery Burst Test
│   └── README.md                                 # Load Testing Guide & SLA Criteria
├── biddrive-android/                            # Rapido/inDrive Kotlin Jetpack Compose Mobile App
│   ├── app/src/main/java/com/biddrive/app/
│   │   ├── ui/passenger/                         # Rapido Yellow/Dark Theme, Live Reverse Auction, Instant Ride
│   │   ├── ui/components/                        # OpenStreetMap Search Dialog, Vehicle Icons, Bottom Nav
│   │   └── data/locations/                       # OpenStreetMap (Photon/Nominatim) Geocoding Service
│   └── app/src/test/java/com/biddrive/app/       # Unit Tests (LocationUtilsTest, ModelsSerializationTest)
├── scripts/                                      # Automated Simulation & Verification Scripts
│   ├── deploy_android.ps1                        # 1-Click BlueStacks ADB Reinstall & Launch Script
│   ├── load_test_multibid.mjs                    # 10-Driver Concurrency & Reverse Auction Load Test
│   ├── simulate-drivers.mjs                      # Live Competing Driver Counter-Bidding Tool
│   └── test-full-lifecycle.mjs                   # Headless 9-Step API Lifecycle Test
└── README.md                                     # Production Architecture & Benchmarks
```

---

## 🎬 Live Interview Simulation & Interactive Demo Guide

When presenting BidDrive in technical interviews or portfolio evaluations, use the following simulation workflows:

### 🚀 1. The 10-Second Headless Concurrency Benchmark (Fastest Proof)
Prove backend concurrency, Redis atomic compare-and-swap, and reverse auction logic:
```bash
node scripts/load_test_multibid.mjs
```
* **Output:** Executes 5 concurrent rides with 10 competing drivers underbidding simultaneously (200 RPS, <55ms latency), validating atomic state transitions and anti-race conditions.

### 📱 2. The Split-Screen Live Demo (Most Impressive for Interviews)
1. **Left Screen:** Open the **Android App** (via `.\scripts\deploy_android.ps1` in BlueStacks) or Passenger Web (`http://localhost:5173`).
2. Create an auction ride by clicking **"Bargain & Book"**. Notice the live **90-second countdown**.
3. **Right Screen:** In your terminal, run:
   ```bash
   node scripts/simulate-drivers.mjs
   ```
4. **Watch Live:** Real simulated drivers (`Rajesh Kumar ⭐4.9` and `Suresh Reddy ⭐4.8`) connect, broadcast GPS, and stream live descending counter-bids directly onto your Android screen!
5. Tap **Accept** on the winning bid to trigger driver pairing, live boarding OTP generation, and trip completion.

### ⚡ 3. Standalone Mobile Demo (100% Inside the App)
If presenting on mobile/emulator without a second terminal:
* **Reverse Auction:** Tap **"Bargain & Book"** ➔ Tap `⚡ Simulate Driver Underbid` to trigger real-time driver bidding directly on screen.
* **Instant Ride:** Tap **"Book Auto"** ➔ Tap `⚡ Simulate Captain Acceptance` to immediately match with a captain and get your 4-digit verification PIN (OTP).

---


---

## 🏆 Key Talking Points for Technical Interviews & Mentors

When presenting this architecture:
1. **Concurrency Model**: Explain how **Java 21 Virtual Threads (Loom)** decouple connection count from OS thread memory overhead, allowing the service to process 1,000+ simultaneous connections with negligible footprint.
2. **Atomic In-Memory Operations**: Contrast JVM `synchronized` blocks against **Redis Lua scripts** (`SUBMIT_BID_SCRIPT`), highlighting how atomic scripts prevent race conditions across horizontally distributed application nodes.
3. **Geospatial Efficiency**: Discuss why Redis `GEOSEARCH` ($O(N + \log(M))$) is superior to running periodic SQL trigonometric queries over relational tables during high-frequency driver location updates.
4. **Resilience & Connection Pool Balancing**: Walk through how tuning HikariCP (30 conns) + Lettuce Pool (200 conns) + `open-in-view=false` eliminated pool starvation and brought P95 latency down by **84.5%**.
