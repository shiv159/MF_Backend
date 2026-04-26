package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RollingReturnsDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PortfolioAiTools {

    private static final DateTimeFormatter NAV_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PortfolioToolFacade portfolioToolFacade;
    private final PortfolioChatPayloadFactory payloadFactory;
    private final FundQueryService fundQueryService;
    private final ObjectMapper objectMapper;

    @Tool("Get the current user's portfolio snapshot with freshness and top holdings.")
    public JsonNode getPortfolioSnapshot(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        List<UserHolding> holdings = portfolioToolFacade.findCurrentHoldings();
        ObjectNode snapshot = portfolioToolFacade.buildPortfolioSnapshot(holdings).deepCopy();
        snapshot.put("detailLevel", detail.name());
        snapshot.set("freshness", portfolioFreshness(holdings));
        if (detail == ToolDetailLevel.ANALYST) {
            snapshot.set("holdings", holdingsSummary(holdings, 10));
        }
        return snapshot;
    }

    @Tool("Get the current user's portfolio diagnostic summary and key issues.")
    public JsonNode getPortfolioDiagnostic(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        UUID userId = portfolioToolFacade.currentUserId();
        PortfolioDiagnosticDTO diagnostic = portfolioToolFacade.runDiagnostic(userId);
        if (detail == ToolDetailLevel.ANALYST) {
            return objectMapper.valueToTree(diagnostic);
        }
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("summary", diagnostic.getSummary());
        summary.put("detailLevel", detail.name());
        summary.put("highSeverityCount", diagnostic.getSuggestions() == null ? 0 : diagnostic.getSuggestions().stream()
                .filter(s -> s.getSeverity() == PortfolioDiagnosticDTO.Severity.HIGH)
                .count());
        ArrayNode topIssues = objectMapper.createArrayNode();
        if (diagnostic.getSuggestions() != null) {
            diagnostic.getSuggestions().stream().limit(4).forEach(suggestion -> topIssues.add(objectMapper.createObjectNode()
                    .put("category", suggestion.getCategory() == null ? "UNKNOWN" : suggestion.getCategory().name())
                    .put("severity", suggestion.getSeverity() == null ? "UNKNOWN" : suggestion.getSeverity().name())
                    .put("message", suggestion.getMessage())));
        }
        summary.set("topIssues", topIssues);
        return summary;
    }

    @Tool("Get the current user's saved risk profile and recommended allocation.")
    public JsonNode getRiskProfile(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Optional<RiskProfileResponse> profile = portfolioToolFacade.findRiskProfile();
        if (profile.isEmpty()) {
            return objectMapper.createObjectNode()
                    .put("status", "MISSING")
                    .put("detailLevel", detail.name());
        }
        if (detail == ToolDetailLevel.ANALYST) {
            return objectMapper.valueToTree(profile.get());
        }
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("detailLevel", detail.name());
        compact.put("level", profile.get().getRiskProfile() == null ? "UNKNOWN" : profile.get().getRiskProfile().getLevel());
        compact.put("score", profile.get().getRiskProfile() == null || profile.get().getRiskProfile().getScore() == null
                ? 0
                : profile.get().getRiskProfile().getScore());
        if (profile.get().getAssetAllocation() != null) {
            compact.set("allocation", objectMapper.createObjectNode()
                    .put("equity", value(profile.get().getAssetAllocation().getEquity()))
                    .put("debt", value(profile.get().getAssetAllocation().getDebt()))
                    .put("gold", value(profile.get().getAssetAllocation().getGold())));
        }
        return compact;
    }

    @Tool("Get a compact or analyst summary for a specific fund.")
    public JsonNode getFundSnapshot(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund fund = requireFund(fundId);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("isin", safe(fund.getIsin()));
        node.put("amcName", safe(fund.getAmcName()));
        node.put("category", safe(fund.getFundCategory()));
        node.put("currentNav", value(fund.getCurrentNav()));
        node.put("navAsOf", fund.getNavAsOf() == null ? "" : fund.getNavAsOf().toLocalDate().toString());
        node.put("detailLevel", detail.name());
        node.set("freshness", fundFreshness(fund));
        node.set("headlineRisk", compactRisk(fund, "for3Year"));
        node.set("topSectors", topSectors(fund, 5));
        node.set("topHoldings", topHoldings(fund, detail == ToolDetailLevel.ANALYST ? 10 : 5));
        if (detail == ToolDetailLevel.ANALYST) {
            node.set("composition", getFundComposition(fundId, detailLevel));
            node.set("trend", computeTrendAndDrawdown(fundId, Optional.of("12"), detailLevel));
            node.set("esgExposure", computeWeightedEsgExposure(fundId, detailLevel));
        }
        return node;
    }

    @Tool("Get sector, holding, and geography composition for a specific fund.")
    public JsonNode getFundComposition(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund fund = requireFund(fundId);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("detailLevel", detail.name());
        node.set("topSectors", topSectors(fund, detail == ToolDetailLevel.ANALYST ? 8 : 5));
        node.set("topHoldings", topHoldings(fund, detail == ToolDetailLevel.ANALYST ? 12 : 5));
        node.set("countryMix", countryMix(fund));
        if (detail == ToolDetailLevel.ANALYST) {
            node.put("domesticWeight", domesticWeight(fund));
            node.put("internationalWeight", roundTwoDecimals(100.0 - domesticWeight(fund)));
            node.set("concentration", computeConcentrationScore(fundId, detailLevel));
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
        String horizonKey = horizonKey(horizon);
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund fund = requireFund(fundId);
        ObjectNode deltas = riskDeltasNode(fund, horizonKey);
        if (detail == ToolDetailLevel.ANALYST) {
            return deltas;
        }
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("fundId", fund.getFundId().toString());
        compact.put("fundName", fund.getFundName());
        compact.put("horizon", horizonKey.substring(3));
        compact.put("detailLevel", detail.name());
        compact.set("risk", deltas.path("fundMetrics"));
        compact.set("labels", deltas.path("labels"));
        compact.set("freshness", fundFreshness(fund));
        return compact;
    }

    @Tool("Get performance summary for a fund at a given horizon.")
    public JsonNode getFundPerformanceSummary(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "horizon", description = "Performance horizon like 1M, 3M, 6M, 1Y, 3Y, or 5Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund fund = requireFund(fundId);
        RollingReturnsDTO rolling = portfolioToolFacade.calculateRollingReturns(fund.getFundId());
        ObjectNode performance = performanceSummaryNode(fund, rolling, horizon.orElse("1Y"), detail);
        if (detail == ToolDetailLevel.ANALYST) {
            performance.set("trend", computeTrendAndDrawdown(fundId, Optional.of("12"), detailLevel));
        }
        return performance;
    }

    @Tool("Compare multiple funds on performance, risk, concentration, and fit.")
    public JsonNode compareFunds(
            @P(name = "fundIds", description = "A list of canonical fund UUIDs") List<String> fundIds,
            @P(name = "horizon", description = "Comparison horizon like 1Y, 3Y, or 5Y", required = false)
            Optional<String> horizon,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        List<Fund> funds = fundIds == null ? List.of() : fundIds.stream()
                .map(this::requireFund)
                .distinct()
                .toList();
        ArrayNode rows = objectMapper.createArrayNode();
        for (Fund fund : funds) {
            ObjectNode row = objectMapper.createObjectNode();
            row.set("snapshot", getFundSnapshot(fund.getFundId().toString(), Optional.of(detail.name())));
            row.set("risk", getFundRiskMetrics(fund.getFundId().toString(), horizon, Optional.of(detail.name())));
            row.set("performance", getFundPerformanceSummary(fund.getFundId().toString(), horizon, Optional.of(detail.name())));
            row.set("concentration", computeConcentrationScore(fund.getFundId().toString(), Optional.of(detail.name())));
            rows.add(row);
        }
        ObjectNode comparison = objectMapper.createObjectNode();
        comparison.put("detailLevel", detail.name());
        comparison.put("fundCount", funds.size());
        comparison.set("funds", rows);
        if (funds.size() >= 2) {
            comparison.set("overlap", computeOverlap(
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

    @Tool("Create a read-only rebalance draft based on the current portfolio, diagnostic, and risk profile.")
    public JsonNode draftRebalance(
            @P(name = "mode", description = "Optional draft mode such as conservative, balanced, or simple", required = false)
            Optional<String> mode,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        UUID userId = portfolioToolFacade.currentUserId();
        List<UserHolding> holdings = portfolioToolFacade.findCurrentHoldings(userId);
        PortfolioDataQualityInspector.Result quality = portfolioToolFacade.inspectDataQuality(holdings);
        PortfolioDiagnosticDTO diagnostic = portfolioToolFacade.runDiagnostic(userId);
        RiskProfileResponse profile = portfolioToolFacade.findRiskProfile(userId).orElse(null);
        List<String> warnings = new ArrayList<>();
        ObjectNode draft = payloadFactory.buildRebalanceDraft(holdings, diagnostic, profile, quality, warnings);
        draft.put("mode", mode.orElse("balanced"));
        draft.put("detailLevel", detail.name());
        draft.set("warnings", objectMapper.valueToTree(warnings.stream().distinct().toList()));
        if (detail == ToolDetailLevel.ANALYST) {
            draft.set("diagnostic", objectMapper.valueToTree(diagnostic));
            draft.set("riskProfile", profile == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(profile));
        }
        return draft;
    }

    @Tool("Resolve fund names or parsed holdings into canonical fund candidates with IDs.")
    public JsonNode enrichCandidateFunds(
            @P(name = "namesOrParsedHoldings", description = "A list of raw fund names or extracted holding strings")
            List<String> namesOrParsedHoldings) {
        ArrayNode results = objectMapper.createArrayNode();
        if (namesOrParsedHoldings == null) {
            return results;
        }
        for (String raw : namesOrParsedHoldings) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            List<Fund> matches = fundQueryService.findByFundNameContainingIgnoreCase(raw.trim()).stream()
                    .sorted(Comparator.comparing(Fund::getFundName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .limit(5)
                    .toList();
            ObjectNode item = objectMapper.createObjectNode();
            item.put("query", raw);
            ArrayNode candidates = objectMapper.createArrayNode();
            for (Fund fund : matches) {
                candidates.add(objectMapper.createObjectNode()
                        .put("fundId", fund.getFundId().toString())
                        .put("fundName", fund.getFundName())
                        .put("category", safe(fund.getFundCategory()))
                        .put("isin", safe(fund.getIsin())));
            }
            item.set("candidates", candidates);
            results.add(item);
        }
        return results;
    }

    @Tool("Compute fund concentration score using holdings and sector weights.")
    public JsonNode computeConcentrationScore(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund fund = requireFund(fundId);
        double topSector = topSectorWeight(fund);
        double topFiveHoldings = topHoldingWeightSum(fund, 5);
        double topSingleHolding = topHoldingWeightSum(fund, 1);
        double score = roundTwoDecimals((topSector * 0.5) + (topFiveHoldings * 0.35) + (topSingleHolding * 0.15));
        String label = score >= 35 ? "HIGH" : score >= 25 ? "MODERATE" : "LOW";

        ObjectNode node = objectMapper.createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("score", score);
        node.put("label", label);
        if (detail == ToolDetailLevel.ANALYST) {
            node.put("topSectorWeight", topSector);
            node.put("topFiveHoldingsWeight", topFiveHoldings);
            node.put("largestHoldingWeight", topSingleHolding);
            node.set("topSectors", topSectors(fund, 5));
            node.set("topHoldings", topHoldings(fund, 5));
        }
        return node;
    }

    @Tool("Compute sector and holding overlap between two funds.")
    public JsonNode computeOverlap(
            @P(name = "leftFundId", description = "The first canonical fund UUID") String leftFundId,
            @P(name = "rightFundId", description = "The second canonical fund UUID") String rightFundId,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund left = requireFund(leftFundId);
        Fund right = requireFund(rightFundId);
        Map<String, Double> leftSectors = sectorWeights(left);
        Map<String, Double> rightSectors = sectorWeights(right);
        Map<String, Double> leftHoldings = holdingWeights(left);
        Map<String, Double> rightHoldings = holdingWeights(right);

        double sectorOverlap = overlap(leftSectors, rightSectors);
        double holdingOverlap = overlap(leftHoldings, rightHoldings);

        ObjectNode node = objectMapper.createObjectNode();
        node.put("leftFund", left.getFundName());
        node.put("rightFund", right.getFundName());
        node.put("sectorOverlapPct", sectorOverlap);
        node.put("holdingOverlapPct", holdingOverlap);
        node.put("label", holdingOverlap >= 25 || sectorOverlap >= 45 ? "HIGH" : holdingOverlap >= 12 || sectorOverlap >= 25 ? "MODERATE" : "LOW");
        if (detail == ToolDetailLevel.ANALYST) {
            node.set("sectorOverlapBreakdown", overlapBreakdown(leftSectors, rightSectors, "sector"));
            node.set("holdingOverlapBreakdown", overlapBreakdown(leftHoldings, rightHoldings, "holding"));
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
        return riskDeltasNode(requireFund(fundId), horizonKey(horizon));
    }

    @Tool("Compute recent NAV trend, drawdown, and recovery for a fund.")
    public JsonNode computeTrendAndDrawdown(
            @P(name = "fundId", description = "The canonical fund UUID") String fundId,
            @P(name = "lookbackMonths", description = "Lookback window in months", required = false)
            Optional<String> lookbackMonths,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund fund = requireFund(fundId);
        List<Map.Entry<YearMonth, Double>> history = navHistory(fund);
        int window = parseInt(lookbackMonths.orElse("12"), 12);
        List<Map.Entry<YearMonth, Double>> slice = history.stream()
                .sorted(Map.Entry.comparingByKey())
                .skip(Math.max(0, history.size() - window))
                .toList();

        ObjectNode node = objectMapper.createObjectNode();
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
        double maxDrawdown = peak == 0 ? 0.0 : roundTwoDecimals(((trough - peak) / peak) * 100.0);
        double totalReturn = first == 0 ? 0.0 : roundTwoDecimals(((latest - first) / first) * 100.0);
        node.put("totalReturnPct", totalReturn);
        node.put("maxDrawdownPct", maxDrawdown);
        node.put("trendLabel", totalReturn >= 8 ? "UP" : totalReturn <= -5 ? "DOWN" : "SIDEWAYS");
        node.put("recoveryLabel", latest >= peak * 0.97 ? "RECOVERED" : latest > trough ? "RECOVERING" : "NEAR_RECENT_LOW");
        if (detail == ToolDetailLevel.ANALYST) {
            ArrayNode series = objectMapper.createArrayNode();
            for (Map.Entry<YearMonth, Double> entry : slice) {
                series.add(objectMapper.createObjectNode()
                        .put("month", entry.getKey().toString())
                        .put("nav", roundTwoDecimals(entry.getValue())));
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
        ToolDetailLevel detail = parseDetail(detailLevel);
        Fund fund = requireFund(fundId);
        ArrayNode holdings = topHoldingsArray(fund);
        double weightedScore = 0.0;
        double severeWeight = 0.0;
        double mediumOrWorse = 0.0;
        double coveredWeight = 0.0;
        ArrayNode breakdown = objectMapper.createArrayNode();

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
                breakdown.add(objectMapper.createObjectNode()
                        .put("securityName", holding.path("securityName").asText(""))
                        .put("weighting", roundTwoDecimals(weight))
                        .put("esgScore", Double.isNaN(esgScore) ? 0.0 : roundTwoDecimals(esgScore))
                        .put("category", category));
            }
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("weightedRiskScore", coveredWeight == 0 ? 0.0 : roundTwoDecimals(weightedScore / coveredWeight));
        node.put("severeExposurePct", roundTwoDecimals(severeWeight));
        node.put("mediumOrWorseExposurePct", roundTwoDecimals(mediumOrWorse));
        node.put("coveragePct", roundTwoDecimals(coveredWeight));
        node.put("label", severeWeight >= 10 ? "ELEVATED" : mediumOrWorse >= 35 ? "MODERATE" : "LOW");
        if (detail == ToolDetailLevel.ANALYST) {
            node.set("holdings", breakdown);
        }
        return node;
    }

    @Tool("Assess how well one or more funds fit the current user's risk profile using deterministic scoring.")
    public JsonNode assessSuitabilityFit(
            @P(name = "fundIds", description = "A list of canonical fund UUIDs") List<String> fundIds,
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = parseDetail(detailLevel);
        Optional<RiskProfileResponse> profile = portfolioToolFacade.findRiskProfile();
        String riskLevel = profile.map(RiskProfileResponse::getRiskProfile)
                .map(risk -> risk.getLevel() == null ? "MODERATE" : risk.getLevel().toUpperCase(Locale.ROOT))
                .orElse("MODERATE");

        ArrayNode results = objectMapper.createArrayNode();
        for (String fundId : fundIds == null ? List.<String>of() : fundIds) {
            Fund fund = requireFund(fundId);
            double stdev = compactRisk(fund, "for3Year").path("standardDeviation").asDouble(0.0);
            double concentration = computeConcentrationScore(fundId, Optional.empty()).path("score").asDouble(0.0);
            double internationalWeight = roundTwoDecimals(100.0 - domesticWeight(fund));
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
            if (compactRisk(fund, "for3Year").path("sharpeRatio").asDouble(0.0) > 0.8) {
                score += 8;
                reasons.add("Sharpe ratio is healthy for the chosen benchmark window.");
            }

            score = Math.max(0, Math.min(100, score));
            results.add(objectMapper.createObjectNode()
                    .put("fundId", fund.getFundId().toString())
                    .put("fundName", fund.getFundName())
                    .put("riskProfileLevel", riskLevel)
                    .put("fitScore", score)
                    .put("fitLabel", score >= 80 ? "STRONG" : score >= 60 ? "GOOD" : score >= 45 ? "MIXED" : "WEAK")
                    .set("reasons", objectMapper.valueToTree(detail == ToolDetailLevel.ANALYST ? reasons : reasons.stream().limit(2).toList())));
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("riskProfileLevel", riskLevel);
        node.put("detailLevel", detail.name());
        node.set("funds", results);
        return node;
    }

    private ToolDetailLevel parseDetail(Optional<String> raw) {
        return ToolDetailLevel.from(raw.orElse(null), ToolDetailLevel.COMPACT);
    }

    private Fund requireFund(String fundId) {
        try {
            return fundQueryService.findById(UUID.fromString(fundId))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown fundId: " + fundId));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown fundId: " + fundId, ex);
        }
    }

    private ObjectNode fundFreshness(Fund fund) {
        ObjectNode node = objectMapper.createObjectNode();
        boolean navFresh = fund.getNavAsOf() != null && !fund.getNavAsOf().toLocalDate().isBefore(LocalDate.now().minusDays(7));
        boolean metadataFresh = fund.getLastUpdated() != null && !fund.getLastUpdated().isBefore(LocalDateTime.now().minusDays(7));
        node.put("status", navFresh && metadataFresh ? "FRESH" : navFresh || metadataFresh ? "MIXED" : "STALE");
        node.put("navFresh", navFresh);
        node.put("metadataFresh", metadataFresh);
        return node;
    }

    private ObjectNode portfolioFreshness(List<UserHolding> holdings) {
        PortfolioDataQualityInspector.Result result = portfolioToolFacade.inspectDataQuality(holdings);
        return objectMapper.createObjectNode()
                .put("freshFunds", result.freshCount())
                .put("staleFunds", result.staleCount())
                .put("missingFunds", result.missingCount())
                .set("warnings", objectMapper.valueToTree(result.warnings()));
    }

    private ArrayNode holdingsSummary(List<UserHolding> holdings, int limit) {
        ArrayNode node = objectMapper.createArrayNode();
        holdings.stream()
                .filter(holding -> holding.getFund() != null)
                .sorted(Comparator.comparing((UserHolding holding) -> Optional.ofNullable(holding.getCurrentValue()).orElse(0.0)).reversed())
                .limit(limit)
                .forEach(holding -> node.add(objectMapper.createObjectNode()
                        .put("fundId", holding.getFund().getFundId().toString())
                        .put("fundName", holding.getFund().getFundName())
                        .put("weightPct", Optional.ofNullable(holding.getWeightPct()).orElse(0))
                        .put("currentValue", Optional.ofNullable(holding.getCurrentValue()).orElse(0.0))));
        return node;
    }

    private Map<String, Double> sectorWeights(Fund fund) {
        Map<String, Double> result = new LinkedHashMap<>();
        JsonNode node = fund.getSectorAllocationJson();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> result.put(normalizeLabel(entry.getKey()), entry.getValue().asDouble(0.0)));
        }
        return result;
    }

    private ArrayNode topSectors(Fund fund, int limit) {
        ArrayNode array = objectMapper.createArrayNode();
        sectorWeights(fund).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .forEach(entry -> array.add(objectMapper.createObjectNode()
                        .put("sector", entry.getKey())
                        .put("weightPct", roundTwoDecimals(entry.getValue()))));
        return array;
    }

    private ArrayNode topHoldings(Fund fund, int limit) {
        ArrayNode source = topHoldingsArray(fund);
        ArrayNode result = objectMapper.createArrayNode();
        int count = 0;
        for (JsonNode holding : source) {
            if (count++ >= limit) {
                break;
            }
            result.add(objectMapper.createObjectNode()
                    .put("securityName", holding.path("securityName").asText(""))
                    .put("ticker", holding.path("ticker").asText(""))
                    .put("weightPct", roundTwoDecimals(holding.path("weighting").asDouble(0.0)))
                    .put("sector", normalizeLabel(holding.path("sector").asText("")))
                    .put("country", holding.path("country").asText("")));
        }
        return result;
    }

    private ArrayNode topHoldingsArray(Fund fund) {
        if (fund.getTopHoldingsJson() instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        return objectMapper.createArrayNode();
    }

    private ObjectNode countryMix(Fund fund) {
        Map<String, Double> countryWeights = new LinkedHashMap<>();
        topHoldingsArray(fund).forEach(holding -> {
            String country = holding.path("country").asText("UNKNOWN");
            countryWeights.merge(country, holding.path("weighting").asDouble(0.0), Double::sum);
        });
        ObjectNode node = objectMapper.createObjectNode();
        countryWeights.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> node.put(entry.getKey(), roundTwoDecimals(entry.getValue())));
        return node;
    }

    private double domesticWeight(Fund fund) {
        return roundTwoDecimals(topHoldingsArray(fund).findValuesAsText("country").isEmpty()
                ? 100.0
                : topHoldingsArray(fund).findParents("country").stream()
                .mapToDouble(node -> "India".equalsIgnoreCase(node.path("country").asText()) ? node.path("weighting").asDouble(0.0) : 0.0)
                .sum());
    }

    private ObjectNode compactRisk(Fund fund, String horizonKey) {
        JsonNode risk = fund.getFundMetadataJson().path("risk_volatility").path("fund_risk_volatility").path(horizonKey);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("beta", risk.path("beta").asDouble(0.0));
        node.put("alpha", risk.path("alpha").asDouble(0.0));
        node.put("sharpeRatio", risk.path("sharpeRatio").asDouble(0.0));
        node.put("standardDeviation", risk.path("standardDeviation").asDouble(0.0));
        return node;
    }

    private ObjectNode performanceSummaryNode(Fund fund, RollingReturnsDTO rolling, String horizon, ToolDetailLevel detail) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("detailLevel", detail.name());
        if (rolling != null) {
            node.set("rollingReturns", objectMapper.valueToTree(rolling));
            node.put("selectedReturnPct", selectRollingReturn(rolling, horizon));
        }
        node.set("freshness", fundFreshness(fund));
        return node;
    }

    private double selectRollingReturn(RollingReturnsDTO rolling, String horizon) {
        return switch (horizon.trim().toUpperCase(Locale.ROOT)) {
            case "1M" -> value(rolling.getReturn1M());
            case "3M" -> value(rolling.getReturn3M());
            case "6M" -> value(rolling.getReturn6M());
            case "3Y" -> value(rolling.getReturn3Y());
            case "5Y" -> value(rolling.getReturn5Y());
            default -> value(rolling.getReturn1Y());
        };
    }

    private ObjectNode riskDeltasNode(Fund fund, String horizonKey) {
        JsonNode riskVolatility = fund.getFundMetadataJson().path("risk_volatility");
        JsonNode fundMetrics = riskVolatility.path("fund_risk_volatility").path(horizonKey);
        JsonNode indexMetrics = riskVolatility.path("index_risk_volatility").path(horizonKey);
        JsonNode categoryMetrics = riskVolatility.path("category_risk_volatility").path(horizonKey);

        ObjectNode node = objectMapper.createObjectNode();
        node.put("fundId", fund.getFundId().toString());
        node.put("fundName", fund.getFundName());
        node.put("horizon", horizonKey.substring(3));
        node.set("fundMetrics", copyMetrics(fundMetrics));
        node.set("benchmarkMetrics", copyMetrics(indexMetrics));
        node.set("categoryMetrics", copyMetrics(categoryMetrics));

        ObjectNode deltas = objectMapper.createObjectNode();
        deltas.put("stdevVsBenchmark", roundTwoDecimals(fundMetrics.path("standardDeviation").asDouble(0.0)
                - indexMetrics.path("standardDeviation").asDouble(0.0)));
        deltas.put("stdevVsCategory", roundTwoDecimals(fundMetrics.path("standardDeviation").asDouble(0.0)
                - categoryMetrics.path("standardDeviation").asDouble(0.0)));
        deltas.put("sharpeVsBenchmark", roundTwoDecimals(fundMetrics.path("sharpeRatio").asDouble(0.0)
                - indexMetrics.path("sharpeRatio").asDouble(0.0)));
        deltas.put("sharpeVsCategory", roundTwoDecimals(fundMetrics.path("sharpeRatio").asDouble(0.0)
                - categoryMetrics.path("sharpeRatio").asDouble(0.0)));
        node.set("deltas", deltas);

        ObjectNode labels = objectMapper.createObjectNode();
        labels.put("volatilityView", deltas.path("stdevVsBenchmark").asDouble() < 0
                ? "Lower volatility than benchmark"
                : "Higher volatility than benchmark");
        labels.put("categoryView", deltas.path("stdevVsCategory").asDouble() < 0
                ? "Lower volatility than category"
                : "Higher volatility than category");
        labels.put("riskAdjustedView", deltas.path("sharpeVsCategory").asDouble() >= 0
                ? "Better risk-adjusted return than category"
                : "Weaker risk-adjusted return than category");
        node.set("labels", labels);
        return node;
    }

    private ObjectNode copyMetrics(JsonNode node) {
        return objectMapper.createObjectNode()
                .put("beta", roundTwoDecimals(node.path("beta").asDouble(0.0)))
                .put("alpha", roundTwoDecimals(node.path("alpha").asDouble(0.0)))
                .put("sharpeRatio", roundTwoDecimals(node.path("sharpeRatio").asDouble(0.0)))
                .put("standardDeviation", roundTwoDecimals(node.path("standardDeviation").asDouble(0.0)))
                .put("rSquared", roundTwoDecimals(node.path("rSquared").asDouble(0.0)));
    }

    private Map<String, Double> holdingWeights(Fund fund) {
        Map<String, Double> weights = new LinkedHashMap<>();
        topHoldingsArray(fund).forEach(holding -> {
            String key = holding.path("isin").asText(holding.path("ticker").asText(holding.path("securityName").asText("UNKNOWN")));
            weights.put(key, holding.path("weighting").asDouble(0.0));
        });
        return weights;
    }

    private double overlap(Map<String, Double> left, Map<String, Double> right) {
        double total = 0.0;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            total += Math.min(entry.getValue(), right.getOrDefault(entry.getKey(), 0.0));
        }
        return roundTwoDecimals(total);
    }

    private ArrayNode overlapBreakdown(Map<String, Double> left, Map<String, Double> right, String label) {
        ArrayNode array = objectMapper.createArrayNode();
        Set<String> keys = new LinkedHashSet<>(left.keySet());
        keys.retainAll(right.keySet());
        keys.stream()
                .sorted((a, b) -> Double.compare(Math.min(right.getOrDefault(b, 0.0), left.getOrDefault(b, 0.0)),
                        Math.min(right.getOrDefault(a, 0.0), left.getOrDefault(a, 0.0))))
                .limit(10)
                .forEach(key -> array.add(objectMapper.createObjectNode()
                        .put(label, key)
                        .put("leftWeightPct", roundTwoDecimals(left.getOrDefault(key, 0.0)))
                        .put("rightWeightPct", roundTwoDecimals(right.getOrDefault(key, 0.0)))
                        .put("overlapPct", roundTwoDecimals(Math.min(left.getOrDefault(key, 0.0), right.getOrDefault(key, 0.0))))));
        return array;
    }

    private List<Map.Entry<YearMonth, Double>> navHistory(Fund fund) {
        List<Map.Entry<YearMonth, Double>> history = new ArrayList<>();
        JsonNode node = fund.getFundMetadataJson().path("nav_history");
        if (!node.isObject()) {
            return history;
        }
        node.fields().forEachRemaining(entry -> {
            try {
                history.add(Map.entry(YearMonth.parse(entry.getKey(), NAV_MONTH_FORMAT), entry.getValue().asDouble()));
            } catch (DateTimeParseException ignored) {
                // Ignore malformed keys and keep the usable time series.
            }
        });
        history.sort(Map.Entry.comparingByKey());
        return history;
    }

    private double topSectorWeight(Fund fund) {
        return roundTwoDecimals(sectorWeights(fund).values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
    }

    private double topHoldingWeightSum(Fund fund, int limit) {
        return roundTwoDecimals(topHoldingsArray(fund).valueStream()
                .sorted((left, right) -> Double.compare(right.path("weighting").asDouble(0.0), left.path("weighting").asDouble(0.0)))
                .limit(limit)
                .mapToDouble(node -> node.path("weighting").asDouble(0.0))
                .sum());
    }

    private String horizonKey(Optional<String> raw) {
        return switch (raw.orElse("3Y").trim().toUpperCase(Locale.ROOT)) {
            case "1Y" -> "for1Year";
            case "5Y" -> "for5Year";
            case "10Y" -> "for10Year";
            default -> "for3Year";
        };
    }

    private double value(Double input) {
        return input == null ? 0.0 : roundTwoDecimals(input);
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String normalizeLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = raw.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return Arrays.stream(normalized.split("\\s+"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
