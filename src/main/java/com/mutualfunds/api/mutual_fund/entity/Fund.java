package com.mutualfunds.api.mutual_fund.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "funds", indexes = {
        @Index(name = "idx_funds_isin", columnList = "isin"),
        @Index(name = "idx_funds_category", columnList = "fund_category")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fund_id")
    private UUID fundId;

    @Column(name = "fund_name", nullable = false)
    private String fundName;

    @Column(unique = true, nullable = false)
    private String isin;

    @Column(name = "amc_name")
    private String amcName;

    @Column(name = "fund_category")
    private String fundCategory;

    @Column(name = "fund_type")
    private String fundType;

    @Column(name = "expense_ratio")
    private Double expenseRatio;

    @Column(name = "min_sip_amount")
    private Double minSipAmount;

    @Column(name = "direct_plan")
    @Builder.Default
    private Boolean directPlan = true;

    @Type(JsonBinaryType.class)
    @Column(name = "sector_allocation_json", columnDefinition = "jsonb")
    private JsonNode sectorAllocationJson;

    @Type(JsonBinaryType.class)
    @Column(name = "top_holdings_json", columnDefinition = "jsonb")
    private JsonNode topHoldingsJson;

    @Type(JsonBinaryType.class)
    @Column(name = "fund_metadata_json", columnDefinition = "jsonb")
    private JsonNode fundMetadataJson;

    @Column(name = "current_nav")
    private Double currentNav;

    @Column(name = "nav_as_of")
    private java.sql.Date navAsOf;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}