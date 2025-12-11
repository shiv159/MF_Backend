package com.mutualfunds.api.mutual_fund.service.contract;

import java.util.UUID;

/**
 * Contract for upload processing service
 * Defines operations for asynchronous portfolio upload processing including
 * file parsing and ETL enrichment coordination
 */
public interface IUploadProcessingService {
    
    /**
     * Process a portfolio upload asynchronously
     * Orchestrates file parsing and ETL enrichment, updates upload status
     * 
     * @param uploadId Unique identifier of the upload record
     * @param fileContentBase64 Base64-encoded file content
     * @param fileType File type (xlsx, pdf)
     */
    void processUploadAsync(UUID uploadId, String fileContentBase64, String fileType);
}
