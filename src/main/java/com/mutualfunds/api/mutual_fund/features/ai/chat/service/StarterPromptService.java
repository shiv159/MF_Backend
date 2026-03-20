package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.StarterPromptsResponse;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StarterPromptService {

    private final PortfolioReadService portfolioReadService;
    private final PortfolioDataQualityInspector dataQualityInspector;

    public StarterPromptsResponse getStarterPrompts(UUID userId, String screenContext) {
        List<UserHolding> holdings = portfolioReadService.findHoldingsWithFund(userId);
        PortfolioDataQualityInspector.Result qualityResult = dataQualityInspector.inspect(holdings);

        List<String> prompts = new ArrayList<>();
        String context = screenContext == null ? "" : screenContext.toUpperCase(Locale.ROOT);

        if (holdings.isEmpty()) {
            prompts.add("What data do I need before I can analyze my portfolio?");
            prompts.add("How should I build a beginner mutual fund allocation?");
            prompts.add("What should I look for before choosing my first fund?");
            prompts.add("How do risk profile and time horizon change portfolio design?");
            return StarterPromptsResponse.builder().prompts(prompts).build();
        }

        switch (context) {
            case "RISK_PROFILE_RESULT" -> {
                prompts.add("Explain my risk level in simple terms");
                prompts.add("Why does this allocation fit my horizon?");
                prompts.add("What should I start with first?");
                prompts.add("How can I reduce risk without losing too much growth?");
            }
            case "MANUAL_SELECTION_RESULT" -> {
                prompts.add("What is the biggest problem in this portfolio?");
                prompts.add("Where do I have overlap or concentration risk?");
                prompts.add("How can I improve this mix without adding too many funds?");
                prompts.add("Draft a lower-risk rebalance plan for this portfolio");
            }
            default -> {
                prompts.add("Analyze my portfolio and tell me the top issue");
                prompts.add("Compare two funds in my portfolio");
                prompts.add("Which fund in my portfolio looks riskiest?");
                prompts.add("Show me a draft rebalance to improve diversification");
            }
        }

        if (qualityResult.staleCount() > 0 || qualityResult.missingCount() > 0) {
            prompts.set(prompts.size() - 1, "What data in my portfolio is stale or incomplete?");
        }

        return StarterPromptsResponse.builder().prompts(prompts).build();
    }
}
