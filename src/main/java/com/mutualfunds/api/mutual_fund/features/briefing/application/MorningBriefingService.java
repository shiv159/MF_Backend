package com.mutualfunds.api.mutual_fund.features.briefing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.briefing.domain.PortfolioBriefing;
import com.mutualfunds.api.mutual_fund.features.briefing.persistence.PortfolioBriefingRepository;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.application.PortfolioDiagnosticService;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.features.risk.api.RiskProfileQuery;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.users.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
public class MorningBriefingService {

    private final PortfolioBriefingRepository briefingRepository;
    private final UserRepository userRepository;
    private final PortfolioReadService portfolioReadService;
    private final PortfolioDiagnosticService diagnosticService;
    private final PortfolioDataQualityInspector qualityInspector;
    private final RiskProfileQuery riskProfileQuery;
    private final ChatClient quickChatClient;
    private final ObjectMapper objectMapper;

    public MorningBriefingService(PortfolioBriefingRepository briefingRepository,
                                   UserRepository userRepository,
                                   PortfolioReadService portfolioReadService,
                                   PortfolioDiagnosticService diagnosticService,
                                   PortfolioDataQualityInspector qualityInspector,
                                   RiskProfileQuery riskProfileQuery,
                                   @Qualifier("quickChatClient") ChatClient quickChatClient,
                                   ObjectMapper objectMapper) {
        this.briefingRepository = briefingRepository;
        this.userRepository = userRepository;
        this.portfolioReadService = portfolioReadService;
        this.diagnosticService = diagnosticService;
        this.qualityInspector = qualityInspector;
        this.riskProfileQuery = riskProfileQuery;
        this.quickChatClient = quickChatClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void generateDailyBriefings() {
        log.info("Starting daily portfolio briefing generation");
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(User::getIsActive)
                .toList();

        for (User user : activeUsers) {
            try {
                generateBriefingForUser(user, "DAILY");
            } catch (Exception e) {
                log.error("Failed to generate briefing for user {}: {}", user.getUserId(), e.getMessage());
            }
        }
        log.info("Completed daily briefing generation for {} users", activeUsers.size());
    }

    @Transactional
    public PortfolioBriefing generateBriefingForUser(User user, String type) {
        UUID userId = user.getUserId();
        List<UserHolding> holdings = portfolioReadService.findHoldingsWithFund(userId);

        if (holdings.isEmpty()) {
            return null;
        }

        ObjectNode metrics = objectMapper.createObjectNode();
        double totalInvestment = holdings.stream()
                .map(UserHolding::getInvestmentAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        double totalCurrent = holdings.stream()
                .map(UserHolding::getCurrentValue).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();

        metrics.put("totalValue", Math.round(totalCurrent * 100.0) / 100.0);
        metrics.put("totalInvested", Math.round(totalInvestment * 100.0) / 100.0);
        metrics.put("totalGainLoss", Math.round((totalCurrent - totalInvestment) * 100.0) / 100.0);
        metrics.put("gainLossPct", totalInvestment > 0
                ? Math.round(((totalCurrent - totalInvestment) / totalInvestment) * 10000.0) / 100.0 : 0);
        metrics.put("fundCount", holdings.size());

        holdings.stream()
                .filter(h -> h.getFund() != null && h.getInvestmentAmount() != null && h.getCurrentValue() != null && h.getInvestmentAmount() > 0)
                .max(Comparator.comparing(h -> (h.getCurrentValue() - h.getInvestmentAmount()) / h.getInvestmentAmount()))
                .ifPresent(h -> {
                    metrics.put("topPerformer", h.getFund().getFundName());
                    metrics.put("topPerformerReturn", Math.round(((h.getCurrentValue() - h.getInvestmentAmount()) / h.getInvestmentAmount()) * 10000.0) / 100.0);
                });

        holdings.stream()
                .filter(h -> h.getFund() != null && h.getInvestmentAmount() != null && h.getCurrentValue() != null && h.getInvestmentAmount() > 0)
                .min(Comparator.comparing(h -> (h.getCurrentValue() - h.getInvestmentAmount()) / h.getInvestmentAmount()))
                .ifPresent(h -> {
                    metrics.put("worstPerformer", h.getFund().getFundName());
                    metrics.put("worstPerformerReturn", Math.round(((h.getCurrentValue() - h.getInvestmentAmount()) / h.getInvestmentAmount()) * 10000.0) / 100.0);
                });

        var quality = qualityInspector.inspect(holdings);
        if (quality.staleCount() > 0) {
            metrics.put("staleFundCount", quality.staleCount());
        }

        ArrayNode alertsSummary = objectMapper.createArrayNode();
        try {
            PortfolioDiagnosticDTO diagnostic = diagnosticService.runDiagnostic(userId);
            diagnostic.getSuggestions().stream()
                    .filter(s -> s.getSeverity() == PortfolioDiagnosticDTO.Severity.HIGH)
                    .limit(3)
                    .forEach(s -> {
                        ObjectNode alert = objectMapper.createObjectNode();
                        alert.put("message", s.getMessage());
                        alert.put("severity", s.getSeverity().name());
                        alert.put("category", s.getCategory().name());
                        alertsSummary.add(alert);
                    });
        } catch (Exception e) {
            log.warn("Could not run diagnostic for briefing: {}", e.getMessage());
        }

        String content = generateBriefingNarrative(metrics, alertsSummary);

        String title = String.format("Portfolio Pulse — ₹%.0f (%s%.1f%%)",
                totalCurrent,
                totalCurrent >= totalInvestment ? "+" : "",
                totalInvestment > 0 ? ((totalCurrent - totalInvestment) / totalInvestment) * 100 : 0);

        PortfolioBriefing briefing = PortfolioBriefing.builder()
                .user(user)
                .briefingType(type)
                .title(title)
                .content(content)
                .metricsJson(metrics)
                .alertsSummary(alertsSummary)
                .isRead(false)
                .build();

        return briefingRepository.save(briefing);
    }

    private String generateBriefingNarrative(ObjectNode metrics, ArrayNode alerts) {
        try {
            String prompt = """
                    Generate a brief, friendly portfolio morning briefing (under 150 words) in markdown.
                    Use ₹ for currency. Be concise and actionable.

                    Portfolio metrics: %s
                    Active alerts: %s

                    Format: Start with a greeting, summarize key numbers, highlight concerns, end with one actionable tip.
                    """.formatted(metrics.toString(), alerts.toString());

            String response = quickChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return response != null && !response.isBlank() ? response.trim() : buildFallbackBriefing(metrics, alerts);
        } catch (Exception e) {
            log.warn("AI briefing generation failed, using fallback: {}", e.getMessage());
            return buildFallbackBriefing(metrics, alerts);
        }
    }

    private String buildFallbackBriefing(ObjectNode metrics, ArrayNode alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Your Portfolio Today**\n\n");
        sb.append(String.format("Portfolio value: ₹%.0f", metrics.path("totalValue").asDouble()));
        double gainPct = metrics.path("gainLossPct").asDouble();
        sb.append(String.format(" (%s%.1f%%)\n", gainPct >= 0 ? "+" : "", gainPct));
        sb.append(String.format("Across %d funds.\n\n", metrics.path("fundCount").asInt()));

        if (metrics.has("topPerformer")) {
            sb.append(String.format("Top performer: %s (+%.1f%%)\n",
                    metrics.path("topPerformer").asText(), metrics.path("topPerformerReturn").asDouble()));
        }

        if (!alerts.isEmpty()) {
            sb.append("\n**Attention needed:**\n");
            for (var alert : alerts) {
                sb.append("- ").append(alert.path("message").asText()).append("\n");
            }
        }

        return sb.toString();
    }

    public List<PortfolioBriefing> getUserBriefings(UUID userId) {
        return briefingRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);
    }

    public List<PortfolioBriefing> getUnreadBriefings(UUID userId) {
        return briefingRepository.findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(UUID userId) {
        return briefingRepository.countByUser_UserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID briefingId) {
        briefingRepository.findById(briefingId).ifPresent(b -> {
            b.setIsRead(true);
            briefingRepository.save(b);
        });
    }
}
