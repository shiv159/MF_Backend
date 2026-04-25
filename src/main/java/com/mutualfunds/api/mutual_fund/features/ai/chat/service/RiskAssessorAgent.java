package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.AgentContextBundle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskAssessorAgent {

    private static final String SYSTEM_PROMPT = """
            You are RiskAssessorAgent for a mutual fund copilot.
            Return ONLY valid JSON with:
            {"summary":"...","riskShift":"LOWER|HIGHER|STABLE","concentration":"...","warnings":["..."],"confidence":0.0}
            Base your answer only on the supplied JSON.
            """;

    private final LangChain4jWorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;

    public JsonNode analyze(AgentContextBundle context) {
        try {
            LangChain4jWorkflowEngine.Response response = workflowEngine.generate(
                    SYSTEM_PROMPT,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context));
            return objectMapper.readTree(stripCodeFence(response.content()));
        } catch (Exception ex) {
            log.warn("RiskAssessorAgent fell back to deterministic output: {}", ex.getMessage());
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("summary", "The portfolio needs suitability and diversification review before acting.");
            fallback.put("riskShift", "STABLE");
            fallback.put("concentration", "Review concentration and suitability against the saved risk profile.");
            fallback.putArray("warnings")
                    .add("Risk agent used fallback reasoning because structured model output was unavailable.");
            fallback.put("confidence", 0.58);
            return fallback;
        }
    }

    private String stripCodeFence(String content) {
        if (content == null) {
            return "{}";
        }
        return content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
    }
}
