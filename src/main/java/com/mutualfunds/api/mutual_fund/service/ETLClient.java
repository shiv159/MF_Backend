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
        log.debug("Enrichment request details: uploadId={}, userId={}", 
                request.getUploadId(), request.getUserId());
        log.debug("Enrichment request full payload: {}", request);

        return webClient
                .post()
                .uri(url)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(EnrichmentResponse.class)
                                .doOnSuccess(body -> {
                                    if (body != null && "completed".equalsIgnoreCase(body.getStatus())) {
                                        log.info("Enrichment completed for upload {}: {} funds enriched, {} failed",
                                                request.getUploadId(),
                                                body.getEnrichmentQuality() != null ? body.getEnrichmentQuality().getSuccessfullyEnriched() : 0,
                                                body.getEnrichmentQuality() != null ? body.getEnrichmentQuality().getFailedToEnrich() : 0);
                                    } else {
                                        log.error("Enrichment failed for upload {}: {}", 
                                                request.getUploadId(), 
                                                body != null ? body.getErrorMessage() : "No response from ETL");
                                    }
                                });
                    } else {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("ETL service returned status {} for upload {}. Error body: {}", 
                                            response.statusCode(), 
                                            request.getUploadId(), 
                                            errorBody);
                                    return Mono.just(EnrichmentResponse.builder()
                                            .uploadId(request.getUploadId() != null ? request.getUploadId().toString() : null)
                                            .status("failed")
                                            .errorMessage("ETL error (" + response.statusCode() + "): " + errorBody)
                                            .build());
                                })
                                .onErrorResume(e -> Mono.just(EnrichmentResponse.builder()
                                        .uploadId(request.getUploadId() != null ? request.getUploadId().toString() : null)
                                        .status("failed")
                                        .errorMessage("ETL error (" + response.statusCode() + "): " + e.getMessage())
                                        .build()));
                    }
                });
    }
    
    /**
     * Blocking wrapper for synchronous callers
     * Converts async Mono result to blocking call
     */
    public EnrichmentResponse enrichHoldings(EnrichmentRequest request) {
        return enrichHoldingsAsync(request).block();
    }
}
