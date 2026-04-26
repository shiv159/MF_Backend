package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class IntentRouterService {

    private static final String CLASSIFIER_PROMPT = """
            You are an intent router for a mutual fund portfolio copilot.
            Return ONLY valid JSON with this exact shape:
            {"intent":"...","toolGroup":"...","route":"...","confidence":0.0,"requiresConfirmation":false}

            Allowed intents:
            REBALANCE_DRAFT, SCENARIO_ANALYSIS, DATA_QUALITY, FUND_COMPARE, FUND_RISK, FUND_PERFORMANCE,
            RISK_PROFILE_EXPLAINER, DIAGNOSTIC_EXPLAINER, PORTFOLIO_SUMMARY, GENERAL_QA

            Allowed routes:
            SPRING_STANDARD_CHAT, LC4J_SCENARIO_ANALYSIS, LC4J_REBALANCE_CRITIQUE,
            LC4J_RECOMMENDATION_SYNTHESIS, SPRING_FALLBACK_CHAT

            Use SCENARIO_ANALYSIS for "what if", SIP change, allocation-shift, debt/equity mix simulation.
            Use LC4J_REBALANCE_CRITIQUE only when the user is asking to critique, explain, or refine a rebalance idea.
            Use LC4J_RECOMMENDATION_SYNTHESIS for suitability-aware comparison or recommendation synthesis.
            Keep requiresConfirmation true only for rebalance-like recommendations.
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AiWorkflowProperties properties;

    public IntentRouterService(ChatClient.Builder builder, ObjectMapper objectMapper, AiWorkflowProperties properties) {
        this.chatClient = builder
        .defaultAdvisors(new SimpleLoggerAdvisor()).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    IntentRouterService() {
        this.chatClient = null;
        this.objectMapper = new ObjectMapper();
        this.properties = AiWorkflowProperties.defaults();
        this.properties.setClassifierEnabled(false);
    }

    public ChatIntent resolveIntent(String message, String screenContext) {
        return resolveDecision(message, screenContext).intent();
    }

    public IntentDecision resolveDecision(String message, String screenContext) {
        IntentDecision fallback = resolveKeywordDecision(message, screenContext);
        if (!properties.isClassifierEnabled() || chatClient == null) {
            return fallback;
        }

        try {
            IntentDecision aiDecision = chatClient.prompt()
                    .system(CLASSIFIER_PROMPT)
                    .user("""
                            Screen context: %s
                            User message: %s
                            """.formatted(
                            screenContext == null ? "LANDING" : screenContext,
                            message == null ? "" : message))
                    .call()
                    .entity(IntentDecision.class);

            if (aiDecision == null || aiDecision.intent() == null || aiDecision.route() == null) {
                return fallback;
            }

            String toolGroup = (aiDecision.toolGroup() == null || aiDecision.toolGroup().isBlank())
                    ? fallback.toolGroup()
                    : aiDecision.toolGroup();

            return new IntentDecision(
                    aiDecision.intent(),
                    toolGroup,
                    aiDecision.route(),
                    clamp(aiDecision.confidence()),
                    aiDecision.requiresConfirmation());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private IntentDecision resolveKeywordDecision(String message, String screenContext) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT).trim();
        String context = screenContext == null ? "" : screenContext.toUpperCase(Locale.ROOT);

        if (mentionsScenario(normalized)) {
            return new IntentDecision(ChatIntent.SCENARIO_ANALYSIS, "SIMULATE",
                    WorkflowRoute.LC4J_SCENARIO_ANALYSIS, 0.84, false);
        }

        if (containsAny(normalized, "rebalance", "reduce risk", "switch fund", "switch funds", "reallocate",
                "improve allocation")) {
            WorkflowRoute route = containsAny(normalized, "why", "critique", "better option", "trade-off", "review this")
                    ? WorkflowRoute.LC4J_REBALANCE_CRITIQUE
                    : WorkflowRoute.SPRING_STANDARD_CHAT;
            return new IntentDecision(ChatIntent.REBALANCE_DRAFT, "REBALANCE", route, 0.86, true);
        }

        if (containsAny(normalized, "stale", "freshness", "outdated", "missing data", "enrichment", "missing nav",
                "data quality")) {
            return new IntentDecision(ChatIntent.DATA_QUALITY, "DATA_QUALITY",
                    WorkflowRoute.SPRING_STANDARD_CHAT, 0.9, false);
        }

        if (containsAny(normalized, " compare ", " vs ", " versus ", "better than")) {
            WorkflowRoute route = containsAny(normalized, "for my profile", "which should i pick", "recommend", "why did you suggest")
                    ? WorkflowRoute.LC4J_RECOMMENDATION_SYNTHESIS
                    : WorkflowRoute.SPRING_STANDARD_CHAT;
            return new IntentDecision(ChatIntent.FUND_COMPARE, "COMPARE", route, 0.81, false);
        }

        if (mentionsFundRisk(normalized)) {
            return new IntentDecision(ChatIntent.FUND_RISK, "FUND_ANALYTICS",
                    WorkflowRoute.SPRING_STANDARD_CHAT, 0.8, false);
        }

        if (mentionsFundPerformance(normalized)) {
            return new IntentDecision(ChatIntent.FUND_PERFORMANCE, "FUND_ANALYTICS",
                    WorkflowRoute.SPRING_STANDARD_CHAT, 0.8, false);
        }

        if ("RISK_PROFILE_RESULT".equals(context)
                || containsAny(normalized, "why was i rated", "why this allocation", "what should i start with",
                        "explain my risk profile")) {
            return new IntentDecision(ChatIntent.RISK_PROFILE_EXPLAINER, "EXPLAIN",
                    WorkflowRoute.SPRING_STANDARD_CHAT, 0.82, false);
        }

        if ("MANUAL_SELECTION_RESULT".equals(context)
                || containsAny(normalized, "what is wrong", "what's wrong", "diagnostic", "overlap", "improve my portfolio")) {
            return new IntentDecision(ChatIntent.DIAGNOSTIC_EXPLAINER, "DIAGNOSE",
                    WorkflowRoute.SPRING_STANDARD_CHAT, 0.82, false);
        }

        if (containsAny(normalized, "analyze my portfolio", "portfolio summary", "my holdings", "portfolio",
                "analyze", "summary")) {
            return new IntentDecision(ChatIntent.PORTFOLIO_SUMMARY, "PORTFOLIO",
                    WorkflowRoute.SPRING_STANDARD_CHAT, 0.74, false);
        }

        WorkflowRoute route = containsAny(normalized, "for my profile", "why did you suggest", "recommend for me")
                ? WorkflowRoute.LC4J_RECOMMENDATION_SYNTHESIS
                : WorkflowRoute.SPRING_STANDARD_CHAT;
        return new IntentDecision(ChatIntent.GENERAL_QA, "GENERAL", route, 0.56, false);
    }

    private boolean mentionsScenario(String message) {
        return containsAny(message, "what if", "scenario", "simulate", "sip", "move 10%", "move 5%", "shift to debt",
                "shift to equity", "more conservative", "more aggressive", "allocation shift");
    }

    private boolean mentionsFundRisk(String message) {
        return containsAny(message, " fund risk", "beta", "alpha", "volatility", "risk label", "standard deviation")
                || (message.contains("risk") && !message.contains("portfolio"));
    }

    private boolean mentionsFundPerformance(String message) {
        return containsAny(message, "performance", "return", "returns", "cagr", "rolling return", "top performer",
                "performing");
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
