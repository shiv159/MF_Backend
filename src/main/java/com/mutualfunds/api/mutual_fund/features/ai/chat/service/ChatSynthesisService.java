package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ChatSynthesisService {

    private static final String SYNTHESIS_PROMPT = """
            You are a grounded mutual fund portfolio assistant.
            Return ONLY valid JSON using this exact structure:
            {"response":"short grounded answer"}

            Rules:
            - Keep the response under 170 words.
            - Use only the provided tool results and conversation context.
            - If data is missing or stale, mention that briefly.
            - Be direct, useful, and non-promotional.
            - Do not include markdown code fences.
            """;

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public ChatSynthesisService(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(20)
                        .build()).build())
                .build();
    }

    public SynthesisResult synthesize(ChatIntent intent, String conversationId, String screenContext,
            String userMessage, JsonNode toolPayload, List<String> warnings) {
        try {
            String effectiveConversationId = conversationId == null || conversationId.isBlank()
                    ? UUID.randomUUID().toString()
                    : conversationId;
            String response = chatClient.prompt()
                    .system(SYNTHESIS_PROMPT)
                    .user(buildPrompt(intent, screenContext, userMessage, toolPayload, warnings))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID,
                            effectiveConversationId))
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return new SynthesisResult(fallback(intent, toolPayload, warnings), true);
            }

            String normalized = stripCodeFences(response);
            JsonNode node = objectMapper.readTree(normalized);
            String content = node.path("response").asText("").trim();
            if (content.isBlank()) {
                return new SynthesisResult(fallback(intent, toolPayload, warnings), true);
            }
            return new SynthesisResult(content, false);
        } catch (Exception ex) {
            log.warn("Chat synthesis failed, using fallback: {}", ex.getMessage());
            return new SynthesisResult(fallback(intent, toolPayload, warnings), true);
        }
    }

    public record SynthesisResult(String response, boolean fallbackUsed) {
    }

    private String buildPrompt(ChatIntent intent, String screenContext, String userMessage, JsonNode toolPayload,
            List<String> warnings) throws Exception {
        return """
                Intent: %s
                Screen context: %s

                User question:
                %s

                Tool results:
                %s

                Warnings:
                %s
                """.formatted(
                intent.name(),
                screenContext == null ? "UNKNOWN" : screenContext,
                userMessage == null || userMessage.isBlank() ? "None" : userMessage,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(toolPayload),
                warnings == null || warnings.isEmpty() ? "None" : String.join("; ", warnings));
    }

    private String fallback(ChatIntent intent, JsonNode toolPayload, List<String> warnings) {
        String note = warnings == null || warnings.isEmpty() ? "" : " Data note: " + warnings.get(0);
        return switch (intent) {
            case PORTFOLIO_SUMMARY, GENERAL_QA -> {
                JsonNode snapshot = toolPayload.path("portfolioSnapshot");
                yield String.format(
                        "I found %d funds with a current value of %.2f and a total gain/loss of %.2f.%s",
                        snapshot.path("fundCount").asInt(),
                        snapshot.path("totalCurrentValue").asDouble(),
                        snapshot.path("totalGainLoss").asDouble(),
                        note);
            }
            case DIAGNOSTIC_EXPLAINER -> toolPayload.path("diagnostic").path("summary")
                    .asText("I reviewed your diagnostic report and found a few structural issues to fix first.") + note;
            case RISK_PROFILE_EXPLAINER -> {
                JsonNode profile = toolPayload.path("riskProfile");
                yield String.format("Your current risk level is %s with a suggested allocation of Equity %.0f%%, Debt %.0f%%, Gold %.0f%%.%s",
                        profile.path("riskProfile").path("level").asText("MODERATE"),
                        profile.path("assetAllocation").path("equity").asDouble(),
                        profile.path("assetAllocation").path("debt").asDouble(),
                        profile.path("assetAllocation").path("gold").asDouble(),
                        note);
            }
            case FUND_PERFORMANCE -> "I pulled the recent return profile for the fund(s) you asked about." + note;
            case FUND_RISK -> "I pulled the current risk metrics for the fund(s) you asked about." + note;
            case FUND_COMPARE -> "I compared the requested funds using available return and risk metrics." + note;
            case DATA_QUALITY -> {
                JsonNode quality = toolPayload.path("dataQuality");
                yield String.format("I found %d stale funds and %d funds with missing enrichment fields.%s",
                        quality.path("staleCount").asInt(),
                        quality.path("missingCount").asInt(),
                        note);
            }
            case SCENARIO_ANALYSIS -> "I evaluated the scenario using your saved portfolio mix, risk profile, and fund analytics." + note;
            case REBALANCE_DRAFT -> toolPayload.path("draftSummary")
                    .asText("I prepared a draft rebalance plan that is still read-only and needs your review.") + note;
        };
    }

    private String stripCodeFences(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        return content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
    }
}
