package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class WealthProjectionDTO {
    private Integer projectedYears;
    private Double totalInvestment;
    private Double likelyScenarioAmount; // 50th percentile outcome
    private Double pessimisticScenarioAmount; // 10th percentile
    private Double optimisticScenarioAmount; // 90th percentile
    private List<YearProjection> timeline;
    private Double probabilityOfTarget; // % chance of hitting goal (optional)
}
