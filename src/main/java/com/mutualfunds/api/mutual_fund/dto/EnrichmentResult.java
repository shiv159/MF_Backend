package com.mutualfunds.api.mutual_fund.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Wrapper class containing enriched data and enrichment metrics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichmentResult {
    private List<Map<String, Object>> enrichedData;
    private Integer parsedHoldingsCount;
    private Integer enrichedFundCount;
    private Integer failedRecords;
}
