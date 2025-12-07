package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.request.EnrichmentRequest;
import com.mutualfunds.api.mutual_fund.dto.response.EnrichmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * REST client for Python ETL service
 * Uses WebClient (async/reactive) to call Python FastAPI service for enriching portfolio holdings
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ETLClient {
    
    private final WebClient webClient;
    
    @Value("${etl.service.url:http://localhost:8081}")
    private String etlServiceUrl;
    
    @Value("${etl.enrich.endpoint:/etl/enrich}")
    private String enrichEndpoint;
    
    /**
     * Send parsed holdings to Python ETL for enrichment (async/reactive)
     * Spring Boot already parsed the PDF/Excel
     * Python ETL will enrich with fund master data (ISIN, AMC, category, sectors, etc.)
     * 
     * @param request Parsed holdings + upload metadata
     * @return Mono containing enriched funds ready for database insertion
     */
    public Mono<EnrichmentResponse> enrichHoldingsAsync(EnrichmentRequest request) {
        String url = etlServiceUrl + enrichEndpoint;
        log.info("Calling Python ETL service for enrichment (async): {} with {} holdings", 
                url, request.getParsedHoldings().size());
        
        return webClient
                .post()
                .uri(url)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EnrichmentResponse.class)
                .doOnSuccess(response -> {
                    if (response != null && "completed".equalsIgnoreCase(response.getStatus())) {
                        log.info("Enrichment completed for upload {}: {} funds enriched, {} failed",
                                request.getUploadId(),
                                response.getEnrichmentQuality() != null ? response.getEnrichmentQuality().getSuccessfullyEnriched() : 0,
                                response.getEnrichmentQuality() != null ? response.getEnrichmentQuality().getFailedToEnrich() : 0);
                    } else {
                        log.error("Enrichment failed for upload {}: {}", 
                                request.getUploadId(), 
                                response != null ? response.getErrorMessage() : "No response from ETL");
                    }
                })
                .doOnError(error -> {
                    log.error("Error calling Python ETL service at {}: {}", etlServiceUrl, error.getMessage(), error);
                })
                .onErrorResume(error -> Mono.just(
                        EnrichmentResponse.builder()
                                .uploadId(request.getUploadId())
                                .status("failed")
                                .errorMessage("ETL service error: " + error.getMessage())
                                .build()
                ));
    }
    
    /**
     * Blocking wrapper for synchronous callers
     * Converts async Mono result to blocking call
     */
    public EnrichmentResponse enrichHoldings(EnrichmentRequest request) {
        return enrichHoldingsAsync(request).block();
    }
}
