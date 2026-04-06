package com.mutualfunds.api.mutual_fund.features.goals.api;

import com.mutualfunds.api.mutual_fund.features.goals.application.GoalPlanningService;
import com.mutualfunds.api.mutual_fund.features.goals.domain.UserGoal;
import com.mutualfunds.api.mutual_fund.shared.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalPlanningService goalPlanningService;

    @PostMapping("/plan")
    public ResponseEntity<GoalPlanningService.GoalPlan> createGoalPlan(
            @RequestBody GoalPlanRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        GoalPlanningService.GoalPlan plan = goalPlanningService.createGoalPlan(
                userId, request.goalType(), request.goalName(),
                request.targetAmount(), request.targetDate(),
                request.currentSavings());
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/save")
    public ResponseEntity<UserGoal> saveGoal(
            @RequestBody GoalPlanRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        GoalPlanningService.GoalPlan plan = goalPlanningService.createGoalPlan(
                userId, request.goalType(), request.goalName(),
                request.targetAmount(), request.targetDate(),
                request.currentSavings());
        UserGoal saved = goalPlanningService.saveGoal(userId, plan);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<UserGoal>> getUserGoals(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(goalPlanningService.getUserGoals(userId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<UserGoal>> getActiveGoals(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(goalPlanningService.getActiveGoals(userId));
    }

    public record GoalPlanRequest(
            String goalType,
            String goalName,
            BigDecimal targetAmount,
            LocalDate targetDate,
            BigDecimal currentSavings
    ) {}

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }
        return userPrincipal.getUserId();
    }
}
