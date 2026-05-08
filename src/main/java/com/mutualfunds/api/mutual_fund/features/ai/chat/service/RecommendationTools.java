package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RecommendationTools {

    private final PortfolioAiToolSupport support;
    private final FundDataTools fundDataTools;
    private final FundAnalyticsTools fundAnalyticsTools;

    @Tool("Create a read-only rebalance draft based on the current portfolio, diagnostic, and risk profile.")
    public JsonNode draftRebalance(
            @P(name = "mode", description = "Optional draft mode such as conservative, balanced, or simple", required = false)
            Optional<String> mode,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        java.util.UUID userId = support.portfolioToolFacade().currentUserId();
        List<UserHolding> holdings = support.portfolioToolFacade().findCurrentHoldings(userId);
        PortfolioDataQualityInspector.Result quality = support.portfolioToolFacade().inspectDataQuality(holdings);
        PortfolioDiagnosticDTO diagnostic = support.portfolioToolFacade().runDiagnostic(userId);
        RiskProfileResponse profile = support.portfolioToolFacade().findRiskProfile(userId).orElse(null);
        List<String> warnings = new ArrayList<>();
        ObjectNode draft = support.payloadFactory().buildRebalanceDraft(holdings, diagnostic, profile, quality, warnings);
        draft.put("mode", mode.orElse("balanced"));
        draft.put("detailLevel", detail.name());
        draft.set("warnings", support.objectMapper().valueToTree(warnings.stream().distinct().toList()));
        if (detail == ToolDetailLevel.ANALYST) {
            draft.set("diagnostic", support.objectMapper().valueToTree(diagnostic));
            draft.set("riskProfile", profile == null
                    ? support.objectMapper().createObjectNode()
                    : support.objectMapper().valueToTree(profile));
        }
        return draft;
    }

    @Tool("Assess how well one or more funds fit the current user's risk profile using deterministic scoring.")
    public JsonNode assessSuitabilityFit(
            @P(name = "fundIds", description = "A list of canonical fund UUIDs") List<String> fundIds,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Optional<RiskProfileResponse> profile = support.portfolioToolFacade().findRiskProfile();
        String riskLevel = profile.map(RiskProfileResponse::getRiskProfile)
                .map(risk -> risk.getLevel() == null ? "MODERATE" : risk.getLevel().toUpperCase(java.util.Locale.ROOT))
                .orElse("MODERATE");

        ArrayNode results = support.objectMapper().createArrayNode();
        for (String fundId : fundIds == null ? List.<String>of() : fundIds) {
            Fund fund = support.requireFund(fundId);
            double stdev = support.compactRisk(fund, "for3Year").path("standardDeviation").asDouble(0.0);
            double concentration = fundAnalyticsTools.computeConcentrationScore(fundId, Optional.empty()).path("score").asDouble(0.0);
            double internationalWeight = support.roundTwoDecimals(100.0 - support.domesticWeight(fund));
            int score = 70;
            List<String> reasons = new ArrayList<>();

            switch (riskLevel) {
                case "CONSERVATIVE" -> {
                    if (stdev > 13) {
                        score -= 20;
                        reasons.add("3Y volatility is high for a conservative profile.");
                    }
                    if (concentration > 30) {
                        score -= 10;
                        reasons.add("Concentration is elevated.");
                    }
                }
                case "AGGRESSIVE" -> {
                    if (stdev < 10) {
                        score -= 8;
                        reasons.add("Volatility is lower than what an aggressive profile typically tolerates.");
                    } else {
                        score += 6;
                    }
                }
                default -> {
                    if (stdev > 15) {
                        score -= 12;
                        reasons.add("Volatility is somewhat high for a moderate profile.");
                    }
                }
            }

            if (internationalWeight > 25) {
                score -= 4;
                reasons.add("International sleeve adds extra policy and currency complexity.");
            }
            if (support.compactRisk(fund, "for3Year").path("sharpeRatio").asDouble(0.0) > 0.8) {
                score += 8;
                reasons.add("Sharpe ratio is healthy for the chosen benchmark window.");
            }

            score = Math.max(0, Math.min(100, score));
            results.add(support.objectMapper().createObjectNode()
                    .put("fundId", fund.getFundId().toString())
                    .put("fundName", fund.getFundName())
                    .put("riskProfileLevel", riskLevel)
                    .put("fitScore", score)
                    .put("fitLabel", score >= 80 ? "STRONG" : score >= 60 ? "GOOD" : score >= 45 ? "MIXED" : "WEAK")
                    .set("reasons", support.objectMapper().valueToTree(detail == ToolDetailLevel.ANALYST ? reasons : reasons.stream().limit(2).toList())));
        }

        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("riskProfileLevel", riskLevel);
        node.put("detailLevel", detail.name());
        node.set("funds", results);
        return node;
    }

    @Tool("Compare multiple funds on performance, risk, concentration, and fit.")
    public JsonNode compareFunds(
            @P(name = "fundIds", description = "A list of canonical fund UUIDs") List<String> fundIds,
            @P(name = "horizon", description = "Comparison horizon like 1Y, 3Y, or 5Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        List<Fund> funds = fundIds == null ? List.of() : fundIds.stream()
                .map(support::requireFund)
                .distinct()
                .toList();
        ArrayNode rows = support.objectMapper().createArrayNode();
        for (Fund fund : funds) {
            ObjectNode row = support.objectMapper().createObjectNode();
            row.set("snapshot", fundDataTools.getFundSnapshot(fund.getFundId().toString(), Optional.of(detail.name())));
            row.set("risk", fundDataTools.getFundRiskMetrics(fund.getFundId().toString(), horizon, Optional.of(detail.name())));
            row.set("performance", fundDataTools.getFundPerformanceSummary(fund.getFundId().toString(), horizon, Optional.of(detail.name())));
            row.set("concentration", fundAnalyticsTools.computeConcentrationScore(fund.getFundId().toString(), Optional.of(detail.name())));
            rows.add(row);
        }
        ObjectNode comparison = support.objectMapper().createObjectNode();
        comparison.put("detailLevel", detail.name());
        comparison.put("fundCount", funds.size());
        comparison.set("funds", rows);
        if (funds.size() >= 2) {
            comparison.set("overlap", fundAnalyticsTools.computeOverlap(
                    funds.get(0).getFundId().toString(),
                    funds.get(1).getFundId().toString(),
                    Optional.of(detail.name())));
        }
        if (detail == ToolDetailLevel.ANALYST) {
            comparison.set("suitability", assessSuitabilityFit(
                    funds.stream().map(fund -> fund.getFundId().toString()).toList(),
                    Optional.of(detail.name())));
        }
        return comparison;
    }
}
