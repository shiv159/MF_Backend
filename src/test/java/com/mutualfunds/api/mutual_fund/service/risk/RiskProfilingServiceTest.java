package com.mutualfunds.api.mutual_fund.features.risk.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.features.risk.dto.BehavioralDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.DemographicsDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.FinancialsDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.GoalDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.PreferencesDTO;
import com.mutualfunds.api.mutual_fund.features.risk.dto.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.users.domain.RiskTolerance;
import com.mutualfunds.api.mutual_fund.features.users.api.UserAccountService;
import com.mutualfunds.api.mutual_fund.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskProfilingServiceTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private RiskProfilingService riskProfilingService;

    @BeforeEach
    void setUp() {
        riskProfilingService = new RiskProfilingService(userAccountService, new ObjectMapper(), currentUserProvider);
    }

    @Test
    void sellSomeReactionAppliesPenalty() {
        User user = User.builder().email("user@example.com").build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(userAccountService.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RiskProfileRequest request = buildRequest(
                55,   // age
                12,   // horizon
                2,    // emergency fund months
                "SELL_SOME");

        User updated = riskProfilingService.updateRiskProfile(request);

        assertThat(updated.getRiskTolerance()).isEqualTo(RiskTolerance.CONSERVATIVE);
    }

    @Test
    void ageAboveSixtyUsesHigherPenaltyBand() {
        User user = User.builder().email("user@example.com").build();

        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(userAccountService.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RiskProfileRequest request = buildRequest(
                65,   // age > 60 should trigger stronger penalty
                12,   // horizon
                2,    // emergency fund months
                "HOLD");

        User updated = riskProfilingService.updateRiskProfile(request);

        assertThat(updated.getRiskTolerance()).isEqualTo(RiskTolerance.CONSERVATIVE);
    }

    private RiskProfileRequest buildRequest(int age, int horizon, int emergencyMonths, String reaction) {
        DemographicsDTO demographics = new DemographicsDTO();
        demographics.setAge(age);
        demographics.setIncomeRange("5-10L");
        demographics.setDependents(0);

        FinancialsDTO financials = new FinancialsDTO();
        financials.setEmergencyFundMonths(emergencyMonths);
        financials.setExistingEmiForLoans(0.0);
        financials.setFinancialKnowledge("INTERMEDIATE");
        financials.setMonthlyInvestmentAmount(5000.0);

        BehavioralDTO behavioral = new BehavioralDTO();
        behavioral.setMarketDropReaction(reaction);
        behavioral.setInvestmentPeriodExperience("3-5_YEARS");

        GoalDTO goals = new GoalDTO();
        goals.setPrimaryGoal("Retirement");
        goals.setTimeHorizonYears(horizon);
        goals.setTargetAmount(10_000_000.0);

        PreferencesDTO preferences = new PreferencesDTO();
        preferences.setPreferredInvestmentStyle("PASSIVE");
        preferences.setTaxSavingNeeded(false);

        RiskProfileRequest request = new RiskProfileRequest();
        request.setDemographics(demographics);
        request.setFinancials(financials);
        request.setBehavioral(behavioral);
        request.setGoals(goals);
        request.setPreferences(preferences);
        return request;
    }
}
