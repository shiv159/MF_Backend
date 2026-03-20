package com.mutualfunds.api.mutual_fund.features.funds.api;

import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.PortfolioCovarianceDTO;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RollingReturnsDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FundAnalyticsFacade {
    RollingReturnsDTO calculateRollingReturns(UUID fundId);
    RiskInsightsDTO calculateRiskInsights(UUID fundId);
    PortfolioCovarianceDTO calculatePortfolioCovariance(List<UUID> fundIds, Map<UUID, Double> weights);
}
