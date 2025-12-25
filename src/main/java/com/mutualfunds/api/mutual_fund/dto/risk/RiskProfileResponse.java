package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RiskProfileResponse {
    private RiskAnalysisDTO riskProfile;
    private AssetAllocationDTO assetAllocation;
    private List<RecommendationCategoryDTO> recommendations;
    private PortfolioHealthDTO portfolioHealth;
}
