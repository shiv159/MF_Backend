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
    auth_provider VARCHAR(20) DEFAULT 'LOCAL' CHECK (auth_provider IN ('LOCAL', 'GOOGLE')),
    investment_horizon_years INTEGER,
    risk_tolerance VARCHAR(20) CHECK (risk_tolerance IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE')),
    monthly_sip_amount DECIMAL(15,2),
    primary_goal VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    profile_data_json JSONB,
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
    fund_metadata_json JSONB,
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
    weight_pct INTEGER CHECK (weight_pct >= 1 AND weight_pct <= 100),
    purchase_date DATE,
    last_nav_update TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, fund_id)
);

-- Backfill / safe evolution: add column if the table already exists
ALTER TABLE IF EXISTS user_holdings
    ADD COLUMN IF NOT EXISTS weight_pct INTEGER CHECK (weight_pct >= 1 AND weight_pct <= 100);

-- Safe migration for OAuth: add auth_provider column if users table exists
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(20) DEFAULT 'LOCAL';

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

-- Portfolio Alerts table
CREATE TABLE IF NOT EXISTS portfolio_alerts (
    alert_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    payload_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    dedupe_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indices
CREATE INDEX IF NOT EXISTS idx_user_holdings_user ON user_holdings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_holdings_fund ON user_holdings(fund_id);
CREATE INDEX IF NOT EXISTS idx_ai_insights_user ON ai_insights(user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_uploads_user ON portfolio_uploads(user_id);
CREATE INDEX IF NOT EXISTS idx_funds_isin ON funds(isin);
CREATE INDEX IF NOT EXISTS idx_funds_category ON funds(fund_category);
CREATE INDEX IF NOT EXISTS idx_portfolio_alerts_user_status ON portfolio_alerts(user_id, status);
CREATE INDEX IF NOT EXISTS idx_portfolio_alerts_dedupe ON portfolio_alerts(user_id, dedupe_key);

-- Chat Conversations table
CREATE TABLE IF NOT EXISTS chat_conversations (
    conversation_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat Messages table
CREATE TABLE IF NOT EXISTS chat_messages (
    message_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES chat_conversations(conversation_id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    intent VARCHAR(50),
    tool_trace JSONB,
    sources JSONB,
    actions JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User Goals table
CREATE TABLE IF NOT EXISTS user_goals (
    goal_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    goal_type VARCHAR(50) NOT NULL,
    goal_name VARCHAR(255) NOT NULL,
    target_amount DECIMAL(15,2),
    target_date DATE,
    current_amount DECIMAL(15,2) DEFAULT 0,
    monthly_sip DECIMAL(15,2),
    expected_return_pct DECIMAL(5,2),
    asset_allocation_json JSONB,
    linked_fund_ids JSONB,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ACHIEVED', 'PAUSED', 'CANCELLED')),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Portfolio Briefings table
CREATE TABLE IF NOT EXISTS portfolio_briefings (
    briefing_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    briefing_type VARCHAR(20) NOT NULL CHECK (briefing_type IN ('DAILY', 'WEEKLY')),
    title VARCHAR(255),
    content TEXT NOT NULL,
    metrics_json JSONB,
    alerts_summary JSONB,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Peer Comparison Snapshots table
CREATE TABLE IF NOT EXISTS peer_comparison_snapshots (
    snapshot_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    risk_profile VARCHAR(20) NOT NULL,
    age_bracket VARCHAR(20) NOT NULL,
    portfolio_size_bracket VARCHAR(20) NOT NULL,
    avg_equity_pct DECIMAL(5,2),
    avg_debt_pct DECIMAL(5,2),
    avg_gold_pct DECIMAL(5,2),
    avg_expense_ratio DECIMAL(5,3),
    avg_fund_count INTEGER,
    avg_returns_1y DECIMAL(5,2),
    avg_overlap_pct DECIMAL(5,2),
    sample_size INTEGER,
    computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indices for new tables
CREATE INDEX IF NOT EXISTS idx_chat_conversations_user ON chat_conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation ON chat_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_user_goals_user ON user_goals(user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_briefings_user ON portfolio_briefings(user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_briefings_unread ON portfolio_briefings(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_peer_snapshots_profile ON peer_comparison_snapshots(risk_profile, age_bracket, portfolio_size_bracket);
