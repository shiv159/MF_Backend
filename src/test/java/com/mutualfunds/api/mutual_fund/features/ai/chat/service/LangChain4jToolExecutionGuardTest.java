package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jToolExecutionGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSuppressDuplicatesAndRespectBudget() {
        AiWorkflowProperties properties = new AiWorkflowProperties();
        properties.setDuplicateToolSuppression(true);
        properties.setMaxToolCallsPerTurn(2);
        LangChain4jToolExecutionGuard guard = new LangChain4jToolExecutionGuard(properties, objectMapper);

        LangChain4jToolExecutionGuard.TurnBudget budget = guard.startTurn();
        assertThat(guard.evaluate(budget, "getPortfolioSnapshot").allowed()).isTrue();
        assertThat(guard.evaluate(budget, "getPortfolioSnapshot").allowed()).isFalse();
        assertThat(guard.evaluate(budget, "getFundSnapshot").allowed()).isTrue();
        assertThat(guard.evaluate(budget, "compareFunds").allowed()).isFalse();
    }

    @Test
    void shouldApplyOutputBudget() throws Exception {
        AiWorkflowProperties properties = new AiWorkflowProperties();
        properties.setMaxToolResultChars(40);
        LangChain4jToolExecutionGuard guard = new LangChain4jToolExecutionGuard(properties, objectMapper);

        String result = guard.executeWithBudget("getPortfolioSnapshot", () -> "x".repeat(100));
        JsonNode node = objectMapper.readTree(result);
        assertThat(node.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(node.path("truncated").asBoolean()).isTrue();
        assertThat(node.path("originalLength").asInt()).isEqualTo(100);
    }

    @Test
    void shouldTimeoutSlowToolCalls() throws Exception {
        AiWorkflowProperties properties = new AiWorkflowProperties();
        properties.setPerToolTimeoutMs(10);
        LangChain4jToolExecutionGuard guard = new LangChain4jToolExecutionGuard(properties, objectMapper);

        String result = guard.executeWithBudget("slowTool", () -> {
            Thread.sleep(100);
            return "{\"status\":\"OK\"}";
        });
        JsonNode node = objectMapper.readTree(result);
        assertThat(node.path("status").asText()).isEqualTo("TIMEOUT");
    }
}
