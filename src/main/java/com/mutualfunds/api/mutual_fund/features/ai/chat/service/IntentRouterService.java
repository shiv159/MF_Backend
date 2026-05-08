package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.mutualfunds.api.mutual_fund.features.ai.chat.config.AiWorkflowProperties;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ChatIntent;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.WorkflowRoute;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptId;
import com.mutualfunds.api.mutual_fund.features.ai.chat.prompt.PromptRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Slf4j
public class IntentRouterService {

    private final ChatClient chatClient;
    private final AiWorkflowProperties properties;
    private final PromptRegistry promptRegistry;

    public IntentRouterService(ChatClient.Builder builder, AiWorkflowProperties properties,
            PromptRegistry promptRegistry) {
        this.chatClient = builder
                .defaultAdvisors(new SimpleLoggerAdvisor()).build();
        this.properties = properties;
        this.promptRegistry = promptRegistry;
    }

    public IntentRouterService() {
        this.chatClient = null;
        this.properties = AiWorkflowProperties.defaults();
        this.properties.setClassifierEnabled(false);
        this.promptRegistry = new PromptRegistry();
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
                .system(promptRegistry.text(PromptId.INTENT_CLASSIFIER))
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
        } catch (Exception ex) {
            log.warn("Intent classifier failed; falling back to keyword scoring: {}", ex.getMessage());
            return fallback;
        }
    }

    private IntentDecision resolveKeywordDecision(String message, String screenContext) {
        String normalized = normalize(message);
        String context = screenContext == null ? "" : screenContext.toUpperCase(Locale.ROOT);

        IntentScore scenario = new IntentScore(ChatIntent.SCENARIO_ANALYSIS,
                scoreScenario(normalized, context));
        IntentScore rebalance = new IntentScore(ChatIntent.REBALANCE_DRAFT,
                scoreRebalance(normalized, context));
        IntentScore dataQuality = new IntentScore(ChatIntent.DATA_QUALITY,
                scoreDataQuality(normalized, context));
        IntentScore compare = new IntentScore(ChatIntent.FUND_COMPARE,
                scoreCompare(normalized, context));
        IntentScore fundRisk = new IntentScore(ChatIntent.FUND_RISK,
                scoreFundRisk(normalized, context));
        IntentScore fundPerformance = new IntentScore(ChatIntent.FUND_PERFORMANCE,
                scoreFundPerformance(normalized, context));
        IntentScore riskProfile = new IntentScore(ChatIntent.RISK_PROFILE_EXPLAINER,
                scoreRiskProfile(normalized, context));
        IntentScore diagnostic = new IntentScore(ChatIntent.DIAGNOSTIC_EXPLAINER,
                scoreDiagnostic(normalized, context));
        IntentScore portfolio = new IntentScore(ChatIntent.PORTFOLIO_SUMMARY,
                scorePortfolioSummary(normalized, context));
        IntentScore general = new IntentScore(ChatIntent.GENERAL_QA,
                scoreGeneral(normalized));

        IntentScore top = Stream.of(scenario, rebalance, dataQuality, compare, fundRisk, fundPerformance,
                        riskProfile, diagnostic, portfolio, general)
                .max(Comparator.comparingDouble(IntentScore::score))
                .orElse(general);

        IntentScore runnerUp = Stream.of(scenario, rebalance, dataQuality, compare, fundRisk, fundPerformance,
                        riskProfile, diagnostic, portfolio, general)
                .filter(score -> !Objects.equals(score.intent(), top.intent()))
                .max(Comparator.comparingDouble(IntentScore::score))
                .orElse(general);

        WorkflowRoute route = routeForIntent(top.intent(), normalized);
        String toolGroup = toolGroupForIntent(top.intent());
        boolean requiresConfirmation = top.intent() == ChatIntent.REBALANCE_DRAFT;
        double confidence = computeConfidence(top.score(), runnerUp.score());

        return new IntentDecision(top.intent(), toolGroup, route, confidence, requiresConfirmation);
    }

    private String normalize(String message) {
        return message == null ? "" : message.toLowerCase(Locale.ROOT).trim();
    }

    private double scoreScenario(String normalized, String context) {
        double score = scoreAny(normalized, 1.6,
                "what if", "scenario", "simulate", "sip", "allocation shift", "shift to debt", "shift to equity",
                "more conservative", "more aggressive");
        score += scoreAny(normalized, 0.6, "move 10%", "move 5%", "move 20%", "increase sip", "decrease sip");
        return score + contextBoost(context, "SCENARIO", 0.4);
    }

    private double scoreRebalance(String normalized, String context) {
        double score = scoreAny(normalized, 1.7,
                "rebalance", "reallocate", "switch fund", "switch funds", "reduce risk", "improve allocation");
        score += scoreAny(normalized, 0.5, "critique", "review", "trade-off", "why", "better option");
        return score + contextBoost(context, "MANUAL_SELECTION_RESULT", 0.3);
    }

    private double scoreDataQuality(String normalized, String context) {
        double score = scoreAny(normalized, 1.8,
                "stale", "freshness", "outdated", "missing data", "enrichment", "missing nav", "data quality");
        return score + contextBoost(context, "DATA_QUALITY", 0.2);
    }

    private double scoreCompare(String normalized, String context) {
        double score = scoreAny(normalized, 1.7,
                " compare ", " vs ", " versus ", "better than", "which fund", "two funds", "which of these");
        score += scoreAny(normalized, 0.6, "recommend", "which should i pick", "for my profile", "better for my");
        if (normalized.contains("better") && normalized.contains("fund")) {
            score += 0.6;
        }
        return score + contextBoost(context, "COMPARE", 0.2);
    }

    private double scoreFundRisk(String normalized, String context) {
        double score = scoreAny(normalized, 1.4, "fund risk", "beta", "alpha", "volatility", "standard deviation");
        if (normalized.contains("risk") && !normalized.contains("portfolio")) {
            score += 0.7;
        }
        return score + contextBoost(context, "RISK", 0.1);
    }

    private double scoreFundPerformance(String normalized, String context) {
        double score = scoreAny(normalized, 1.4,
                "performance", "return", "returns", "cagr", "rolling return", "top performer", "perform", "performed");
        if (normalized.contains("over") && normalized.contains("year")) {
            score += 0.4;
        }
        return score + contextBoost(context, "PERFORMANCE", 0.1);
    }

    private double scoreRiskProfile(String normalized, String context) {
        double score = scoreAny(normalized, 1.4,
                "why was i rated", "why this allocation", "what should i start with", "explain my risk profile");
        return score + contextBoost(context, "RISK_PROFILE_RESULT", 1.2);
    }

    private double scoreDiagnostic(String normalized, String context) {
        double score = scoreAny(normalized, 1.3, "what is wrong", "what's wrong", "diagnostic", "overlap", "improve my portfolio");
        return score + contextBoost(context, "MANUAL_SELECTION_RESULT", 1.1);
    }

    private double scorePortfolioSummary(String normalized, String context) {
        double score = scoreAny(normalized, 1.2, "analyze my portfolio", "portfolio summary", "my holdings", "portfolio", "summary");
        score += normalized.contains("analyze") ? 0.4 : 0.0;
        return score + contextBoost(context, "PORTFOLIO", 0.2);
    }

    private double scoreGeneral(String normalized) {
        double score = normalized.isBlank() ? 0.1 : 0.4;
        if (normalized.contains("recommend for me") || normalized.contains("for my profile")) {
            score += 0.4;
        }
        return score;
    }

    private WorkflowRoute routeForIntent(ChatIntent intent, String normalized) {
        return switch (intent) {
            case SCENARIO_ANALYSIS -> WorkflowRoute.LC4J_SCENARIO_ANALYSIS;
            case REBALANCE_DRAFT -> containsAny(normalized, "why", "critique", "better option", "trade-off", "review")
                    ? WorkflowRoute.LC4J_REBALANCE_CRITIQUE
                    : WorkflowRoute.SPRING_STANDARD_CHAT;
            case FUND_COMPARE, GENERAL_QA -> containsAny(normalized, "for my profile", "which should i pick",
                    "recommend", "why did you suggest", "recommend for me")
                    ? WorkflowRoute.LC4J_RECOMMENDATION_SYNTHESIS
                    : WorkflowRoute.SPRING_STANDARD_CHAT;
            default -> WorkflowRoute.SPRING_STANDARD_CHAT;
        };
    }

    private String toolGroupForIntent(ChatIntent intent) {
        return switch (intent) {
            case SCENARIO_ANALYSIS -> "SIMULATE";
            case REBALANCE_DRAFT -> "REBALANCE";
            case DATA_QUALITY -> "DATA_QUALITY";
            case FUND_COMPARE -> "COMPARE";
            case FUND_RISK, FUND_PERFORMANCE -> "FUND_ANALYTICS";
            case RISK_PROFILE_EXPLAINER -> "EXPLAIN";
            case DIAGNOSTIC_EXPLAINER -> "DIAGNOSE";
            case PORTFOLIO_SUMMARY -> "PORTFOLIO";
            case GENERAL_QA -> "GENERAL";
        };
    }

    private double computeConfidence(double topScore, double runnerUpScore) {
        double base = Math.min(0.92, 0.45 + (topScore / 8.0));
        double separation = Math.min(0.2, Math.max(0.0, (topScore - runnerUpScore) / 8.0));
        return clamp(base + separation);
    }

    private double scoreAny(String normalized, double weight, String... needles) {
        double score = 0.0;
        for (String needle : needles) {
            if (needle == null || needle.isBlank()) {
                continue;
            }
            if (normalized.contains(needle)) {
                score += weight;
            }
        }
        return score;
    }

    private double contextBoost(String context, String needle, double boost) {
        return context != null && context.contains(needle) ? boost : 0.0;
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

    private record IntentScore(ChatIntent intent, double score) {
    }
}
