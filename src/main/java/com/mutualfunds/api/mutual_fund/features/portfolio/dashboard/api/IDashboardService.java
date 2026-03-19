package com.mutualfunds.api.mutual_fund.features.portfolio.dashboard.api;

import com.mutualfunds.api.mutual_fund.features.portfolio.dashboard.dto.DashboardResponse;

import java.util.UUID;

/**
 * Contract for dashboard service
 * Defines operations for retrieving user dashboard data
 */
public interface IDashboardService {
    
    /**
     * Get complete dashboard data for a user
     * Includes user profile, portfolio summary, holdings, upload history, and AI insights
     * 
     * @param userId UUID of the user
     * @return DashboardResponse with all user-related data
     */
    DashboardResponse getDashboardData(UUID userId);
}
