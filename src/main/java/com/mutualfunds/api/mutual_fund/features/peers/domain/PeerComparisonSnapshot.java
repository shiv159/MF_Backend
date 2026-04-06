package com.mutualfunds.api.mutual_fund.features.peers.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "peer_comparison_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerComparisonSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "risk_profile", nullable = false)
    private String riskProfile;

    @Column(name = "age_bracket", nullable = false)
    private String ageBracket;

    @Column(name = "portfolio_size_bracket", nullable = false)
    private String portfolioSizeBracket;

    @Column(name = "avg_equity_pct")
    private Double avgEquityPct;

    @Column(name = "avg_debt_pct")
    private Double avgDebtPct;

    @Column(name = "avg_gold_pct")
    private Double avgGoldPct;

    @Column(name = "avg_expense_ratio")
    private Double avgExpenseRatio;

    @Column(name = "avg_fund_count")
    private Integer avgFundCount;

    @Column(name = "avg_returns_1y")
    private Double avgReturns1y;

    @Column(name = "avg_overlap_pct")
    private Double avgOverlapPct;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @CreationTimestamp
    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
