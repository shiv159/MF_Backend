package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LangChain4jWorkflowEngine {

    private final AiWorkflowProperties properties;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public Response generate(String systemPrompt, String userPrompt) {
        List<String> candidates = new ArrayList<>();
        candidates.add(properties.getLangchain4j().getPrimaryModelProfile());
        candidates.addAll(properties.getLangchain4j().getAlternateModelProfiles());

        RuntimeException lastFailure = null;
        for (String modelProfile : candidates) {
            try {
                ChatModel model = OpenAiChatModel.builder()
                        .baseUrl(properties.getLangchain4j().getBaseUrl())
                        .apiKey(apiKey)
                        .modelName(modelProfile)
                        .temperature(properties.getLangchain4j().getTemperature())
                        .build();

                String content = model.chat("""
                        %s

                        %s
                        """.formatted(systemPrompt, userPrompt));
                if (content != null && !content.isBlank()) {
                    return new Response(content, modelProfile);
                }
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("LangChain4j advanced flow failed for model {}: {}", modelProfile, ex.getMessage());
            }
        }

        throw lastFailure == null ? new IllegalStateException("No LangChain4j model response was produced") : lastFailure;
    }

    public record Response(String content, String modelProfileUsed) {
    }
}
