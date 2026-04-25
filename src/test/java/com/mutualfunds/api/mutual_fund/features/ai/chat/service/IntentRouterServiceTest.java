package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRouterServiceTest {

    private final IntentRouterService intentRouterService = new IntentRouterService();

    @Test
    void resolvesRebalanceDraftBeforePortfolioSummary() {
        ChatIntent intent = intentRouterService.resolveIntent(
                "Analyze my portfolio and rebalance it to reduce risk",
                "LANDING");

        assertThat(intent).isEqualTo(ChatIntent.REBALANCE_DRAFT);
    }

    @Test
    void usesScreenContextForRiskProfileExplainer() {
        ChatIntent intent = intentRouterService.resolveIntent(
                "why this allocation",
                "RISK_PROFILE_RESULT");

        assertThat(intent).isEqualTo(ChatIntent.RISK_PROFILE_EXPLAINER);
    }

    @Test
    void detectsDataQualityQuestions() {
        ChatIntent intent = intentRouterService.resolveIntent(
                "What data is stale or missing in my portfolio?",
                "LANDING");

        assertThat(intent).isEqualTo(ChatIntent.DATA_QUALITY);
    }

    @Test
    void detectsScenarioQuestions() {
        ChatIntent intent = intentRouterService.resolveIntent(
                "What if I move 10% to debt?",
                "LANDING");

        assertThat(intent).isEqualTo(ChatIntent.SCENARIO_ANALYSIS);
    }
}
