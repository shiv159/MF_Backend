package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class FundRecommendationDTO {
    private UUID id;
    private String name;
    private String category;

    // Rich Data Fields
    private FundRiskMetricsDTO riskMetrics;
    private com.fasterxml.jackson.databind.JsonNode sectorAllocation;
    private com.fasterxml.jackson.databind.JsonNode topHoldings;

    private String reason;
}
