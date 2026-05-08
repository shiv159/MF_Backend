package com.mutualfunds.api.mutual_fund.features.ai.application;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record DiagnosticInsightsPayload(
        String summary,
        Map<String, String> suggestionMessages,
        List<String> strengths) {
}
