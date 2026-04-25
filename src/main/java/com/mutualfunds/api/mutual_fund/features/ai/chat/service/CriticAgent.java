package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CriticAgent {

    private static final String SYSTEM_PROMPT = """
            You are CriticAgent for a mutual fund copilot.
            Return ONLY valid JSON:
            {"approved":true,"summaryAdjustment":"...","warnings":["..."],"confidenceAdjustment":0.0}
            Reject overclaiming, stale-data blind spots, or anything that sounds like automatic execution.
            """;

    private final LangChain4jWorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public AgentResponse review(AgentContextBundle context, AgentResponse draft) {
        try {
            ObjectNode prompt = objectMapper.createObjectNode();
            prompt.set("context", objectMapper.valueToTree(context));
            prompt.set("draft", objectMapper.valueToTree(draft));

            LangChain4jWorkflowEngine.Response response = workflowEngine.generate(
                    SYSTEM_PROMPT,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt));
            JsonNode node = objectMapper.readTree(stripCodeFence(response.content()));

            List<String> warnings = new ArrayList<>(draft.getWarnings());
            node.path("warnings").forEach(item -> {
                if (item.isTextual()) {
                    warnings.add(item.asText());
                }
            });
            warnings.add("Advisory only. Review the rationale before making any portfolio change.");

            String summary = node.path("summaryAdjustment").asText("").isBlank()
                    ? draft.getSummary()
                    : draft.getSummary() + " " + node.path("summaryAdjustment").asText();
            double confidence = Math.max(0.2, Math.min(0.95,
                    draft.getConfidence() + node.path("confidenceAdjustment").asDouble(0.0)));

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

    private String stripCodeFence(String content) {
        if (content == null) {
            return "{}";
        }
        return content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
    }
}
