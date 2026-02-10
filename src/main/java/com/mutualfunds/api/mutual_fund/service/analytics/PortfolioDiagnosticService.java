package com.mutualfunds.api.mutual_fund.service.analytics;

import com.mutualfunds.api.mutual_fund.dto.analytics.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.PortfolioDiagnosticDTO.*;
import com.mutualfunds.api.mutual_fund.dto.risk.PortfolioHealthDTO;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import com.mutualfunds.api.mutual_fund.repository.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based portfolio diagnostic engine.
 * Analyzes user holdings and generates structured suggestions,
 * strengths, and metrics without requiring AI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioDiagnosticService {

    private final UserHoldingRepository userHoldingRepository;
    private final UserRepository userRepository;
    private final PortfolioAnalyzerService portfolioAnalyzerService;

    // ─── Thresholds ──────────────────────────────────────────────────
    private static final double FUND_HOUSE_CONCENTRATION_THRESHOLD = 0.5; // >60% from same AMC
    private static final int OVER_DIVERSIFICATION_THRESHOLD = 5; // >8 funds of similar type
    private static final double SECTOR_CONCENTRATION_THRESHOLD = 30.0; // Top sector >40%
    private static final double HIGH_EXPENSE_RATIO_THRESHOLD = 1.5; // >1.5% expense ratio
    private static final int MIN_FUNDS_FOR_DIVERSIFICATION = 2; // <3 funds is too few
    private static final double LARGE_CAP_SKEW_THRESHOLD = 0.75; // >75% Large Cap
    private static final double SMALL_MID_CAP_SKEW_THRESHOLD = 0.60; // >60% Small/Mid Cap
    private static final double FLEXI_CAP_SKEW_THRESHOLD = 0.70; // >70% Flexi/Multi Cap

    /**
     * Run full portfolio diagnostic for the given user.
     */
    public PortfolioDiagnosticDTO runDiagnostic(UUID userId) {
        log.info("Running portfolio diagnostic for user: {}", userId);

        // 1. Fetch data
        List<UserHolding> holdings = userHoldingRepository.findByUserIdWithFund(userId);
        if (holdings.isEmpty()) {
            return buildEmptyDiagnostic();
        }

        User user = userRepository.findById(userId).orElse(null);

        // 2. Extract funds and weights
        List<Fund> funds = new ArrayList<>();
        Map<UUID, Double> weights = new HashMap<>();
        for (UserHolding h : holdings) {
            if (h.getFund() != null) {
                funds.add(h.getFund());
                double weight = (h.getWeightPct() != null ? h.getWeightPct() : 0) / 100.0;
                weights.put(h.getFund().getFundId(), weight);
            }
        }

        // 3. Run existing portfolio analysis
        PortfolioHealthDTO healthReport = portfolioAnalyzerService.analyzePortfolio(funds, weights);

        // 4. Build metrics
        DiagnosticMetrics metrics = buildMetrics(holdings, funds, healthReport);

        // 5. Run rule-based checks
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();
        suggestions.addAll(checkFundHouseConcentration(funds));
        suggestions.addAll(checkAssetClassDiversity(funds, weights));
        suggestions.addAll(checkOverDiversification(funds));
        suggestions.addAll(checkSectorConcentration(healthReport));
        suggestions.addAll(checkStockOverlap(healthReport));
        suggestions.addAll(checkExpenseRatios(funds));
        suggestions.addAll(checkMarketCapAllocation(funds, weights));
        suggestions.addAll(checkEmotionalDecisions(user));

        // Sort by severity (HIGH first)
        suggestions.sort(Comparator.comparingInt(s -> s.getSeverity().ordinal()));

        // 6. Identify strengths
        List<String> strengths = identifyStrengths(holdings, funds, metrics, healthReport);

        // 7. Generate template-based summary
        String summary = generateTemplateSummary(metrics, holdings, funds);

        return PortfolioDiagnosticDTO.builder()
                .summary(summary)
                .suggestions(suggestions)
                .strengths(strengths)
                .metrics(metrics)
                .build();
    }

    // ─── Metrics Builder ─────────────────────────────────────────────

    private DiagnosticMetrics buildMetrics(List<UserHolding> holdings, List<Fund> funds,
            PortfolioHealthDTO healthReport) {

        // Fund house distribution
        Map<String, Integer> fundHouseDist = funds.stream()
                .filter(f -> f.getAmcName() != null)
                .collect(Collectors.groupingBy(Fund::getAmcName, Collectors.summingInt(f -> 1)));

        // Asset class breakdown
        Map<String, Double> assetClassBreakdown = calculateAssetClassBreakdown(funds, holdings);

        // Top sector
        String topSector = "";
        double topSectorAlloc = 0.0;
        if (healthReport.getAggregateSectorAllocation() != null
                && !healthReport.getAggregateSectorAllocation().isEmpty()) {
            var topEntry = healthReport.getAggregateSectorAllocation().entrySet().iterator().next();
            topSector = topEntry.getKey();
            topSectorAlloc = topEntry.getValue();
        }

        return DiagnosticMetrics.builder()
                .totalFunds(funds.size())
                .totalFunds(funds.size())
                .fundHouseDistribution(fundHouseDist)
                .assetClassBreakdown(assetClassBreakdown)
                .diversificationScore(
                        healthReport.getDiversificationScore() != null ? healthReport.getDiversificationScore() : 0.0)
                .overlapStatus(healthReport.getOverlapStatus() != null ? healthReport.getOverlapStatus() : "N/A")
                .sectorConcentration(
                        healthReport.getSectorConcentration() != null ? healthReport.getSectorConcentration() : "N/A")
                .topSector(topSector)
                .topSectorAllocation(topSectorAlloc)
                .build();
    }

    private Map<String, Double> calculateAssetClassBreakdown(List<Fund> funds, List<UserHolding> holdings) {
        Map<String, Double> breakdown = new LinkedHashMap<>();
        double totalWeight = 0.0;

        Map<UUID, Integer> fundWeightMap = holdings.stream()
                .filter(h -> h.getFund() != null && h.getWeightPct() != null)
                .collect(Collectors.toMap(h -> h.getFund().getFundId(), UserHolding::getWeightPct, (a, b) -> a));

        for (Fund fund : funds) {
            String category = fund.getFundCategory() != null ? fund.getFundCategory() : "Unknown";
            String assetClass = categorizeAssetClass(category);
            double weight = fundWeightMap.getOrDefault(fund.getFundId(), 0);
            breakdown.merge(assetClass, weight * 1.0, Double::sum);
            totalWeight += weight;
        }

        // Normalize to percentages
        if (totalWeight > 0) {
            double finalTotalWeight = totalWeight;
            breakdown.replaceAll((key, value) -> Math.round((value / finalTotalWeight) * 100.0 * 100.0) / 100.0);
        }

        return breakdown;
    }

    private String categorizeAssetClass(String fundCategory) {
        if (fundCategory == null)
            return "Unknown";
        String lower = fundCategory.toLowerCase();
        if (lower.contains("debt") || lower.contains("bond") || lower.contains("liquid")
                || lower.contains("gilt") || lower.contains("money market")
                || lower.contains("ultra short") || lower.contains("overnight")
                || lower.contains("floating rate") || lower.contains("credit risk")) {
            return "Debt";
        } else if (lower.contains("hybrid") || lower.contains("balanced")
                || lower.contains("dynamic asset") || lower.contains("multi asset")
                || lower.contains("arbitrage") || lower.contains("equity savings")) {
            return "Hybrid";
        } else {
            return "Equity"; // Large-Cap, Mid-Cap, Small-Cap, Flexi-Cap, Sectoral, Index, etc.
        }
    }

    // ─── Rule-Based Checks ───────────────────────────────────────────

    private List<DiagnosticSuggestion> checkFundHouseConcentration(List<Fund> funds) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();
        long totalFunds = funds.size();
        if (totalFunds <= 1)
            return suggestions;

        Map<String, Long> amcCount = funds.stream()
                .filter(f -> f.getAmcName() != null)
                .collect(Collectors.groupingBy(Fund::getAmcName, Collectors.counting()));

        List<String> concentratedAmcs = new ArrayList<>();
        for (Map.Entry<String, Long> entry : amcCount.entrySet()) {
            double ratio = (double) entry.getValue() / totalFunds;
            if (ratio >= FUND_HOUSE_CONCENTRATION_THRESHOLD) {
                concentratedAmcs.add(entry.getKey());
            }
        }

        if (!concentratedAmcs.isEmpty()) {
            String amcNames = String.join(", ", concentratedAmcs);
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.FUND_HOUSE_CONCENTRATION)
                    .severity(Severity.MEDIUM)
                    .message(String.format(
                            "You have high concentration in %s. Diversify across multiple fund houses to reduce AMC-specific risks.",
                            amcNames))
                    .details(Map.of("concentratedAMCs", amcNames, "count", concentratedAmcs.size()))
                    .build());
        }
        return suggestions;
    }

    private List<DiagnosticSuggestion> checkAssetClassDiversity(List<Fund> funds, Map<UUID, Double> weights) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();

        Map<String, List<Fund>> byAssetClass = funds.stream()
                .collect(Collectors.groupingBy(f -> categorizeAssetClass(f.getFundCategory())));

        boolean hasEquity = byAssetClass.containsKey("Equity");
        boolean hasDebt = byAssetClass.containsKey("Debt");

        if (hasEquity && !hasDebt && funds.size() >= MIN_FUNDS_FOR_DIVERSIFICATION) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.NO_DEBT_ALLOCATION)
                    .severity(Severity.MEDIUM)
                    .message(
                            "Your portfolio is 100% equity with no debt allocation. Consider adding debt funds (corporate bonds, liquid funds) for stability during market corrections, especially if your investment horizon is under 5 years.")
                    .details(Map.of("equityFunds", byAssetClass.get("Equity").size()))
                    .build());
        }

        if (hasDebt && !hasEquity && funds.size() >= MIN_FUNDS_FOR_DIVERSIFICATION) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.NO_EQUITY_ALLOCATION)
                    .severity(Severity.MEDIUM)
                    .message(
                            "Your portfolio is 100% debt with no equity exposure. For long-term wealth creation, consider adding equity funds (large-cap index or flexi-cap) to beat inflation.")
                    .details(Map.of("debtFunds", byAssetClass.get("Debt").size()))
                    .build());
        }

        if (funds.size() < MIN_FUNDS_FOR_DIVERSIFICATION && funds.size() > 0) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.LACK_OF_DIVERSIFICATION)
                    .severity(Severity.LOW)
                    .message(String.format(
                            "You have only %d fund(s). Consider adding more funds across different categories (large-cap, mid-cap, debt) for better diversification.",
                            funds.size()))
                    .details(Map.of("totalFunds", funds.size()))
                    .build());
        }

        return suggestions;
    }

    private List<DiagnosticSuggestion> checkOverDiversification(List<Fund> funds) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();

        if (funds.size() > OVER_DIVERSIFICATION_THRESHOLD) {
            // Check how many are of similar category
            Map<String, Long> categoryCount = funds.stream()
                    .filter(f -> f.getFundCategory() != null)
                    .collect(Collectors.groupingBy(Fund::getFundCategory, Collectors.counting()));

            List<String> overlappingCategories = categoryCount.entrySet().stream()
                    .filter(e -> e.getValue() > 2)
                    .map(e -> String.format("%s (%d funds)", e.getKey(), e.getValue()))
                    .toList();

            String detail = overlappingCategories.isEmpty()
                    ? "Consider consolidating to 5-7 well-chosen funds."
                    : "Overlapping categories: " + String.join(", ", overlappingCategories)
                            + ". Consider consolidating to avoid redundancy, higher costs, and diluted returns.";

            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.OVER_DIVERSIFICATION)
                    .severity(Severity.MEDIUM)
                    .message(String.format(
                            "Your portfolio has %d funds which may lead to over-diversification. %s",
                            funds.size(), detail))
                    .details(Map.of("totalFunds", funds.size(), "categoryBreakdown", categoryCount))
                    .build());
        }

        return suggestions;
    }

    private List<DiagnosticSuggestion> checkSectorConcentration(PortfolioHealthDTO healthReport) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();

        if (healthReport.getAggregateSectorAllocation() != null
                && !healthReport.getAggregateSectorAllocation().isEmpty()) {
            var topEntry = healthReport.getAggregateSectorAllocation().entrySet().iterator().next();
            if (topEntry.getValue() > SECTOR_CONCENTRATION_THRESHOLD) {
                suggestions.add(DiagnosticSuggestion.builder()
                        .category(SuggestionCategory.SECTOR_CONCENTRATION)
                        .severity(Severity.HIGH)
                        .message(String.format(
                                "Your portfolio is heavily concentrated in %s (%.1f%%). A downturn in this sector could significantly impact your returns. Consider rebalancing to spread risk across multiple sectors.",
                                topEntry.getKey(), topEntry.getValue()))
                        .details(Map.of("sector", topEntry.getKey(), "allocation", topEntry.getValue()))
                        .build());
            }
        }

        return suggestions;
    }

    private List<DiagnosticSuggestion> checkStockOverlap(PortfolioHealthDTO healthReport) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();

        if ("High".equals(healthReport.getOverlapStatus())) {
            int overlappingStockCount = healthReport.getTopOverlappingStocks() != null
                    ? healthReport.getTopOverlappingStocks().size()
                    : 0;

            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.STOCK_OVERLAP)
                    .severity(Severity.HIGH)
                    .message(String.format(
                            "High stock overlap detected — %d stocks appear across multiple funds. This means you're paying multiple expense ratios for essentially the same exposure. Consider replacing overlapping funds with a single diversified fund.",
                            overlappingStockCount))
                    .details(Map.of("overlappingStocks", overlappingStockCount, "overlapStatus", "High"))
                    .build());
        } else if ("Moderate".equals(healthReport.getOverlapStatus())) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.STOCK_OVERLAP)
                    .severity(Severity.LOW)
                    .message(
                            "Moderate stock overlap detected across your funds. This is common but worth monitoring — review if multiple funds hold the same top stocks.")
                    .details(Map.of("overlapStatus", "Moderate"))
                    .build());
        }

        return suggestions;
    }

    private List<DiagnosticSuggestion> checkExpenseRatios(List<Fund> funds) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();

        List<String> highErFunds = funds.stream()
                .filter(f -> f.getExpenseRatio() != null && f.getExpenseRatio() > HIGH_EXPENSE_RATIO_THRESHOLD)
                .map(f -> String.format("%s (%.2f%%)", f.getFundName(), f.getExpenseRatio()))
                .toList();

        if (!highErFunds.isEmpty()) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.HIGH_EXPENSE_RATIO)
                    .severity(Severity.LOW)
                    .message(String.format(
                            "%d fund(s) have expense ratios above %.1f%%: %s. Consider switching to direct plans or lower-cost index fund alternatives.",
                            highErFunds.size(), HIGH_EXPENSE_RATIO_THRESHOLD, String.join("; ", highErFunds)))
                    .details(Map.of("highExpenseFunds", highErFunds))
                    .build());
        }

        return suggestions;
    }

    private List<DiagnosticSuggestion> checkEmotionalDecisions(User user) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();
        if (user == null || user.getProfileDataJson() == null)
            return suggestions;

        try {
            var profileData = user.getProfileDataJson();
            // Navigate: behavioral.marketDropReaction
            if (profileData.has("behavioral") && profileData.get("behavioral").has("marketDropReaction")) {
                String reaction = profileData.get("behavioral").get("marketDropReaction").asText();
                if ("PANIC_SELL".equalsIgnoreCase(reaction) || "SELL".equalsIgnoreCase(reaction)) {
                    suggestions.add(DiagnosticSuggestion.builder()
                            .category(SuggestionCategory.EMOTIONAL_DECISIONS)
                            .severity(Severity.MEDIUM)
                            .message(
                                    "Your risk profile indicates a tendency to sell during market drops. Avoid panicking during dips — historically, markets recover. Use SIPs for rupee-cost averaging and stick to your long-term plan. Consider periodic reviews (quarterly) instead of reacting to daily market movements.")
                            .details(Map.of("marketDropReaction", reaction))
                            .build());
                } else if ("SELL_SOME".equalsIgnoreCase(reaction)) {
                    suggestions.add(DiagnosticSuggestion.builder()
                            .category(SuggestionCategory.EMOTIONAL_DECISIONS)
                            .severity(Severity.LOW)
                            .message(
                                    "Your profile shows moderate anxiety during market corrections. This is normal — consider setting up SIPs to automate investing and reduce emotional decision-making. Review your portfolio quarterly, not daily.")
                            .details(Map.of("marketDropReaction", reaction))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse behavioral data for emotional decision check: {}", e.getMessage());
        }

        return suggestions;
    }

    // ─── Strengths Identification ────────────────────────────────────

    private List<String> identifyStrengths(List<UserHolding> holdings, List<Fund> funds,
            DiagnosticMetrics metrics, PortfolioHealthDTO healthReport) {
        List<String> strengths = new ArrayList<>();

        // 1. Good diversification score
        if (metrics.getDiversificationScore() >= 7.0) {
            strengths.add(String.format(
                    "Excellent diversification score of %.1f/10 — your portfolio is well-spread across sectors and stocks.",
                    metrics.getDiversificationScore()));
        } else if (metrics.getDiversificationScore() >= 5.0) {
            strengths.add(String.format(
                    "Decent diversification score of %.1f/10 — your portfolio has a reasonable spread across different sectors.",
                    metrics.getDiversificationScore()));
        }

        // 2. Low overlap
        if ("Low".equals(metrics.getOverlapStatus())) {
            strengths.add(
                    "Low stock overlap across your funds — each fund is adding unique exposure to your portfolio.");
        }

        // 4. Low expense ratios
        double avgExpenseRatio = funds.stream()
                .filter(f -> f.getExpenseRatio() != null)
                .mapToDouble(Fund::getExpenseRatio)
                .average().orElse(0);
        if (avgExpenseRatio > 0 && avgExpenseRatio < 0.5) {
            strengths.add(String.format(
                    "Very low average expense ratio of %.2f%% — this means more of your returns stay in your pocket.",
                    avgExpenseRatio));
        } else if (avgExpenseRatio > 0 && avgExpenseRatio < 1.0) {
            strengths.add(String.format("Competitive average expense ratio of %.2f%% — your cost efficiency is good.",
                    avgExpenseRatio));
        }

        // 5. Direct plans
        long directPlanCount = funds.stream()
                .filter(f -> Boolean.TRUE.equals(f.getDirectPlan()))
                .count();
        if (directPlanCount == funds.size() && funds.size() > 1) {
            strengths.add("All your funds are direct plans — this saves ~0.5-1% annually compared to regular plans.");
        }

        // 6. Multi asset-class
        if (metrics.getAssetClassBreakdown().size() >= 2) {
            strengths.add(
                    "Your portfolio includes multiple asset classes — this provides natural hedging during market volatility.");
        }

        // Ensure we have at least 2 and at most 3 strengths
        if (strengths.isEmpty()) {
            strengths.add(
                    "You've taken the first step by building a portfolio — that puts you ahead of most investors.");
            strengths.add("Regular portfolio reviews like this one help you stay on track with your financial goals.");
        }

        return strengths.size() > 3 ? strengths.subList(0, 3) : strengths;
    }

    // ─── Summary Generator ───────────────────────────────────────────

    private String generateTemplateSummary(DiagnosticMetrics metrics, List<UserHolding> holdings, List<Fund> funds) {
        StringBuilder sb = new StringBuilder();

        // Sentence 1: Portfolio overview
        sb.append(String.format(
                "Your portfolio consists of %d fund(s). ",
                metrics.getTotalFunds()));

        // Sentence 2: Asset allocation
        Map<String, Double> assetBreakdown = metrics.getAssetClassBreakdown();
        if (assetBreakdown.size() > 1) {
            List<String> parts = assetBreakdown.entrySet().stream()
                    .map(e -> String.format("%.0f%% %s", e.getValue(), e.getKey()))
                    .toList();
            sb.append("The allocation is ").append(String.join(", ", parts)).append(". ");
        } else if (assetBreakdown.size() == 1) {
            var entry = assetBreakdown.entrySet().iterator().next();
            sb.append(String.format("The portfolio is entirely %s-focused. ", entry.getKey().toLowerCase()));
        }

        // Sentence 3: Risk/diversification
        sb.append(String.format("Diversification score is %.1f/10 with %s stock overlap",
                metrics.getDiversificationScore(), metrics.getOverlapStatus().toLowerCase()));

        if (!metrics.getTopSector().isEmpty()) {
            sb.append(String.format(", and the top sector exposure is %s at %.1f%%.",
                    metrics.getTopSector(), metrics.getTopSectorAllocation()));
        } else {
            sb.append(".");
        }

        return sb.toString();
    }

    // ─── Empty Diagnostic ────────────────────────────────────────────

    private PortfolioDiagnosticDTO buildEmptyDiagnostic() {
        return PortfolioDiagnosticDTO.builder()
                .summary(
                        "No portfolio holdings found. Add funds to your portfolio to receive a detailed diagnostic analysis.")
                .suggestions(List.of())
                .strengths(List.of("You're exploring portfolio diagnostics — that's a great first step!"))
                .metrics(DiagnosticMetrics.builder()
                        .totalFunds(0)
                        .fundHouseDistribution(Map.of())
                        .assetClassBreakdown(Map.of())
                        .diversificationScore(0)
                        .overlapStatus("N/A")
                        .sectorConcentration("N/A")
                        .topSector("")
                        .topSectorAllocation(0)
                        .build())
                .build();
    }

    // ─── Public: Build Diagnostic Context for AI Insights ─────────────

    /**
     * Builds a detailed text representation of the diagnostic data to feed to the
     * AI.
     * Includes metrics, detected issues with their details, and current strengths
     * so the AI can generate personalized messages referencing specific data.
     */
    public String buildDiagnosticContextForAI(PortfolioDiagnosticDTO diagnostic) {
        StringBuilder sb = new StringBuilder();
        DiagnosticMetrics m = diagnostic.getMetrics();

        sb.append("Portfolio: ").append(m.getTotalFunds()).append(" funds\n\n");

        sb.append("Fund House Distribution:\n");
        m.getFundHouseDistribution()
                .forEach((amc, count) -> sb.append("  - ").append(amc).append(": ").append(count).append(" fund(s)\n"));

        sb.append("\nAsset Class Breakdown:\n");
        m.getAssetClassBreakdown().forEach((cls, pct) -> sb.append("  - ").append(cls).append(": ")
                .append(String.format("%.0f%%", pct)).append("\n"));

        sb.append(String.format("\nDiversification Score: %.1f/10\n", m.getDiversificationScore()));
        sb.append("Overlap Status: ").append(m.getOverlapStatus()).append("\n");
        sb.append("Sector Concentration: ").append(m.getSectorConcentration()).append("\n");

        if (!m.getTopSector().isEmpty()) {
            sb.append(String.format("Top Sector: %s (%.1f%%)\n", m.getTopSector(), m.getTopSectorAllocation()));
        }

        // Include detected issues with full details so AI can personalize messages
        sb.append("\n─── DETECTED ISSUES (generate a 'suggestionMessages' entry for each) ───\n");
        for (DiagnosticSuggestion s : diagnostic.getSuggestions()) {
            sb.append(String.format("  [%s] %s\n", s.getSeverity(), s.getCategory()));
            if (s.getDetails() != null && !s.getDetails().isEmpty()) {
                s.getDetails()
                        .forEach((key, value) -> sb.append("    ").append(key).append(": ").append(value).append("\n"));
            }
        }

        // Include current template strengths as examples/context
        if (!diagnostic.getStrengths().isEmpty()) {
            sb.append("\n─── CURRENT STRENGTHS (improve upon these) ───\n");
            for (String strength : diagnostic.getStrengths()) {
                sb.append("  - ").append(strength).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Checks for market cap imbalances in equity funds.
     * Triggers if >75% in Large Cap (too conservative) or >60% in Small/Mid Cap
     * (too
     * risky).
     */
    private List<DiagnosticSuggestion> checkMarketCapAllocation(List<Fund> funds, Map<UUID, Double> weights) {
        List<DiagnosticSuggestion> suggestions = new ArrayList<>();
        // Allow analysis for even 2 funds to catch "all small cap" cases
        if (funds.size() < 2)
            return suggestions;

        double largeCapWeight = 0.0;
        double midSmallCapWeight = 0.0;
        double flexiCapWeight = 0.0;
        double totalEquityWeight = 0.0;

        for (Fund f : funds) {
            String category = f.getFundCategory() != null ? f.getFundCategory().toLowerCase() : "";

            // Simple check for equity funds
            boolean isEquity = category.contains("equity") || category.contains("index") || category.contains("cap")
                    || category.contains("sector") || category.contains("thematic");

            if (!isEquity)
                continue;

            double w = weights.getOrDefault(f.getFundId(), 0.0);
            totalEquityWeight += w;

            if (category.contains("large") || category.contains("bluechip") || category.contains("nifty 50")
                    || category.contains("sensex")) {
                largeCapWeight += w;
            } else if (category.contains("mid") || category.contains("small")) {
                midSmallCapWeight += w;
            } else if (category.contains("flexi") || category.contains("multi") || category.contains("focused")
                    || category.contains("contra") || category.contains("value")) {
                flexiCapWeight += w;
            }
        }

        if (totalEquityWeight < 1.0)
            return suggestions; // Skip if negligible equity exposure

        // Normalize weights to equity portion
        double largeCapPct = largeCapWeight / totalEquityWeight;
        double midSmallPct = midSmallCapWeight / totalEquityWeight;
        double flexiCapPct = flexiCapWeight / totalEquityWeight;

        if (largeCapPct > LARGE_CAP_SKEW_THRESHOLD) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.MARKET_CAP_IMBALANCE)
                    .severity(Severity.MEDIUM)
                    .message(String.format(
                            "Your portfolio is heavily skewed towards Large Cap funds (%.0f%%). Consider adding Flexi or Mid Cap funds for better growth potential.",
                            largeCapPct * 100))
                    .details(Map.of("largeCapAllocation", String.format("%.0f%%", largeCapPct * 100)))
                    .build());
        }

        if (midSmallPct > SMALL_MID_CAP_SKEW_THRESHOLD) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.MARKET_CAP_IMBALANCE)
                    .severity(Severity.HIGH)
                    .message(String.format(
                            "High risk alert: %.0f%% of your equity allocation is in Small/Mid Cap funds. This can be very volatile. Consider stabilizing with Large Cap or Flexi Cap funds.",
                            midSmallPct * 100))
                    .details(Map.of("midSmallAllocation", String.format("%.0f%%", midSmallPct * 100)))
                    .build());
        }

        if (flexiCapPct > FLEXI_CAP_SKEW_THRESHOLD) {
            suggestions.add(DiagnosticSuggestion.builder()
                    .category(SuggestionCategory.MARKET_CAP_IMBALANCE)
                    .severity(Severity.MEDIUM)
                    .message(String.format(
                            "You have a very high allocation to Flexi/Multi Cap funds (%.0f%%). While they offer flexibility, ensure the fund managers' styles don't overlap too much.",
                            flexiCapPct * 100))
                    .details(Map.of("flexiCapAllocation", String.format("%.0f%%", flexiCapPct * 100)))
                    .build());
        }

        return suggestions;
    }
}
