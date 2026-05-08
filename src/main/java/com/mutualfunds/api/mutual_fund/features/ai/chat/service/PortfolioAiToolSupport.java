package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RollingReturnsDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
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
public class PortfolioAiToolSupport {

    private static final DateTimeFormatter NAV_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PortfolioToolFacade portfolioToolFacade;
    private final PortfolioChatPayloadFactory payloadFactory;
    private final FundQueryService fundQueryService;
    private final ObjectMapper objectMapper;

    PortfolioToolFacade portfolioToolFacade() {
        return portfolioToolFacade;
    }

    PortfolioChatPayloadFactory payloadFactory() {
        return payloadFactory;
    }

    FundQueryService fundQueryService() {
        return fundQueryService;
    }

    ObjectMapper objectMapper() {
        return objectMapper;
    }

    ToolDetailLevel parseDetail(Optional<String> raw) {
        return ToolDetailLevel.from(raw.orElse(null), ToolDetailLevel.COMPACT);
    }

    Fund requireFund(String fundId) {
        try {
            return fundQueryService.findById(UUID.fromString(fundId))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown fundId: " + fundId));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown fundId: " + fundId, ex);
        }
    }

    ObjectNode fundFreshness(Fund fund) {
        ObjectNode node = objectMapper.createObjectNode();
        boolean navFresh = fund.getNavAsOf() != null && !fund.getNavAsOf().toLocalDate().isBefore(LocalDate.now().minusDays(7));
        boolean metadataFresh = fund.getLastUpdated() != null && !fund.getLastUpdated().isBefore(LocalDateTime.now().minusDays(7));
        node.put("status", navFresh && metadataFresh ? "FRESH" : navFresh || metadataFresh ? "MIXED" : "STALE");
        node.put("navFresh", navFresh);
        node.put("metadataFresh", metadataFresh);
        return node;
    }

    ObjectNode portfolioFreshness(List<UserHolding> holdings) {
        PortfolioDataQualityInspector.Result result = portfolioToolFacade.inspectDataQuality(holdings);
        return objectMapper.createObjectNode()
                .put("freshFunds", result.freshCount())
                .put("staleFunds", result.staleCount())
                .put("missingFunds", result.missingCount())
                .set("warnings", objectMapper.valueToTree(result.warnings()));
    }

    ArrayNode holdingsSummary(List<UserHolding> holdings, int limit) {
        ArrayNode node = objectMapper.createArrayNode();
        holdings.stream()
                .filter(holding -> holding.getFund() != null)
                .sorted(java.util.Comparator.comparing((UserHolding holding) -> Optional.ofNullable(holding.getCurrentValue()).orElse(0.0)).reversed())
                .limit(limit)
                .forEach(holding -> node.add(objectMapper.createObjectNode()
                        .put("fundId", holding.getFund().getFundId().toString())
                        .put("fundName", holding.getFund().getFundName())
                        .put("weightPct", Optional.ofNullable(holding.getWeightPct()).orElse(0))
                        .put("currentValue", Optional.ofNullable(holding.getCurrentValue()).orElse(0.0))));
        return node;
    }

    Map<String, Double> sectorWeights(Fund fund) {
        Map<String, Double> result = new LinkedHashMap<>();
        JsonNode node = fund.getSectorAllocationJson();
        if (node != null && node.isObject()) {
            java.util.Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String key = fields.next();
                result.put(normalizeLabel(key), node.path(key).asDouble(0.0));
            }
        }
        return result;
    }

    ArrayNode topSectors(Fund fund, int limit) {
        ArrayNode array = objectMapper.createArrayNode();
        sectorWeights(fund).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .forEach(entry -> array.add(objectMapper.createObjectNode()
                        .put("sector", entry.getKey())
                        .put("weightPct", roundTwoDecimals(entry.getValue()))));
        return array;
    }

    ArrayNode topHoldings(Fund fund, int limit) {
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

    ArrayNode topHoldingsArray(Fund fund) {
        if (fund.getTopHoldingsJson() instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        return objectMapper.createArrayNode();
    }

    ObjectNode countryMix(Fund fund) {
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

    double domesticWeight(Fund fund) {
        return roundTwoDecimals(topHoldingsArray(fund).findValuesAsText("country").isEmpty()
                ? 100.0
                : topHoldingsArray(fund).findParents("country").stream()
                .mapToDouble(node -> "India".equalsIgnoreCase(node.path("country").asText()) ? node.path("weighting").asDouble(0.0) : 0.0)
                .sum());
    }

    ObjectNode compactRisk(Fund fund, String horizonKey) {
        JsonNode risk = fund.getFundMetadataJson().path("risk_volatility").path("fund_risk_volatility").path(horizonKey);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("beta", risk.path("beta").asDouble(0.0));
        node.put("alpha", risk.path("alpha").asDouble(0.0));
        node.put("sharpeRatio", risk.path("sharpeRatio").asDouble(0.0));
        node.put("standardDeviation", risk.path("standardDeviation").asDouble(0.0));
        return node;
    }

    ObjectNode performanceSummaryNode(Fund fund, RollingReturnsDTO rolling, String horizon, ToolDetailLevel detail) {
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

    double selectRollingReturn(RollingReturnsDTO rolling, String horizon) {
        return switch (horizon.trim().toUpperCase(Locale.ROOT)) {
            case "1M" -> value(rolling.getReturn1M());
            case "3M" -> value(rolling.getReturn3M());
            case "6M" -> value(rolling.getReturn6M());
            case "3Y" -> value(rolling.getReturn3Y());
            case "5Y" -> value(rolling.getReturn5Y());
            default -> value(rolling.getReturn1Y());
        };
    }

    ObjectNode riskDeltasNode(Fund fund, String horizonKey) {
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

    ObjectNode copyMetrics(JsonNode node) {
        return objectMapper.createObjectNode()
                .put("beta", roundTwoDecimals(node.path("beta").asDouble(0.0)))
                .put("alpha", roundTwoDecimals(node.path("alpha").asDouble(0.0)))
                .put("sharpeRatio", roundTwoDecimals(node.path("sharpeRatio").asDouble(0.0)))
                .put("standardDeviation", roundTwoDecimals(node.path("standardDeviation").asDouble(0.0)))
                .put("rSquared", roundTwoDecimals(node.path("rSquared").asDouble(0.0)));
    }

    Map<String, Double> holdingWeights(Fund fund) {
        Map<String, Double> weights = new LinkedHashMap<>();
        topHoldingsArray(fund).forEach(holding -> {
            String key = holding.path("isin").asText(holding.path("ticker").asText(holding.path("securityName").asText("UNKNOWN")));
            weights.put(key, holding.path("weighting").asDouble(0.0));
        });
        return weights;
    }

    double overlap(Map<String, Double> left, Map<String, Double> right) {
        double total = 0.0;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            total += Math.min(entry.getValue(), right.getOrDefault(entry.getKey(), 0.0));
        }
        return roundTwoDecimals(total);
    }

    ArrayNode overlapBreakdown(Map<String, Double> left, Map<String, Double> right, String label) {
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

    List<Map.Entry<YearMonth, Double>> navHistory(Fund fund) {
        List<Map.Entry<YearMonth, Double>> history = new ArrayList<>();
        JsonNode node = fund.getFundMetadataJson().path("nav_history");
        if (!node.isObject()) {
            return history;
        }
        java.util.Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String key = fields.next();
            try {
                history.add(Map.entry(YearMonth.parse(key, NAV_MONTH_FORMAT), node.path(key).asDouble()));
            } catch (DateTimeParseException ignored) {
                // Ignore malformed keys and keep the usable time series.
            }
        }
        history.sort(Map.Entry.comparingByKey());
        return history;
    }

    double topSectorWeight(Fund fund) {
        return roundTwoDecimals(sectorWeights(fund).values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
    }

    double topHoldingWeightSum(Fund fund, int limit) {
        return roundTwoDecimals(topHoldingsArray(fund).valueStream()
                .sorted((left, right) -> Double.compare(right.path("weighting").asDouble(0.0), left.path("weighting").asDouble(0.0)))
                .limit(limit)
                .mapToDouble(node -> node.path("weighting").asDouble(0.0))
                .sum());
    }

    String horizonKey(Optional<String> raw) {
        return switch (raw.orElse("3Y").trim().toUpperCase(Locale.ROOT)) {
            case "1Y" -> "for1Year";
            case "5Y" -> "for5Year";
            case "10Y" -> "for10Year";
            default -> "for3Year";
        };
    }

    double value(Double input) {
        return input == null ? 0.0 : roundTwoDecimals(input);
    }

    int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    String normalizeLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = raw.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return Arrays.stream(normalized.split("\\s+"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    String safe(String value) {
        return value == null ? "" : value;
    }

    double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
