package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.response.FundRecommendation;
import com.mutualfunds.api.mutual_fund.dto.response.StarterPlanDTO;
import com.mutualfunds.api.mutual_fund.entity.Fund;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.RiskTolerance;
import com.mutualfunds.api.mutual_fund.repository.FundRepository;
import com.mutualfunds.api.mutual_fund.service.contract.IRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RecommendationService implements IRecommendationService {

    private final FundRepository fundRepository;

    public StarterPlanDTO generateStarterPlan(User user) {
        // Step 1: Calculate Equity/Debt Split
        Map<String, Double> equityDebtSplit = calculateEquityDebtSplit(user.getRiskTolerance(), user.getInvestmentHorizonYears());

        // Step 2: Apply Goal Tilts
        applyGoalTilts(equityDebtSplit, user.getPrimaryGoal());

        // Step 3: Select Funds
        List<Fund> eligibleFunds = fundRepository.findByDirectPlanTrueAndExpenseRatioLessThanOrderByExpenseRatioAsc(0.75);

        List<FundRecommendation> recommendations = selectFunds(equityDebtSplit, eligibleFunds);

        // Step 4: Calculate SIP per Fund
        double totalSip = user.getMonthlySipAmount();
        calculateSipPerFund(recommendations, totalSip);

        // Rationale
        String rationale = buildRationale(user.getRiskTolerance(), user.getInvestmentHorizonYears(), user.getPrimaryGoal());

        return new StarterPlanDTO(equityDebtSplit, recommendations, totalSip, rationale);
    }

    private Map<String, Double> calculateEquityDebtSplit(RiskTolerance risk, Integer horizon) {
        Map<String, Double> split = new HashMap<>();
        double equity = 0.0;
        double debt = 0.0;

        if (risk == RiskTolerance.CONSERVATIVE) {
            if (horizon < 5) {
                equity = 40.0;
                debt = 60.0;
            } else if (horizon <= 10) {
                equity = 50.0;
                debt = 50.0;
            } else {
                equity = 60.0;
                debt = 40.0;
            }
        } else if (risk == RiskTolerance.MODERATE) {
            if (horizon < 5) {
                equity = 60.0;
                debt = 40.0;
            } else if (horizon <= 10) {
                equity = 70.0;
                debt = 30.0;
            } else {
                equity = 80.0;
                debt = 20.0;
            }
        } else if (risk == RiskTolerance.AGGRESSIVE) {
            if (horizon < 5) {
                equity = 80.0;
                debt = 20.0;
            } else if (horizon <= 10) {
                equity = 90.0;
                debt = 10.0;
            } else {
                equity = 100.0;
                debt = 0.0;
            }
        }

        split.put("equity", equity);
        split.put("debt", debt);
        return split;
    }

    private void applyGoalTilts(Map<String, Double> split, String goal) {
        if ("wealth_creation".equals(goal)) {
            double equity = split.get("equity") + 10.0;
            double debt = split.get("debt") - 10.0;
            if (equity > 100.0) {
                equity = 100.0;
                debt = 0.0;
            }
            split.put("equity", equity);
            split.put("debt", debt);
        } else if ("retirement".equals(goal)) {
            // no change
        } else if ("tax_saving".equals(goal)) {
            // add ELSS category with 15% allocation - but since we don't have ELSS in funds, maybe adjust later
        } else if ("child_education".equals(goal)) {
            double debt = split.get("debt");
            if (debt < 30.0) {
                double diff = 30.0 - debt;
                split.put("debt", 30.0);
                split.put("equity", split.get("equity") - diff);
            }
        }
    }

    private List<FundRecommendation> selectFunds(Map<String, Double> split, List<Fund> funds) {
        List<FundRecommendation> recommendations = new ArrayList<>();

        double equityPercent = split.get("equity");
        double debtPercent = split.get("debt");

        // Categorize funds
        List<Fund> largeCapIndex = funds.stream()
                .filter(f -> "Large-Cap Index".equals(f.getFundCategory()))
                .toList();
        List<Fund> flexiCap = funds.stream()
                .filter(f -> "Flexi-Cap".equals(f.getFundCategory()) || "Multi-Cap".equals(f.getFundCategory()))
                .toList();
        List<Fund> midCap = funds.stream()
                .filter(f -> "Mid-Cap".equals(f.getFundCategory()))
                .toList();
        List<Fund> smallCap = funds.stream()
                .filter(f -> "Small-Cap".equals(f.getFundCategory()) || f.getFundCategory().contains("Sectoral"))
                .toList();
        List<Fund> corporateBond = funds.stream()
                .filter(f -> "Corporate Bond".equals(f.getFundCategory()))
                .toList();
        List<Fund> liquid = funds.stream()
                .filter(f -> "Liquid".equals(f.getFundCategory()) || "Ultra Short-Term".equals(f.getFundCategory()))
                .toList();

        // Select for equity
        if (equityPercent > 0) {
            if (!largeCapIndex.isEmpty()) {
                Fund fund = largeCapIndex.get(0); // lowest ER
                recommendations.add(new FundRecommendation(fund.getFundId(), fund.getFundName(), fund.getFundCategory(), equityPercent * 0.4, 0.0, fund.getExpenseRatio()));
            }
            if (!flexiCap.isEmpty()) {
                Fund fund = flexiCap.get(0);
                recommendations.add(new FundRecommendation(fund.getFundId(), fund.getFundName(), fund.getFundCategory(), equityPercent * 0.3, 0.0, fund.getExpenseRatio()));
            }
            if (!midCap.isEmpty()) {
                Fund fund = midCap.get(0);
                recommendations.add(new FundRecommendation(fund.getFundId(), fund.getFundName(), fund.getFundCategory(), equityPercent * 0.2, 0.0, fund.getExpenseRatio()));
            }
            if (!smallCap.isEmpty()) {
                Fund fund = smallCap.get(0);
                recommendations.add(new FundRecommendation(fund.getFundId(), fund.getFundName(), fund.getFundCategory(), equityPercent * 0.1, 0.0, fund.getExpenseRatio()));
            }
        }

        // Select for debt
        if (debtPercent > 0) {
            if (!corporateBond.isEmpty()) {
                Fund fund = corporateBond.get(0);
                recommendations.add(new FundRecommendation(fund.getFundId(), fund.getFundName(), fund.getFundCategory(), debtPercent * 0.5, 0.0, fund.getExpenseRatio()));
            }
            if (!liquid.isEmpty()) {
                Fund fund = liquid.get(0);
                recommendations.add(new FundRecommendation(fund.getFundId(), fund.getFundName(), fund.getFundCategory(), debtPercent * 0.5, 0.0, fund.getExpenseRatio()));
            }
        }

        return recommendations;
    }

    private void calculateSipPerFund(List<FundRecommendation> recommendations, double totalSip) {
        for (FundRecommendation rec : recommendations) {
            double sip = (rec.getAllocationPercent() / 100.0) * totalSip;
            rec.setSuggestedSip(Math.round(sip / 100.0) * 100.0);
        }
    }

    private String buildRationale(RiskTolerance risk, Integer horizon, String goal) {
        return String.format("Based on your %s risk tolerance and %d year horizon, with goal of %s.", risk, horizon, goal);
    }
}