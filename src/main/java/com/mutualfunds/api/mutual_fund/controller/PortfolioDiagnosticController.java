package com.mutualfunds.api.mutual_fund.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.ai.service.AiService;
import com.mutualfunds.api.mutual_fund.dto.analytics.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.PortfolioDiagnosticDTO.DiagnosticSuggestion;
import com.mutualfunds.api.mutual_fund.security.UserPrincipal;
import com.mutualfunds.api.mutual_fund.service.analytics.PortfolioDiagnosticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for portfolio diagnostic analysis.
 * Provides structured portfolio health reports with AI-generated insights
 * and rule-based fallback messages.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
@Slf4j
public class PortfolioDiagnosticController {

    private final PortfolioDiagnosticService diagnosticService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    /**
     * GET /api/v1/portfolio/diagnostic
     *
     * Runs full portfolio diagnostic analysis for the authenticated user.
     * By default, uses AI to generate personalized suggestion messages,
     * summary, and strengths. Falls back to template messages if AI fails.
     *
     * @param includeAiSummary If false, skips AI and uses only template messages.
     *                         Default is true (AI-generated insights).
     * @param authentication   Spring Security authentication principal
     * @return PortfolioDiagnosticDTO with complete diagnostic report
     */
    @GetMapping("/diagnostic")
    public ResponseEntity<PortfolioDiagnosticDTO> getDiagnostic(
            @RequestParam(value = "includeAiSummary", defaultValue = "true") boolean includeAiSummary,
            Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = ((UserPrincipal) authentication.getPrincipal()).getUserId();
        log.info("Portfolio diagnostic requested for user: {}, includeAi: {}", userId, includeAiSummary);

        // 1. Run rule-based diagnostic (detects issues with template fallback messages)
        PortfolioDiagnosticDTO diagnostic = diagnosticService.runDiagnostic(userId);

        // 2. Enrich with AI-generated insights if requested
        if (includeAiSummary && diagnostic.getMetrics().getTotalFunds() > 0) {
            enrichWithAI(diagnostic);
        }

        return ResponseEntity.ok(diagnostic);
    }

    /**
     * Calls the AI service to generate personalized messages, then merges
     * them back into the rule-detected suggestions/summary/strengths.
     * If AI fails at any stage, the original template content is preserved.
     */
    private void enrichWithAI(PortfolioDiagnosticDTO diagnostic) {
        try {
            String context = diagnosticService.buildDiagnosticContextForAI(diagnostic);
            String aiResponse = aiService.generateDiagnosticInsights(context);

            if (aiResponse == null || aiResponse.isBlank()) {
                log.warn("AI returned empty response, keeping template messages");
                return;
            }

            // Strip markdown code fences if present
            String jsonStr = aiResponse;
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(jsonStr);

            // Replace summary
            if (root.has("summary") && !root.get("summary").asText().isBlank()) {
                diagnostic.setSummary(root.get("summary").asText());
            }

            // Merge suggestion messages
            if (root.has("suggestionMessages") && root.get("suggestionMessages").isObject()) {
                JsonNode messages = root.get("suggestionMessages");
                for (DiagnosticSuggestion suggestion : diagnostic.getSuggestions()) {
                    String categoryKey = suggestion.getCategory().name();
                    if (messages.has(categoryKey) && !messages.get(categoryKey).asText().isBlank()) {
                        suggestion.setMessage(messages.get(categoryKey).asText());
                    }
                }
            }

            // Replace strengths
            if (root.has("strengths") && root.get("strengths").isArray()) {
                List<String> aiStrengths = new ArrayList<>();
                for (JsonNode node : root.get("strengths")) {
                    String text = node.asText();
                    if (text != null && !text.isBlank()) {
                        aiStrengths.add(text);
                    }
                }
                if (!aiStrengths.isEmpty()) {
                    diagnostic.setStrengths(aiStrengths);
                }
            }

            log.info("AI diagnostic enrichment successful");
        } catch (Exception e) {
            log.warn("AI enrichment failed, keeping template messages: {}", e.getMessage());
            // Template messages remain intact — no action needed
        }
    }
}
