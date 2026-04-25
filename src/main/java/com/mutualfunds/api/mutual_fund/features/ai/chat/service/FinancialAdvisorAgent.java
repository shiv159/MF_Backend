package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.dto.ChatAction;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentResponse;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialAdvisorAgent {

    private static final String SYSTEM_PROMPT = """
            You are FinancialAdvisorAgent for a mutual fund portfolio copilot.
            Return ONLY valid JSON:
            {"summary":"...","warnings":["..."],"confidence":0.0}
            Rules:
            - Advisory only. Do not imply execution.
            - Reference only the supplied context, risk analysis, and market analysis.
            - Mention uncertainty briefly when context is stale or incomplete.
            """;

    private final LangChain4jWorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public AgentResponse advise(
            AgentContextBundle context,
            WorkflowRoute route,
            JsonNode riskAssessment,
            JsonNode marketAssessment) {
        try {
            ObjectNode prompt = objectMapper.createObjectNode();
            prompt.set("context", objectMapper.valueToTree(context));
            prompt.set("riskAssessment", riskAssessment);
            prompt.set("marketAssessment", marketAssessment);

            LangChain4jWorkflowEngine.Response response = workflowEngine.generate(
                    SYSTEM_PROMPT,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt));
            JsonNode node = objectMapper.readTree(stripCodeFence(response.content()));
            List<String> warnings = new ArrayList<>(context.getWarnings());
            warnings.addAll(readArray(node.path("warnings")));
            return AgentResponse.builder()
                    .summary(node.path("summary").asText(fallbackSummary(route)))
                    .warnings(warnings.stream().distinct().limit(8).toList())
                    .actions(defaultActions(route))
                    .confidence(node.path("confidence").asDouble(0.67))
                    .workflowRoute(route)
                    .toolCalls(List.of("risk_assessor", "market_analyst", "financial_advisor", "critic"))
                    .modelProfileUsed(response.modelProfileUsed())
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

    private List<String> readArray(JsonNode node) {
        if (!(node instanceof ArrayNode arrayNode)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        arrayNode.forEach(item -> {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        });
        return values;
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

    private String stripCodeFence(String content) {
        if (content == null) {
            return "{}";
        }
        return content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
    }
}
