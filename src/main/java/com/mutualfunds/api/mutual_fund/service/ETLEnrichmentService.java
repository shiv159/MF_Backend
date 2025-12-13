package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.EnrichmentResult;
import com.mutualfunds.api.mutual_fund.dto.request.EnrichmentRequest;
import com.mutualfunds.api.mutual_fund.dto.request.ParsedHoldingEntry;
import com.mutualfunds.api.mutual_fund.dto.response.EnrichmentResponse;
import com.mutualfunds.api.mutual_fund.dto.response.EnrichedFund;
import com.mutualfunds.api.mutual_fund.integration.etl.IETLIntegration;
import com.mutualfunds.api.mutual_fund.service.contract.IETLEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for enriching portfolio data using the Python ETL service
 * Bridges parsed file data with external fund master data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ETLEnrichmentService implements IETLEnrichmentService {

    private final IETLIntegration etlIntegration;

    /**
     * Enrich portfolio holdings with fund master data
     * Calls Python ETL service to match holdings with fund information
     *
     * @param enrichmentData List of ParsedHoldingEntry objects (already typed and validated)
     * @param userId User ID for tracking
     * @param fileType File type (xlsx, pdf) for context
     * @return EnrichmentResult containing enriched data and metrics
     */
    public EnrichmentResult enrichPortfolioData(
            List<ParsedHoldingEntry> enrichmentData,
            UUID userId,
            String fileType) {

        log.info("Starting ETL enrichment for {} holdings for user: {}", enrichmentData.size(), userId);

        try {
            // Build enrichment request with already-typed holdings
            EnrichmentRequest request = EnrichmentRequest.builder()
                    .uploadId(UUID.randomUUID())
                    .userId(userId)
                    .fileType(fileType)
                    .parsedHoldings(enrichmentData)
                    .enrichmentTimestamp(System.currentTimeMillis())
                    .build();

            log.debug("Sending {} holdings to ETL service for enrichment", enrichmentData.size());
            log.debug("Enrichment request full payload: {}", request);

            // Call ETL service
            EnrichmentResponse response = etlIntegration.enrichHoldings(request);

            if (response == null) {
                log.error("ETL service returned null response");
                throw new RuntimeException("ETL service enrichment failed - null response");
            }

            if (!"completed".equalsIgnoreCase(response.getStatus())) {
                log.error("ETL service returned non-completed status: {}. Full response: {}", response.getStatus(), response);
                log.error("Error message from ETL: {}", response.getErrorMessage());
                throw new RuntimeException("ETL service enrichment failed with status: " + response.getStatus() + " - " + response.getErrorMessage());
            }

            log.info("ETL enrichment completed successfully");
            
            Integer total = response.getTotalRecords();
            Integer enriched = response.getEnrichedRecords();
            Integer failed = response.getFailedRecords();
            
            if (total != null && enriched != null && failed != null) {
                log.info("Enrichment summary - Total: {}, Enriched: {}, Failed: {}", total, enriched, failed);
            }

            // Return enriched funds, preferring raw maps over EnrichedFund objects
            List<Map<String, Object>> enrichedData = response.getEnrichedFundsMaps();
            if (enrichedData == null || enrichedData.isEmpty()) {
                // Fall back to EnrichedFund objects if maps aren't available
                enrichedData = new ArrayList<>();
                if (response.getEnrichedFunds() != null) {
                    for (EnrichedFund fund : response.getEnrichedFunds()) {
                        Map<String, Object> fundMap = new HashMap<>();
                        fundMap.put("fundName", fund.getFundName());
                        fundMap.put("isin", fund.getIsin());
                        fundMap.put("amc", fund.getAmc());
                        fundMap.put("category", fund.getCategory());
                        fundMap.put("units", fund.getUnits());
                        fundMap.put("nav", fund.getNav());
                        fundMap.put("value", fund.getValue());
                        fundMap.put("expenseRatio", fund.getExpenseRatio());
                        fundMap.put("currentNav", fund.getCurrentNav());
                        fundMap.put("navAsOf", fund.getNavAsOf());
                        fundMap.put("sectorAllocation", fund.getSectorAllocation());
                        fundMap.put("topHoldings", fund.getTopHoldings());
                        enrichedData.add(fundMap);
                    }
                }
            }
            
            log.info("Returning {} enriched holdings", enrichedData != null ? enrichedData.size() : 0);

            return EnrichmentResult.builder()
                    .enrichedData(enrichedData != null ? enrichedData : new ArrayList<>())
                    .parsedHoldingsCount(response.getTotalRecords())
                    .enrichedFundCount(response.getEnrichedRecords())
                    .failedRecords(response.getFailedRecords())
                    .build();

        } catch (Exception e) {
            log.error("Error during ETL enrichment", e);
            throw new RuntimeException("Failed to enrich portfolio data: " + e.getMessage(), e);
        }
    }


}
