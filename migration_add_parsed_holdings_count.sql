-- Migration: Add parsed_holdings_count column to portfolio_uploads table if it doesn't exist
-- Run this SQL against your Aiven PostgreSQL database

ALTER TABLE portfolio_uploads
ADD COLUMN IF NOT EXISTS parsed_holdings_count INTEGER;

-- Verify the column was added
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'portfolio_uploads' 
ORDER BY ordinal_position;
