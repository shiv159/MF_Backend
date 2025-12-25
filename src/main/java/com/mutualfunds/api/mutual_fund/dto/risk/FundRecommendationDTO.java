package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;
import java.util.Map;

@Data
@Builder
public class FundRecommendationDTO {
    private UUID id;
    private String name;
    private String category;
    private Map<String, Double> metrics;
    private String reason;
}
