package com.mutualfunds.api.mutual_fund.features.briefing.persistence;

import com.mutualfunds.api.mutual_fund.features.briefing.domain.PortfolioBriefing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioBriefingRepository extends JpaRepository<PortfolioBriefing, UUID> {
    List<PortfolioBriefing> findByUser_UserIdOrderByCreatedAtDesc(UUID userId);
    List<PortfolioBriefing> findByUser_UserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);
    long countByUser_UserIdAndIsReadFalse(UUID userId);
}
