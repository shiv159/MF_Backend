package com.mutualfunds.api.mutual_fund.features.ai.application;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredOutputSupportTest {

    private final StructuredOutputSupport support = new StructuredOutputSupport(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void shouldPreferNativeStructuredOutputWhenItValidates() {
        AtomicInteger rawCalls = new AtomicInteger();
        DiagnosticInsightsPayload payload = DiagnosticInsightsPayload.builder()
                .summary("Healthy diversification")
                .suggestionMessages(Map.of())
                .build();

        Optional<DiagnosticInsightsPayload> result = support.generate(
                DiagnosticInsightsPayload.class,
                () -> payload,
                () -> {
                    rawCalls.incrementAndGet();
                    return "{\"summary\":\"ignored\"}";
                },
                raw -> "{\"summary\":\"ignored\"}",
                DiagnosticInsightsValidator::validate);

        assertThat(result).contains(DiagnosticInsightsPayload.builder()
                .summary("Healthy diversification")
                .suggestionMessages(Map.of())
                .strengths(java.util.List.of())
                .build());
        assertThat(rawCalls).hasValue(0);
    }

    @Test
    void shouldRepairInvalidRawJsonOnce() {
        AtomicInteger repairCalls = new AtomicInteger();

        Optional<DiagnosticInsightsPayload> result = support.generate(
                DiagnosticInsightsPayload.class,
                () -> {
                    throw new IllegalStateException("native structured output unavailable");
                },
                () -> "{\"summary\":\"\",\"suggestionMessages\":{}}",
                raw -> {
                    repairCalls.incrementAndGet();
                    return """
                            {"summary":"Recovered summary","suggestionMessages":{"OVERLAP":"Reduce overlap"},"strengths":["Disciplined SIPs"]}
                            """;
                },
                DiagnosticInsightsValidator::validate);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().summary()).isEqualTo("Recovered summary");
        assertThat(repairCalls).hasValue(1);
    }

    @Test
    void shouldReturnEmptyWhenNativeRawAndRepairAllFail() {
        AtomicInteger repairCalls = new AtomicInteger();

        Optional<DiagnosticInsightsPayload> result = support.generate(
                DiagnosticInsightsPayload.class,
                () -> {
                    throw new IllegalStateException("native failed");
                },
                () -> "not-json",
                raw -> {
                    repairCalls.incrementAndGet();
                    return "{\"summary\":\"   \"}";
                },
                DiagnosticInsightsValidator::validate);

        assertThat(result).isEmpty();
        assertThat(repairCalls).hasValue(1);
    }
}
