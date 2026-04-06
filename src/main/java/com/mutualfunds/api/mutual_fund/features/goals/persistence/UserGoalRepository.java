package com.mutualfunds.api.mutual_fund.features.goals.persistence;

import com.mutualfunds.api.mutual_fund.features.goals.domain.UserGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserGoalRepository extends JpaRepository<UserGoal, UUID> {
    List<UserGoal> findByUser_UserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
    List<UserGoal> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);
}
