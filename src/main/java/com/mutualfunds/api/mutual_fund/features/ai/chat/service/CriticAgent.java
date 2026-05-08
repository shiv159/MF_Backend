package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.CriticReviewResult;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRequest;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowResponse;
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
public class CriticAgent {

    private final LangChain4jWorkflowEngine workflowEngine;
        private final PromptRegistry promptRegistry;

    public AgentResponse review(AgentContextBundle context, AgentResponse draft, UUID conversationId) {
        try {
            WorkflowResponse<CriticReviewResult> response = workflowEngine.generate(WorkflowRequest.<CriticReviewResult>builder()
                    .conversationId(conversationId)
                    .executionUserId(context.getUserId())
                    .scope("critic")
                    .route(draft.getWorkflowRoute())
                    .detailLevel(ToolDetailLevel.ANALYST)
                    .userQuestion(context.getUserMessage())
                    .seedContext(seedContext(context, draft))
                    .systemPrompt(promptRegistry.text(PromptId.CRITIC_SYSTEM))
                    .outputType(CriticReviewResult.class)
                    .selectedTools(ToolSelectionCatalog.CRITIC)
                    .build());
            CriticReviewResult node = response.getContent();

            List<String> warnings = new ArrayList<>(draft.getWarnings());
            if (node.getWarnings() != null) {
                warnings.addAll(node.getWarnings());
            }
            warnings.add("Advisory only. Review the rationale before making any portfolio change.");

            String summary = node.getSummaryAdjustment() == null || node.getSummaryAdjustment().isBlank()
                    ? draft.getSummary()
                    : draft.getSummary() + " " + node.getSummaryAdjustment();
            double confidence = Math.max(0.2, Math.min(0.95,
                    draft.getConfidence() + node.getConfidenceAdjustment()));

            return AgentResponse.builder()
                    .summary(summary)
                    .warnings(warnings.stream().distinct().limit(8).toList())
                    .actions(draft.getActions())
                    .confidence(confidence)
                    .toolCalls(draft.getToolCalls())
                    .modelProfileUsed(draft.getModelProfileUsed())
                    .workflowRoute(draft.getWorkflowRoute())
                    .requiresConfirmation(draft.isRequiresConfirmation())
                    .build();
        } catch (Exception ex) {
            log.warn("CriticAgent used fallback review: {}", ex.getMessage());
            List<String> warnings = new ArrayList<>(draft.getWarnings());
            warnings.add("Advisory only. Review the rationale before making any portfolio change.");
            return AgentResponse.builder()
                    .summary(draft.getSummary())
                    .warnings(warnings.stream().distinct().limit(8).toList())
                    .actions(draft.getActions())
                    .confidence(Math.min(draft.getConfidence(), 0.65))
                    .toolCalls(draft.getToolCalls())
                    .modelProfileUsed(draft.getModelProfileUsed())
                    .workflowRoute(draft.getWorkflowRoute())
                    .requiresConfirmation(draft.isRequiresConfirmation())
                    .build();
        }
    }

    private Map<String, Object> seedContext(AgentContextBundle context, AgentResponse draft) {
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("route", draft.getWorkflowRoute() == null ? "UNKNOWN" : draft.getWorkflowRoute().name());
        seed.put("screenContext", context.getScreenContext() == null ? "LANDING" : context.getScreenContext());
        seed.put("draftSummary", draft.getSummary());
        seed.put("draftWarnings", draft.getWarnings());
        seed.put("criticObjective", "Validate evidence quality and remove unsupported claims.");
        return seed;
    }

}