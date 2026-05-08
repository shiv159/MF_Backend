package com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.api;

import com.mutualfunds.api.mutual_fund.features.ai.application.AiService;
import com.mutualfunds.api.mutual_fund.features.ai.application.DiagnosticInsightsPayload;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO.DiagnosticSuggestion;
import com.mutualfunds.api.mutual_fund.shared.security.UserPrincipal;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.application.PortfolioDiagnosticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            Optional<DiagnosticInsightsPayload> payload = aiService.generateDiagnosticInsights(context);
            if (payload.isEmpty()) {
                log.warn("AI returned empty response, keeping template messages");
                return;
            }

            DiagnosticInsightsPayload insight = payload.get();
            if (insight.summary() != null && !insight.summary().isBlank()) {
                diagnostic.setSummary(insight.summary());
            }

            Map<String, String> suggestionMessages = insight.suggestionMessages() == null
                    ? Map.of()
                    : insight.suggestionMessages();
            if (!suggestionMessages.isEmpty()) {
                for (DiagnosticSuggestion suggestion : diagnostic.getSuggestions()) {
                    String value = suggestionMessages.get(suggestion.getCategory().name());
                    if (value != null && !value.isBlank()) {
                        suggestion.setMessage(value);
                    }
                }
            }

            if (insight.strengths() != null) {
                List<String> aiStrengths = new ArrayList<>(insight.strengths().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .toList());
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
