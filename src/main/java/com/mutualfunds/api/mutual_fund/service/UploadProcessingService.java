package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.response.ParsedHoldingDto;
import com.mutualfunds.api.mutual_fund.dto.request.ParsedHoldingEntry;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
     * 1. Parse file to extract holdings using robust three-part extraction
     * 2. Enrich holdings via ETL service
     * 3. Persist enriched holdings to user_holdings table
     * 4. Update upload status and metrics
     * 
     * @param uploadId Unique identifier of the upload record
     * @param fileBytes Raw file content as byte array
     * @param fileType File type (xlsx, xls)
     */
    @Override
    @Async
    public void processUploadAsync(UUID uploadId, byte[] fileBytes, String fileType) {
        log.info("Starting async upload processing for upload ID: {}", uploadId);
        try {
            PortfolioUpload upload = portfolioUploadRepository.findById(uploadId)
                    .orElseThrow(() -> new RuntimeException("Upload not found"));
            
            // Step 1: Parse the file and get strongly-typed ParsedHoldingDto objects
            log.debug("Parsing file for upload ID: {}", uploadId);
            List<ParsedHoldingDto> parsedHoldings = fileParsingService.parseFileFromBytes(fileBytes, fileType);
            log.info("File parsed successfully. Extracted {} holdings for upload ID: {}", parsedHoldings.size(), uploadId);
            
            // Convert ParsedHoldingDto to ParsedHoldingEntry for type-safe ETL service integration
            List<ParsedHoldingEntry> enrichmentData = parsedHoldings.stream()
                    .map(this::dtoToEntry)
                    .collect(Collectors.toList());
            
            // Step 2: Enrich the data using ETL service
            log.debug("Starting ETL enrichment for {} records", enrichmentData.size());
            EnrichmentResult enrichmentResult = etlEnrichmentService.enrichPortfolioData(
                    enrichmentData,
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
    
    /**
     * Convert ParsedHoldingDto to ParsedHoldingEntry for type-safe ETL service integration
     * Maps 11 Excel columns to ETL schema with core fields (fundName, units, nav, value, purchaseDate)
     * 
     * @param dto ParsedHoldingDto from Excel parsing
     * @return ParsedHoldingEntry ready for ETL enrichment
     */
    private ParsedHoldingEntry dtoToEntry(ParsedHoldingDto dto) {
        return ParsedHoldingEntry.builder()
                .fundName(dto.getFundName())
                .units(dto.getUnits())
                .nav(null) // Will be populated by ETL service
                .value(dto.getInvestedValue())
                .purchaseDate(null) // Not available from Excel, ETL service may infer
                .isin(dto.getIsin())
                .amc(dto.getAmc())
                .category(dto.getCategory())
                .folioNumber(dto.getFolioNumber())
                .currentValue(dto.getCurrentValue())
                .returns(dto.getReturns())
                .xirr(dto.getXirr())
                .build();
    }
}
