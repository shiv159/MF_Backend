package com.mutualfunds.api.mutual_fund.features.portfolio.holdings.application;

import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.persistence.UserHoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioReadServiceImpl implements PortfolioReadService {

    private final UserHoldingRepository userHoldingRepository;

    @Override
    public List<UserHolding> findHoldingsWithFund(UUID userId) {
        return userHoldingRepository.findByUserIdWithFund(userId);
    }

    @Override
    public List<UUID> findDistinctUserIds() {
        return userHoldingRepository.findDistinctUserIds();
    }
}
