package com.mutualfunds.api.mutual_fund.ai.service;

import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import com.mutualfunds.api.mutual_fund.repository.UserHoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
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

        sb.append(String.format("**Total Invested**: ₹%.2f\n", totalInvested));
        sb.append(String.format("**Current Value**: ₹%.2f\n", totalCurrentValue));
        sb.append(String.format("**P&L**: ₹%.2f (%.2f%%)\n\n", totalGainLoss, totalGainLossPct));

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
            if (units != null && nav != null) {
                sb.append(String.format("  Units: %.3f | NAV: ₹%.2f\n", units, nav));
            }
            if (invested != null && current != null) {
                double gl = current - invested;
                double glPct = invested > 0 ? (gl / invested) * 100 : 0;
                sb.append(String.format("  Invested: ₹%.2f | Current: ₹%.2f | P&L: ₹%.2f (%.1f%%)\n", invested, current,
                        gl, glPct));
            }
            if (weight != null) {
                sb.append(String.format("  Weight: %d%%\n", weight));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
