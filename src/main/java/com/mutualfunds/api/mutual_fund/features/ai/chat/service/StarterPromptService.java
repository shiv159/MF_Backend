package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.StarterPromptGroup;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.StarterPromptsResponse;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StarterPromptService {

    private final PortfolioToolFacade portfolioToolFacade;

    public StarterPromptsResponse getStarterPrompts(String screenContext) {
        List<UserHolding> holdings = portfolioToolFacade.findCurrentHoldings();
        PortfolioDataQualityInspector.Result qualityResult = portfolioToolFacade.inspectDataQuality(holdings);

        List<StarterPromptGroup> groups = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        String context = screenContext == null ? "" : screenContext.toUpperCase(Locale.ROOT);

        if (holdings.isEmpty()) {
            groups.add(group("explain", "Explain",
                    "What data do I need before I can analyze my portfolio?",
                    "How should I build a beginner mutual fund allocation?"));
            groups.add(group("simulate", "Simulate",
                    "How do risk profile and time horizon change portfolio design?",
                    "What should I look for before choosing my first fund?"));
            groups.forEach(group -> prompts.addAll(group.getPrompts()));
            return StarterPromptsResponse.builder().prompts(prompts).groups(groups).build();
        }

        switch (context) {
            case "RISK_PROFILE_RESULT" -> {
                groups.add(group("explain", "Explain",
                        "Explain my risk level in simple terms",
                        "Why does this allocation fit my horizon?"));
                groups.add(group("simulate", "Simulate",
                        "How can I reduce risk without losing too much growth?",
                        "What if I move 10% to debt?"));
            }
            case "MANUAL_SELECTION_RESULT" -> {
                groups.add(group("diagnose", "Diagnose",
                        "What is the biggest problem in this portfolio?",
                        "Where do I have overlap or concentration risk?"));
                groups.add(group("simulate", "Simulate",
                        "Draft a lower-risk rebalance plan for this portfolio",
                        "My portfolio looks too aggressive"));
            }
            default -> {
                groups.add(group("explain", "Explain",
                        "Analyze my portfolio and tell me the top issue",
                        "Why did you suggest this?"));
                groups.add(group("compare", "Compare",
                        "Compare these two funds for my profile",
                        "Which fund in my portfolio looks riskiest?"));
                groups.add(group("simulate", "Simulate",
                        "What if I move 10% to debt?",
                        "Show me a draft rebalance to improve diversification"));
                groups.add(group("data-quality", "Data Quality",
                        "What data in my portfolio is stale or incomplete?",
                        "Which of my funds are missing enrichment data?"));
            }
        }

        if (qualityResult.staleCount() > 0 || qualityResult.missingCount() > 0) {
            groups.add(group("data-quality", "Data Quality",
                    "What data in my portfolio is stale or incomplete?",
                    "How much does stale data limit these recommendations?"));
        }

        groups.forEach(group -> prompts.addAll(group.getPrompts()));
        return StarterPromptsResponse.builder().prompts(prompts).groups(groups).build();
    }

    private StarterPromptGroup group(String key, String title, String... prompts) {
        return StarterPromptGroup.builder()
                .key(key)
                .title(title)
                .prompts(List.of(prompts))
                .build();
    }
}
