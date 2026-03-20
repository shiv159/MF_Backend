package com.mutualfunds.api.mutual_fund.features.portfolio.api;

import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;

import java.util.List;
import java.util.UUID;

public interface PortfolioReadService {
    List<UserHolding> findHoldingsWithFund(UUID userId);
    List<UUID> findDistinctUserIds();
}
