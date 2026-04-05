package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class LLMIntentRouterService {

    private static final String INTENT_PROMPT = """
            You are an intent classifier for a mutual fund portfolio assistant.
            Classify the user message into exactly ONE of these intents:
            - REBALANCE_DRAFT: user wants to rebalance, reallocate, switch funds, improve allocation
            - DATA_QUALITY: user asks about stale/outdated/missing data
            - FUND_COMPARE: user wants to compare two or more funds
            - FUND_RISK: user asks about fund risk metrics (beta, alpha, volatility)
            - FUND_PERFORMANCE: user asks about fund returns, CAGR, performance
            - RISK_PROFILE_EXPLAINER: user asks about their risk profile or allocation rationale
            - DIAGNOSTIC_EXPLAINER: user asks what's wrong with portfolio, overlap, diagnostic
            - PORTFOLIO_SUMMARY: user asks for portfolio analysis or summary
            - WHAT_IF: user asks hypothetical questions ("what if I invest", "what happens if I sell")
            - GOAL_PLANNING: user talks about financial goals (education, retirement, house, wedding)
            - FUND_STORY: user asks for deep dive or story about a specific fund
            - GENERAL_QA: anything else

            Also extract entities:
            - funds: fund names mentioned (array of strings)
            - timeframe: investment horizon or comparison period if mentioned
            - amount: monetary amount if mentioned
            - goalType: type of goal if mentioned (education, retirement, house, emergency, wedding, travel)

            Respond with ONLY valid JSON:
            {"intent":"INTENT_NAME","entities":{"funds":[],"timeframe":null,"amount":null,"goalType":null}}

            Screen context: %s
            User message: %s
            """;

    private final ChatClient routerClient;
    private final IntentRouterService fallbackRouter;
    private final ObjectMapper objectMapper;

    public LLMIntentRouterService(@Qualifier("routerChatClient") ChatClient routerClient,
                                   IntentRouterService fallbackRouter,
                                   ObjectMapper objectMapper) {
        this.routerClient = routerClient;
        this.fallbackRouter = fallbackRouter;
        this.objectMapper = objectMapper;
    }

    public record IntentResult(ChatIntent intent, Map<String, Object> entities) {}

    public IntentResult resolveIntent(String message, String screenContext) {
        try {
            String response = routerClient.prompt()
                    .user(INTENT_PROMPT.formatted(
                            screenContext == null ? "NONE" : screenContext,
                            message))
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return fallback(message, screenContext);
            }

            String normalized = response.strip();
            if (normalized.startsWith("```")) {
                normalized = normalized.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode node = objectMapper.readTree(normalized);
            String intentStr = node.path("intent").asText("GENERAL_QA");
            ChatIntent intent;
            try {
                intent = ChatIntent.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                intent = ChatIntent.GENERAL_QA;
            }

            Map<String, Object> entities = new HashMap<>();
            JsonNode entitiesNode = node.path("entities");
            if (entitiesNode.has("funds") && entitiesNode.path("funds").isArray()) {
                List<String> funds = new ArrayList<>();
                entitiesNode.path("funds").forEach(f -> {
                    if (!f.asText("").isBlank()) funds.add(f.asText());
                });
                entities.put("funds", funds);
            }
            if (entitiesNode.has("timeframe") && !entitiesNode.path("timeframe").isNull()) {
                entities.put("timeframe", entitiesNode.path("timeframe").asText());
            }
            if (entitiesNode.has("amount") && !entitiesNode.path("amount").isNull()) {
                entities.put("amount", entitiesNode.path("amount").asDouble());
            }
            if (entitiesNode.has("goalType") && !entitiesNode.path("goalType").isNull()) {
                entities.put("goalType", entitiesNode.path("goalType").asText());
            }

            log.info("LLM intent resolved: {} with entities: {}", intent, entities);
            return new IntentResult(intent, entities);
        } catch (Exception ex) {
            log.warn("LLM intent routing failed, falling back to keyword: {}", ex.getMessage());
            return fallback(message, screenContext);
        }
    }

    private IntentResult fallback(String message, String screenContext) {
        return new IntentResult(fallbackRouter.resolveIntent(message, screenContext), Map.of());
    }
}
