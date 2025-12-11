package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.entity.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.enums.UploadStatus;
import com.mutualfunds.api.mutual_fund.repository.PortfolioUploadRepository;
import com.mutualfunds.api.mutual_fund.service.contract.IFileParsingService;
import com.mutualfunds.api.mutual_fund.service.contract.IETLEnrichmentService;
import com.mutualfunds.api.mutual_fund.service.contract.IUploadProcessingService;
import com.mutualfunds.api.mutual_fund.dto.EnrichmentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for asynchronous portfolio upload processing
 * Orchestrates file parsing and ETL enrichment, manages upload lifecycle
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadProcessingService implements IUploadProcessingService {

    private final PortfolioUploadRepository portfolioUploadRepository;
    private final IFileParsingService fileParsingService;
    private final IETLEnrichmentService etlEnrichmentService;
    private final HoldingsPersistenceService holdingsPersistenceService;

    /**
     * Process a portfolio upload asynchronously
     * Steps:
     * 1. Decode Base64 file content
     * 2. Parse file to extract holdings
     * 3. Enrich holdings via ETL service
     * 4. Persist enriched holdings to user_holdings table
     * 5. Update upload status and metrics
     * 
     * @param uploadId Unique identifier of the upload record
     * @param fileContentBase64 Base64-encoded file content
     * @param fileType File type (xlsx, pdf)
     */
    @Override
    @Async
    public void processUploadAsync(UUID uploadId, String fileContentBase64, String fileType) {
        log.info("Starting async upload processing for upload ID: {}", uploadId);
        try {
            PortfolioUpload upload = portfolioUploadRepository.findById(uploadId)
                    .orElseThrow(() -> new RuntimeException("Upload not found"));
            
            // Step 1: Decode Base64 and parse the file directly
            log.debug("Decoding and parsing file for upload ID: {}", uploadId);
            String cleanBase64 = fileContentBase64.replaceAll("\\s+", "");
            byte[] fileBytes = Base64.getDecoder().decode(cleanBase64);
            List<Map<String, Object>> parsedData = fileParsingService.parseFileFromBytes(fileBytes, fileType);
            log.info("File parsed successfully. Extracted {} records for upload ID: {}", parsedData.size(), uploadId);
            
            // Step 2: Enrich the data using ETL service
            log.debug("Starting ETL enrichment for {} records", parsedData.size());
            EnrichmentResult enrichmentResult = etlEnrichmentService.enrichPortfolioData(
                    parsedData,
                    upload.getUser().getUserId(),
                    fileType
            );
            log.info("ETL enrichment completed. Enriched {} records for upload ID: {}", enrichmentResult.getEnrichedFundCount(), uploadId);
            
            // Step 3: Persist enriched holdings to the database
            log.debug("Starting to persist enriched holdings for user ID: {}", upload.getUser().getUserId());
            Integer persistedCount = holdingsPersistenceService.persistEnrichedHoldings(
                    enrichmentResult.getEnrichedData(),
                    upload.getUser().getUserId()
            );
            log.info("Enriched holdings persisted successfully. {} holdings saved for user ID: {}", 
                    persistedCount, upload.getUser().getUserId());
            
            // Step 4: Update upload status with enrichment metrics
            upload.setStatus(UploadStatus.completed);
            upload.setParsedHoldingsCount(enrichmentResult.getParsedHoldingsCount());
            upload.setEnrichedFundCount(enrichmentResult.getEnrichedFundCount());
            portfolioUploadRepository.save(upload);
            log.info("Upload processing completed successfully for upload ID: {}. Parsed: {}, Enriched: {} records", 
                    uploadId, enrichmentResult.getParsedHoldingsCount(), enrichmentResult.getEnrichedFundCount());
            
        } catch (Exception e) {
            log.error("Upload processing failed for upload ID: {}", uploadId, e);
            try {
                PortfolioUpload upload = portfolioUploadRepository.findById(uploadId).orElseThrow();
                upload.setStatus(UploadStatus.failed);
                upload.setErrorMessage(e.getMessage());
                portfolioUploadRepository.save(upload);
            } catch (Exception ex) {
                log.error("Failed to update upload status to failed for upload ID: {}", uploadId, ex);
            }
        }
    }
}
