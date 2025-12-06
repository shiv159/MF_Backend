package com.mutualfunds.api.mutual_fund.repository;

import com.mutualfunds.api.mutual_fund.entity.AIInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AIInsightRepository extends JpaRepository<AIInsight, UUID> {

    List<AIInsight> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT a FROM AIInsight a WHERE a.user.userId = :userId AND a.question LIKE CONCAT('%', :question, '%')")
    List<AIInsight> findByUser_UserIdAndQuestionContainingIgnoreCase(@Param("userId") UUID userId, @Param("question") String question);
}