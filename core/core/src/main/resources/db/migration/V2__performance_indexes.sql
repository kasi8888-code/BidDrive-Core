-- =====================================================================
-- V2__performance_indexes.sql
-- High-concurrency composite indexes for 1,000 VU load testing
-- =====================================================================

-- Speed up driver browse active rides ordered by latest (GET /api/rides?status=INITIATED)
CREATE INDEX IF NOT EXISTS idx_rides_status_created_at ON rides(status, created_at DESC);

-- Speed up auction bid queries and status lookups
CREATE INDEX IF NOT EXISTS idx_bids_ride_status ON bids(ride_id, status);

-- Speed up driver availability status lookups
CREATE INDEX IF NOT EXISTS idx_drivers_status ON drivers(status);
