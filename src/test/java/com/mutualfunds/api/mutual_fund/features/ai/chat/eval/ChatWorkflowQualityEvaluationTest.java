package com.mutualfunds.api.mutual_fund.features.ai.chat.eval;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptId;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptRegistry;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.IntentRouterService;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.WorkflowEngineSelector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWorkflowQualityEvaluationTest {

    @Test
    void chatSynthesisPromptShouldKeepGroundednessConstraint() {
        PromptRegistry prompts = new PromptRegistry();
        String text = prompts.text(PromptId.CHAT_SYNTHESIS_SYSTEM);

        assertThat(text).contains("Use only the provided tool results");
        assertThat(text).contains("If data is missing or stale");
    }

    @Test
    void diagnosticPromptShouldDemandJsonOnlyOutput() {
        PromptRegistry prompts = new PromptRegistry();
        String text = prompts.text(PromptId.AI_DIAGNOSTIC_SYSTEM);

        assertThat(text).contains("Respond ONLY with valid JSON");
        assertThat(text).contains("Do NOT wrap the response in markdown code blocks");
    }

    @Test
    void selectorShouldFallbackWhenAdvancedRouteRequestedAndLangchainDisabled() {
        AiWorkflowProperties properties = new AiWorkflowProperties();
        properties.getLangchain4j().setEnabled(false);
        WorkflowEngineSelector selector = new WorkflowEngineSelector(properties);

        IntentDecision decision = new IntentDecision(
                ChatIntent.SCENARIO_ANALYSIS,
                "SIMULATE",
                WorkflowRoute.LC4J_SCENARIO_ANALYSIS,
                0.9,
                false);

        WorkflowEngineSelector.Selection selection = selector.select(decision);
        assertThat(selection.fallbackUsed()).isTrue();
        assertThat(selection.workflowRoute()).isEqualTo(WorkflowRoute.SPRING_FALLBACK_CHAT);
    }

    @Test
    void keywordRoutingShouldStayWithinLatencyBudget() {
        IntentRouterService router = new IntentRouterService();
        long startedAt = System.nanoTime();
        router.resolveDecision("Show my portfolio summary and top issue", "LANDING");
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        assertThat(elapsedMs).isLessThan(250L);
    }
}
