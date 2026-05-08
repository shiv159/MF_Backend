package com.mutualfunds.api.mutual_fund.features.ai.chat.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.IntentDecision;
import com.mutualfunds.api.mutual_fund.features.ai.chat.service.IntentRouterService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenIntentRouterEvaluationTest {

    private static final String DATASET = "/ai/golden-intent-router.json";

    @Test
    void shouldMatchGoldenIntentDataset() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        IntentRouterService routerService = new IntentRouterService();

        List<GoldenCase> cases = loadDataset(objectMapper);
        List<String> failures = new ArrayList<>();

        for (GoldenCase entry : cases) {
            IntentDecision decision = routerService.resolveDecision(entry.message(), entry.screenContext());
            if (!entry.expectedIntent().equals(decision.intent().name())
                    || !entry.expectedRoute().equals(decision.route().name())) {
                failures.add("message='" + entry.message() + "' expected intent=" + entry.expectedIntent()
                        + " route=" + entry.expectedRoute() + " got intent=" + decision.intent()
                        + " route=" + decision.route());
            }
        }

        assertThat(failures).isEmpty();
    }

    private List<GoldenCase> loadDataset(ObjectMapper objectMapper) throws Exception {
        try (InputStream input = GoldenIntentRouterEvaluationTest.class.getResourceAsStream(DATASET)) {
            if (input == null) {
                throw new IllegalStateException("Golden dataset not found: " + DATASET);
            }
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        }
    }

    private record GoldenCase(String message, String screenContext, String expectedIntent, String expectedRoute) {
    }
}
