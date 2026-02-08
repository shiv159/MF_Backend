package com.mutualfunds.api.mutual_fund.service.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.dto.risk.BehavioralDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.DemographicsDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.FinancialsDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.GoalDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.PreferencesDTO;
import com.mutualfunds.api.mutual_fund.dto.risk.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.RiskTolerance;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskProfilingServiceTest {

    @Mock
    private UserRepository userRepository;

    private RiskProfilingService riskProfilingService;

    @BeforeEach
    void setUp() {
        riskProfilingService = new RiskProfilingService(userRepository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sellSomeReactionAppliesPenalty() {
        setAuthenticatedUser("user@example.com");
        User user = User.builder().email("user@example.com").build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        setAuthenticatedUser("user@example.com");
        User user = User.builder().email("user@example.com").build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RiskProfileRequest request = buildRequest(
                65,   // age > 60 should trigger stronger penalty
                12,   // horizon
                2,    // emergency fund months
                "HOLD");

        User updated = riskProfilingService.updateRiskProfile(request);

        assertThat(updated.getRiskTolerance()).isEqualTo(RiskTolerance.CONSERVATIVE);
    }

    private void setAuthenticatedUser(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER")));
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
