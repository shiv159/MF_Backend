package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RebalanceExecutionService {

    private final PortfolioReadService portfolioReadService;
    private final ObjectMapper objectMapper;

    public ObjectNode generateExecutionPlan(UUID userId, JsonNode rebalanceDraft) {
        ObjectNode plan = objectMapper.createObjectNode();
        List<UserHolding> holdings = portfolioReadService.findHoldingsWithFund(userId);

        double totalValue = holdings.stream()
                .map(UserHolding::getCurrentValue).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();

        ArrayNode steps = objectMapper.createArrayNode();
        int stepNumber = 1;

        ArrayNode reductions = (ArrayNode) rebalanceDraft.path("proposedReductions");
        double totalRedemption = 0;
        for (JsonNode reduction : reductions) {
            String category = reduction.path("category").asText();
            double changePct = reduction.path("change").asDouble();
            double redeemAmount = totalValue * changePct / 100;
            totalRedemption += redeemAmount;

            for (UserHolding h : holdings) {
                if (h.getFund() == null || h.getCurrentValue() == null) continue;
                String fundCat = h.getFund().getFundCategory() != null ? h.getFund().getFundCategory().toLowerCase() : "";
                if (matchesCategory(category, fundCat) && redeemAmount > 0) {
                    double sellAmount = Math.min(redeemAmount, h.getCurrentValue());
                    double units = h.getCurrentNav() != null && h.getCurrentNav() > 0
                            ? sellAmount / h.getCurrentNav() : 0;

                    ObjectNode step = objectMapper.createObjectNode();
                    step.put("step", stepNumber++);
                    step.put("action", "SELL");
                    step.put("fundName", h.getFund().getFundName());
                    step.put("amount", Math.round(sellAmount));
                    step.put("units", Math.round(units * 100.0) / 100.0);
                    step.put("nav", h.getCurrentNav() != null ? h.getCurrentNav() : 0);
                    step.put("instruction", String.format("Redeem ₹%.0f from %s (approx %.2f units at NAV ₹%.2f)",
                            sellAmount, h.getFund().getFundName(), units, h.getCurrentNav() != null ? h.getCurrentNav() : 0));

                    ObjectNode taxImpact = estimateTaxImpact(h, sellAmount);
                    step.set("taxImpact", taxImpact);

                    steps.add(step);
                    redeemAmount -= sellAmount;
                }
            }
        }

        ArrayNode additions = (ArrayNode) rebalanceDraft.path("proposedAdditions");
        for (JsonNode addition : additions) {
            double changePct = addition.path("change").asDouble();
            double investAmount = totalValue * changePct / 100;
            JsonNode suggestedFunds = addition.path("suggestedFunds");

            for (JsonNode fund : suggestedFunds) {
                if (!fund.has("fundName")) continue;
                double perFundAmount = investAmount / Math.max(suggestedFunds.size(), 1);

                ObjectNode step = objectMapper.createObjectNode();
                step.put("step", stepNumber++);
                step.put("action", "BUY");
                step.put("fundName", fund.path("fundName").asText());
                step.put("amount", Math.round(perFundAmount));
                step.put("instruction", String.format("Invest ₹%.0f in %s via lumpsum or start SIP of ₹%.0f/month",
                        perFundAmount, fund.path("fundName").asText(), Math.ceil(perFundAmount / 6)));
                steps.add(step);
            }
        }

        plan.set("steps", steps);
        plan.put("totalRedemption", Math.round(totalRedemption));
        plan.put("totalInvestment", Math.round(totalRedemption));

        ObjectNode taxSummary = objectMapper.createObjectNode();
        double totalTax = 0;
        for (JsonNode step : steps) {
            if ("SELL".equals(step.path("action").asText()) && step.has("taxImpact")) {
                totalTax += step.path("taxImpact").path("estimatedTax").asDouble(0);
            }
        }
        taxSummary.put("estimatedTotalTax", Math.round(totalTax));
        taxSummary.put("note", "Tax estimates are approximate. Consult a CA for exact calculations.");
        plan.set("taxSummary", taxSummary);

        plan.put("disclaimer", "This is a guidance plan only. Execute through your broker/AMC. PlanMyFunds does not execute trades.");

        return plan;
    }

    private ObjectNode estimateTaxImpact(UserHolding holding, double sellAmount) {
        ObjectNode tax = objectMapper.createObjectNode();
        if (holding.getInvestmentAmount() == null || holding.getCurrentValue() == null) {
            tax.put("estimatedTax", 0);
            tax.put("note", "Cannot estimate tax without investment cost data");
            return tax;
        }

        double costRatio = holding.getInvestmentAmount() / holding.getCurrentValue();
        double costBasis = sellAmount * costRatio;
        double gain = sellAmount - costBasis;

        boolean isEquity = holding.getFund() != null && holding.getFund().getFundCategory() != null
                && !holding.getFund().getFundCategory().toLowerCase().contains("debt")
                && !holding.getFund().getFundCategory().toLowerCase().contains("liquid");

        boolean isLongTerm = holding.getPurchaseDate() != null
                && holding.getPurchaseDate().toLocalDate().plusYears(1).isBefore(java.time.LocalDate.now());

        if (gain <= 0) {
            tax.put("estimatedTax", 0);
            tax.put("type", "NO_TAX");
            tax.put("note", "No capital gains");
        } else if (isEquity) {
            if (isLongTerm) {
                double taxableGain = Math.max(0, gain);
                tax.put("estimatedTax", Math.round(taxableGain * 0.125));
                tax.put("type", "LTCG_EQUITY");
                tax.put("rate", "12.5%");
            } else {
                tax.put("estimatedTax", Math.round(gain * 0.20));
                tax.put("type", "STCG_EQUITY");
                tax.put("rate", "20%");
            }
        } else {
            tax.put("estimatedTax", Math.round(gain * 0.30));
            tax.put("type", isLongTerm ? "LTCG_DEBT" : "STCG_DEBT");
            tax.put("rate", "As per income tax slab");
        }

        tax.put("gain", Math.round(gain));
        return tax;
    }

    private boolean matchesCategory(String target, String fundCategory) {
        String t = target.toLowerCase();
        return switch (t) {
            case "equity" -> fundCategory.contains("equity") || fundCategory.contains("cap")
                    || fundCategory.contains("index") || fundCategory.contains("sector");
            case "debt" -> fundCategory.contains("debt") || fundCategory.contains("liquid")
                    || fundCategory.contains("bond") || fundCategory.contains("gilt");
            case "gold" -> fundCategory.contains("gold");
            default -> fundCategory.contains(t);
        };
    }
}
