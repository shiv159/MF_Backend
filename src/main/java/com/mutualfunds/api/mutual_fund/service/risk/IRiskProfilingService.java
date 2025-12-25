package com.mutualfunds.api.mutual_fund.service.risk;

import com.mutualfunds.api.mutual_fund.dto.risk.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.entity.User;

public interface IRiskProfilingService {
    /**
     * Updates the user's risk profile with rich data.
     * Overwrites existing profile data on retest.
     * Calculates and updates derived risk tolerance and horizon.
     * 
     * @param request The rich risk profile questionnaire data
     * @return The updated User entity
     */
    User updateRiskProfile(RiskProfileRequest request);
}
