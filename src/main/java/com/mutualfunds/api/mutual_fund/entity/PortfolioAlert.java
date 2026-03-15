package com.mutualfunds.api.mutual_fund.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

import com.mutualfunds.api.mutual_fund.alert.model.AlertSeverity;
import com.mutualfunds.api.mutual_fund.alert.model.AlertStatus;
import com.mutualfunds.api.mutual_fund.alert.model.AlertType;

@Entity
@Table(name = "portfolio_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id")
    private UUID alertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AlertSeverity severity;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Type(JsonBinaryType.class)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private JsonNode payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
