package com.mutualfunds.api.mutual_fund.service.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutualfunds.api.mutual_fund.dto.risk.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.RiskTolerance;
import com.mutualfunds.api.mutual_fund.exception.ResourceNotFoundException;
import com.mutualfunds.api.mutual_fund.exception.UnauthorizedException;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskProfilingService implements IRiskProfilingService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public User updateRiskProfile(RiskProfileRequest request) {
        User user = getCurrentUser();
        log.info("Updating risk profile");

        // 1. Overwrite profile data JSON
        JsonNode profileData = objectMapper.valueToTree(request);
        user.setProfileDataJson(profileData);

        // 2. Update Direct Fields
        user.setPrimaryGoal(request.getGoals().getPrimaryGoal());
        user.setInvestmentHorizonYears(request.getGoals().getTimeHorizonYears());
        if (request.getFinancials().getMonthlyInvestmentAmount() != null) {
            user.setMonthlySipAmount(request.getFinancials().getMonthlyInvestmentAmount());
        }
        // Monthly SIP is usually provided in the goal or financials, but old request
        // had it.
        // The new GoalDTO doesn't strict have SIP amount, it has TargetAmount.
        // But OnboardingService.updateRiskProfile previously updated sip.
        // Let's assume we derive it or leave it if not in request?
        // Wait, User entity has monthlySipAmount. The new DTO doesn't have it
        // explicitly as "sip",
        // maybe we should add it to Financials or Goals?
        // Checking GoalDTO... it has targetAmount.
        // Checking RiskProfileRequest... it has Demographics, Financials, Behavioral,
        // Goals, Preferences.
        // FinancialsDTO has existingEmi.
        // I should probably add sipAmount to Financials or Goals.
        // For now, I will NOT update monthlySipAmount if it's not in the request, or
        // should I add it?
        // The user request JSON example didn't have SIP amount. It had targetAmount.
        // I will calculate needed SIP later or just leave it for now.

        // 3. Calculate Risk Tolerance
        RiskTolerance tolerance = calculateRiskTolerance(request);
        user.setRiskTolerance(tolerance);

        return userRepository.save(user);
    }

    private RiskTolerance calculateRiskTolerance(RiskProfileRequest request) {
        // Simple scoring logic based on discussions
        // Base Score: 50
        int score = 50;

        // 1. Horizon Impact (Capacity)
        int horizon = request.getGoals().getTimeHorizonYears();
        if (horizon > 10)
            score += 20;
        else if (horizon > 5)
            score += 10;
        else if (horizon < 3)
            score -= 10;

        // 2. Age Impact (Capacity)
        int age = request.getDemographics().getAge();
        if (age < 35)
            score += 10;
        else if (age > 50)
            score -= 10;
        else if (age > 60)
            score -= 20;

        // 3. Financial Stability (Capacity)
        int emergencyMonths = request.getFinancials().getEmergencyFundMonths();
        if (emergencyMonths < 3)
            score -= 15; // Penalty for low safety net
        else if (emergencyMonths > 6)
            score += 5;

        // 4. Behavioral (Tolerance)
        String reaction = request.getBehavioral().getMarketDropReaction();
        if ("PANIC_SELL".equalsIgnoreCase(reaction) || "SELL".equalsIgnoreCase(reaction)) {
            score -= 25; // Massive penalty for panic
        } else if ("BUY_MORE".equalsIgnoreCase(reaction)) {
            score += 15;
        }

        log.debug("Calculated Risk Score: {}", score);

        if (score >= 70)
            return RiskTolerance.AGGRESSIVE;
        if (score >= 40)
            return RiskTolerance.MODERATE;
        return RiskTolerance.CONSERVATIVE;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }
}
