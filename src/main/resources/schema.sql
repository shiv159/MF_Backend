-- PostgreSQL Schema for Mutual Fund Portfolio Platform

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(255),
    phone VARCHAR(20),
    user_type VARCHAR(20) CHECK (user_type IN ('existing_investor', 'new_investor')),
    investment_horizon_years INTEGER,
    risk_tolerance VARCHAR(20) CHECK (risk_tolerance IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE')),
    monthly_sip_amount DECIMAL(15,2),
    primary_goal VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Funds table
CREATE TABLE IF NOT EXISTS funds (
    fund_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    fund_name VARCHAR(255) UNIQUE NOT NULL,
    isin VARCHAR(12) UNIQUE NOT NULL,
    amc_name VARCHAR(255),
    fund_category VARCHAR(50),
    fund_type VARCHAR(50),
    expense_ratio DECIMAL(5,3),
    min_sip_amount DECIMAL(10,2),
    direct_plan BOOLEAN DEFAULT TRUE,
    sector_allocation_json JSONB,
    top_holdings_json JSONB,
    current_nav DECIMAL(10,4),
    nav_as_of DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User Holdings table
CREATE TABLE IF NOT EXISTS user_holdings (
    user_holding_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    fund_id UUID NOT NULL REFERENCES funds(fund_id) ON DELETE CASCADE,
    units_held DECIMAL(15,4),
    current_nav DECIMAL(10,4),
    investment_amount DECIMAL(15,2),
    current_value DECIMAL(15,2),
    purchase_date DATE,
    last_nav_update TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, fund_id)
);

-- Portfolio Uploads table
CREATE TABLE IF NOT EXISTS portfolio_uploads (
    upload_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    file_name VARCHAR(255),
    file_type VARCHAR(10),
    file_size BIGINT,
    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) CHECK (status IN ('parsing', 'enriching', 'completed', 'failed')),
    parsed_holdings_count INTEGER,
    enriched_fund_count INTEGER,
    error_message TEXT
);

-- AI Insights table
CREATE TABLE IF NOT EXISTS ai_insights (
    insight_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    question TEXT,
    ai_response TEXT,
    insight_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indices
CREATE INDEX IF NOT EXISTS idx_user_holdings_user ON user_holdings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_holdings_fund ON user_holdings(fund_id);
CREATE INDEX IF NOT EXISTS idx_ai_insights_user ON ai_insights(user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_uploads_user ON portfolio_uploads(user_id);
CREATE INDEX IF NOT EXISTS idx_funds_isin ON funds(isin);
CREATE INDEX IF NOT EXISTS idx_funds_category ON funds(fund_category);