package com.mutualfunds.api.mutual_fund.service.contract;

import com.mutualfunds.api.mutual_fund.dto.response.StarterPlanDTO;
import com.mutualfunds.api.mutual_fund.entity.User;

/**
 * Contract for recommendation service
 * Defines operations for generating investment recommendations based on user profile
 */
public interface IRecommendationService {
    
    /**
     * Generate a starter investment plan for new investors
     * 
     * @param user User entity with risk profile and investment details
     * @return StarterPlanDTO with equity/debt split and fund recommendations
     */
    StarterPlanDTO generateStarterPlan(User user);
}
