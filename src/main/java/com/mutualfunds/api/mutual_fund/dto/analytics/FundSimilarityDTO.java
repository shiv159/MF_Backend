package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FundSimilarityDTO {
    private String fundA;
    private String fundB;
    private Double stockOverlapPct;
    private Double sectorCorrelation;
}
