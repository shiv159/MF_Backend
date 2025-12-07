package com.mutualfunds.api.mutual_fund.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spring Boot sends already-parsed holdings to Python ETL for enrichment
 * Endpoint: POST /etl/enrich (on Python service)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrichmentRequest {
    private UUID uploadId;                              // To track which upload this is for
    private UUID userId;                                // User making the upload
    private String fileType;                            // "pdf" or "excel"
    private List<Map<String, Object>> parsedHoldings;  // Already extracted holdings
    private long enrichmentTimestamp;                   // Timestamp of enrichment request
    
    /**
     * NO file_path! Holdings are already extracted by Spring Boot.
     * Python ETL only focuses on enrichment:
     * - Fund name → ISIN matching
     * - Fetching fund master data from mftool
     * - Validating enriched data
     */
}
