package com.mutualfunds.api.mutual_fund.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_insights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "insight_id")
    private UUID insightId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String question;

    @Lob
    @Column(name = "ai_response", columnDefinition = "TEXT")
    private String aiResponse;

    @Column(name = "insight_type")
    private String insightType;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}