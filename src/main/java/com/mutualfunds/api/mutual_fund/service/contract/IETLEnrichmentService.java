package com.mutualfunds.api.mutual_fund.service.contract;

import com.mutualfunds.api.mutual_fund.dto.EnrichmentResult;
import com.mutualfunds.api.mutual_fund.dto.request.ParsedHoldingEntry;

import java.util.List;
import java.util.UUID;

/**
 * Contract for ETL enrichment service
 * Defines operations for enriching parsed portfolio data with fund master data
 */
public interface IETLEnrichmentService {
    
    /**
     * Enrich portfolio holdings with fund master data
     * Calls Python ETL service to match holdings with fund information
     * 
     * @param enrichmentData List of ParsedHoldingEntry objects from file parsing
     * @param userId User ID for tracking
     * @param fileType File type (xlsx, pdf) for context
     * @return EnrichmentResult containing enriched data and metrics
     * @throws RuntimeException if enrichment fails
     */
    EnrichmentResult enrichPortfolioData(
            List<ParsedHoldingEntry> enrichmentData,
            UUID userId,
            String fileType);
}
