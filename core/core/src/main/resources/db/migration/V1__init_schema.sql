-- =====================================================================
-- V1__init_schema.sql
-- BidDrive Core — Initial Schema Migration
-- Created: Phase 8 (Production Engineering)
--
-- WHY Flyway instead of ddl-auto=update?
--   ddl-auto=update is fine for development but DANGEROUS in production:
--   - It can silently drop columns if you rename a field in Java
--   - It never tracks WHAT changed or WHEN — no audit trail
--   - It cannot run data migrations (only DDL)
--   Flyway runs each .sql file exactly ONCE, tracks it in flyway_schema_history,
--   and refuses to start the app if a migration file was tampered with.
--
-- HOW to add a new schema change in the future:
--   Create V2__describe_change.sql in this same directory.
--   Flyway will automatically detect and run it on the next app start.
--   NEVER modify this V1 file after it has run — Flyway checksum will fail.
-- =====================================================================

-- ─── PASSENGERS ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS passengers (
    id              SERIAL          PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    phone_number    VARCHAR(15)     NOT NULL UNIQUE,
    email           VARCHAR(100)    NOT NULL UNIQUE,
    password        VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'PASSENGER',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ─── DRIVERS ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS drivers (
    id                      SERIAL          PRIMARY KEY,
    name                    VARCHAR(100)    NOT NULL,
    phone_number            VARCHAR(15)     NOT NULL UNIQUE,
    email                   VARCHAR(100)    NOT NULL UNIQUE,
    password                VARCHAR(255)    NOT NULL,
    license_plate           VARCHAR(20)     NOT NULL UNIQUE,
    car_type                VARCHAR(50)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'Available',
    preference_mode         VARCHAR(20)     NOT NULL DEFAULT 'BOTH',
    rating                  NUMERIC(3,2)    NOT NULL DEFAULT 5.00,
    total_ratings           INTEGER         NOT NULL DEFAULT 0,
    current_latitude        DOUBLE PRECISION,
    current_longitude       DOUBLE PRECISION,
    last_location_update    TIMESTAMP,
    role                    VARCHAR(20)     NOT NULL DEFAULT 'DRIVER',
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ─── RIDES ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rides (
    id                      SERIAL          PRIMARY KEY,
    passenger_id            INTEGER         NOT NULL REFERENCES passengers(id),
    ride_type               VARCHAR(20)     NOT NULL DEFAULT 'AUCTION',
    pickup_location         VARCHAR(100)    NOT NULL,
    destination_location    VARCHAR(100)    NOT NULL,
    pickup_latitude         DOUBLE PRECISION,
    pickup_longitude        DOUBLE PRECISION,
    destination_latitude    DOUBLE PRECISION,
    destination_longitude   DOUBLE PRECISION,
    estimated_distance_km   DOUBLE PRECISION,
    base_fare               NUMERIC(10,2)   NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'INITIATED',
    driver_id               INTEGER         REFERENCES drivers(id),
    final_fare              NUMERIC(10,2),
    otp                     VARCHAR(10),
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    cancelled_at            TIMESTAMP,
    cancellation_reason     VARCHAR(255),
    cancelled_by            VARCHAR(50),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ─── BIDS ─────────────────────────────────────────────────────────────
-- status values: ACCEPTED_LOWEST | OUTBID | REJECTED | ACCEPTED
CREATE TABLE IF NOT EXISTS bids (
    id          SERIAL          PRIMARY KEY,
    ride_id     INTEGER         NOT NULL REFERENCES rides(id),
    driver_id   INTEGER         NOT NULL REFERENCES drivers(id),
    bid_amount  NUMERIC(10,2)   NOT NULL,
    status      VARCHAR(30)     NOT NULL DEFAULT 'ACCEPTED_LOWEST',
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ─── RATINGS ─────────────────────────────────────────────────────────
-- One rating per ride per rater (enforced by unique constraint)
CREATE TABLE IF NOT EXISTS ratings (
    id          SERIAL          PRIMARY KEY,
    ride_id     INTEGER         NOT NULL REFERENCES rides(id),
    rater_id    INTEGER         NOT NULL REFERENCES passengers(id),
    ratee_id    INTEGER         NOT NULL REFERENCES drivers(id),
    score       INTEGER         NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT  uq_rating_per_ride_rater UNIQUE (ride_id, rater_id)
);

-- ─── DRIVER WALLETS ───────────────────────────────────────────────────
-- One wallet per driver (enforced by unique constraint on driver_id)
CREATE TABLE IF NOT EXISTS driver_wallets (
    id                      SERIAL          PRIMARY KEY,
    driver_id               INTEGER         NOT NULL UNIQUE REFERENCES drivers(id),
    available_balance       NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    total_gross_earnings    NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    total_commission_paid   NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    total_trips_completed   INTEGER         NOT NULL DEFAULT 0,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ─── INDEXES (performance) ────────────────────────────────────────────
-- Speed up the most common queries in BidDrive

-- Drivers look up rides by status frequently (to find INITIATED auctions to bid on)
CREATE INDEX IF NOT EXISTS idx_rides_status ON rides(status);

-- Passengers look up their own rides
CREATE INDEX IF NOT EXISTS idx_rides_passenger_id ON rides(passenger_id);

-- Auction engine: find all bids for a ride quickly
CREATE INDEX IF NOT EXISTS idx_bids_ride_id ON bids(ride_id);

-- Driver bid history lookup
CREATE INDEX IF NOT EXISTS idx_bids_driver_id ON bids(driver_id);

-- Rating lookups per driver (for leaderboard / display)
CREATE INDEX IF NOT EXISTS idx_ratings_ratee_id ON ratings(ratee_id);
