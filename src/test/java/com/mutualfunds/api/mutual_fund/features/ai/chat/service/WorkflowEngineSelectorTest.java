package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowEngineType;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineSelectorTest {

    @Test
    void usesLangChain4jForAdvancedRoutesWhenEnabled() {
        WorkflowEngineSelector selector = new WorkflowEngineSelector(AiWorkflowProperties.defaults());

        WorkflowEngineSelector.Selection selection = selector.select(new IntentDecision(
                ChatIntent.SCENARIO_ANALYSIS,
                "SIMULATE",
                WorkflowRoute.LC4J_SCENARIO_ANALYSIS,
                0.84,
                false));

        assertThat(selection.engineType()).isEqualTo(WorkflowEngineType.LANGCHAIN4J);
        assertThat(selection.workflowRoute()).isEqualTo(WorkflowRoute.LC4J_SCENARIO_ANALYSIS);
        assertThat(selection.fallbackUsed()).isFalse();
    }
}
