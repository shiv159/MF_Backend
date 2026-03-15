package com.mutualfunds.api.mutual_fund.ai.chat.service;

import com.mutualfunds.api.mutual_fund.ai.chat.model.ChatIntent;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class IntentRouterService {

    public ChatIntent resolveIntent(String message, String screenContext) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT).trim();
        String context = screenContext == null ? "" : screenContext.toUpperCase(Locale.ROOT);

        if (containsAny(normalized, "rebalance", "reduce risk", "switch fund", "switch funds", "reallocate",
                "improve allocation")) {
            return ChatIntent.REBALANCE_DRAFT;
        }

        if (containsAny(normalized, "stale", "freshness", "outdated", "missing data", "enrichment", "missing nav",
                "data quality")) {
            return ChatIntent.DATA_QUALITY;
        }

        if (containsAny(normalized, " compare ", " vs ", " versus ", "better than")) {
            return ChatIntent.FUND_COMPARE;
        }

        if (mentionsFundRisk(normalized)) {
            return ChatIntent.FUND_RISK;
        }

        if (mentionsFundPerformance(normalized)) {
            return ChatIntent.FUND_PERFORMANCE;
        }

        if ("RISK_PROFILE_RESULT".equals(context)
                || containsAny(normalized, "why was i rated", "why this allocation", "what should i start with",
                        "explain my risk profile")) {
            return ChatIntent.RISK_PROFILE_EXPLAINER;
        }

        if ("MANUAL_SELECTION_RESULT".equals(context)
                || containsAny(normalized, "what is wrong", "what's wrong", "diagnostic", "overlap", "improve my portfolio")) {
            return ChatIntent.DIAGNOSTIC_EXPLAINER;
        }

        if (containsAny(normalized, "analyze my portfolio", "portfolio summary", "my holdings", "portfolio",
                "analyze", "summary")) {
            return ChatIntent.PORTFOLIO_SUMMARY;
        }

        return ChatIntent.GENERAL_QA;
    }

    private boolean mentionsFundRisk(String message) {
        return containsAny(message, " fund risk", "beta", "alpha", "volatility", "risk label", "standard deviation")
                || (message.contains("risk") && !message.contains("portfolio"));
    }

    private boolean mentionsFundPerformance(String message) {
        return containsAny(message, "performance", "return", "returns", "cagr", "rolling return", "top performer",
                "performing");
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
