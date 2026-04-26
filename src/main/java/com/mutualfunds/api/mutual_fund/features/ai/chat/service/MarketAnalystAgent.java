package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.MarketAssessmentResult;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketAnalystAgent {

    private static final String SYSTEM_PROMPT = """
            You are MarketAnalystAgent for a portfolio copilot.
            Return ONLY valid JSON:
            {"benchmarkContext":"...","categoryContext":"...","marketEvidence":["..."],"marketWarnings":["..."],"freshnessStatus":"FRESH|STALE|MISSING|MIXED","confidence":0.0}
            Tool-first policy:
            - Use fund and analytics tools before making comparative claims.
            - Do not cite external news or macro feeds.
            - If required facts are not present in seed context or memory, call tools.
            """;

    private final LangChain4jWorkflowEngine workflowEngine;

    public MarketAssessmentResult analyze(WorkflowRoute route, AgentContextBundle context, UUID conversationId) {
        MarketAssessmentResult deterministic = deterministicContext(context);
        try {
            WorkflowResponse<MarketAssessmentResult> response = workflowEngine.generate(WorkflowRequest.<MarketAssessmentResult>builder()
                    .conversationId(conversationId)
                    .executionUserId(context.getUserId())
                    .scope("market-analyst")
                    .route(route)
                    .detailLevel(ToolDetailLevel.ANALYST)
                    .userQuestion(context.getUserMessage())
                    .seedContext(seedContext(route, context))
                    .systemPrompt(SYSTEM_PROMPT)
                    .outputType(MarketAssessmentResult.class)
                    .selectedTools(List.of(
                            "getFundSnapshot",
                            "getFundComposition",
                            "getFundRiskMetrics",
                            "getFundPerformanceSummary",
                            "computeRiskDeltas",
                            "computeTrendAndDrawdown",
                            "computeWeightedEsgExposure",
                            "compareFunds"))
                    .build());
            return response.getContent();
        } catch (Exception ex) {
            log.warn("MarketAnalystAgent fell back to deterministic output: {}", ex.getMessage());
            return deterministic;
        }
    }

    private Map<String, Object> seedContext(WorkflowRoute route, AgentContextBundle context) {
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("route", route == null ? "UNKNOWN" : route.name());
        seed.put("screenContext", context.getScreenContext() == null ? "LANDING" : context.getScreenContext());
        seed.put("targetFundIds", context.getHoldingsSummary() == null
                ? List.of()
                : context.getHoldingsSummary().valueStream()
                        .map(node -> node.path("fundId").asText(""))
                        .filter(value -> !value.isBlank())
                        .limit(4)
                        .toList());
        seed.put("comparisonMode", "benchmark_and_category");
        return seed;
    }

    private MarketAssessmentResult deterministicContext(AgentContextBundle context) {
        ArrayNode marketContext = context.getMarketContext();
        List<String> evidence = new java.util.ArrayList<>();
        List<String> warnings = new java.util.ArrayList<>();

        int fresh = 0;
        int stale = 0;
        int missing = 0;
        for (JsonNode fundNode : marketContext) {
            String freshnessStatus = fundNode.path("freshnessStatus").asText("MISSING");
            switch (freshnessStatus) {
                case "FRESH" -> fresh++;
                case "STALE" -> stale++;
                default -> missing++;
            }

            String fundName = fundNode.path("fundName").asText("Unknown fund");
            String category = fundNode.path("categoryName").asText("Unknown category");
            String trend = fundNode.path("navTrendDirection").asText("UNKNOWN");
            evidence.add("%s is positioned in %s with a recent NAV trend of %s.".formatted(fundName, category, trend));
            if ("STALE".equals(freshnessStatus)) {
                warnings.add(fundName + " has stale benchmark/category context.");
            }
            if ("MISSING".equals(freshnessStatus)) {
                warnings.add(fundName + " is missing benchmark/category context from stored fund metadata.");
            }
        }

        String freshness = fresh > 0 && stale == 0 && missing == 0
                ? "FRESH"
                : stale > 0 && missing == 0
                ? "STALE"
                : fresh > 0
                ? "MIXED"
                : "MISSING";

        MarketAssessmentResult result = new MarketAssessmentResult();
        result.setBenchmarkContext(stale > 0 || missing > 0
                ? "Stored benchmark context is partial, so scenario guidance should be treated as advisory."
                : "Stored benchmark context is fresh enough to support benchmark-relative explanation.");
        result.setCategoryContext(marketContext.isEmpty()
                ? "Category context is unavailable."
                : "Stored category and benchmark volatility data was used to interpret the requested portfolio change.");
        result.setMarketEvidence(evidence);
        result.setMarketWarnings(warnings);
        result.setFreshnessStatus(freshness);
        result.setConfidence(fresh > 0 ? 0.72 : 0.45);
        return result;
    }
}