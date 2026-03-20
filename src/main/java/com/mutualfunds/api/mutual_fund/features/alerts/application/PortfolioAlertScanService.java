package com.mutualfunds.api.mutual_fund.features.alerts.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.alerts.domain.AlertSeverity;
import com.mutualfunds.api.mutual_fund.features.alerts.domain.AlertType;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import com.mutualfunds.api.mutual_fund.features.alerts.domain.PortfolioAlert;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.alerts.persistence.PortfolioAlertRepository;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.users.api.UserAccountService;
import com.mutualfunds.api.mutual_fund.features.portfolio.quality.application.PortfolioDataQualityInspector;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.application.PortfolioDiagnosticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAlertScanService {

    private final PortfolioReadService portfolioReadService;
    private final UserAccountService userAccountService;
    private final PortfolioAlertRepository portfolioAlertRepository;
    private final PortfolioDiagnosticService portfolioDiagnosticService;
    private final PortfolioDataQualityInspector dataQualityInspector;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${alerts.scan.cron:0 0 8 * * *}", zone = "${alerts.scan.zone:Asia/Kolkata}")
    public void scanAllUsers() {
        for (UUID userId : portfolioReadService.findDistinctUserIds()) {
            try {
                scanUser(userId);
            } catch (Exception ex) {
                log.warn("Alert scan failed for user {}: {}", userId, ex.getMessage());
            }
        }
    }

    public void scanUser(UUID userId) {
        List<UserHolding> holdings = portfolioReadService.findHoldingsWithFund(userId);
        if (holdings.isEmpty()) {
            return;
        }

        User user = userAccountService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        PortfolioDataQualityInspector.Result qualityResult = dataQualityInspector.inspect(holdings);
        PortfolioDiagnosticDTO diagnostic = portfolioDiagnosticService.runDiagnostic(userId);

        createStaleDataAlert(user, qualityResult);
        createMissingEnrichmentAlert(user, qualityResult);
        createHighConcentrationAlert(user, diagnostic);
        createPortfolioHealthAlert(user, diagnostic);
    }

    private void createStaleDataAlert(User user, PortfolioDataQualityInspector.Result qualityResult) {
        if (qualityResult.staleCount() <= 0) {
            return;
        }
        List<String> fundNames = qualityResult.staleFunds().stream()
                .map(PortfolioDataQualityInspector.FundIssue::fundName)
                .sorted()
                .toList();
        String dedupeKey = "STALE_DATA:" + String.join("|", fundNames);
        saveIfNotRecent(user, dedupeKey, AlertType.STALE_DATA, AlertSeverity.MEDIUM,
                "Some held funds have stale NAV data",
                "At least " + qualityResult.staleCount() + " held fund(s) have stale or missing NAV updates.",
                objectMapper.valueToTree(qualityResult.staleFunds()),
                24);
    }

    private void createMissingEnrichmentAlert(User user, PortfolioDataQualityInspector.Result qualityResult) {
        if (qualityResult.missingCount() <= 0) {
            return;
        }
        List<String> fundNames = qualityResult.missingFunds().stream()
                .map(PortfolioDataQualityInspector.FundIssue::fundName)
                .sorted()
                .toList();
        String dedupeKey = "MISSING_ENRICHMENT:" + String.join("|", fundNames);
        saveIfNotRecent(user, dedupeKey, AlertType.MISSING_ENRICHMENT, AlertSeverity.MEDIUM,
                "Some held funds are missing enrichment details",
                "At least " + qualityResult.missingCount() + " held fund(s) are missing holdings, sector, or metadata fields.",
                objectMapper.valueToTree(qualityResult.missingFunds()),
                24);
    }

    private void createHighConcentrationAlert(User user, PortfolioDiagnosticDTO diagnostic) {
        List<PortfolioDiagnosticDTO.DiagnosticSuggestion> relevant = diagnostic.getSuggestions().stream()
                .filter(suggestion -> suggestion.getSeverity() == PortfolioDiagnosticDTO.Severity.HIGH)
                .filter(suggestion -> suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.SECTOR_CONCENTRATION
                        || suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.STOCK_OVERLAP
                        || suggestion.getCategory() == PortfolioDiagnosticDTO.SuggestionCategory.MARKET_CAP_IMBALANCE)
                .sorted(Comparator.comparing(suggestion -> suggestion.getCategory().name()))
                .toList();
        if (relevant.isEmpty()) {
            return;
        }

        String dedupeKey = "HIGH_CONCENTRATION:" + relevant.stream()
                .map(suggestion -> suggestion.getCategory().name())
                .collect(Collectors.joining("|"));
        saveIfNotRecent(user, dedupeKey, AlertType.HIGH_CONCENTRATION, AlertSeverity.HIGH,
                "Portfolio concentration risk needs attention",
                relevant.get(0).getMessage(),
                objectMapper.valueToTree(relevant),
                24);
    }

    private void createPortfolioHealthAlert(User user, PortfolioDiagnosticDTO diagnostic) {
        List<PortfolioDiagnosticDTO.DiagnosticSuggestion> highSeverity = diagnostic.getSuggestions().stream()
                .filter(suggestion -> suggestion.getSeverity() == PortfolioDiagnosticDTO.Severity.HIGH)
                .toList();
        if (highSeverity.isEmpty()) {
            return;
        }

        String dedupeKey = "PORTFOLIO_HEALTH_WARNING:" + highSeverity.stream()
                .map(suggestion -> suggestion.getCategory().name())
                .sorted()
                .collect(Collectors.joining("|"));
        saveIfNotRecent(user, dedupeKey, AlertType.PORTFOLIO_HEALTH_WARNING, AlertSeverity.HIGH,
                "Portfolio health warning",
                diagnostic.getSummary(),
                objectMapper.valueToTree(highSeverity),
                24);
    }

    private void saveIfNotRecent(User user,
            String dedupeKey,
            AlertType type,
            AlertSeverity severity,
            String title,
            String body,
            com.fasterxml.jackson.databind.JsonNode payload,
            int dedupeHours) {
        boolean exists = portfolioAlertRepository.existsByUser_UserIdAndDedupeKeyAndCreatedAtAfter(
                user.getUserId(), dedupeKey, LocalDateTime.now().minusHours(dedupeHours));
        if (exists) {
            return;
        }

        portfolioAlertRepository.save(PortfolioAlert.builder()
                .user(user)
                .type(type)
                .severity(severity)
                .title(title)
                .body(body)
                .payloadJson(payload)
                .dedupeKey(dedupeKey)
                .build());
    }
}
