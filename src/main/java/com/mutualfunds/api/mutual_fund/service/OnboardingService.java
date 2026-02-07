package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.entity.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.UploadStatus;
import com.mutualfunds.api.mutual_fund.exception.ResourceNotFoundException;
import com.mutualfunds.api.mutual_fund.exception.UnauthorizedException;
import com.mutualfunds.api.mutual_fund.repository.PortfolioUploadRepository;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import com.mutualfunds.api.mutual_fund.service.contract.IOnboardingService;
import com.mutualfunds.api.mutual_fund.dto.request.RiskProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final UserRepository userRepository;
    private final PortfolioUploadRepository portfolioUploadRepository;

    /**
     * Get current authenticated user from security context
     * 
     * @return User entity
     * @throws UnauthorizedException if user not authenticated or not found
     */
    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        String email = authentication.getName();
        log.debug("Resolved authenticated user from security context");

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /**
     * Update user's risk profile and investment parameters
     * 
     * @param request RiskProfileRequest with investment parameters
     * @return Updated User entity
     */
    @Override
    public User updateRiskProfile(RiskProfileRequest request) {
        User user = getCurrentUser();
        log.debug("Updating risk profile");

        user.setInvestmentHorizonYears(request.getHorizon());
        user.setRiskTolerance(request.getRisk());
        user.setMonthlySipAmount(request.getSip());
        user.setPrimaryGoal(request.getGoal());

        User updatedUser = userRepository.save(user);
        log.info("Risk profile updated successfully");

        return updatedUser;
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.forResource("User", userId));

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
