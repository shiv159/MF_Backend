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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FundDataTools {

    private final PortfolioAiToolSupport support;
    private final FundAnalyticsTools fundAnalyticsTools;

    @Tool("Get a compact or analyst summary for a specific fund.")
    public JsonNode getFundSnapshot(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund fund = support.requireFund(fundId);
        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("isin", support.safe(fund.getIsin()));
        node.put("amcName", support.safe(fund.getAmcName()));
        node.put("category", support.safe(fund.getFundCategory()));
        node.put("currentNav", support.value(fund.getCurrentNav()));
        node.put("navAsOf", fund.getNavAsOf() == null ? "" : fund.getNavAsOf().toLocalDate().toString());
        node.put("detailLevel", detail.name());
        node.set("freshness", support.fundFreshness(fund));
        node.set("headlineRisk", support.compactRisk(fund, "for3Year"));
        node.set("topSectors", support.topSectors(fund, 5));
        node.set("topHoldings", support.topHoldings(fund, detail == ToolDetailLevel.ANALYST ? 10 : 5));
        if (detail == ToolDetailLevel.ANALYST) {
            node.set("composition", getFundComposition(fundId, detailLevel));
            node.set("trend", fundAnalyticsTools.computeTrendAndDrawdown(fundId, Optional.of("12"), detailLevel));
            node.set("esgExposure", fundAnalyticsTools.computeWeightedEsgExposure(fundId, detailLevel));
        }
        return node;
    }

    @Tool("Get sector, holding, and geography composition for a specific fund.")
    public JsonNode getFundComposition(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund fund = support.requireFund(fundId);
        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("detailLevel", detail.name());
        node.set("topSectors", support.topSectors(fund, detail == ToolDetailLevel.ANALYST ? 8 : 5));
        node.set("topHoldings", support.topHoldings(fund, detail == ToolDetailLevel.ANALYST ? 12 : 5));
        node.set("countryMix", support.countryMix(fund));
        if (detail == ToolDetailLevel.ANALYST) {
            node.put("domesticWeight", support.domesticWeight(fund));
            node.put("internationalWeight", support.roundTwoDecimals(100.0 - support.domesticWeight(fund)));
            node.set("concentration", fundAnalyticsTools.computeConcentrationScore(fundId, detailLevel));
        }
        return node;
    }

    @Tool("Get fund risk metrics for a time horizon and compare them with the benchmark and category.")
    public JsonNode getFundRiskMetrics(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "horizon", description = "Risk horizon like 1Y, 3Y, 5Y, or 10Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        String horizonKey = support.horizonKey(horizon);
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund fund = support.requireFund(fundId);
        ObjectNode deltas = support.riskDeltasNode(fund, horizonKey);
        if (detail == ToolDetailLevel.ANALYST) {
            return deltas;
        }
        ObjectNode compact = support.objectMapper().createObjectNode();
        compact.put("fundId", fund.getFundId().toString());
        compact.put("fundName", fund.getFundName());
        compact.put("horizon", horizonKey.substring(3));
        compact.put("detailLevel", detail.name());
        compact.set("risk", deltas.path("fundMetrics"));
        compact.set("labels", deltas.path("labels"));
        compact.set("freshness", support.fundFreshness(fund));
        return compact;
    }

    @Tool("Get performance summary for a fund at a given horizon.")
    public JsonNode getFundPerformanceSummary(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "horizon", description = "Performance horizon like 1M, 3M, 6M, 1Y, 3Y, or 5Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Fund fund = support.requireFund(fundId);
        com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RollingReturnsDTO rolling =
                support.portfolioToolFacade().calculateRollingReturns(fund.getFundId());
        ObjectNode performance = support.performanceSummaryNode(fund, rolling, horizon.orElse("1Y"), detail);
        if (detail == ToolDetailLevel.ANALYST) {
            performance.set("trend", fundAnalyticsTools.computeTrendAndDrawdown(fundId, Optional.of("12"), detailLevel));
        }
        return performance;
    }

    @Tool("Resolve fund names or parsed holdings into canonical fund candidates with IDs.")
    public JsonNode enrichCandidateFunds(
            @P(name = "namesOrParsedHoldings", description = "A list of raw fund names or extracted holding strings")
            List<String> namesOrParsedHoldings) {
        ArrayNode results = support.objectMapper().createArrayNode();
        if (namesOrParsedHoldings == null) {
            return results;
        }
        for (String raw : namesOrParsedHoldings) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            List<Fund> matches = support.fundQueryService().findByFundNameContainingIgnoreCase(raw.trim()).stream()
                    .sorted(Comparator.comparing(Fund::getFundName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .limit(5)
                    .toList();
            ObjectNode item = support.objectMapper().createObjectNode();
            item.put("query", raw);
            ArrayNode candidates = support.objectMapper().createArrayNode();
            for (Fund fund : matches) {
                candidates.add(support.objectMapper().createObjectNode()
                        .put("fundId", fund.getFundId().toString())
                        .put("fundName", fund.getFundName())
                        .put("category", support.safe(fund.getFundCategory()))
                        .put("isin", support.safe(fund.getIsin())));
            }
            item.set("candidates", candidates);
            results.add(item);
        }
        return results;
    }
}