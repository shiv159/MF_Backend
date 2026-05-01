package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatAction;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AdvisorAssessmentResult;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.MarketAssessmentResult;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.RiskAssessmentResult;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptId;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialAdvisorAgent {

    private final LangChain4jWorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
        private final PromptRegistry promptRegistry;

    public AgentResponse advise(
            AgentContextBundle context,
            WorkflowRoute route,
            RiskAssessmentResult riskAssessment,
            MarketAssessmentResult marketAssessment,
            UUID conversationId) {
        try {
            WorkflowResponse<AdvisorAssessmentResult> response = workflowEngine.generate(WorkflowRequest.<AdvisorAssessmentResult>builder()
                    .conversationId(conversationId)
                    .executionUserId(context.getUserId())
                    .scope("financial-advisor")
                    .route(route)
                    .detailLevel(ToolDetailLevel.ANALYST)
                    .userQuestion(context.getUserMessage())
                    .seedContext(seedContext(route, context, riskAssessment, marketAssessment))
                    .systemPrompt(promptRegistry.text(PromptId.FINANCIAL_ADVISOR_SYSTEM))
                    .outputType(AdvisorAssessmentResult.class)
                    .selectedTools(ToolSelectionCatalog.FINANCIAL_ADVISOR)
                    .build());
            AdvisorAssessmentResult node = response.getContent();
            List<String> warnings = new ArrayList<>(context.getWarnings());
            warnings.addAll(node.getWarnings() == null ? List.of() : node.getWarnings());
            return AgentResponse.builder()
                    .summary(node.getSummary() == null || node.getSummary().isBlank() ? fallbackSummary(route) : node.getSummary())
                    .warnings(warnings.stream().distinct().limit(8).toList())
                    .actions(defaultActions(route))
                    .confidence(node.getConfidence() <= 0 ? 0.67 : node.getConfidence())
                    .workflowRoute(route)
                    .toolCalls(List.of("risk_assessor", "market_analyst", "financial_advisor", "critic"))
                    .modelProfileUsed(response.getModelProfileUsed())
                    .requiresConfirmation(route == WorkflowRoute.LC4J_REBALANCE_CRITIQUE)
                    .build();
        } catch (Exception ex) {
            log.warn("FinancialAdvisorAgent fell back to deterministic summary: {}", ex.getMessage());
            return AgentResponse.builder()
                    .summary(fallbackSummary(route))
                    .warnings(context.getWarnings())
                    .actions(defaultActions(route))
                    .confidence(0.55)
                    .workflowRoute(route)
                    .toolCalls(List.of("risk_assessor", "market_analyst", "financial_advisor"))
                    .modelProfileUsed("fallback")
                    .requiresConfirmation(route == WorkflowRoute.LC4J_REBALANCE_CRITIQUE)
                    .build();
        }
    }

    private Map<String, Object> seedContext(
            WorkflowRoute route,
            AgentContextBundle context,
            RiskAssessmentResult riskAssessment,
            MarketAssessmentResult marketAssessment) {
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("route", route == null ? "UNKNOWN" : route.name());
        seed.put("screenContext", context.getScreenContext() == null ? "LANDING" : context.getScreenContext());
        seed.put("objectiveHint", "Provide advisory-only recommendation with suitability-first framing.");
        seed.put("candidateFundIds", context.getHoldingsSummary() == null
                ? List.of()
                : context.getHoldingsSummary().valueStream()
                        .map(node -> node.path("fundId").asText(""))
                        .filter(value -> !value.isBlank())
                        .limit(4)
                        .toList());

        Map<String, Object> priorAssessments = new LinkedHashMap<>();
        priorAssessments.put("riskSummary", riskAssessment == null ? "" : riskAssessment.getSummary());
        priorAssessments.put("riskShift", riskAssessment == null ? "STABLE" : riskAssessment.getRiskShift());
        priorAssessments.put("marketFreshness", marketAssessment == null ? "UNKNOWN" : marketAssessment.getFreshnessStatus());
        priorAssessments.put("marketWarnings", marketAssessment == null ? List.of() : marketAssessment.getMarketWarnings());
        seed.put("priorAssessments", priorAssessments);
        return seed;
    }

    private List<ChatAction> defaultActions(WorkflowRoute route) {
        return switch (route) {
            case LC4J_SCENARIO_ANALYSIS -> List.of(
                    followUp("Show a more conservative version", "Show a more conservative version"),
                    followUp("What if I increase SIP instead?", "What if I increase SIP instead?"),
                    followUp("Why did you suggest this?", "Why did you suggest this?"));
            case LC4J_REBALANCE_CRITIQUE -> List.of(
                    followUp("Explain the biggest trade-off", "Explain the biggest trade-off"),
                    followUp("Show a simpler rebalance", "Show a simpler rebalance"),
                    followUp("Why does this fit my profile?", "Why does this fit my profile?"));
            case LC4J_RECOMMENDATION_SYNTHESIS -> List.of(
                    followUp("Compare the downside risk", "Compare the downside risk"),
                    followUp("Why does this fit my profile?", "Why does this fit my profile?"),
                    followUp("Show a more conservative choice", "Show a more conservative choice"));
            default -> List.of();
        };
    }

    private ChatAction followUp(String label, String prompt) {
        return ChatAction.builder()
                .type("FOLLOW_UP_PROMPT")
                .label(label)
                .payload(objectMapper.createObjectNode().put("prompt", prompt))
                .build();
    }

    private String fallbackSummary(WorkflowRoute route) {
        return switch (route) {
            case LC4J_SCENARIO_ANALYSIS ->
                    "This scenario changes your portfolio mix in an advisory way, but the exact benefit depends on your risk tolerance and data freshness.";
            case LC4J_REBALANCE_CRITIQUE ->
                    "This rebalance critique focuses on suitability, diversification, and category-level trade-offs before any action is taken.";
            case LC4J_RECOMMENDATION_SYNTHESIS ->
                    "This recommendation synthesis weighs suitability, risk, and available fund evidence to explain the safer fit.";
            default -> "I reviewed the portfolio context and prepared an advisory response.";
        };
    }

}