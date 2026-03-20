package com.mutualfunds.api.mutual_fund.features.alerts.persistence;

import com.mutualfunds.api.mutual_fund.features.alerts.domain.AlertStatus;
import com.mutualfunds.api.mutual_fund.features.alerts.domain.PortfolioAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioAlertRepository extends JpaRepository<PortfolioAlert, UUID> {

    List<PortfolioAlert> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PortfolioAlert> findByAlertIdAndUser_UserId(UUID alertId, UUID userId);

    boolean existsByUser_UserIdAndDedupeKeyAndCreatedAtAfter(UUID userId, String dedupeKey, LocalDateTime createdAt);

    long countByUser_UserIdAndStatus(UUID userId, AlertStatus status);
}
