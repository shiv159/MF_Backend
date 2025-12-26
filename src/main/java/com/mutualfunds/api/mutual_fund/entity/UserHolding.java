package com.mutualfunds.api.mutual_fund.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_holdings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "fund_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_holding_id")
    private UUID userHoldingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "units_held")
    private Double unitsHeld;

    @Column(name = "current_nav")
    private Double currentNav;

    @Column(name = "investment_amount")
    private Double investmentAmount;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(name = "weight_pct")
    private Integer weightPct;

    @Column(name = "purchase_date")
    private java.sql.Date purchaseDate;

    @Column(name = "last_nav_update")
    private LocalDateTime lastNavUpdate;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}