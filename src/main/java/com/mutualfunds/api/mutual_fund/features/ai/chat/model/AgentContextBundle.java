package com.mutualfunds.api.mutual_fund.features.ai.chat.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class AgentContextBundle {
    UUID userId;
    String userMessage;
    String screenContext;
    ObjectNode portfolioSnapshot;
    ArrayNode holdingsSummary;
    JsonNode diagnostics;
    JsonNode riskProfile;
    JsonNode dataQuality;
    ArrayNode fundAnalytics;
    JsonNode covariance;
    ArrayNode marketContext;
    ObjectNode conversationContext;
    List<String> warnings;
}
