package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioChatPayloadFactory {

    private final ObjectMapper objectMapper;
    private final PortfolioToolFacade portfolioToolFacade;

    public ObjectNode buildDataQualityPayload(PortfolioDataQualityInspector.Result qualityResult) {
        ObjectNode qualityNode = objectMapper.createObjectNode();
        qualityNode.put("staleCount", qualityResult.staleCount());
        qualityNode.put("missingCount", qualityResult.missingCount());
        qualityNode.put("freshCount", qualityResult.freshCount());
        qualityNode.set("warnings", objectMapper.valueToTree(qualityResult.warnings()));
        qualityNode.set("staleFunds", objectMapper.valueToTree(qualityResult.staleFunds()));
        qualityNode.set("missingFunds", objectMapper.valueToTree(qualityResult.missingFunds()));
        return qualityNode;
    }

    public ArrayNode buildFundPayload(List<Fund> funds, boolean includePerformance, boolean includeRisk) {
        ArrayNode fundNodes = objectMapper.createArrayNode();
        for (Fund fund : funds) {
            ObjectNode fundNode = objectMapper.createObjectNode();
            fundNode.put("fundId", fund.getFundId().toString());
            fundNode.put("fundName", fund.getFundName());
            fundNode.put("category", fund.getFundCategory());
            if (includePerformance) {
                JsonNode performance = objectMapper.valueToTree(
                        portfolioToolFacade.calculateRollingReturns(fund.getFundId()));
                fundNode.set("performance", performance);
            }
            if (includeRisk) {
                JsonNode risk = objectMapper.valueToTree(
                        portfolioToolFacade.calculateRiskInsights(fund.getFundId()));
                fundNode.set("risk", risk);
            }
            fundNodes.add(fundNode);
        }
        return fundNodes;
    }

    public ObjectNode buildScenarioFallbackPayload(List<UserHolding> holdings,
            PortfolioDataQualityInspector.Result qualityResult) {
        ObjectNode scenario = objectMapper.createObjectNode();
        scenario.put("status", "fallback");
        scenario.put("note", "Scenario analysis used the Spring AI fallback path.");
        scenario.set("portfolioSnapshot", portfolioToolFacade.buildPortfolioSnapshot(holdings));
        scenario.set("dataQuality", objectMapper.valueToTree(qualityResult));
        return scenario;
    }

    public ObjectNode buildRebalanceDraft(List<UserHolding> holdings,
            PortfolioDiagnosticDTO diagnostic,
            RiskProfileResponse riskProfile,
            PortfolioDataQualityInspector.Result qualityResult,
            List<String> warnings) {
        ObjectNode draft = objectMapper.createObjectNode();
        Map<String, Double> currentAllocation = new LinkedHashMap<>();
        if (diagnostic.getMetrics() != null && diagnostic.getMetrics().getAssetClassBreakdown() != null) {
            currentAllocation.putAll(diagnostic.getMetrics().getAssetClassBreakdown());
        }

        Map<String, Double> targetAllocation = new LinkedHashMap<>();
        if (riskProfile != null && riskProfile.getAssetAllocation() != null) {
            targetAllocation.put("Equity", riskProfile.getAssetAllocation().getEquity());
            targetAllocation.put("Debt", riskProfile.getAssetAllocation().getDebt());
            targetAllocation.put("Gold", riskProfile.getAssetAllocation().getGold());
        } else {
            targetAllocation.putAll(currentAllocation);
            if (!currentAllocation.containsKey("Debt") || currentAllocation.getOrDefault("Debt", 0.0) < 15.0) {
                targetAllocation.put("Debt", 20.0);
                targetAllocation.put("Equity", Math.max(0.0, 100.0 - targetAllocation.get("Debt")));
            }
        }

        ArrayNode proposedReductions = objectMapper.createArrayNode();
        ArrayNode proposedAdditions = objectMapper.createArrayNode();

        for (Map.Entry<String, Double> entry : targetAllocation.entrySet()) {
            String key = entry.getKey();
            double current = currentAllocation.getOrDefault(key, 0.0);
            double target = entry.getValue();
            double delta = roundTwoDecimals(target - current);
            if (delta > 5.0) {
                ObjectNode addition = objectMapper.createObjectNode();
                addition.put("category", key);
                addition.put("current", current);
                addition.put("target", target);
                addition.put("change", delta);
                addition.set("suggestedFunds", buildSuggestedFundsForCategory(key, holdings));
                proposedAdditions.add(addition);
            } else if (delta < -5.0) {
                ObjectNode reduction = objectMapper.createObjectNode();
                reduction.put("category", key);
                reduction.put("current", current);
                reduction.put("target", target);
                reduction.put("change", Math.abs(delta));
                proposedReductions.add(reduction);
            }
        }

        if (proposedAdditions.isEmpty() && proposedReductions.isEmpty()) {
            warnings.add("Current allocation is already close to the recommended mix, so the draft focuses on fund quality.");
        }

        draft.set("currentAllocationSummary", objectMapper.valueToTree(currentAllocation));
        draft.set("targetAllocationSummary", objectMapper.valueToTree(targetAllocation));
        draft.set("proposedReductions", proposedReductions);
        draft.set("proposedAdditions", proposedAdditions);
        draft.put("rationale", buildRebalanceRationale(diagnostic, qualityResult));
        draft.put("expectedRiskShift", expectedRiskShift(currentAllocation, targetAllocation));
        draft.put("expectedDiversificationImprovement", expectedDiversificationImprovement(diagnostic));
        draft.put("requiresConfirmation", true);
        return draft;
    }

    public String buildDraftSummary(ObjectNode draft) {
        JsonNode current = draft.path("currentAllocationSummary");
        JsonNode target = draft.path("targetAllocationSummary");
        return String.format("Current mix: Equity %.0f%% / Debt %.0f%%. Target mix: Equity %.0f%% / Debt %.0f%%.",
                current.path("Equity").asDouble(),
                current.path("Debt").asDouble(),
                target.path("Equity").asDouble(),
                target.path("Debt").asDouble());
    }

    private ArrayNode buildSuggestedFundsForCategory(String category, List<UserHolding> holdings) {
        Set<UUID> heldFundIds = holdings.stream()
                .map(UserHolding::getFund)
                .filter(java.util.Objects::nonNull)
                .map(Fund::getFundId)
                .collect(Collectors.toSet());

        List<Fund> alternatives = findAlternativeFunds(category, heldFundIds, 3);
        ArrayNode results = objectMapper.createArrayNode();
        if (alternatives.isEmpty()) {
            ObjectNode categoryOnly = objectMapper.createObjectNode();
            categoryOnly.put("category", category);
            categoryOnly.put("note", "No strong fund-level match found, so keep this as category guidance.");
            results.add(categoryOnly);
            return results;
        }

        for (Fund fund : alternatives) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("fundId", fund.getFundId().toString());
            node.put("fundName", fund.getFundName());
            node.put("category", fund.getFundCategory());
            if (fund.getExpenseRatio() != null) {
                node.put("expenseRatio", fund.getExpenseRatio());
            }
            if (fund.getCurrentNav() != null) {
                node.put("currentNav", fund.getCurrentNav());
            }
            node.put("fresh", isFreshFund(fund));
            results.add(node);
        }
        return results;
    }

    private List<Fund> findAlternativeFunds(String category, Set<UUID> heldFundIds, int limit) {
        return portfolioToolFacade.findAlternativeFunds(category, heldFundIds, limit);
    }

    private boolean isFreshFund(Fund fund) {
        if (fund.getCurrentNav() == null || fund.getLastUpdated() == null) {
            return false;
        }
        return fund.getLastUpdated().isAfter(LocalDateTime.now().minusDays(7));
    }

    private String buildRebalanceRationale(PortfolioDiagnosticDTO diagnostic,
            PortfolioDataQualityInspector.Result qualityResult) {
        List<String> points = new ArrayList<>();
        if (diagnostic.getSuggestions() != null) {
            diagnostic.getSuggestions().stream()
                    .limit(3)
                    .forEach(suggestion -> points.add(suggestion.getMessage()));
        }
        if (qualityResult.staleCount() > 0 || qualityResult.missingCount() > 0) {
            points.add("Some fund data is stale or incomplete, so the draft prioritizes fresher alternatives.");
        }
        return String.join(" ", points);
    }

    private String expectedRiskShift(Map<String, Double> currentAllocation, Map<String, Double> targetAllocation) {
        double currentEquity = currentAllocation.getOrDefault("Equity", 0.0);
        double targetEquity = targetAllocation.getOrDefault("Equity", currentEquity);
        if (targetEquity < currentEquity) {
            return "LOWER";
        }
        if (targetEquity > currentEquity) {
            return "HIGHER";
        }
        return "STABLE";
    }

    private String expectedDiversificationImprovement(PortfolioDiagnosticDTO diagnostic) {
        if (diagnostic.getSuggestions() == null) {
            return "LOW";
        }
        long concentrationIssues = diagnostic.getSuggestions().stream()
                .filter(suggestion -> suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.SECTOR_CONCENTRATION
                        || suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.STOCK_OVERLAP
                        || suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.MARKET_CAP_IMBALANCE)
                .count();
        if (concentrationIssues >= 2) {
            return "HIGH";
        }
        if (concentrationIssues == 1) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}