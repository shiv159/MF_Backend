package com.mutualfunds.api.mutual_fund.features.goals.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mutualfunds.api.mutual_fund.features.funds.api.FundQueryService;
import com.mutualfunds.api.mutual_fund.features.funds.domain.Fund;
import com.mutualfunds.api.mutual_fund.features.goals.domain.UserGoal;
import com.mutualfunds.api.mutual_fund.features.goals.persistence.UserGoalRepository;
import com.mutualfunds.api.mutual_fund.features.portfolio.api.PortfolioReadService;
import com.mutualfunds.api.mutual_fund.features.portfolio.holdings.domain.UserHolding;
import com.mutualfunds.api.mutual_fund.features.risk.api.RiskProfileQuery;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.users.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalPlanningService {

    private final UserGoalRepository goalRepository;
    private final UserRepository userRepository;
    private final RiskProfileQuery riskProfileQuery;
    private final PortfolioReadService portfolioReadService;
    private final FundQueryService fundQueryService;
    private final ObjectMapper objectMapper;

    public record GoalPlan(ObjectNode goalSummary, ObjectNode sipPlan, ObjectNode allocationAdvice,
                           ArrayNode recommendedFunds) {}

    public GoalPlan createGoalPlan(UUID userId, String goalType, String goalName,
                                     BigDecimal targetAmount, LocalDate targetDate,
                                     BigDecimal currentSavings) {
        long monthsToGoal = ChronoUnit.MONTHS.between(LocalDate.now(), targetDate);
        if (monthsToGoal <= 0) monthsToGoal = 1;

        double expectedReturnPct = getExpectedReturn(monthsToGoal, goalType);

        double monthlyRate = expectedReturnPct / 100.0 / 12.0;
        double existingFV = currentSavings != null
                ? currentSavings.doubleValue() * Math.pow(1 + monthlyRate, monthsToGoal) : 0;
        double remainingTarget = targetAmount.doubleValue() - existingFV;

        double monthlySip;
        if (remainingTarget <= 0) {
            monthlySip = 0;
        } else if (monthlyRate > 0) {
            double factor = (Math.pow(1 + monthlyRate, monthsToGoal) - 1) / monthlyRate * (1 + monthlyRate);
            monthlySip = remainingTarget / factor;
        } else {
            monthlySip = remainingTarget / monthsToGoal;
        }

        ObjectNode goalSummary = objectMapper.createObjectNode();
        goalSummary.put("goalType", goalType);
        goalSummary.put("goalName", goalName);
        goalSummary.put("targetAmount", targetAmount.doubleValue());
        goalSummary.put("targetDate", targetDate.toString());
        goalSummary.put("monthsToGoal", monthsToGoal);
        goalSummary.put("yearsToGoal", Math.round(monthsToGoal / 12.0 * 10.0) / 10.0);
        goalSummary.put("currentSavings", currentSavings != null ? currentSavings.doubleValue() : 0);
        goalSummary.put("expectedReturnPct", expectedReturnPct);

        ObjectNode sipPlan = objectMapper.createObjectNode();
        sipPlan.put("requiredMonthlySip", Math.ceil(monthlySip));
        sipPlan.put("totalInvestment", Math.ceil(monthlySip) * monthsToGoal + (currentSavings != null ? currentSavings.doubleValue() : 0));
        sipPlan.put("expectedCorpus", targetAmount.doubleValue());
        sipPlan.put("wealthGain", targetAmount.doubleValue() - (Math.ceil(monthlySip) * monthsToGoal + (currentSavings != null ? currentSavings.doubleValue() : 0)));

        ObjectNode allocation = buildAllocationForHorizon(monthsToGoal, userId);

        ArrayNode recommendedFunds = recommendFundsForGoal(userId, goalType, monthsToGoal, allocation);

        return new GoalPlan(goalSummary, sipPlan, allocation, recommendedFunds);
    }

    @Transactional
    public UserGoal saveGoal(UUID userId, GoalPlan plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ObjectNode goalSummary = plan.goalSummary();
        UserGoal goal = UserGoal.builder()
                .user(user)
                .goalType(goalSummary.path("goalType").asText())
                .goalName(goalSummary.path("goalName").asText())
                .targetAmount(BigDecimal.valueOf(goalSummary.path("targetAmount").asDouble()))
                .targetDate(Date.valueOf(goalSummary.path("targetDate").asText()))
                .currentAmount(BigDecimal.valueOf(goalSummary.path("currentSavings").asDouble()))
                .monthlySip(BigDecimal.valueOf(plan.sipPlan().path("requiredMonthlySip").asDouble()))
                .expectedReturnPct(BigDecimal.valueOf(goalSummary.path("expectedReturnPct").asDouble()))
                .assetAllocationJson(plan.allocationAdvice())
                .linkedFundIds(plan.recommendedFunds())
                .status("ACTIVE")
                .build();

        return goalRepository.save(goal);
    }

    public List<UserGoal> getUserGoals(UUID userId) {
        return goalRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);
    }

    public List<UserGoal> getActiveGoals(UUID userId) {
        return goalRepository.findByUser_UserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");
    }

    private double getExpectedReturn(long months, String goalType) {
        if (months <= 12) return 6.0;
        if (months <= 36) return 9.0;
        if (months <= 60) return 11.0;
        return 12.5;
    }

    private ObjectNode buildAllocationForHorizon(long months, UUID userId) {
        ObjectNode allocation = objectMapper.createObjectNode();

        Optional<RiskProfileResponse> profile = riskProfileQuery.getRiskProfile(userId);
        if (profile.isPresent() && profile.get().getAssetAllocation() != null) {
            var aa = profile.get().getAssetAllocation();
            if (months <= 12) {
                allocation.put("equity", Math.min(aa.getEquity(), 20.0));
                allocation.put("debt", Math.max(aa.getDebt(), 70.0));
                allocation.put("gold", 10.0);
            } else if (months <= 36) {
                allocation.put("equity", Math.min(aa.getEquity(), 50.0));
                allocation.put("debt", Math.max(aa.getDebt(), 40.0));
                allocation.put("gold", 10.0);
            } else {
                allocation.put("equity", aa.getEquity());
                allocation.put("debt", aa.getDebt());
                allocation.put("gold", aa.getGold());
            }
        } else {
            if (months <= 12) {
                allocation.put("equity", 20.0);
                allocation.put("debt", 70.0);
                allocation.put("gold", 10.0);
            } else if (months <= 36) {
                allocation.put("equity", 50.0);
                allocation.put("debt", 40.0);
                allocation.put("gold", 10.0);
            } else if (months <= 60) {
                allocation.put("equity", 65.0);
                allocation.put("debt", 25.0);
                allocation.put("gold", 10.0);
            } else {
                allocation.put("equity", 75.0);
                allocation.put("debt", 17.0);
                allocation.put("gold", 8.0);
            }
        }

        allocation.put("rationale", months <= 36
                ? "Short horizon prioritizes capital preservation with limited equity exposure."
                : "Long horizon allows higher equity allocation for wealth creation.");

        return allocation;
    }

    private ArrayNode recommendFundsForGoal(UUID userId, String goalType, long months, ObjectNode allocation) {
        ArrayNode recommendations = objectMapper.createArrayNode();

        List<UserHolding> holdings = portfolioReadService.findHoldingsWithFund(userId);
        Set<UUID> existingFundIds = holdings.stream()
                .map(UserHolding::getFund).filter(Objects::nonNull)
                .map(Fund::getFundId).collect(Collectors.toSet());

        double equityPct = allocation.path("equity").asDouble(0);
        if (equityPct > 0) {
            addFundRecommendations(recommendations, "Equity", existingFundIds, holdings,
                    months > 60 ? "Large Cap" : "Flexi Cap", 2);
        }

        double debtPct = allocation.path("debt").asDouble(0);
        if (debtPct > 0) {
            addFundRecommendations(recommendations, "Debt", existingFundIds, holdings,
                    months <= 12 ? "Liquid" : "Corporate Bond", 1);
        }

        return recommendations;
    }

    private void addFundRecommendations(ArrayNode recommendations, String assetClass,
                                          Set<UUID> existingFundIds, List<UserHolding> holdings,
                                          String preferredCategory, int count) {
        List<UserHolding> suitable = holdings.stream()
                .filter(h -> h.getFund() != null && h.getFund().getFundCategory() != null)
                .filter(h -> h.getFund().getFundCategory().toLowerCase().contains(assetClass.toLowerCase())
                        || h.getFund().getFundCategory().toLowerCase().contains(preferredCategory.toLowerCase()))
                .sorted(Comparator.comparing((UserHolding h) ->
                        h.getFund().getExpenseRatio() != null ? h.getFund().getExpenseRatio() : Double.MAX_VALUE))
                .limit(count)
                .toList();

        for (UserHolding h : suitable) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("fundName", h.getFund().getFundName());
            node.put("fundId", h.getFund().getFundId().toString());
            node.put("category", h.getFund().getFundCategory());
            node.put("source", "EXISTING_PORTFOLIO");
            node.put("reason", "Already in your portfolio with good " + assetClass.toLowerCase() + " exposure");
            if (h.getFund().getExpenseRatio() != null) node.put("expenseRatio", h.getFund().getExpenseRatio());
            recommendations.add(node);
        }

        int remaining = count - suitable.size();
        if (remaining > 0) {
            fundQueryService.findAll().stream()
                    .filter(f -> f.getFundCategory() != null &&
                            (f.getFundCategory().toLowerCase().contains(preferredCategory.toLowerCase())
                                    || f.getFundCategory().toLowerCase().contains(assetClass.toLowerCase())))
                    .filter(f -> !existingFundIds.contains(f.getFundId()))
                    .filter(f -> Boolean.TRUE.equals(f.getDirectPlan()))
                    .sorted(Comparator.comparing(f -> f.getExpenseRatio() != null ? f.getExpenseRatio() : Double.MAX_VALUE))
                    .limit(remaining)
                    .forEach(f -> {
                        ObjectNode node = objectMapper.createObjectNode();
                        node.put("fundName", f.getFundName());
                        node.put("fundId", f.getFundId().toString());
                        node.put("category", f.getFundCategory());
                        node.put("source", "NEW_RECOMMENDATION");
                        node.put("reason", "Low expense ratio " + assetClass.toLowerCase() + " fund for your goal");
                        if (f.getExpenseRatio() != null) node.put("expenseRatio", f.getExpenseRatio());
                        recommendations.add(node);
                    });
        }
    }
}
