package com.mutualfunds.api.mutual_fund.features.integration.etl.etl;

import com.mutualfunds.api.mutual_fund.features.integration.etl.dto.EnrichmentRequest;
import com.mutualfunds.api.mutual_fund.features.integration.etl.dto.EnrichmentResponse;
import reactor.core.publisher.Mono;

/**
 * Contract for ETL integration
 * Defines operations for communicating with external ETL service
 */
public interface IETLIntegration {
    
    /**
     * Send parsed holdings to Python ETL service for enrichment (async/reactive)
     * 
     * @param request EnrichmentRequest containing parsed holdings and metadata
     * @return Mono containing enriched funds response
     */
    Mono<EnrichmentResponse> enrichHoldingsAsync(EnrichmentRequest request);
    
    /**
     * Send parsed holdings to Python ETL service for enrichment (blocking)
     * Convenience method that blocks on the async operation
     * 
     * @param request EnrichmentRequest containing parsed holdings and metadata
     * @return EnrichmentResponse with enriched data
     */
    EnrichmentResponse enrichHoldings(EnrichmentRequest request);
}
