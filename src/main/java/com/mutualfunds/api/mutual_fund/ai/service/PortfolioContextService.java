package com.mutualfunds.api.mutual_fund.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import com.mutualfunds.api.mutual_fund.repository.UserHoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service to build portfolio context for AI chat.
 * Fetches user holdings and formats them as text for the LLM.
 */
@Service
@RequiredArgsConstructor
public class PortfolioContextService {

    private final UserHoldingRepository userHoldingRepository;

    /**
     * Builds a textual summary of the user's portfolio holdings.
     * This context is injected into the AI prompt.
     */
    public String buildPortfolioContext(UUID userId) {
        List<UserHolding> holdings = userHoldingRepository.findByUserIdWithFund(userId);

        if (holdings.isEmpty()) {
            return "The user has no portfolio holdings on record.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## User Portfolio Summary\n\n");

        double totalInvested = 0.0;
        double totalCurrentValue = 0.0;

        for (UserHolding h : holdings) {
            double invested = h.getInvestmentAmount() != null ? h.getInvestmentAmount() : 0;
            double current = h.getCurrentValue() != null ? h.getCurrentValue() : 0;
            totalInvested += invested;
            totalCurrentValue += current;
        }

        double totalGainLoss = totalCurrentValue - totalInvested;
        double totalGainLossPct = totalInvested > 0 ? (totalGainLoss / totalInvested) * 100 : 0;

        if (totalInvested > 0) {
            sb.append(String.format("**Total Invested**: ₹%.2f\n", totalInvested));
            sb.append(String.format("**Current Value**: ₹%.2f\n", totalCurrentValue));
            sb.append(String.format("**P&L**: ₹%.2f (%.2f%%)\n\n", totalGainLoss, totalGainLossPct));
        }

        sb.append(String.format("### Holdings (%d funds)\n\n", holdings.size()));

        for (UserHolding h : holdings) {
            var fund = h.getFund();
            String fundName = fund != null ? fund.getFundName() : "Unknown";
            String category = fund != null ? fund.getFundCategory() : "N/A";
            Double nav = h.getCurrentNav();
            Double units = h.getUnitsHeld();
            Double current = h.getCurrentValue();
            Double invested = h.getInvestmentAmount();
            Integer weight = h.getWeightPct();

            sb.append(String.format("- **%s** (%s)\n", fundName, category));
            if (units != null && nav != null && units > 0) {
                sb.append(String.format("  Units: %.3f | NAV: ₹%.2f\n", units, nav));
            }
            if (invested != null && current != null && invested > 0) {
                double gl = current - invested;
                double glPct = invested > 0 ? (gl / invested) * 100 : 0;
                sb.append(String.format("  Invested: ₹%.2f | Current: ₹%.2f | P&L: ₹%.2f (%.1f%%)\n", invested, current,
                        gl, glPct));
            }
            if (weight != null) {
                sb.append(String.format("  Weight: %d%%\n", weight));
            }

            // Add Sector Allocation if available
            if (fund != null && fund.getSectorAllocationJson() != null) {
                String sectors = parseJsonToText(fund.getSectorAllocationJson(), 5);
                if (!sectors.isEmpty()) {
                    sb.append("  **Top Sectors**: ").append(sectors).append("\n");
                }
            }

            // Add Top Holdings if available
            if (fund != null && fund.getTopHoldingsJson() != null) {
                String topHoldings = parseJsonToText(fund.getTopHoldingsJson(), 5);
                if (!topHoldings.isEmpty()) {
                    sb.append("  **Top Holdings**: ").append(topHoldings).append("\n");
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Helper to parse JsonNode (Array or Object) into a comma-separated string.
     * Limit to 'limit' items.
     */
    private String parseJsonToText(JsonNode json, int limit) {
        if (json == null || json.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;

        if (json.isArray()) {
            for (JsonNode node : json) {
                if (count >= limit)
                    break;
                // Try reasonable field names for Sector/Holding objects
                String name = getText(node, "sector", "name", "company", "instrument");
                String value = getText(node, "allocation", "percentage", "weight", "value");

                if (name != null) {
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append(name);
                    if (value != null)
                        sb.append(" (").append(value).append("%)");
                }
                count++;
            }
        } else if (json.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
            while (fields.hasNext() && count < limit) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(field.getKey()).append(" (").append(field.getValue().asText()).append("%)");
                count++;
            }
        }
        return sb.toString();
    }

    private String getText(JsonNode node, String... possibleKeys) {
        for (String key : possibleKeys) {
            if (node.has(key))
                return node.get(key).asText();
        }
        return null;
    }
}
