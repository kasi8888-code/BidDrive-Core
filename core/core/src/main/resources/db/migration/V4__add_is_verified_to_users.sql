-- V4: Add email/phone OTP verification status to passengers and drivers
ALTER TABLE passengers ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;

-- Ensure all existing pre-registered test & demo accounts are marked verified
UPDATE passengers SET is_verified = TRUE WHERE is_verified IS FALSE OR is_verified IS NULL;
UPDATE drivers SET is_verified = TRUE WHERE is_verified IS FALSE OR is_verified IS NULL;
