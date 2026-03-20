package com.mutualfunds.api.mutual_fund.features.risk.api;

import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;

import java.util.Optional;
import java.util.UUID;

public interface RiskProfileQuery {
    Optional<RiskProfileResponse> getRiskProfile(UUID userId);
}
