package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SectorOverlapDTO {
    private String sectorName;
    private Double totalAllocation;
    private List<FundContribution> fundContributions;

    @Data
    @Builder
    public static class FundContribution {
        private String fundName;
        private Double contribution; // The weight this fund adds to the sector
    }
}
