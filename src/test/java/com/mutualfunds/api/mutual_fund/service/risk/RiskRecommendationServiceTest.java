package com.mutualfunds.api.mutual_fund.service.risk;

import com.mutualfunds.api.mutual_fund.dto.analytics.WealthProjectionDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.PortfolioHealthDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.RecommendationCategoryDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.RiskTolerance;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import com.mutualfunds.api.mutual_fund.service.analytics.PortfolioAnalyzerService;
import com.mutualfunds.api.mutual_fund.service.analytics.WealthProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskRecommendationServiceTest {

    @Mock
    private FundRepository fundRepository;

    @Mock
    private PortfolioAnalyzerService portfolioAnalyzerService;

    @Mock
    private WealthProjectionService wealthProjectionService;

    @InjectMocks
    private RiskRecommendationService riskRecommendationService;

    @Test
    void generateRecommendationReturnsNormalizedCategoryPercentages() {
        Fund largeCap = fund("Large Cap Fund", "Large Cap");
        Fund midCap = fund("Mid Cap Fund", "Mid Cap");
        Fund smallCap = fund("Small Cap Fund", "Small Cap");
        Fund corporateBond = fund("Corporate Bond Fund", "Corporate Bond");

        List<Fund> allFunds = List.of(largeCap, midCap, smallCap, corporateBond);
        Map<UUID, Fund> fundById = allFunds.stream().collect(Collectors.toMap(Fund::getFundId, f -> f));

        when(fundRepository.findAll()).thenReturn(allFunds);
        when(fundRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(fundById.get(invocation.getArgument(0))));
        when(portfolioAnalyzerService.analyzePortfolio(any(List.class), any(Map.class)))
                .thenReturn(PortfolioHealthDTO.builder().build());
        when(wealthProjectionService.calculateProjection(any(List.class), any(Map.class), anyDouble(), anyInt()))
                .thenReturn(WealthProjectionDTO.builder()
                        .projectedYears(10)
                        .totalInvestment(100000.0)
                        .build());

        User user = User.builder()
                .riskTolerance(RiskTolerance.AGGRESSIVE)
                .investmentHorizonYears(11)
                .monthlySipAmount(10000.0)
                .build();

        RiskProfileResponse response = riskRecommendationService.generateRecommendation(user);
        List<RecommendationCategoryDTO> categories = response.getRecommendations();

        double totalCategoryPercent = categories.stream()
                .map(RecommendationCategoryDTO::getAllocationPercent)
                .mapToDouble(Double::doubleValue)
                .sum();

        assertThat(categories).isNotEmpty();
        assertThat(categories)
                .extracting(RecommendationCategoryDTO::getAllocationPercent)
                .allMatch(p -> p >= 0.0 && p <= 100.0);
        assertThat(categories)
                .extracting(RecommendationCategoryDTO::getAllocationPercent)
                .isNotEqualTo(List.of(100.0, 100.0, 100.0, 100.0));
        assertThat(totalCategoryPercent).isCloseTo(100.0, within(0.01));

        double allocationSum = response.getAssetAllocation().getEquity()
                + response.getAssetAllocation().getDebt()
                + response.getAssetAllocation().getGold();
        assertThat(allocationSum).isCloseTo(100.0, within(0.01));
    }

    private Fund fund(String name, String category) {
        return Fund.builder()
                .fundId(UUID.randomUUID())
                .fundName(name)
                .isin("ISIN" + UUID.randomUUID().toString().substring(0, 8))
                .fundCategory(category)
                .build();
    }
}
