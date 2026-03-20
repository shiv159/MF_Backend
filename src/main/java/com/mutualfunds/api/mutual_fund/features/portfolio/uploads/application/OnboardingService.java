package com.mutualfunds.api.mutual_fund.features.portfolio.uploads.application;

import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.domain.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.features.users.domain.User;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.domain.UploadStatus;
import com.mutualfunds.api.mutual_fund.shared.exception.ResourceNotFoundException;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.persistence.PortfolioUploadRepository;
import com.mutualfunds.api.mutual_fund.features.portfolio.uploads.api.IOnboardingService;
import com.mutualfunds.api.mutual_fund.features.users.api.UserAccountService;
import com.mutualfunds.api.mutual_fund.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for onboarding business operations
 * Handles all repository operations for user profile management and portfolio
 * uploads
 * Encapsulates persistence logic from controllers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService implements IOnboardingService {

    private final UserAccountService userAccountService;
    private final PortfolioUploadRepository portfolioUploadRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Get current authenticated user from security context
     * 
     * @return User entity
     * @throws UnauthorizedException if user not authenticated or not found
     */
    @Override
    public User getCurrentUser() {
        log.debug("Resolved authenticated user from security context");
        return currentUserProvider.getCurrentUser();
    }

    /**
     * Create a new portfolio upload record
     * 
     * @param userId   User ID for the upload
     * @param fileName Name of the uploaded file
     * @param fileType File type (xlsx, pdf)
     * @param fileSize Size of the file in bytes
     * @return Created PortfolioUpload entity
     */
    @Override
    public PortfolioUpload createPortfolioUpload(UUID userId, String fileName, String fileType, long fileSize) {
        log.debug("Creating portfolio upload record");

        User user = userAccountService.getById(userId);

        PortfolioUpload upload = PortfolioUpload.builder()
                .user(user)
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(fileSize)
                .uploadDate(LocalDateTime.now())
                .status(UploadStatus.parsing)
                .build();

        PortfolioUpload saved = portfolioUploadRepository.save(upload);
        log.info("Portfolio upload record created with ID: {}", saved.getUploadId());

        return saved;
    }

    /**
     * Get upload status by upload ID
     * 
     * @param uploadId Upload ID to look up
     * @return PortfolioUpload entity
     */
    @Override
    public PortfolioUpload getUploadById(UUID uploadId) {
        log.debug("Retrieving upload for ID: {}", uploadId);
        return portfolioUploadRepository.findById(uploadId)
                .orElseThrow(() -> ResourceNotFoundException.forResource("PortfolioUpload", uploadId));
    }
}
