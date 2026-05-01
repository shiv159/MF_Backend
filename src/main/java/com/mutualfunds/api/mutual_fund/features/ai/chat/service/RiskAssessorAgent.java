package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskAssessorAgent {

    private final LangChain4jWorkflowEngine workflowEngine;
    private final PromptRegistry promptRegistry;

    public RiskAssessmentResult analyze(WorkflowRoute route, AgentContextBundle context, UUID conversationId) {
        try {
            WorkflowResponse<RiskAssessmentResult> response = workflowEngine.generate(WorkflowRequest.<RiskAssessmentResult>builder()
                    .conversationId(conversationId)
                    .executionUserId(context.getUserId())
                    .scope("risk-assessor")
                    .route(route)
                    .detailLevel(ToolDetailLevel.ANALYST)
                    .userQuestion(context.getUserMessage())
                    .seedContext(seedContext(route, context))
                        .systemPrompt(promptRegistry.text(PromptId.RISK_ASSESSOR_SYSTEM))
                    .outputType(RiskAssessmentResult.class)
                        .selectedTools(ToolSelectionCatalog.RISK_ASSESSOR)
                    .build());
            return response.getContent();
        } catch (Exception ex) {
            log.warn("RiskAssessorAgent fell back to deterministic output: {}", ex.getMessage());
            RiskAssessmentResult fallback = new RiskAssessmentResult();
            fallback.setSummary("The portfolio needs suitability and diversification review before acting.");
            fallback.setRiskShift("STABLE");
            fallback.setConcentration("Review concentration and suitability against the saved risk profile.");
            fallback.setWarnings(List.of("Risk agent used fallback reasoning because structured model output was unavailable."));
            fallback.setConfidence(0.58);
            return fallback;
        }
    }

    private Map<String, Object> seedContext(WorkflowRoute route, AgentContextBundle context) {
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("route", route == null ? "UNKNOWN" : route.name());
        seed.put("screenContext", context.getScreenContext() == null ? "LANDING" : context.getScreenContext());
        seed.put("fundIds", context.getHoldingsSummary() == null
                ? List.of()
                : context.getHoldingsSummary().valueStream()
                        .map(node -> node.path("fundId").asText(""))
                        .filter(value -> !value.isBlank())
                        .limit(4)
                        .toList());
        seed.put("objectiveHint", "Assess suitability and concentration risk before recommendation.");
        return seed;
    }
}