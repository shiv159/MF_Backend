package com.mutualfunds.api.mutual_fund.features.goals.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_goals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "goal_id")
    private UUID goalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "goal_type", nullable = false)
    private String goalType;

    @Column(name = "goal_name", nullable = false)
    private String goalName;

    @Column(name = "target_amount")
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private Date targetDate;

    @Column(name = "current_amount")
    @Builder.Default
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(name = "monthly_sip")
    private BigDecimal monthlySip;

    @Column(name = "expected_return_pct")
    private BigDecimal expectedReturnPct;

    @Type(JsonBinaryType.class)
    @Column(name = "asset_allocation_json", columnDefinition = "jsonb")
    private JsonNode assetAllocationJson;

    @Type(JsonBinaryType.class)
    @Column(name = "linked_fund_ids", columnDefinition = "jsonb")
    private JsonNode linkedFundIds;

    @Builder.Default
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
