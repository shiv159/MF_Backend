package com.mutualfunds.api.mutual_fund.features.ai.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DiagnosticInsightsValidator {

    private DiagnosticInsightsValidator() {
    }

    static Optional<DiagnosticInsightsPayload> validate(DiagnosticInsightsPayload payload) {
        if (payload == null || payload.summary() == null || payload.summary().isBlank()) {
            return Optional.empty();
        }
        Map<String, String> suggestionMessages = payload.suggestionMessages() == null
                ? Map.of()
                : payload.suggestionMessages();
        List<String> strengths = payload.strengths() == null
                ? List.of()
                : payload.strengths().stream().filter(text -> text != null && !text.isBlank()).toList();
        return Optional.of(DiagnosticInsightsPayload.builder()
                .summary(payload.summary().trim())
                .suggestionMessages(suggestionMessages)
                .strengths(strengths)
                .build());
    }
}
