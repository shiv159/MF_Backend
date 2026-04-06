package com.mutualfunds.api.mutual_fund.features.briefing.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_briefings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioBriefing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "briefing_id")
    private UUID briefingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "briefing_type", nullable = false)
    private String briefingType;

    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Type(JsonBinaryType.class)
    @Column(name = "metrics_json", columnDefinition = "jsonb")
    private JsonNode metricsJson;

    @Type(JsonBinaryType.class)
    @Column(name = "alerts_summary", columnDefinition = "jsonb")
    private JsonNode alertsSummary;

    @Builder.Default
    @Column(name = "is_read")
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
