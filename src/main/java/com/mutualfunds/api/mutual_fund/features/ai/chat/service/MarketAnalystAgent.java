package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketAnalystAgent {

    private static final String SYSTEM_PROMPT = """
            You are MarketAnalystAgent for a portfolio copilot.
            Use only the supplied database-backed market context. Do not mention news, macro, or external feeds.
            Return ONLY valid JSON:
            {"benchmarkContext":"...","categoryContext":"...","marketEvidence":["..."],"marketWarnings":["..."],"freshnessStatus":"FRESH|STALE|MISSING|MIXED","confidence":0.0}
            """;

    private final LangChain4jWorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public JsonNode analyze(AgentContextBundle context) {
        ObjectNode deterministic = deterministicContext(context);
        try {
            ObjectNode promptPayload = objectMapper.createObjectNode();
            promptPayload.set("marketContext", context.getMarketContext());
            promptPayload.set("fundAnalytics", context.getFundAnalytics());
            promptPayload.set("portfolioSnapshot", context.getPortfolioSnapshot());
            promptPayload.set("deterministicContext", deterministic);
            LangChain4jWorkflowEngine.Response response = workflowEngine.generate(
                    SYSTEM_PROMPT,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(promptPayload));
            return objectMapper.readTree(stripCodeFence(response.content()));
        } catch (Exception ex) {
            log.warn("MarketAnalystAgent fell back to deterministic output: {}", ex.getMessage());
            return deterministic;
        }
    }

    private ObjectNode deterministicContext(AgentContextBundle context) {
        ArrayNode marketContext = context.getMarketContext();
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode evidence = objectMapper.createArrayNode();
        ArrayNode warnings = objectMapper.createArrayNode();

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

        node.put("benchmarkContext",
                stale > 0 || missing > 0
                        ? "Stored benchmark context is partial, so scenario guidance should be treated as advisory."
                        : "Stored benchmark context is fresh enough to support benchmark-relative explanation.");
        node.put("categoryContext",
                marketContext.isEmpty()
                        ? "Category context is unavailable."
                        : "Stored category and benchmark volatility data was used to interpret the requested portfolio change.");
        node.set("marketEvidence", evidence);
        node.set("marketWarnings", warnings);
        node.put("freshnessStatus", freshness);
        node.put("confidence", fresh > 0 ? 0.72 : 0.45);
        return node;
    }

    private String stripCodeFence(String content) {
        if (content == null) {
            return "{}";
        }
        return content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
    }
}
