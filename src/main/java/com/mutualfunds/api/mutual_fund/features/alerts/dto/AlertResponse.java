package com.mutualfunds.api.mutual_fund.features.alerts.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {
    private String alertId;
    private String type;
    private String severity;
    private String title;
    private String body;
    private String status;
    private JsonNode payload;
    private LocalDateTime createdAt;
}
