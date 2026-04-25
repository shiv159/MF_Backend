package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowEngineType;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowEngineSelector {

    private final AiWorkflowProperties properties;

    public Selection select(IntentDecision decision) {
        boolean advancedRoute = decision.route() == WorkflowRoute.LC4J_SCENARIO_ANALYSIS
                || decision.route() == WorkflowRoute.LC4J_REBALANCE_CRITIQUE
                || decision.route() == WorkflowRoute.LC4J_RECOMMENDATION_SYNTHESIS;

        if (advancedRoute && properties.getLangchain4j().isEnabled()) {
            return new Selection(decision.route(), WorkflowEngineType.LANGCHAIN4J, false);
        }

        if (advancedRoute) {
            return new Selection(WorkflowRoute.SPRING_FALLBACK_CHAT, WorkflowEngineType.SPRING_AI, true);
        }

        return new Selection(WorkflowRoute.SPRING_STANDARD_CHAT, WorkflowEngineType.SPRING_AI, false);
    }

    public record Selection(
            WorkflowRoute workflowRoute,
            WorkflowEngineType engineType,
            boolean fallbackUsed) {
    }
}
