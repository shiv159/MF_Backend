package com.mutualfunds.api.mutual_fund.features.risk.api;

import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;

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
