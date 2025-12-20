package com.mutualfunds.api.mutual_fund.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


/**
 * Response from Python ETL service after enrichment
 * Endpoint: POST /etl/enrich (on Python service)
 * Uses @JsonProperty and @JsonAlias for snake_case JSON deserialization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class EnrichmentResponse {
    @JsonProperty("upload_id")
    @JsonAlias("uploadId")
    private String uploadId;
    
    @JsonProperty("status")
    @JsonAlias("status")
    private String status;              // "completed" or "failed"
    
    @JsonProperty("duration_seconds")
    @JsonAlias("durationSeconds")
    private Integer durationSeconds;    // How long enrichment took
    
    @JsonProperty("enriched_funds")
    @JsonAlias("enrichedFunds")
    private List<EnrichedFund> enrichedFunds;      // Enriched holdings as EnrichedFund objects
    
    @JsonProperty("enriched_funds_maps")
    @JsonAlias("enrichedFundsMaps")
    private List<Map<String, Object>> enrichedFundsMaps;  // Alternative format: raw maps
    
    @JsonProperty("enrichment_quality")
    @JsonAlias("enrichmentQuality")
    private EnrichmentQuality enrichmentQuality;   // Contains successfully_enriched, failed_to_enrich, warnings
    
    @JsonProperty("error_message")
    @JsonAlias("errorMessage")
    private String errorMessage;        // If status = "failed"
    
    // Helper methods to get metrics from enrichmentQuality
    public Integer getTotalRecords() {
        if (enrichmentQuality == null) return null;
        Integer success = enrichmentQuality.getSuccessfullyEnriched();
        Integer failed = enrichmentQuality.getFailedToEnrich();
        if (success != null && failed != null) {
            return success + failed;
        }
        return success;
    }
    
    public Integer getEnrichedRecords() {
        if (enrichmentQuality == null) return null;
        return enrichmentQuality.getSuccessfullyEnriched();
    }
    
    public Integer getFailedRecords() {
        if (enrichmentQuality == null) return null;
        return enrichmentQuality.getFailedToEnrich();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrichmentQuality {
        @JsonProperty("successfully_enriched")
        @JsonAlias("successfullyEnriched")
        private Integer successfullyEnriched;  // e.g., 2
        
        @JsonProperty("failed_to_enrich")
        @JsonAlias("failedToEnrich")
        private Integer failedToEnrich;        // e.g., 0
        
        @JsonProperty("warnings")
        @JsonAlias("warnings")
        private List<String> warnings;         // e.g., ["Fund XYZ not found in mftool"]
    }
}
