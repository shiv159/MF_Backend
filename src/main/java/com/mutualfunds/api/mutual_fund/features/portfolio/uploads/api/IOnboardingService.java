package com.mutualfunds.api.mutual_fund.features.portfolio.uploads.api;

import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.domain.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;

import java.util.UUID;

/**
 * Contract for onboarding business operations
 * Handles user profile management and portfolio upload creation
 * Encapsulates all repository and persistence operations
 */
public interface IOnboardingService {
    
    /**
     * Get current authenticated user by email from security context
     * 
     * @return User entity
     * @throws RuntimeException if user not found
     */
    User getCurrentUser();
    
    /**
     * Create a new portfolio upload record
     * 
     * @param userId User ID for the upload
     * @param fileName Name of the uploaded file
     * @param fileType File type (xlsx, pdf)
     * @param fileSize Size of the file in bytes
     * @return Created PortfolioUpload entity
     * @throws RuntimeException if user not found
     */
    PortfolioUpload createPortfolioUpload(UUID userId, String fileName, String fileType, long fileSize);
    
    /**
     * Get upload status by upload ID
     * 
     * @param uploadId Upload ID to look up
     * @return PortfolioUpload entity
     * @throws RuntimeException if upload not found
     */
    PortfolioUpload getUploadById(UUID uploadId);
}
