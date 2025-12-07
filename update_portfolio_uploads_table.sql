-- Migration script to update portfolio_uploads table to match new schema
-- This should be run directly against your Aiven PostgreSQL database

-- Step 1: Drop the old CHECK constraint
ALTER TABLE portfolio_uploads 
DROP CONSTRAINT IF EXISTS portfolio_uploads_status_check;

-- Step 2: Drop the old table and recreate with new schema (if columns are missing)
-- First, check if file_path exists and drop if it does
ALTER TABLE portfolio_uploads 
DROP COLUMN IF EXISTS file_path;

-- Step 3: Add missing columns if they don't exist
ALTER TABLE portfolio_uploads 
ADD COLUMN IF NOT EXISTS parsed_holdings_count INTEGER,
ADD COLUMN IF NOT EXISTS enriched_fund_count INTEGER;

-- Step 4: Add the correct CHECK constraint
ALTER TABLE portfolio_uploads 
ADD CONSTRAINT portfolio_uploads_status_check 
CHECK (status IN ('parsing', 'enriching', 'completed', 'failed'));

-- Verify the table structure
\d portfolio_uploads
