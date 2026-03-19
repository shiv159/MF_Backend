package com.mutualfunds.api.mutual_fund.features.risk.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FundRiskMetricsDTO {
    private Double alpha;
    private Double beta;
    private Double sharpeRatio;
    private Double standardDeviation;
    private Double rSquared;
}
