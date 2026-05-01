package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.ai.chat.model.ToolDetailLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PortfolioStateTools {

    private final PortfolioAiToolSupport support;

    @Tool("Get the current user's portfolio snapshot with freshness and top holdings.")
    public JsonNode getPortfolioSnapshot(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        List<com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding> holdings =
                support.portfolioToolFacade().findCurrentHoldings();
        ObjectNode snapshot = support.portfolioToolFacade().buildPortfolioSnapshot(holdings).deepCopy();
        snapshot.put("detailLevel", detail.name());
        snapshot.set("freshness", support.portfolioFreshness(holdings));
        if (detail == ToolDetailLevel.ANALYST) {
            snapshot.set("holdings", support.holdingsSummary(holdings, 10));
        }
        return snapshot;
    }

    @Tool("Get the current user's portfolio diagnostic summary and key issues.")
    public JsonNode getPortfolioDiagnostic(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        java.util.UUID userId = support.portfolioToolFacade().currentUserId();
        com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO diagnostic =
            support.portfolioToolFacade().runDiagnostic(userId);
        if (detail == ToolDetailLevel.ANALYST) {
            return support.objectMapper().valueToTree(diagnostic);
        }
        ObjectNode summary = support.objectMapper().createObjectNode();
        summary.put("summary", diagnostic.getSummary());
        summary.put("detailLevel", detail.name());
        summary.put("highSeverityCount", diagnostic.getSuggestions() == null ? 0 : diagnostic.getSuggestions().stream()
            .filter(s -> s.getSeverity() == com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO.Severity.HIGH)
            .count());
        ArrayNode topIssues = support.objectMapper().createArrayNode();
        if (diagnostic.getSuggestions() != null) {
            diagnostic.getSuggestions().stream().limit(4).forEach(suggestion -> topIssues.add(support.objectMapper().createObjectNode()
                .put("category", suggestion.getCategory() == null ? "UNKNOWN" : suggestion.getCategory().name())
                .put("severity", suggestion.getSeverity() == null ? "UNKNOWN" : suggestion.getSeverity().name())
                .put("message", suggestion.getMessage())));
        }
        summary.set("topIssues", topIssues);
        return summary;
    }

    @Tool("Get the current user's saved risk profile and recommended allocation.")
    public JsonNode getRiskProfile(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        Optional<com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse> profile =
            support.portfolioToolFacade().findRiskProfile();
        if (profile.isEmpty()) {
            return support.objectMapper().createObjectNode()
                .put("status", "MISSING")
                .put("detailLevel", detail.name());
        }
        if (detail == ToolDetailLevel.ANALYST) {
            return support.objectMapper().valueToTree(profile.get());
        }
        ObjectNode compact = support.objectMapper().createObjectNode();
        compact.put("detailLevel", detail.name());
        compact.put("level", profile.get().getRiskProfile() == null ? "UNKNOWN" : profile.get().getRiskProfile().getLevel());
        compact.put("score", profile.get().getRiskProfile() == null || profile.get().getRiskProfile().getScore() == null
            ? 0
            : profile.get().getRiskProfile().getScore());
        if (profile.get().getAssetAllocation() != null) {
            compact.set("allocation", support.objectMapper().createObjectNode()
                .put("equity", support.value(profile.get().getAssetAllocation().getEquity()))
                .put("debt", support.value(profile.get().getAssetAllocation().getDebt()))
                .put("gold", support.value(profile.get().getAssetAllocation().getGold())));
        }
        return compact;
    }

    @Tool("Get open portfolio alerts derived from current diagnostic findings.")
    public JsonNode getOpenAlerts(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        ToolDetailLevel detail = support.parseDetail(detailLevel);
        com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO diagnostic =
                support.portfolioToolFacade().runDiagnostic();
        ArrayNode alerts = support.objectMapper().createArrayNode();
        if (diagnostic.getSuggestions() != null) {
            diagnostic.getSuggestions().stream().limit(6).forEach(suggestion -> alerts.add(support.objectMapper().createObjectNode()
                    .put("category", suggestion.getCategory() == null ? "UNKNOWN" : suggestion.getCategory().name())
                    .put("severity", suggestion.getSeverity() == null ? "UNKNOWN" : suggestion.getSeverity().name())
                    .put("message", suggestion.getMessage() == null ? "" : suggestion.getMessage())));
        }
        ObjectNode node = support.objectMapper().createObjectNode();
        node.put("detailLevel", detail.name());
        node.put("count", alerts.size());
        node.set("alerts", alerts);
        return node;
    }
}