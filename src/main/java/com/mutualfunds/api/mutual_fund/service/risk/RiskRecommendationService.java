package com.mutualfunds.api.mutual_fund.service.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.dto.risk.*;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.RiskTolerance;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import com.mutualfunds.api.mutual_fund.service.analytics.PortfolioAnalyzerService;
import com.mutualfunds.api.mutual_fund.service.analytics.WealthProjectionService;
import com.mutualfunds.api.mutual_fund.dto.analytics.WealthProjectionDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.FundSimilarityDTO;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskRecommendationService {

    private final FundRepository fundRepository;
    private final PortfolioAnalyzerService portfolioAnalyzerService;
    private final WealthProjectionService wealthProjectionService;

    public RiskProfileResponse generateRecommendation(User user) {
        log.info("Generating recommendation for user: {}", user.getEmail());

        // 1. Risk Analysis
        RiskAnalysisDTO riskAnalysis = analyzeRisk(user);

        // 2. Asset Allocation (Goal-Duration Matching)
        AssetAllocationDTO allocation = calculateAssetAllocation(user);

        // 3. Fund Selection
        List<RecommendationCategoryDTO> recommendations = selectFunds(user, allocation);

        // 4. Portfolio Health
        PortfolioHealthDTO health = checkPortfolioHealth(recommendations);

        return RiskProfileResponse.builder()
                .riskProfile(riskAnalysis)
                .assetAllocation(allocation)
                .recommendations(recommendations)
                .portfolioHealth(health)
                .build();
    }

    private RiskAnalysisDTO analyzeRisk(User user) {
        // In a real scenario, this score might be stored in DB or recalculated.
        // For now, we map RiskTolerance enum back to a representative score range
        int score = 50;
        String rationale = "Based on your profile.";

        if (user.getRiskTolerance() == RiskTolerance.AGGRESSIVE) {
            score = 80;
            rationale = "High risk capacity due to long horizon (>10y) and stable financials allows for aggressive equity exposure.";
        } else if (user.getRiskTolerance() == RiskTolerance.MODERATE) {
            score = 50;
            rationale = "Balanced approach suitable for medium term goals.";
        } else {
            score = 30;
            rationale = "Conservative approach prioritizing capital protection.";
        }

        return RiskAnalysisDTO.builder()
                .score(score)
                .level(user.getRiskTolerance().name())
                .rationale(rationale)
                .build();
    }

    private AssetAllocationDTO calculateAssetAllocation(User user) {
        double equity = 0;
        double debt = 0;
        double gold = 0;
        int horizon = user.getInvestmentHorizonYears();

        // Goal Duration Matching
        if (horizon > 10) {
            equity = 80;
            debt = 15;
            gold = 5;
        } else if (horizon > 5) {
            equity = 60;
            debt = 30;
            gold = 10;
        } else if (horizon > 3) {
            equity = 40;
            debt = 50;
            gold = 10;
        } else {
            equity = 10;
            debt = 90;
            gold = 0;
        }

        // Adjust based on Risk Tolerance (True Risk Score tilt)
        if (user.getRiskTolerance() == RiskTolerance.CONSERVATIVE) {
            equity = Math.max(0, equity - 20);
            debt += 20;
        } else if (user.getRiskTolerance() == RiskTolerance.AGGRESSIVE) {
            // Cap equity at 90
            if (equity < 90) {
                equity += 10;
                debt = Math.max(0, debt - 10);
            }
        }

        // Normalize
        double total = equity + debt + gold;
        if (total != 100) {
            // simple normalization
        }

        return AssetAllocationDTO.builder()
                .equity(equity)
                .debt(debt)
                .gold(gold)
                .build();
    }

    private List<RecommendationCategoryDTO> selectFunds(User user, AssetAllocationDTO allocation) {
        List<RecommendationCategoryDTO> categories = new ArrayList<>();
        double totalSip = user.getMonthlySipAmount() != null ? user.getMonthlySipAmount() : 5000.0;

        // Fetch all funds (in prod, filter efficiently)
        // Using existing repository method for simplicity or findAll and filter in
        // memory if dataset is small (<100 funds)
        List<Fund> allFunds = fundRepository.findAll();

        if (allocation.getEquity() > 0) {
            double equityAmt = totalSip * (allocation.getEquity() / 100.0);
            categories.addAll(selectEquityFunds(allFunds, equityAmt, user.getRiskTolerance()));
        }

        if (allocation.getDebt() > 0) {
            double debtAmt = totalSip * (allocation.getDebt() / 100.0);
            categories.addAll(selectDebtFunds(allFunds, debtAmt, user.getInvestmentHorizonYears()));
        }

        return categories;
    }

    private List<RecommendationCategoryDTO> selectEquityFunds(List<Fund> funds, double amount, RiskTolerance risk) {
        List<RecommendationCategoryDTO> equityCats = new ArrayList<>();

        // Define Split Strategy
        Map<String, Double> split = new HashMap<>();
        if (risk == RiskTolerance.AGGRESSIVE) {
            split.put("Large Cap", 0.40);
            split.put("Mid Cap", 0.40);
            split.put("Small Cap", 0.20);
        } else if (risk == RiskTolerance.MODERATE) {
            split.put("Large Cap", 0.70);
            split.put("Flexi Cap", 0.30);
        } else {
            // Conservative - Use Hybrid
            split.put("Aggressive Hybrid", 1.0);
        }

        // Execute Split
        for (Map.Entry<String, Double> entry : split.entrySet()) {
            String category = entry.getKey();
            double percent = entry.getValue();
            double catAmount = amount * percent;

            List<Fund> catFunds = filterFundsByCategory(funds, category);

            if (!catFunds.isEmpty()) {
                Fund bestFund = getBestFund(catFunds);
                equityCats.add(createCategoryRecommendation(category, catAmount, bestFund));
            } else {
                log.warn("No funds found for category: {}.", category);
                // Fallback logic could go here
            }
        }

        return equityCats;
    }

    private List<RecommendationCategoryDTO> selectDebtFunds(List<Fund> funds, double amount, int horizon) {
        List<RecommendationCategoryDTO> debtCats = new ArrayList<>();
        String targetCategory;

        if (horizon < 3) {
            targetCategory = "Liquid"; // or Ultra Short Term
        } else {
            targetCategory = "Corporate Bond";
        }

        List<Fund> debtFunds = filterFundsByCategory(funds, targetCategory);
        if (!debtFunds.isEmpty()) {
            Fund bestFund = getBestFund(debtFunds);
            debtCats.add(createCategoryRecommendation(targetCategory, amount, bestFund));
        }

        return debtCats;
    }

    private List<Fund> filterFundsByCategory(List<Fund> funds, String targetCategory) {
        return funds.stream().filter(f -> {
            String fc = f.getFundCategory();
            if (fc == null)
                return false;

            if ("Large Cap".equals(targetCategory)) {
                // Prioritize Pure Large Cap, exclude Mid Cap to avoid duplicates
                return (fc.contains("Large Cap") || fc.contains("Large-Cap Index")) && !fc.contains("Mid");
            } else if ("Mid Cap".equals(targetCategory)) {
                return fc.contains("Mid-Cap") || fc.contains("Mid Cap") || fc.contains("Large & Mid");
            } else if ("Small Cap".equals(targetCategory)) {
                return fc.contains("Small-Cap") || fc.contains("Small Cap") || fc.contains("Sectoral");
            } else if ("Flexi Cap".equals(targetCategory)) {
                return fc.contains("Flexi-Cap") || fc.contains("Multi-Cap") || fc.contains("Flexi");
            } else if ("Aggressive Hybrid".equals(targetCategory)) {
                return fc.contains("Aggressive Hybrid") || fc.contains("Hybrid");
            } else if ("Liquid".equals(targetCategory)) {
                return fc.contains("Liquid");
            } else if ("Corporate Bond".equals(targetCategory)) {
                return fc.contains("Corporate Bond");
            }
            return false;
        }).collect(Collectors.toList());
    }

    private Fund getBestFund(List<Fund> funds) {
        // Scoring logic
        return funds.stream().max(Comparator.comparingDouble(this::calculateFunctionScore)).orElse(funds.get(0));
    }

    private double calculateFunctionScore(Fund fund) {
        // Default to 0 if no metadata
        if (fund.getFundMetadataJson() == null)
            return 0.0;

        JsonNode threeYear = fund.getFundMetadataJson().at("/risk_volatility/fund_risk_volatility/for3Year");
        JsonNode oneYear = fund.getFundMetadataJson().at("/risk_volatility/fund_risk_volatility/for1Year");
        JsonNode category = fund.getFundMetadataJson().at("/risk_volatility/category_risk_volatility/for3Year");

        if (threeYear.isMissingNode()) {
            // Check top level fallback if nested structure is missing
            double topAlpha = getDouble(fund.getFundMetadataJson(), "alpha", 0.0);
            if (topAlpha == 0.0)
                return 0.0;
            // Map top level if threeYear missing... but threeYear is usually better.
        }

        // Extract core metrics with fallbacks
        double alpha = getDouble(threeYear, "alpha", 0.0);
        double sharpe = getDouble(threeYear, "sharpeRatio", 0.0);
        double beta = getDouble(threeYear, "beta", 1.0);
        double stdev = getDouble(threeYear, "standardDeviation", 10.0); // high default penalty
        double rSquared = getDouble(threeYear, "rSquared", 50.0);

        // Relative performance (very powerful signal)
        double categorySharpe = getDouble(category, "sharpeRatio", sharpe); // fallback to own
        double categoryAlpha = getDouble(category, "alpha", alpha);
        double relativeSharpeAdvantage = sharpe - categorySharpe;
        double relativeAlphaAdvantage = alpha - categoryAlpha;

        // Fund size bonus (liquidity & stability)
        double fundSizeCr = getDouble(fund.getFundMetadataJson(), "fund_size", 0.0) / 10_000_000.0; // in Crores
        double sizeBonus = Math.min(fundSizeCr / 1000.0, 1.0); // cap at +1 for >10,000 Cr

        // Consistency check: compare 1Y vs 3Y Sharpe (penalize sharp drop-offs)
        double sharpe1Y = getDouble(oneYear, "sharpeRatio", sharpe);
        double consistencyPenalty = sharpe1Y < sharpe * 0.7 ? -0.5 : 0.0; // big drop = red flag

        // Composite Score (normalized weights summing ~1.0 influence)
        double score = 0.0;

        score += sharpe * 0.4; // 40% - Risk-adjusted return (core)
        score += alpha * 0.15; // 15% - Pure outperformance
        score += relativeSharpeAdvantage * 0.2; // 20% - Beats peers (strong signal)
        score += relativeAlphaAdvantage * 0.1; // 10% - Skill vs category
        score += (1.0 / (1.0 + stdev / 10.0)) * 0.08; // Low volatility bonus (inverse)
        score += (beta < 1.0 ? (1.0 - beta) * 0.05 : 0.0); // Bonus for defensive beta only
        score += (rSquared / 100.0) * 0.02; // Slight bonus for predictability
        score += sizeBonus * 0.05; // Stability
        score += consistencyPenalty; // Penalize inconsistency

        return Math.max(score, 0.0); // Never negative
    }

    private double getMetric(Fund fund, String key) {
        if (fund.getFundMetadataJson() == null)
            return 0.0;
        JsonNode riskVol = fund.getFundMetadataJson().at("/risk_volatility/fund_risk_volatility/for3Year");
        String jsonKey = "sharpe".equalsIgnoreCase(key) ? "sharpeRatio" : key;
        return getDouble(riskVol, jsonKey, 0.0);
    }

    private double getDouble(JsonNode node, String field, double defaultValue) {
        if (node == null || node.isMissingNode() || !node.has(field))
            return defaultValue;
        JsonNode value = node.get(field);
        return value.isNull() ? defaultValue : value.asDouble();
    }

    private RecommendationCategoryDTO createCategoryRecommendation(String category, double amount, Fund fund) {
        // Extract Risk Metrics
        FundRiskMetricsDTO metrics = FundRiskMetricsDTO.builder()
                .alpha(getMetric(fund, "alpha"))
                .beta(getMetric(fund, "beta"))
                .sharpeRatio(getMetric(fund, "sharpeRatio"))
                .standardDeviation(getMetric(fund, "standardDeviation"))
                .rSquared(getMetric(fund, "rSquared"))
                .build();

        FundRecommendationDTO fundDto = FundRecommendationDTO.builder()
                .id(fund.getFundId())
                .name(fund.getFundName())
                .category(fund.getFundCategory())
                .reason(generateReason(fund))
                .riskMetrics(metrics)
                .sectorAllocation(fund.getSectorAllocationJson())
                .topHoldings(fund.getTopHoldingsJson())
                .build();

        return RecommendationCategoryDTO.builder()
                .allocationCategory(category)
                .allocationPercent(100.0) // simplified
                .amount(amount)
                .funds(List.of(fundDto))
                .build();
    }

    private String generateReason(Fund fund) {
        double alpha = getMetric(fund, "alpha");
        if (alpha > 2.0)
            return "High Alpha Generator";
        return "Consistent Performer";
    }

    private PortfolioHealthDTO checkPortfolioHealth(List<RecommendationCategoryDTO> recs) {
        // Flatten funds and weights
        List<Fund> funds = new ArrayList<>();
        Map<UUID, Double> weights = new HashMap<>();

        // Calculate total amount to normalize weights
        double totalAmount = recs.stream().mapToDouble(RecommendationCategoryDTO::getAmount).sum();

        for (RecommendationCategoryDTO cat : recs) {
            for (FundRecommendationDTO dto : cat.getFunds()) {
                // We need the Full Fund Entity. But DTO only has ID.
                // This service already fetched funds earlier in selectFunds().
                // However, we don't have easy access to Entity here without re-fetching or
                // passing it down.
                // Optimization: Let's fetch by ID here since it's only 3-5 funds.
                fundRepository.findById(dto.getId()).ifPresent(fund -> {
                    funds.add(fund);
                    // Approximate weight based on category amount / number of funds in category
                    double weight = (cat.getAmount() / cat.getFunds().size()) / totalAmount;
                    weights.put(fund.getFundId(), weight);
                });
            }
        }

        PortfolioHealthDTO health = portfolioAnalyzerService.analyzePortfolio(funds, weights);

        // Add Wealth Projection (Default: ₹1 Lakh for 10 Years for visualization)
        var projection = wealthProjectionService.calculateProjection(funds, weights, 100000.0, 10);
        health.setWealthProjection(projection);

        return health;
    }
}
