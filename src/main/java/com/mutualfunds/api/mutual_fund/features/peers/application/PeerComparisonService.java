package com.mutualfunds.api.mutual_fund.features.peers.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.peers.domain.PeerComparisonSnapshot;
import com.mutualfunds.api.mutual_fund.features.peers.persistence.PeerComparisonSnapshotRepository;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.diagnostics.application.PortfolioDiagnosticService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.risk.api.RiskProfileQuery;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.features.users.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeerComparisonService {

    private final PeerComparisonSnapshotRepository snapshotRepository;
    private final UserRepository userRepository;
    private final PortfolioReadService portfolioReadService;
    private final PortfolioDiagnosticService diagnosticService;
    private final RiskProfileQuery riskProfileQuery;
    private final ObjectMapper objectMapper;

    public ObjectNode compareToPeers(UUID userId) {
        ObjectNode result = objectMapper.createObjectNode();

        List<UserHolding> holdings = portfolioReadService.findHoldingsWithFund(userId);
        if (holdings.isEmpty()) {
            result.put("error", "No portfolio found to compare");
            return result;
        }

        String riskProfile = "MODERATE";
        Optional<RiskProfileResponse> profileOpt = riskProfileQuery.getRiskProfile(userId);
        if (profileOpt.isPresent() && profileOpt.get().getRiskProfile() != null) {
            riskProfile = profileOpt.get().getRiskProfile().getLevel() != null
                    ? profileOpt.get().getRiskProfile().getLevel() : "MODERATE";
        }

        double totalValue = holdings.stream()
                .map(UserHolding::getCurrentValue).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        String sizeBracket = totalValue < 100000 ? "SMALL" : totalValue < 1000000 ? "MEDIUM" : "LARGE";
        String ageBracket = "25-35";

        ObjectNode userMetrics = objectMapper.createObjectNode();
        userMetrics.put("fundCount", holdings.size());
        userMetrics.put("totalValue", Math.round(totalValue * 100.0) / 100.0);

        OptionalDouble userAvgExpense = holdings.stream()
                .filter(h -> h.getFund() != null && h.getFund().getExpenseRatio() != null)
                .mapToDouble(h -> h.getFund().getExpenseRatio())
                .average();
        userMetrics.put("avgExpenseRatio", userAvgExpense.isPresent()
                ? Math.round(userAvgExpense.getAsDouble() * 1000.0) / 1000.0 : 0);

        double totalCurrentValue = totalValue > 0 ? totalValue : 1;
        double equityPct = 0, debtPct = 0, goldPct = 0;
        for (UserHolding h : holdings) {
            if (h.getFund() == null || h.getFund().getFundCategory() == null || h.getCurrentValue() == null) continue;
            String cat = h.getFund().getFundCategory().toLowerCase();
            double weight = (h.getCurrentValue() / totalCurrentValue) * 100;
            if (cat.contains("debt") || cat.contains("liquid") || cat.contains("bond")) debtPct += weight;
            else if (cat.contains("gold")) goldPct += weight;
            else equityPct += weight;
        }
        userMetrics.put("equityPct", Math.round(equityPct * 100.0) / 100.0);
        userMetrics.put("debtPct", Math.round(debtPct * 100.0) / 100.0);
        userMetrics.put("goldPct", Math.round(goldPct * 100.0) / 100.0);

        double totalInvested = holdings.stream()
                .map(UserHolding::getInvestmentAmount).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        userMetrics.put("returnPct", totalInvested > 0
                ? Math.round(((totalValue - totalInvested) / totalInvested) * 10000.0) / 100.0 : 0);

        result.set("you", userMetrics);

        Optional<PeerComparisonSnapshot> peerOpt = snapshotRepository
                .findTopByRiskProfileAndAgeBracketAndPortfolioSizeBracketOrderByComputedAtDesc(
                        riskProfile, ageBracket, sizeBracket);

        ObjectNode peerMetrics = objectMapper.createObjectNode();
        if (peerOpt.isPresent()) {
            PeerComparisonSnapshot peer = peerOpt.get();
            peerMetrics.put("avgFundCount", peer.getAvgFundCount() != null ? peer.getAvgFundCount() : 8);
            peerMetrics.put("avgExpenseRatio", peer.getAvgExpenseRatio() != null ? peer.getAvgExpenseRatio() : 1.1);
            peerMetrics.put("equityPct", peer.getAvgEquityPct() != null ? peer.getAvgEquityPct() : 65);
            peerMetrics.put("debtPct", peer.getAvgDebtPct() != null ? peer.getAvgDebtPct() : 25);
            peerMetrics.put("goldPct", peer.getAvgGoldPct() != null ? peer.getAvgGoldPct() : 10);
            peerMetrics.put("returnPct", peer.getAvgReturns1y() != null ? peer.getAvgReturns1y() : 12);
            peerMetrics.put("sampleSize", peer.getSampleSize() != null ? peer.getSampleSize() : 0);
        } else {
            peerMetrics.put("avgFundCount", 8);
            peerMetrics.put("avgExpenseRatio", 1.1);
            peerMetrics.put("equityPct", 65.0);
            peerMetrics.put("debtPct", 25.0);
            peerMetrics.put("goldPct", 10.0);
            peerMetrics.put("returnPct", 12.0);
            peerMetrics.put("sampleSize", 0);
            peerMetrics.put("note", "Using market benchmarks as peer data is still being collected");
        }
        result.set("peers", peerMetrics);

        ObjectNode highlights = objectMapper.createObjectNode();
        highlights.put("expenseRatioVsPeers",
                userMetrics.path("avgExpenseRatio").asDouble() < peerMetrics.path("avgExpenseRatio").asDouble()
                        ? "BETTER" : "WORSE");
        highlights.put("diversificationVsPeers",
                userMetrics.path("fundCount").asInt() >= peerMetrics.path("avgFundCount").asInt()
                        ? "SIMILAR_OR_BETTER" : "LESS_DIVERSIFIED");
        highlights.put("returnsVsPeers",
                userMetrics.path("returnPct").asDouble() >= peerMetrics.path("returnPct").asDouble()
                        ? "OUTPERFORMING" : "UNDERPERFORMING");
        result.set("highlights", highlights);

        result.put("riskProfile", riskProfile);
        result.put("portfolioSizeBracket", sizeBracket);

        return result;
    }
}
