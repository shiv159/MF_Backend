package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.risk.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.dto.risk.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.dto.response.UploadResponse;
import com.mutualfunds.api.mutual_fund.service.risk.IRiskProfilingService;
import com.mutualfunds.api.mutual_fund.service.risk.RiskRecommendationService;
import com.mutualfunds.api.mutual_fund.entity.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.service.contract.IUploadProcessingService;
import com.mutualfunds.api.mutual_fund.service.contract.IOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Onboarding controller
 * Handles risk profile updates and portfolio file uploads via MultipartFile
 * Delegates all business logic and data operations to service layer
 */
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Slf4j
public class OnboardingController {

    private final IOnboardingService onboardingService;
    private final IUploadProcessingService uploadProcessingService;
    private final IRiskProfilingService riskProfilingService;
    private final RiskRecommendationService riskRecommendationService;

    @PostMapping("/risk-profile")
    public ResponseEntity<RiskProfileResponse> updateRiskProfile(@Valid @RequestBody RiskProfileRequest request) {
        log.info("Processing enhanced risk profile update request");

        // 1. Update Profile (Overwrite mode)
        User user = riskProfilingService.updateRiskProfile(request);
        log.info("Risk profile updated successfully");

        // 2. Generate Recommendation
        RiskProfileResponse response = riskRecommendationService.generateRecommendation(user);

        // Note: Logic for 'portfolio_upload' next step is preserved implicit logic if
        // needed,
        // but current response structure focuses on recommendation.
        // If strict NextStep needed, we can add it to RiskProfileResponse DTO later.

        return ResponseEntity.ok(response);
    }

    /**
     * Upload portfolio file as MultipartFile
     * Accepts Excel files (.xlsx, .xls)
     * User ID is extracted from JWT token (authenticated user)
     * 
     * @param file Excel file (MultipartFile)
     * @return UploadResponse with upload ID and status
     */
    @PostMapping("/uploads")
    public ResponseEntity<UploadResponse> uploadPortfolio(@RequestPart("file") MultipartFile file)
            throws IOException {
        // Get authenticated user ID from security context
        User currentUser = onboardingService.getCurrentUser();
        UUID userId = currentUser.getUserId();

        log.info("Processing portfolio upload request");

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Validate file size (max 10MB)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }

        String filename = file.getOriginalFilename();
        String fileType = extractFileExtension(filename);

        // Validate file type (Excel only)
        if (!isValidExcelFile(fileType)) {
            throw new IllegalArgumentException("Only Excel files (.xlsx, .xls) are supported");
        }

        // Create portfolio upload record
        PortfolioUpload saved = onboardingService.createPortfolioUpload(
                userId,
                filename,
                fileType,
                file.getSize());

        // Extract file bytes and start async processing
        byte[] fileBytes = file.getBytes();
        uploadProcessingService.processUploadAsync(saved.getUploadId(), fileBytes, fileType);

        return ResponseEntity.ok(
                new UploadResponse(saved.getUploadId(), saved.getStatus() != null ? saved.getStatus().name() : null));
    }

    /**
     * Extract file extension from filename
     * 
     * @param filename Filename with extension
     * @return File extension (without dot), or empty string
     */
    private String extractFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Validate if file type is Excel (.xlsx or .xls)
     * 
     * @param fileType File extension
     * @return true if Excel file
     */
    private boolean isValidExcelFile(String fileType) {
        return "xlsx".equalsIgnoreCase(fileType) || "xls".equalsIgnoreCase(fileType);
    }

    @GetMapping("/uploads/{uploadId}")
    public ResponseEntity<UploadResponse> getUploadStatus(@PathVariable UUID uploadId) {
        log.info("Checking upload status request");

        // Get authenticated user
        User currentUser = onboardingService.getCurrentUser();

        // Get upload
        PortfolioUpload upload = onboardingService.getUploadById(uploadId);

        // Verify ownership
        if (!upload.getUser().getUserId().equals(currentUser.getUserId())) {
            log.warn("Upload access denied due to ownership mismatch");
            throw new com.mutualfunds.api.mutual_fund.exception.ForbiddenException(
                    "Access denied: You can only view your own uploads");
        }

        UploadResponse response = new UploadResponse();
        response.setUploadId(upload.getUploadId());
        response.setStatus(upload.getStatus() != null ? upload.getStatus().name() : null);

        return ResponseEntity.ok(response);
    }
}
