package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ToolSelectionCatalog {

    public static final List<String> RISK_ASSESSOR = List.of(
            "getPortfolioSnapshot",
            "getPortfolioDiagnostic",
            "getRiskProfile",
            "computeConcentrationScore",
            "computeRiskDeltas",
            "assessSuitabilityFit");

    public static final List<String> MARKET_ANALYST = List.of(
            "getFundSnapshot",
            "getFundComposition",
            "getFundRiskMetrics",
            "getFundPerformanceSummary",
            "computeRiskDeltas",
            "computeTrendAndDrawdown",
            "computeWeightedEsgExposure",
            "compareFunds");

    public static final List<String> FINANCIAL_ADVISOR = List.of(
            "getPortfolioSnapshot",
            "getPortfolioDiagnostic",
            "getRiskProfile",
            "compareFunds",
            "draftRebalance",
            "computeConcentrationScore",
            "computeOverlap",
            "computeRiskDeltas",
            "computeTrendAndDrawdown",
            "computeWeightedEsgExposure",
            "assessSuitabilityFit");

    public static final List<String> CRITIC = List.of(
            "getPortfolioSnapshot",
            "getPortfolioDiagnostic",
            "getRiskProfile",
            "computeConcentrationScore",
            "computeRiskDeltas",
            "assessSuitabilityFit");

    private ToolSelectionCatalog() {
    }

    public static List<String> allSelectedTools() {
        Set<String> tools = Stream.of(RISK_ASSESSOR, MARKET_ANALYST, FINANCIAL_ADVISOR, CRITIC)
                .flatMap(List::stream)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return List.copyOf(tools);
    }
}
