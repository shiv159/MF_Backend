package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundAnalyticsFacade;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.PortfolioCovarianceDTO;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RollingReturnsDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.application.PortfolioDiagnosticService;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.features.risk.api.RiskProfileQuery;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioToolFacade {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\b(?:vs|versus|compare|and)\\b|,");

    private final CurrentUserProvider currentUserProvider;
    private final PortfolioReadService portfolioReadService;
    private final FundQueryService fundQueryService;
    private final PortfolioDiagnosticService portfolioDiagnosticService;
    private final RiskProfileQuery riskProfileQuery;
    private final FundAnalyticsFacade fundAnalyticsFacade;
    private final PortfolioDataQualityInspector dataQualityInspector;
    private final ObjectMapper objectMapper;
    private final AiWorkflowProperties properties;

    public UUID currentUserId() {
        return currentUserProvider.getCurrentUserId();
    }

    public List<UserHolding> findCurrentHoldings() {
        return portfolioReadService.findHoldingsWithFund(currentUserId());
    }

    public List<UserHolding> findCurrentHoldings(UUID userId) {
        return portfolioReadService.findHoldingsWithFund(userId);
    }

    public PortfolioDataQualityInspector.Result inspectDataQuality(List<UserHolding> holdings) {
        return dataQualityInspector.inspect(holdings);
    }

    public Optional<RiskProfileResponse> findRiskProfile() {
        return safeTool("risk_profile_query", () -> riskProfileQuery.getRiskProfile(currentUserId()), Optional.empty());
    }

    public Optional<RiskProfileResponse> findRiskProfile(UUID userId) {
        return safeTool("risk_profile_query", () -> riskProfileQuery.getRiskProfile(userId), Optional.empty());
    }

    public PortfolioDiagnosticDTO runDiagnostic() {
        return safeTool("portfolio_diagnostic", () -> portfolioDiagnosticService.runDiagnostic(currentUserId()),
                PortfolioDiagnosticDTO.builder()
                        .summary("Diagnostic data is temporarily unavailable.")
                        .suggestions(List.of())
                        .strengths(List.of())
                        .metrics(PortfolioDiagnosticDTO.DiagnosticMetrics.builder()
                                .assetClassBreakdown(Map.of())
                                .build())
                        .build());
    }

    public PortfolioDiagnosticDTO runDiagnostic(UUID userId) {
        return safeTool("portfolio_diagnostic", () -> portfolioDiagnosticService.runDiagnostic(userId),
                PortfolioDiagnosticDTO.builder()
                        .summary("Diagnostic data is temporarily unavailable.")
                        .suggestions(List.of())
                        .strengths(List.of())
                        .metrics(PortfolioDiagnosticDTO.DiagnosticMetrics.builder()
                                .assetClassBreakdown(Map.of())
                                .build())
                        .build());
    }

    public RollingReturnsDTO calculateRollingReturns(UUID fundId) {
        return safeTool("rolling_returns", () -> fundAnalyticsFacade.calculateRollingReturns(fundId), null);
    }

    public RiskInsightsDTO calculateRiskInsights(UUID fundId) {
        return safeTool("risk_insights", () -> fundAnalyticsFacade.calculateRiskInsights(fundId), null);
    }

    public PortfolioCovarianceDTO calculatePortfolioCovariance(List<UserHolding> holdings) {
        List<UUID> fundIds = holdings.stream()
                .map(UserHolding::getFund)
                .filter(java.util.Objects::nonNull)
                .map(Fund::getFundId)
                .toList();
        if (fundIds.size() < 2) {
            return null;
        }

        Map<UUID, Double> weights = holdings.stream()
                .filter(holding -> holding.getFund() != null && holding.getWeightPct() != null)
                .collect(Collectors.toMap(
                        holding -> holding.getFund().getFundId(),
                        holding -> holding.getWeightPct() / 100.0,
                        (left, right) -> left,
                        LinkedHashMap::new));

        return safeTool("portfolio_covariance",
                () -> fundAnalyticsFacade.calculatePortfolioCovariance(fundIds, weights), null);
    }

    public ObjectNode buildPortfolioSnapshot(List<UserHolding> holdings) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        double totalInvestment = holdings.stream()
                .map(UserHolding::getInvestmentAmount)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        double totalCurrent = holdings.stream()
                .map(UserHolding::getCurrentValue)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        snapshot.put("fundCount", holdings.size());
        snapshot.put("totalInvestmentAmount", roundTwoDecimals(totalInvestment));
        snapshot.put("totalCurrentValue", roundTwoDecimals(totalCurrent));
        snapshot.put("totalGainLoss", roundTwoDecimals(totalCurrent - totalInvestment));
        snapshot.put("gainLossPercentage", totalInvestment > 0
                ? roundTwoDecimals(((totalCurrent - totalInvestment) / totalInvestment) * 100.0)
                : 0.0);

        ArrayNode topHoldings = objectMapper.createArrayNode();
        holdings.stream()
                .filter(holding -> holding.getFund() != null)
                .sorted(Comparator.comparing((UserHolding holding) -> holding.getWeightPct() == null ? 0 : holding.getWeightPct())
                        .reversed())
                .limit(5)
                .forEach(holding -> {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("fundId", holding.getFund().getFundId().toString());
                    node.put("fundName", holding.getFund().getFundName());
                    node.put("weightPct", holding.getWeightPct() == null ? 0 : holding.getWeightPct());
                    node.put("currentValue", holding.getCurrentValue() == null ? 0.0 : holding.getCurrentValue());
                    topHoldings.add(node);
                });
        snapshot.set("topHoldings", topHoldings);
        return snapshot;
    }

    public List<Fund> resolveRelevantFunds(String message, List<UserHolding> holdings, int limit) {
        Map<UUID, Fund> matches = new LinkedHashMap<>();
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

        for (UserHolding holding : holdings) {
            Fund fund = holding.getFund();
            if (fund == null || fund.getFundName() == null) {
                continue;
            }
            String fundName = fund.getFundName().toLowerCase(Locale.ROOT);
            if (normalized.contains(fundName) || fundName.contains(normalized)) {
                matches.put(fund.getFundId(), fund);
            }
        }

        if (matches.size() < limit) {
            for (String term : extractSearchTerms(message)) {
                if (term.length() < 4) {
                    continue;
                }
                List<Fund> found = fundQueryService.findByFundNameContainingIgnoreCase(term);
                for (Fund fund : found) {
                    matches.putIfAbsent(fund.getFundId(), fund);
                    if (matches.size() >= limit) {
                        break;
                    }
                }
                if (matches.size() >= limit) {
                    break;
                }
            }
        }

        if (matches.isEmpty()) {
            holdings.stream()
                    .map(UserHolding::getFund)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(Fund::getFundName, Comparator.nullsLast(String::compareTo)))
                    .limit(limit)
                    .forEach(fund -> matches.putIfAbsent(fund.getFundId(), fund));
        }

        return new ArrayList<>(matches.values()).subList(0, Math.min(limit, matches.size()));
    }

    public List<Fund> findAlternativeFunds(String category, Set<UUID> heldFundIds, int limit) {
        return fundQueryService.findAll().stream()
                .filter(fund -> fund.getFundId() != null && !heldFundIds.contains(fund.getFundId()))
                .filter(fund -> matchesCategory(category, fund.getFundCategory()))
                .filter(this::hasEnoughDataForSuggestion)
                .sorted(Comparator
                        .comparing((Fund fund) -> !Boolean.TRUE.equals(fund.getDirectPlan()))
                        .thenComparing(fund -> fund.getExpenseRatio() == null ? Double.MAX_VALUE : fund.getExpenseRatio())
                        .thenComparing(Fund::getFundName))
                .limit(limit)
                .toList();
    }

    public AgentContextBundle buildAgentContextBundle(
            UUID userId,
            String userMessage,
            String screenContext,
            List<UserHolding> holdings,
            PortfolioDataQualityInspector.Result qualityResult,
            Optional<RiskProfileResponse> riskProfile,
            PortfolioDiagnosticDTO diagnostic) {
        ArrayNode holdingsSummary = objectMapper.createArrayNode();
        ArrayNode fundAnalytics = objectMapper.createArrayNode();
        ArrayNode marketContext = objectMapper.createArrayNode();
        List<String> warnings = new ArrayList<>(qualityResult.warnings());

        holdings.stream()
                .filter(holding -> holding.getFund() != null)
                .sorted(Comparator.comparing((UserHolding holding) -> holding.getWeightPct() == null ? 0 : holding.getWeightPct())
                        .reversed())
                .limit(5)
                .forEach(holding -> {
                    Fund fund = holding.getFund();
                    holdingsSummary.add(objectMapper.createObjectNode()
                            .put("fundId", fund.getFundId().toString())
                            .put("fundName", fund.getFundName())
                            .put("category", fund.getFundCategory() == null ? "UNKNOWN" : fund.getFundCategory())
                            .put("weightPct", holding.getWeightPct() == null ? 0 : holding.getWeightPct())
                            .put("currentValue", holding.getCurrentValue() == null ? 0.0 : holding.getCurrentValue()));

                    RollingReturnsDTO rollingReturns = calculateRollingReturns(fund.getFundId());
                    RiskInsightsDTO riskInsights = calculateRiskInsights(fund.getFundId());
                    ObjectNode analyticsNode = objectMapper.createObjectNode()
                            .put("fundId", fund.getFundId().toString())
                            .put("fundName", fund.getFundName())
                            .put("category", fund.getFundCategory() == null ? "UNKNOWN" : fund.getFundCategory());
                    if (rollingReturns != null) {
                        analyticsNode.set("rollingReturns", objectMapper.valueToTree(rollingReturns));
                    }
                    if (riskInsights != null) {
                        analyticsNode.set("riskInsights", objectMapper.valueToTree(riskInsights));
                    }
                    fundAnalytics.add(analyticsNode);

                    ObjectNode marketNode = objectMapper.createObjectNode()
                            .put("fundId", fund.getFundId().toString())
                            .put("fundName", fund.getFundName())
                            .put("category", fund.getFundCategory() == null ? "UNKNOWN" : fund.getFundCategory())
                            .put("navAsOf", fund.getNavAsOf() == null ? "" : fund.getNavAsOf().toLocalDate().toString())
                            .put("metadataUpdatedAt", fund.getLastUpdated() == null ? "" : fund.getLastUpdated().toString())
                            .put("freshnessStatus", freshnessStatus(fund));
                    JsonNode metadata = fund.getFundMetadataJson();
                    if (metadata != null && !metadata.isNull()) {
                        JsonNode riskVolatility = metadata.path("risk_volatility");
                        marketNode.put("benchmarkName",
                                riskVolatility.path("index_name").asText(riskVolatility.path("calculation_benchmark").asText("UNKNOWN")));
                        marketNode.put("categoryName",
                                riskVolatility.path("category_name").asText(fund.getFundCategory()));
                        marketNode.put("fundStdDev3Y",
                                riskVolatility.path("fund_risk_volatility").path("for3Year").path("standardDeviation").asDouble(0.0));
                        marketNode.put("benchmarkStdDev3Y",
                                riskVolatility.path("index_risk_volatility").path("for3Year").path("standardDeviation").asDouble(0.0));
                        marketNode.put("categoryStdDev3Y",
                                riskVolatility.path("category_risk_volatility").path("for3Year").path("standardDeviation").asDouble(0.0));
                        marketNode.put("benchmarkSharpe3Y",
                                riskVolatility.path("index_risk_volatility").path("for3Year").path("sharpeRatio").asDouble(0.0));
                        marketNode.put("categorySharpe3Y",
                                riskVolatility.path("category_risk_volatility").path("for3Year").path("sharpeRatio").asDouble(0.0));
                        marketNode.put("navTrendDirection", deriveNavTrend(metadata));
                    } else {
                        marketNode.put("benchmarkName", "UNKNOWN");
                        marketNode.put("categoryName", fund.getFundCategory() == null ? "UNKNOWN" : fund.getFundCategory());
                        warnings.add(fund.getFundName() + " is missing fund metadata, so market context is limited.");
                    }
                    if ("STALE".equals(marketNode.path("freshnessStatus").asText())) {
                        warnings.add(fund.getFundName() + " has stale market context data.");
                    }
                    marketContext.add(marketNode);
                });

        PortfolioCovarianceDTO covariance = calculatePortfolioCovariance(holdings);
        ObjectNode conversationContext = objectMapper.createObjectNode()
                .put("screenContext", screenContext == null ? "LANDING" : screenContext)
                .put("userMessage", userMessage == null ? "" : userMessage);

        if (riskProfile.isEmpty()) {
            warnings.add("Risk profile is incomplete, so personalized guidance may stay category-level.");
        }

        return AgentContextBundle.builder()
                .userId(userId)
                .userMessage(userMessage)
                .screenContext(screenContext)
                .portfolioSnapshot(buildPortfolioSnapshot(holdings))
                .holdingsSummary(holdingsSummary)
                .diagnostics(objectMapper.valueToTree(diagnostic))
                .riskProfile(riskProfile
                        .map(value -> (JsonNode) objectMapper.valueToTree(value))
                        .orElseGet(objectMapper::createObjectNode))
                .dataQuality(objectMapper.valueToTree(qualityResult))
                .fundAnalytics(fundAnalytics)
                .covariance(covariance == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(covariance))
                .marketContext(marketContext)
                .conversationContext(conversationContext)
                .warnings(warnings.stream().distinct().limit(8).toList())
                .build();
    }

    public ObjectNode toToolTrace(AgentContextBundle bundle) {
        ObjectNode toolTrace = objectMapper.createObjectNode();
        toolTrace.set("portfolioSnapshot", bundle.getPortfolioSnapshot());
        toolTrace.set("holdingsSummary", bundle.getHoldingsSummary());
        toolTrace.set("diagnostic", bundle.getDiagnostics());
        toolTrace.set("riskProfile", bundle.getRiskProfile());
        toolTrace.set("dataQuality", bundle.getDataQuality());
        toolTrace.set("fundAnalytics", bundle.getFundAnalytics());
        toolTrace.set("covariance", bundle.getCovariance());
        toolTrace.set("marketContext", bundle.getMarketContext());
        return toolTrace;
    }

    private <T> T safeTool(String toolName, Supplier<T> supplier, T fallback) {
        try {
            return CompletableFuture.supplyAsync(supplier::get)
                    .orTimeout(properties.getPerToolTimeoutMs(), TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("Tool {} timed out or failed: {}", toolName, ex.getMessage());
                        return fallback;
                    })
                    .join();
        } catch (RuntimeException ex) {
            log.warn("Tool {} failed: {}", toolName, ex.getMessage());
            return fallback;
        }
    }

    private Set<String> extractSearchTerms(String message) {
        if (message == null || message.isBlank()) {
            return Set.of();
        }
        Set<String> results = new LinkedHashSet<>();
        for (String raw : SPLIT_PATTERN.split(message)) {
            String cleaned = raw.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9\\s-]", " ")
                    .replaceAll("\\b(fund|risk|return|returns|compare|performance|portfolio|show|me|the|my|please)\\b", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!cleaned.isBlank()) {
                results.add(cleaned);
            }
        }
        return results;
    }

    private String freshnessStatus(Fund fund) {
        if (fund.getFundMetadataJson() == null || fund.getFundMetadataJson().isNull()) {
            return "MISSING";
        }
        if (fund.getNavAsOf() == null || fund.getLastUpdated() == null) {
            return "STALE";
        }

        LocalDate navDate = fund.getNavAsOf().toLocalDate();
        boolean staleNav = navDate.isBefore(LocalDate.now().minusDays(properties.getMarketContextStaleDays()));
        boolean staleMetadata = fund.getLastUpdated().isBefore(LocalDateTime.now().minusDays(properties.getMarketContextStaleDays()));
        return staleNav || staleMetadata ? "STALE" : "FRESH";
    }

    private String deriveNavTrend(JsonNode metadata) {
        JsonNode history = metadata.path("nav_history");
        if (!history.isObject() || history.size() < 2) {
            return "UNKNOWN";
        }
        List<Double> values = new ArrayList<>();
        history.fields().forEachRemaining(entry -> {
            if (entry.getValue().isNumber()) {
                values.add(entry.getValue().asDouble());
            }
        });
        if (values.size() < 2) {
            return "UNKNOWN";
        }
        double latest = values.get(values.size() - 1);
        double prior = values.get(Math.max(0, values.size() - 4));
        if (latest > prior) {
            return "UP";
        }
        if (latest < prior) {
            return "DOWN";
        }
        return "FLAT";
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean matchesCategory(String targetCategory, String fundCategory) {
        if (targetCategory == null || fundCategory == null) {
            return false;
        }
        String normalizedTarget = targetCategory.toLowerCase(Locale.ROOT);
        String normalizedCategory = fundCategory.toLowerCase(Locale.ROOT);
        return switch (normalizedTarget) {
            case "equity" -> normalizedCategory.contains("equity")
                    || normalizedCategory.contains("cap")
                    || normalizedCategory.contains("index")
                    || normalizedCategory.contains("sector");
            case "debt" -> normalizedCategory.contains("debt")
                    || normalizedCategory.contains("liquid")
                    || normalizedCategory.contains("bond");
            case "gold" -> normalizedCategory.contains("gold");
            default -> normalizedCategory.contains(normalizedTarget);
        };
    }

    private boolean hasEnoughDataForSuggestion(Fund fund) {
        return isFreshFund(fund) && fund.getFundMetadataJson() != null;
    }

    private boolean isFreshFund(Fund fund) {
        if (fund.getCurrentNav() == null || fund.getLastUpdated() == null) {
            return false;
        }
        return fund.getLastUpdated().isAfter(LocalDateTime.now().minusDays(7));
    }
}
