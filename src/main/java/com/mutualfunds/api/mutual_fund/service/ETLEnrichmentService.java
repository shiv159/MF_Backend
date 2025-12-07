package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.request.EnrichmentRequest;
import com.mutualfunds.api.mutual_fund.dto.response.EnrichmentResponse;
import com.mutualfunds.api.mutual_fund.dto.response.EnrichedFund;
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
public class ETLEnrichmentService {

    private final ETLClient etlClient;

    /**
     * Enrich portfolio holdings with fund master data
     * Calls Python ETL service to match holdings with fund information
     *
     * @param parsedData List of parsed holdings from file
     * @param userId User ID for tracking
     * @return Enriched holdings with complete fund information
     */
    public List<Map<String, Object>> enrichPortfolioData(
            List<Map<String, Object>> parsedData,
            UUID userId) {

        log.info("Starting ETL enrichment for {} holdings for user: {}", parsedData.size(), userId);

        try {
            // Build enrichment request
            EnrichmentRequest request = new EnrichmentRequest();
            request.setUploadId(UUID.randomUUID());
            request.setUserId(userId);
            request.setParsedHoldings(parsedData);
            request.setEnrichmentTimestamp(System.currentTimeMillis());

            log.debug("Sending {} holdings to ETL service for enrichment", parsedData.size());

            // Call ETL service
            EnrichmentResponse response = etlClient.enrichHoldings(request);

            if (response == null) {
                log.error("ETL service returned null response");
                throw new RuntimeException("ETL service enrichment failed - null response");
            }

            if (!"completed".equalsIgnoreCase(response.getStatus())) {
                log.error("ETL service returned non-completed status: {}", response.getStatus());
                throw new RuntimeException("ETL service enrichment failed with status: " + response.getStatus());
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

            return enrichedData != null ? enrichedData : new ArrayList<>();

        } catch (Exception e) {
            log.error("Error during ETL enrichment", e);
            throw new RuntimeException("Failed to enrich portfolio data: " + e.getMessage(), e);
        }
    }
}
