package com.mutualfunds.api.mutual_fund.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

/**
 * DTO for enrichment request sent to Python ETL service.
 * Maps to Python ETL service's EnrichmentRequest Pydantic model.
 * 
 * Spring Boot sends already-parsed holdings to Python ETL for enrichment.
 * Endpoint: POST /etl/enrich (on Python service)
 * 
 * Uses @JsonProperty and @JsonAlias for snake_case JSON serialization to match Python API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichmentRequest {
    
    @NotNull(message = "Upload ID is required")
    @JsonProperty("upload_id")
    @JsonAlias("uploadId")
    private UUID uploadId;  // Unique identifier for this upload
    
    @NotNull(message = "User ID is required")
    @JsonProperty("user_id")
    @JsonAlias("userId")
    private UUID userId;  // User who initiated the upload
    
    @JsonProperty("file_type")
    @JsonAlias("fileType")
    private String fileType;  // Original file type parsed by Spring Boot (xlsx, xls, pdf)
    
    @NotEmpty(message = "Parsed holdings list cannot be empty")
    @JsonProperty("parsed_holdings")
    @JsonAlias("parsedHoldings")
    private List<ParsedHoldingEntry> parsedHoldings;  // Holdings extracted by Spring Boot parser
    
    @JsonProperty("enrichment_timestamp")
    @JsonAlias("enrichmentTimestamp")
    private long enrichmentTimestamp;  // Timestamp of enrichment request
    
    /**
     * Holdings are already extracted by Spring Boot's RobustExcelParser.
     * Python ETL focuses on enrichment:
     * - Fund name → ISIN matching
     * - Fetching fund master data from mftool
     * - Calculating NAV and returns
     * - Validating enriched data
     */
}
