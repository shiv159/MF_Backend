package com.mutualfunds.api.mutual_fund.features.peers.persistence;

import com.mutualfunds.api.mutual_fund.features.peers.domain.PeerComparisonSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PeerComparisonSnapshotRepository extends JpaRepository<PeerComparisonSnapshot, UUID> {
    Optional<PeerComparisonSnapshot> findTopByRiskProfileAndAgeBracketAndPortfolioSizeBracketOrderByComputedAtDesc(
            String riskProfile, String ageBracket, String portfolioSizeBracket);
}
