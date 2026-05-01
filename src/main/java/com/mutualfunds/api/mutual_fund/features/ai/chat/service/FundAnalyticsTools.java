package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FundAnalyticsTools {

    private final PortfolioAiToolSupport support;

    @Tool("Compute fund concentration score using holdings and sector weights.")
    public JsonNode computeConcentrationScore(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund fund = support.requireFund(fundId);
        double topSector = support.topSectorWeight(fund);
        double topFiveHoldings = support.topHoldingWeightSum(fund, 5);
        double topSingleHolding = support.topHoldingWeightSum(fund, 1);
        double score = support.roundTwoDecimals((topSector * 0.5) + (topFiveHoldings * 0.35) + (topSingleHolding * 0.15));
        String label = score >= 35 ? "HIGH" : score >= 25 ? "MODERATE" : "LOW";

        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("score", score);
        node.put("label", label);
        if (detail == ToolDetailLevel.ANALYST) {
            node.put("topSectorWeight", topSector);
            node.put("topFiveHoldingsWeight", topFiveHoldings);
            node.put("largestHoldingWeight", topSingleHolding);
            node.set("topSectors", support.topSectors(fund, 5));
            node.set("topHoldings", support.topHoldings(fund, 5));
        }
        return node;
    }

    @Tool("Compute sector and holding overlap between two funds.")
    public JsonNode computeOverlap(
            @P(name = "leftFundId", description = "The first canonical fund UUID") String leftFundId,
            @P(name = "rightFundId", description = "The second canonical fund UUID") String rightFundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund left = support.requireFund(leftFundId);
        Fund right = support.requireFund(rightFundId);
        Map<String, Double> leftSectors = support.sectorWeights(left);
        Map<String, Double> rightSectors = support.sectorWeights(right);
        Map<String, Double> leftHoldings = support.holdingWeights(left);
        Map<String, Double> rightHoldings = support.holdingWeights(right);

        double sectorOverlap = support.overlap(leftSectors, rightSectors);
        double holdingOverlap = support.overlap(leftHoldings, rightHoldings);

        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("leftFund", left.getFundName());
        node.put("rightFund", right.getFundName());
        node.put("sectorOverlapPct", sectorOverlap);
        node.put("holdingOverlapPct", holdingOverlap);
        node.put("label", holdingOverlap >= 25 || sectorOverlap >= 45 ? "HIGH" : holdingOverlap >= 12 || sectorOverlap >= 25 ? "MODERATE" : "LOW");
        if (detail == ToolDetailLevel.ANALYST) {
            node.set("sectorOverlapBreakdown", support.overlapBreakdown(leftSectors, rightSectors, "sector"));
            node.set("holdingOverlapBreakdown", support.overlapBreakdown(leftHoldings, rightHoldings, "holding"));
        }
        return node;
    }

    @Tool("Compute fund risk deltas versus its benchmark and category for a chosen horizon.")
    public JsonNode computeRiskDeltas(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "horizon", description = "Risk horizon like 1Y, 3Y, 5Y, or 10Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return support.riskDeltasNode(support.requireFund(fundId), support.horizonKey(horizon));
    }

    @Tool("Compute recent NAV trend, drawdown, and recovery for a fund.")
    public JsonNode computeTrendAndDrawdown(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "lookbackMonths", description = "Lookback window in months", required = false)
            Optional<String> lookbackMonths,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund fund = support.requireFund(fundId);
        List<Map.Entry<YearMonth, Double>> history = support.navHistory(fund);
        int window = support.parseInt(lookbackMonths.orElse("12"), 12);
        List<Map.Entry<YearMonth, Double>> slice = history.stream()
                .sorted(Map.Entry.comparingByKey())
                .skip(Math.max(0, history.size() - window))
                .toList();

        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("lookbackMonths", window);
        if (slice.size() < 2) {
            node.put("status", "INSUFFICIENT_HISTORY");
            return node;
        }
        double first = slice.getFirst().getValue();
        double latest = slice.getLast().getValue();
        double peak = slice.stream().map(Map.Entry::getValue).max(Double::compareTo).orElse(latest);
        double trough = slice.stream().map(Map.Entry::getValue).min(Double::compareTo).orElse(latest);
        double maxDrawdown = peak == 0 ? 0.0 : support.roundTwoDecimals(((trough - peak) / peak) * 100.0);
        double totalReturn = first == 0 ? 0.0 : support.roundTwoDecimals(((latest - first) / first) * 100.0);
        node.put("totalReturnPct", totalReturn);
        node.put("maxDrawdownPct", maxDrawdown);
        node.put("trendLabel", totalReturn >= 8 ? "UP" : totalReturn <= -5 ? "DOWN" : "SIDEWAYS");
        node.put("recoveryLabel", latest >= peak * 0.97 ? "RECOVERED" : latest > trough ? "RECOVERING" : "NEAR_RECENT_LOW");
        if (detail == ToolDetailLevel.ANALYST) {
            ArrayNode series = support.objectMapper().createArrayNode();
            for (Map.Entry<YearMonth, Double> entry : slice) {
                series.add(support.objectMapper().createObjectNode()
                        .put("month", entry.getKey().toString())
                        .put("nav", support.roundTwoDecimals(entry.getValue())));
            }
            node.set("series", series);
        }
        return node;
    }

    @Tool("Compute weighted ESG exposure using top holdings and their weights.")
    public JsonNode computeWeightedEsgExposure(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund fund = support.requireFund(fundId);
        ArrayNode holdings = support.topHoldingsArray(fund);
        double weightedScore = 0.0;
        double severeWeight = 0.0;
        double mediumOrWorse = 0.0;
        double coveredWeight = 0.0;
        ArrayNode breakdown = support.objectMapper().createArrayNode();

        for (JsonNode holding : holdings) {
            double weight = holding.path("weighting").asDouble(0.0);
            double esgScore = holding.path("susEsgRiskScore").asDouble(Double.NaN);
            String category = holding.path("susEsgRiskCategory").asText("UNKNOWN");
            if (!Double.isNaN(esgScore) && weight > 0) {
                weightedScore += esgScore * weight;
                coveredWeight += weight;
            }
            if ("Severe".equalsIgnoreCase(category)) {
                severeWeight += weight;
            }
            if ("Severe".equalsIgnoreCase(category) || "High".equalsIgnoreCase(category) || "Medium".equalsIgnoreCase(category)) {
                mediumOrWorse += weight;
            }
            if (detail == ToolDetailLevel.ANALYST) {
                breakdown.add(support.objectMapper().createObjectNode()
                        .put("securityName", holding.path("securityName").asText(""))
                        .put("weighting", support.roundTwoDecimals(weight))
                        .put("esgScore", Double.isNaN(esgScore) ? 0.0 : support.roundTwoDecimals(esgScore))
                        .put("category", category));
            }
        }

        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("weightedRiskScore", coveredWeight == 0 ? 0.0 : support.roundTwoDecimals(weightedScore / coveredWeight));
        node.put("severeExposurePct", support.roundTwoDecimals(severeWeight));
        node.put("mediumOrWorseExposurePct", support.roundTwoDecimals(mediumOrWorse));
        node.put("coveragePct", support.roundTwoDecimals(coveredWeight));
        node.put("label", severeWeight >= 10 ? "ELEVATED" : mediumOrWorse >= 35 ? "MODERATE" : "LOW");
        if (detail == ToolDetailLevel.ANALYST) {
            node.set("holdings", breakdown);
        }
        return node;
    }
}