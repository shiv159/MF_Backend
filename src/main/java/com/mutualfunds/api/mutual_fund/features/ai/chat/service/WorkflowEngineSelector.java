package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowEngineType;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service responsible for selecting the appropriate AI workflow engine and route
 * based on the intent decision from the chat message. It determines whether to use
 * Langchain4j for advanced scenarios or fall back to Spring AI for standard or fallback cases.
 */
@Service
@RequiredArgsConstructor
public class WorkflowEngineSelector {

    private final AiWorkflowProperties properties;

    /**
     * Selects the workflow route and engine type based on the given intent decision.
     * For advanced routes (scenario analysis, rebalance critique, recommendation synthesis),
     * it prefers Langchain4j if enabled, otherwise falls back to Spring AI.
     * For standard intents, it uses Spring AI with the standard chat route.
     *
     * @param decision the intent decision containing the route and other details
     * @return a Selection object with the chosen route, engine type, and fallback flag
     */
    public Selection select(IntentDecision decision) {
        // Determine if the route requires advanced AI capabilities
        boolean advancedRoute = decision.route() == WorkflowRoute.LC4J_SCENARIO_ANALYSIS
                || decision.route() == WorkflowRoute.LC4J_REBALANCE_CRITIQUE
                || decision.route() == WorkflowRoute.LC4J_RECOMMENDATION_SYNTHESIS;

        // If advanced route and Langchain4j is enabled, use it
        if (advancedRoute && properties.getLangchain4j().isEnabled()) {
            return new Selection(decision.route(), WorkflowEngineType.LANGCHAIN4J, false);
        }

        // If advanced route but Langchain4j not enabled, fallback to Spring AI
        if (advancedRoute) {
            return new Selection(WorkflowRoute.SPRING_FALLBACK_CHAT, WorkflowEngineType.SPRING_AI, true);
        }

        // For standard routes, use Spring AI with standard chat
        return new Selection(WorkflowRoute.SPRING_STANDARD_CHAT, WorkflowEngineType.SPRING_AI, false);
    }

    /**
     * Record representing the selection result, containing the workflow route,
     * the engine type to use, and whether a fallback was applied.
     */
    public record Selection(
            WorkflowRoute workflowRoute,
            WorkflowEngineType engineType,
            boolean fallbackUsed) {
    }
}
