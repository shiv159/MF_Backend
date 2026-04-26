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
public class RecommendationTools {

    private final PortfolioAiTools portfolioAiTools;

    @Tool("Create a read-only rebalance draft based on the current portfolio, diagnostic, and risk profile.")
    public JsonNode draftRebalance(
            @P(name = "mode", description = "Optional draft mode such as conservative, balanced, or simple", required = false)
            Optional<String> mode,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.draftRebalance(mode, detailLevel);
    }

    @Tool("Assess how well one or more funds fit the current user's risk profile using deterministic scoring.")
    public JsonNode assessSuitabilityFit(
            @P(name = "fundIds", description = "A list of canonical fund UUIDs") List<String> fundIds,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.assessSuitabilityFit(fundIds, detailLevel);
    }

    @Tool("Compare multiple funds on performance, risk, concentration, and fit.")
    public JsonNode compareFunds(
            @P(name = "fundIds", description = "A list of canonical fund UUIDs") List<String> fundIds,
            @P(name = "horizon", description = "Comparison horizon like 1Y, 3Y, or 5Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.compareFunds(fundIds, horizon, detailLevel);
    }
}