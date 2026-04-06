package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundAnalyticsFacade;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatIfSimulatorService {

    private final PortfolioReadService portfolioReadService;
    private final FundQueryService fundQueryService;
    private final FundAnalyticsFacade fundAnalyticsFacade;
    private final ObjectMapper objectMapper;

    public record WhatIfResult(ObjectNode before, ObjectNode after, ObjectNode diff) {}

    public WhatIfResult simulate(UUID userId, Map<String, Object> entities) {
        List<UserHolding> currentHoldings = portfolioReadService.findHoldingsWithFund(userId);
        ObjectNode before = buildPortfolioMetrics(currentHoldings, "current");

        @SuppressWarnings("unchecked")
        List<String> fundNames = (List<String>) entities.getOrDefault("funds", List.of());
        Double amount = entities.containsKey("amount") ? ((Number) entities.get("amount")).doubleValue() : null;
        String action = (String) entities.getOrDefault("action", "add");

        List<UserHolding> simulatedHoldings = new ArrayList<>(currentHoldings);

        if (!fundNames.isEmpty() && amount != null) {
            for (String fundName : fundNames) {
                applyChange(simulatedHoldings, fundName, amount, action);
            }
        } else if (!fundNames.isEmpty()) {
            for (String fundName : fundNames) {
                simulatedHoldings.removeIf(h -> h.getFund() != null &&
                        h.getFund().getFundName().toLowerCase().contains(fundName.toLowerCase()));
            }
        }

        recalculateWeights(simulatedHoldings);
        ObjectNode after = buildPortfolioMetrics(simulatedHoldings, "simulated");
        ObjectNode diff = buildDiff(before, after);

        return new WhatIfResult(before, after, diff);
    }

    private void applyChange(List<UserHolding> holdings, String fundName, double amount, String action) {
        Optional<UserHolding> existing = holdings.stream()
                .filter(h -> h.getFund() != null &&
                        h.getFund().getFundName().toLowerCase().contains(fundName.toLowerCase()))
                .findFirst();

        switch (action.toLowerCase()) {
            case "add" -> {
                if (existing.isPresent()) {
                    UserHolding h = existing.get();
                    h.setInvestmentAmount((h.getInvestmentAmount() != null ? h.getInvestmentAmount() : 0) + amount);
                    h.setCurrentValue((h.getCurrentValue() != null ? h.getCurrentValue() : 0) + amount);
                } else {
                    List<Fund> found = fundQueryService.findByFundNameContainingIgnoreCase(fundName);
                    if (!found.isEmpty()) {
                        Fund fund = found.getFirst();
                        UserHolding newHolding = UserHolding.builder()
                                .fund(fund)
                                .investmentAmount(amount)
                                .currentValue(amount)
                                .currentNav(fund.getCurrentNav())
                                .unitsHeld(fund.getCurrentNav() != null && fund.getCurrentNav() > 0
                                        ? amount / fund.getCurrentNav() : 0)
                                .build();
                        holdings.add(newHolding);
                    }
                }
            }
            case "remove", "sell" -> {
                if (existing.isPresent()) {
                    UserHolding h = existing.get();
                    double currentVal = h.getCurrentValue() != null ? h.getCurrentValue() : 0;
                    if (amount >= currentVal) {
                        holdings.remove(h);
                    } else {
                        double ratio = 1 - (amount / currentVal);
                        h.setCurrentValue(currentVal - amount);
                        h.setInvestmentAmount(h.getInvestmentAmount() != null ? h.getInvestmentAmount() * ratio : 0);
                        h.setUnitsHeld(h.getUnitsHeld() != null ? h.getUnitsHeld() * ratio : 0);
                    }
                }
            }
        }
    }

    private void recalculateWeights(List<UserHolding> holdings) {
        double total = holdings.stream()
                .map(UserHolding::getCurrentValue)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        for (UserHolding h : holdings) {
            if (total > 0 && h.getCurrentValue() != null) {
                h.setWeightPct((int) Math.round((h.getCurrentValue() / total) * 100));
            }
        }
    }

    private ObjectNode buildPortfolioMetrics(List<UserHolding> holdings, String label) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("label", label);
        node.put("fundCount", holdings.size());

        double totalInvestment = holdings.stream()
                .map(UserHolding::getInvestmentAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        double totalCurrent = holdings.stream()
                .map(UserHolding::getCurrentValue).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();

        node.put("totalInvestment", Math.round(totalInvestment * 100.0) / 100.0);
        node.put("totalCurrentValue", Math.round(totalCurrent * 100.0) / 100.0);
        node.put("totalGainLoss", Math.round((totalCurrent - totalInvestment) * 100.0) / 100.0);
        node.put("gainLossPct", totalInvestment > 0
                ? Math.round(((totalCurrent - totalInvestment) / totalInvestment) * 10000.0) / 100.0 : 0);

        Map<String, Double> categoryWeights = new LinkedHashMap<>();
        for (UserHolding h : holdings) {
            if (h.getFund() != null && h.getFund().getFundCategory() != null && h.getCurrentValue() != null && totalCurrent > 0) {
                String cat = classifyAssetClass(h.getFund().getFundCategory());
                categoryWeights.merge(cat, (h.getCurrentValue() / totalCurrent) * 100, Double::sum);
            }
        }
        node.set("assetBreakdown", objectMapper.valueToTree(categoryWeights));

        ArrayNode topHoldings = objectMapper.createArrayNode();
        holdings.stream()
                .filter(h -> h.getFund() != null)
                .sorted(Comparator.comparing((UserHolding h) -> h.getCurrentValue() != null ? h.getCurrentValue() : 0.0).reversed())
                .limit(5)
                .forEach(h -> {
                    ObjectNode fund = objectMapper.createObjectNode();
                    fund.put("name", h.getFund().getFundName());
                    fund.put("value", h.getCurrentValue() != null ? h.getCurrentValue() : 0);
                    fund.put("weightPct", h.getWeightPct() != null ? h.getWeightPct() : 0);
                    topHoldings.add(fund);
                });
        node.set("topHoldings", topHoldings);

        return node;
    }

    private String classifyAssetClass(String category) {
        String lower = category.toLowerCase();
        if (lower.contains("debt") || lower.contains("liquid") || lower.contains("bond") || lower.contains("gilt")) return "Debt";
        if (lower.contains("gold")) return "Gold";
        if (lower.contains("hybrid")) return "Hybrid";
        return "Equity";
    }

    private ObjectNode buildDiff(ObjectNode before, ObjectNode after) {
        ObjectNode diff = objectMapper.createObjectNode();
        diff.put("fundCountChange", after.path("fundCount").asInt() - before.path("fundCount").asInt());
        diff.put("valueChange", Math.round((after.path("totalCurrentValue").asDouble() - before.path("totalCurrentValue").asDouble()) * 100.0) / 100.0);
        diff.put("gainLossChange", Math.round((after.path("gainLossPct").asDouble() - before.path("gainLossPct").asDouble()) * 100.0) / 100.0);

        ObjectNode assetShifts = objectMapper.createObjectNode();
        var beforeBreakdown = before.path("assetBreakdown");
        var afterBreakdown = after.path("assetBreakdown");
        Set<String> allKeys = new LinkedHashSet<>();
        beforeBreakdown.fieldNames().forEachRemaining(allKeys::add);
        afterBreakdown.fieldNames().forEachRemaining(allKeys::add);
        for (String key : allKeys) {
            double shift = afterBreakdown.path(key).asDouble(0) - beforeBreakdown.path(key).asDouble(0);
            if (Math.abs(shift) > 0.5) {
                assetShifts.put(key, Math.round(shift * 100.0) / 100.0);
            }
        }
        diff.set("assetClassShifts", assetShifts);

        return diff;
    }
}
