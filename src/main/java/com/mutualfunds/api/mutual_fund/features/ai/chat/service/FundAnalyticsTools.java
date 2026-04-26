package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FundAnalyticsTools {

    private final PortfolioAiTools portfolioAiTools;

    @Tool("Compute fund concentration score using holdings and sector weights.")
    public JsonNode computeConcentrationScore(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.computeConcentrationScore(fundId, detailLevel);
    }

    @Tool("Compute sector and holding overlap between two funds.")
    public JsonNode computeOverlap(
            @P(name = "leftFundId", description = "The first canonical fund UUID") String leftFundId,
            @P(name = "rightFundId", description = "The second canonical fund UUID") String rightFundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.computeOverlap(leftFundId, rightFundId, detailLevel);
    }

    @Tool("Compute fund risk deltas versus its benchmark and category for a chosen horizon.")
    public JsonNode computeRiskDeltas(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "horizon", description = "Risk horizon like 1Y, 3Y, 5Y, or 10Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.computeRiskDeltas(fundId, horizon, detailLevel);
    }

    @Tool("Compute recent NAV trend, drawdown, and recovery for a fund.")
    public JsonNode computeTrendAndDrawdown(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "lookbackMonths", description = "Lookback window in months", required = false)
            Optional<String> lookbackMonths,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.computeTrendAndDrawdown(fundId, lookbackMonths, detailLevel);
    }

    @Tool("Compute weighted ESG exposure using top holdings and their weights.")
    public JsonNode computeWeightedEsgExposure(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.computeWeightedEsgExposure(fundId, detailLevel);
    }
}