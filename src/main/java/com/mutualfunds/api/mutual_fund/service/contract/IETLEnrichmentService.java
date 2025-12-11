package com.mutualfunds.api.mutual_fund.service.contract;

import com.mutualfunds.api.mutual_fund.dto.EnrichmentResult;

import java.util.List;
import java.util.Map;
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
     * @param parsedData List of parsed holdings from file
     * @param userId User ID for tracking
     * @param fileType File type (xlsx, pdf) for context
     * @return EnrichmentResult containing enriched data and metrics
     * @throws RuntimeException if enrichment fails
     */
    EnrichmentResult enrichPortfolioData(
            List<Map<String, Object>> parsedData,
            UUID userId,
            String fileType);
}
