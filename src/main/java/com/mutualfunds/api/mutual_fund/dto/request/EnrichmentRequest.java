package com.mutualfunds.api.mutual_fund.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Spring Boot sends already-parsed holdings to Python ETL for enrichment
 * Endpoint: POST /etl/enrich (on Python service)
 * Uses @JsonProperty and @JsonAlias for snake_case JSON serialization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrichmentRequest {
    @JsonProperty("upload_id")
    @JsonAlias("uploadId")
    private UUID uploadId;                              // To track which upload this is for
    
    @JsonProperty("user_id")
    @JsonAlias("userId")
    private UUID userId;                                // User making the upload
    
    @JsonProperty("file_type")
    @JsonAlias("fileType")
    private String fileType;                            // "pdf" or "excel"
    
    @JsonProperty("parsed_holdings")
    @JsonAlias("parsedHoldings")
    private List<Map<String, Object>> parsedHoldings;  // Already extracted holdings
    
    @JsonProperty("enrichment_timestamp")
    @JsonAlias("enrichmentTimestamp")
    private long enrichmentTimestamp;                   // Timestamp of enrichment request
    
    /**
     * NO file_path! Holdings are already extracted by Spring Boot.
     * Python ETL only focuses on enrichment:
     * - Fund name → ISIN matching
     * - Fetching fund master data from mftool
     * - Validating enriched data
     */
}
