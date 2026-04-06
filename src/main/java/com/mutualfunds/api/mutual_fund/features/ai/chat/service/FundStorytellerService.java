package com.mutualfunds.api.mutual_fund.features.ai.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundAnalyticsFacade;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.features.funds.dto.analytics.RollingReturnsDTO;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundStorytellerService {

    private final PortfolioReadService portfolioReadService;
    private final FundQueryService fundQueryService;
    private final FundAnalyticsFacade fundAnalyticsFacade;
    private final ObjectMapper objectMapper;

    public ObjectNode buildFundStory(UUID userId, String fundName) {
        ObjectNode story = objectMapper.createObjectNode();

        List<UserHolding> holdings = portfolioReadService.findHoldingsWithFund(userId);
        Optional<UserHolding> holdingOpt = holdings.stream()
                .filter(h -> h.getFund() != null && h.getFund().getFundName() != null)
                .filter(h -> h.getFund().getFundName().toLowerCase().contains(fundName.toLowerCase()))
                .findFirst();

        if (holdingOpt.isEmpty()) {
            List<Fund> found = fundQueryService.findByFundNameContainingIgnoreCase(fundName);
            if (found.isEmpty()) {
                story.put("error", "Could not find a fund matching: " + fundName);
                return story;
            }
            return buildStoryForFund(found.getFirst(), null, holdings);
        }

        UserHolding holding = holdingOpt.get();
        return buildStoryForFund(holding.getFund(), holding, holdings);
    }

    private ObjectNode buildStoryForFund(Fund fund, UserHolding holding, List<UserHolding> allHoldings) {
        ObjectNode story = objectMapper.createObjectNode();

        ObjectNode identity = objectMapper.createObjectNode();
        identity.put("fundName", fund.getFundName());
        identity.put("fundId", fund.getFundId().toString());
        identity.put("category", fund.getFundCategory() != null ? fund.getFundCategory() : "Unknown");
        identity.put("amc", fund.getAmcName() != null ? fund.getAmcName() : "Unknown");
        identity.put("directPlan", Boolean.TRUE.equals(fund.getDirectPlan()));
        if (fund.getExpenseRatio() != null) identity.put("expenseRatio", fund.getExpenseRatio());
        if (fund.getCurrentNav() != null) identity.put("currentNav", fund.getCurrentNav());
        story.set("identity", identity);

        if (holding != null) {
            ObjectNode position = objectMapper.createObjectNode();
            if (holding.getInvestmentAmount() != null) position.put("invested", holding.getInvestmentAmount());
            if (holding.getCurrentValue() != null) position.put("currentValue", holding.getCurrentValue());
            if (holding.getInvestmentAmount() != null && holding.getCurrentValue() != null) {
                double gain = holding.getCurrentValue() - holding.getInvestmentAmount();
                position.put("gainLoss", Math.round(gain * 100.0) / 100.0);
                position.put("gainLossPct", holding.getInvestmentAmount() > 0
                        ? Math.round((gain / holding.getInvestmentAmount()) * 10000.0) / 100.0 : 0);
            }
            if (holding.getUnitsHeld() != null) position.put("unitsHeld", holding.getUnitsHeld());
            if (holding.getWeightPct() != null) position.put("portfolioWeightPct", holding.getWeightPct());
            if (holding.getPurchaseDate() != null) position.put("purchaseDate", holding.getPurchaseDate().toString());
            story.set("position", position);
        }

        try {
            RollingReturnsDTO returns = fundAnalyticsFacade.calculateRollingReturns(fund.getFundId());
            story.set("performance", objectMapper.valueToTree(returns));
        } catch (Exception e) {
            log.warn("Could not fetch rolling returns for fund {}: {}", fund.getFundId(), e.getMessage());
        }

        try {
            RiskInsightsDTO risk = fundAnalyticsFacade.calculateRiskInsights(fund.getFundId());
            story.set("riskMetrics", objectMapper.valueToTree(risk));
        } catch (Exception e) {
            log.warn("Could not fetch risk insights for fund {}: {}", fund.getFundId(), e.getMessage());
        }

        if (fund.getSectorAllocationJson() != null) {
            story.set("sectorAllocation", fund.getSectorAllocationJson());
        }

        if (fund.getTopHoldingsJson() != null) {
            story.set("topStockHoldings", fund.getTopHoldingsJson());
        }

        if (fund.getFundCategory() != null) {
            ArrayNode peers = objectMapper.createArrayNode();
            fundQueryService.findAll().stream()
                    .filter(f -> fund.getFundCategory().equals(f.getFundCategory()))
                    .filter(f -> !f.getFundId().equals(fund.getFundId()))
                    .filter(f -> Boolean.TRUE.equals(f.getDirectPlan()))
                    .filter(f -> f.getExpenseRatio() != null)
                    .sorted(Comparator.comparing(Fund::getExpenseRatio))
                    .limit(5)
                    .forEach(peer -> {
                        ObjectNode peerNode = objectMapper.createObjectNode();
                        peerNode.put("fundName", peer.getFundName());
                        peerNode.put("expenseRatio", peer.getExpenseRatio());
                        if (peer.getCurrentNav() != null) peerNode.put("currentNav", peer.getCurrentNav());
                        peers.add(peerNode);
                    });
            story.set("categoryPeers", peers);

            OptionalDouble avgExpense = fundQueryService.findAll().stream()
                    .filter(f -> fund.getFundCategory().equals(f.getFundCategory()))
                    .filter(f -> f.getExpenseRatio() != null)
                    .mapToDouble(Fund::getExpenseRatio)
                    .average();
            if (avgExpense.isPresent()) {
                story.put("categoryAvgExpenseRatio", Math.round(avgExpense.getAsDouble() * 1000.0) / 1000.0);
            }
        }

        return story;
    }
}
