package com.mutualfunds.api.mutual_fund.dto.risk;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioHealthDTO {
    private String sectorConcentration;
    private String overlapStatus;

    // Rich Analysis
    private Double diversificationScore; // 0-10
    private java.util.List<StockOverviewDTO> topOverlappingStocks;
    private java.util.List<FundSimilarityDTO> fundSimilarities;
    private java.util.Map<String, Double> aggregateSectorAllocation;
}
