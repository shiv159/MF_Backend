package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.request.EnrichmentRequest;
import com.mutualfunds.api.mutual_fund.dto.response.EnrichmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * REST client for Python ETL service
 * Calls Python FastAPI service to enrich parsed holdings with fund master data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ETLClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${etl.service.url:http://localhost:8000}")
    private String etlServiceUrl;
    
    @Value("${etl.enrich.endpoint:/etl/enrich}")
    private String enrichEndpoint;
    
    /**
     * Send parsed holdings to Python ETL for enrichment
     * Spring Boot already parsed the PDF/Excel
     * Python ETL will enrich with fund master data (ISIN, AMC, category, sectors, etc.)
     * 
     * @param request Parsed holdings + upload metadata
     * @return Enriched funds ready for database insertion
     */
    public EnrichmentResponse enrichHoldings(EnrichmentRequest request) {
        try {
            String url = etlServiceUrl + enrichEndpoint;
            log.info("Calling Python ETL service for enrichment: {} with {} holdings", 
                    url, request.getParsedHoldings().size());
            
            EnrichmentResponse response = restTemplate.postForObject(
                    url,
                    request,
                    EnrichmentResponse.class
            );
            
            if (response != null && "completed".equalsIgnoreCase(response.getStatus())) {
                log.info("Enrichment completed for upload {}: {} funds enriched, {} failed",
                        request.getUploadId(),
                        response.getEnrichmentQuality().getSuccessfullyEnriched(),
                        response.getEnrichmentQuality().getFailedToEnrich());
            } else {
                log.error("Enrichment failed for upload {}: {}", 
                        request.getUploadId(), 
                        response != null ? response.getErrorMessage() : "No response from ETL");
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error calling Python ETL service at {}: {}", etlServiceUrl, e.getMessage(), e);
            return EnrichmentResponse.builder()
                    .uploadId(request.getUploadId())
                    .status("failed")
                    .errorMessage("ETL service error: " + e.getMessage())
                    .build();
        }
    }
}
