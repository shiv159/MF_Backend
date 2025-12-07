package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response from Python ETL service after enrichment
 * Endpoint: POST /etl/enrich (on Python service)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichmentResponse {
    private UUID uploadId;
    private String status;              // "completed" or "failed"
    private Integer durationSeconds;    // How long enrichment took
    
    private List<EnrichedFund> enrichedFunds;      // Enriched holdings as EnrichedFund objects
    private List<Map<String, Object>> enrichedFundsMaps;  // Alternative format: raw maps
    
    private Integer totalRecords;       // Total holdings sent for enrichment
    private Integer enrichedRecords;    // Successfully enriched
    private Integer failedRecords;      // Failed to enrich
    
    private EnrichmentQuality enrichmentQuality;
    
    private String errorMessage;        // If status = "failed"
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrichmentQuality {
        private Integer successfullyEnriched;  // e.g., 2
        private Integer failedToEnrich;        // e.g., 0
        private List<String> warnings;         // e.g., ["Fund XYZ not found in mftool"]
    }
}
