package com.mutualfunds.api.mutual_fund.features.ai.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AiSafetyService {

    private static final String DISCLAIMER =
            "This is educational guidance, not personalized investment advice. Review any change against your full financial situation before acting.";
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    public InputSafetyAssessment assessInput(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return InputSafetyAssessment.allow();
        }
        if (containsAny(normalized,
                "ignore previous instructions",
                "ignore all previous instructions",
                "reveal your system prompt",
                "show me the system prompt",
                "developer message",
                "bypass guardrails",
                "jailbreak")) {
            return InputSafetyAssessment.block(
                    "Blocked prompt-injection style request.",
                    "I can help with portfolio analysis, but I can't follow requests to override instructions or reveal internal prompts.");
        }
        if (containsAny(normalized,
                "manipulate the market",
                "insider trading",
                "hide the trades",
                "wash trade",
                "pump and dump")) {
            return InputSafetyAssessment.block(
                    "Blocked financial policy request.",
                    "I can help explain lawful investing concepts, but I can't assist with market manipulation or hiding trades.");
        }
        return InputSafetyAssessment.allow();
    }

    public String enforceOutputPolicy(String text) {
        String sanitized = sanitizeText(text);
        boolean executionAdviceRemoved = !normalize(text).equals(normalize(sanitized));
        if (sanitized.isBlank()) {
            sanitized = "I can explain the trade-offs, risk signals, and suitability considerations, but I can't tell you to execute a specific transaction.";
        }
        if ((executionAdviceRemoved || needsDisclaimer(sanitized)) && !containsDisclaimer(sanitized)) {
            sanitized = sanitized + " " + DISCLAIMER;
        }
        return sanitized.trim();
    }

    public DiagnosticInsightsPayload sanitizeDiagnosticPayload(DiagnosticInsightsPayload payload) {
        if (payload == null) {
            return null;
        }
        Map<String, String> suggestionMessages = payload.suggestionMessages() == null
                ? Map.of()
                : payload.suggestionMessages().entrySet().stream()
                        .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> enforceOutputPolicy(entry.getValue())));
        List<String> strengths = payload.strengths() == null
                ? List.of()
                : payload.strengths().stream()
                        .map(this::sanitizeText)
                        .filter(value -> !value.isBlank())
                        .toList();
        return DiagnosticInsightsPayload.builder()
                .summary(enforceOutputPolicy(payload.summary()))
                .suggestionMessages(suggestionMessages)
                .strengths(strengths)
                .build();
    }

    private String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] sentences = SENTENCE_SPLIT.split(text.trim());
        List<String> retained = new ArrayList<>();
        boolean removedExecutionAdvice = false;
        for (String sentence : sentences) {
            if (containsExecutionAdvice(sentence)) {
                removedExecutionAdvice = true;
                continue;
            }
            retained.add(sentence.trim());
        }
        String sanitized = String.join(" ", retained).replaceAll("\\s+", " ").trim();
        if (removedExecutionAdvice) {
            String prefix = "I can explain the trade-offs, but I can't tell you to execute specific trades.";
            return sanitized.isBlank() ? prefix : prefix + " " + sanitized;
        }
        return sanitized;
    }

    private boolean containsExecutionAdvice(String sentence) {
        String normalized = normalize(sentence);
        return containsAny(normalized,
                "sell ",
                "buy ",
                "switch ",
                "redeem ",
                "invest ",
                "move ",
                "allocate ",
                "increase your sip now",
                "decrease your sip now",
                "immediately",
                "today");
    }

    private boolean needsDisclaimer(String text) {
        String normalized = normalize(text);
        return containsAny(normalized,
                "consider",
                "rebalance",
                "portfolio",
                "fund",
                "allocation",
                "risk");
    }

    private boolean containsDisclaimer(String text) {
        String normalized = normalize(text);
        return normalized.contains("not personalized investment advice")
                || normalized.contains("educational guidance");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    public record InputSafetyAssessment(boolean allowed, String reason, String safeResponse) {
        static InputSafetyAssessment allow() {
            return new InputSafetyAssessment(true, "", "");
        }

        static InputSafetyAssessment block(String reason, String safeResponse) {
            return new InputSafetyAssessment(false, reason, safeResponse);
        }
    }
}
