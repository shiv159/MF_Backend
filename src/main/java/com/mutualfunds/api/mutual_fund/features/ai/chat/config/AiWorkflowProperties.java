package com.mutualfunds.api.mutual_fund.features.ai.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "ai.workflow")
@Data
public class AiWorkflowProperties {

    private boolean classifierEnabled = true;
    private String defaultEngine = "spring-ai";
    private boolean duplicateToolSuppression = true;
    private int maxToolCallsPerTurn = 8;
    private int maxToolLoopIterations = 4;
    private long perToolTimeoutMs = 8_000L;
    private long totalAdvancedFlowTimeoutMs = 25_000L;
    private int marketContextStaleDays = 7;
    private int memoryWindowMessages = 20;
    private int memoryWindowTokens = 6000;
    private final Langchain4j langchain4j = new Langchain4j();

    public static AiWorkflowProperties defaults() {
        return new AiWorkflowProperties();
    }

    @Data
    public static class Langchain4j {
        private boolean enabled = true;
        private String primaryModelProfile = "openai/gpt-oss-120b:free";
        private List<String> alternateModelProfiles = new ArrayList<>(
                List.of("z-ai/glm-4.5-air:free", "nvidia/nemotron-3-super-120b-a12b:free"));
        private String baseUrl = "https://openrouter.ai/api/v1";
        private double temperature = 0.2;
        private boolean logRequests = false;
        private boolean logResponses = false;
    }
}
