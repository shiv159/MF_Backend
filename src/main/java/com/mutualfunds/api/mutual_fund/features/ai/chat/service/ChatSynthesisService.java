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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ChatSynthesisService {

    private static final String SYNTHESIS_PROMPT = """
            You are PlanMyFunds AI — a grounded, data-driven mutual fund portfolio assistant for Indian investors.

            Rules:
            - Keep responses under 300 words, use markdown formatting.
            - Use ONLY the provided tool results and conversation context. Never invent data.
            - If data is missing or stale, mention that briefly.
            - Be direct, useful, and non-promotional.
            - Use ₹ for currency, Indian number formatting (lakhs/crores).
            - When comparing funds, use tables.
            - When explaining risk, relate it to the user's profile.
            - End with 1-2 actionable next steps when appropriate.
            """;

    private final ObjectMapper objectMapper;
    private final ChatClient synthesisChatClient;
    private final ChatMemory chatMemory;

    public ChatSynthesisService(@Qualifier("synthesisChatClient") ChatClient synthesisChatClient,
                                 ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.synthesisChatClient = synthesisChatClient;
    }

    public SynthesisResult synthesize(ChatIntent intent, String conversationId, String screenContext,
            String userMessage, JsonNode toolPayload, List<String> warnings) {
        try {
            String effectiveConversationId = conversationId == null || conversationId.isBlank()
                    ? UUID.randomUUID().toString()
                    : conversationId;
            String response = synthesisChatClient.prompt()
                    .system(SYNTHESIS_PROMPT)
                    .user(buildPrompt(intent, screenContext, userMessage, toolPayload, warnings))
                    .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, effectiveConversationId))
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return new SynthesisResult(fallback(intent, toolPayload, warnings), true);
            }

            return new SynthesisResult(response.trim(), false);
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
        String note = warnings == null || warnings.isEmpty() ? "" : " Data note: " + warnings.getFirst();
        return switch (intent) {
            case PORTFOLIO_SUMMARY, GENERAL_QA -> {
                JsonNode snapshot = toolPayload.path("portfolioSnapshot");
                yield String.format(
                        "I found %d funds with a current value of ₹%.2f and a total gain/loss of ₹%.2f.%s",
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
            case REBALANCE_DRAFT -> toolPayload.path("draftSummary")
                    .asText("I prepared a draft rebalance plan that is still read-only and needs your review.") + note;
            case WHAT_IF -> "I've simulated the portfolio change you described." + note;
            case GOAL_PLANNING -> "I've started building a goal plan based on your inputs." + note;
            case FUND_STORY -> "Here's a deep dive into the fund you asked about." + note;
            case STATEMENT_ANALYZE -> "I've analyzed the uploaded statement." + note;
            case PEER_COMPARE -> "Here's how your portfolio compares to similar investors." + note;
        };
    }
}
