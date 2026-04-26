package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.dto.PortfolioDiagnosticDTO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PortfolioStateTools {

    private final PortfolioAiTools portfolioAiTools;
    private final PortfolioToolFacade portfolioToolFacade;
    private final ObjectMapper objectMapper;

    @Tool("Get the current user's portfolio snapshot with freshness and top holdings.")
    public JsonNode getPortfolioSnapshot(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.getPortfolioSnapshot(detailLevel);
    }

    @Tool("Get the current user's portfolio diagnostic summary and key issues.")
    public JsonNode getPortfolioDiagnostic(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.getPortfolioDiagnostic(detailLevel);
    }

    @Tool("Get the current user's saved risk profile and recommended allocation.")
    public JsonNode getRiskProfile(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        return portfolioAiTools.getRiskProfile(detailLevel);
    }

    @Tool("Get open portfolio alerts derived from current diagnostic findings.")
    public JsonNode getOpenAlerts(
            @P(name = "detailLevel", description = "Optional detail level: COMPACT or ANALYST", required = false)
            Optional<String> detailLevel) {
        PortfolioDiagnosticDTO diagnostic = portfolioToolFacade.runDiagnostic();
        ArrayNode alerts = objectMapper.createArrayNode();
        if (diagnostic.getSuggestions() != null) {
            diagnostic.getSuggestions().stream().limit(6).forEach(suggestion -> alerts.add(objectMapper.createObjectNode()
                    .put("category", suggestion.getCategory() == null ? "UNKNOWN" : suggestion.getCategory().name())
                    .put("severity", suggestion.getSeverity() == null ? "UNKNOWN" : suggestion.getSeverity().name())
                    .put("message", suggestion.getMessage() == null ? "" : suggestion.getMessage())));
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("detailLevel", detailLevel.orElse("COMPACT"));
        node.put("count", alerts.size());
        node.set("alerts", alerts);
        return node;
    }
}