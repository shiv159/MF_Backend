package com.mutualfunds.api.mutual_fund.features.risk.application;

import com.mutualfunds.api.mutual_fund.features.risk.api.RiskProfileQuery;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.features.users.api.UserAccountService;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskProfileQueryImpl implements RiskProfileQuery {

    private final UserAccountService userAccountService;
    private final RiskRecommendationService riskRecommendationService;

    @Override
    public Optional<RiskProfileResponse> getRiskProfile(UUID userId) {
        User user = userAccountService.getById(userId);
        if (user.getRiskTolerance() == null || user.getInvestmentHorizonYears() == null) {
            return Optional.empty();
        }
        return Optional.of(riskRecommendationService.generateRecommendation(user));
    }
}
