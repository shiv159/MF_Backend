package com.mutualfunds.api.mutual_fund.features.ai.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticInsightsValidatorTest {

    @Test
    void shouldRejectMissingSummary() {
        DiagnosticInsightsPayload payload = DiagnosticInsightsPayload.builder()
                .summary(" ")
                .suggestionMessages(Map.of("OVERLAP", "Trim overlapping large-cap exposure"))
                .strengths(List.of("Good debt diversification"))
                .build();

        assertThat(DiagnosticInsightsValidator.validate(payload)).isEmpty();
    }

    @Test
    void shouldNormalizeOptionalFieldsWhenValid() {
        DiagnosticInsightsPayload payload = DiagnosticInsightsPayload.builder()
                .summary("  Portfolio risk is improving  ")
                .suggestionMessages(null)
                .strengths(List.of("Consistent SIP discipline", " "))
                .build();

        DiagnosticInsightsPayload normalized = DiagnosticInsightsValidator.validate(payload).orElseThrow();
        assertThat(normalized.summary()).isEqualTo("Portfolio risk is improving");
        assertThat(normalized.suggestionMessages()).isEmpty();
        assertThat(normalized.strengths()).containsExactly("Consistent SIP discipline");
    }
}
