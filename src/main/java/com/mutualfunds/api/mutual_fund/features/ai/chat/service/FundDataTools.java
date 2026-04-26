package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FundDataTools {

    private final PortfolioAiTools portfolioAiTools;

    @Tool("Get a compact or analyst summary for a specific fund.")
    public JsonNode getFundSnapshot(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.getFundSnapshot(fundId, detailLevel);
    }

    @Tool("Get sector, holding, and geography composition for a specific fund.")
    public JsonNode getFundComposition(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.getFundComposition(fundId, detailLevel);
    }

    @Tool("Get fund risk metrics for a time horizon and compare them with the benchmark and category.")
    public JsonNode getFundRiskMetrics(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "horizon", description = "Risk horizon like 1Y, 3Y, 5Y, or 10Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.getFundRiskMetrics(fundId, horizon, detailLevel);
    }

    @Tool("Get performance summary for a fund at a given horizon.")
    public JsonNode getFundPerformanceSummary(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "horizon", description = "Performance horizon like 1M, 3M, 6M, 1Y, 3Y, or 5Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.getFundPerformanceSummary(fundId, horizon, detailLevel);
    }

    @Tool("Resolve fund names or parsed holdings into canonical fund candidates with IDs.")
    public JsonNode enrichCandidateFunds(
            @P(name = "namesOrParsedHoldings", description = "A list of raw fund names or extracted holding strings")
            List<String> namesOrParsedHoldings) {
        return portfolioAiTools.enrichCandidateFunds(namesOrParsedHoldings);
    }
}