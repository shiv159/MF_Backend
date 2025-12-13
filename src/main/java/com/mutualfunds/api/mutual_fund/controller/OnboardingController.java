package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.request.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.dto.response.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.dto.response.StarterPlanDTO;
import com.mutualfunds.api.mutual_fund.dto.response.UploadResponse;
import com.mutualfunds.api.mutual_fund.entity.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.UserType;
import com.mutualfunds.api.mutual_fund.service.contract.IRecommendationService;
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
    private final IRecommendationService recommendationService;
    private final IUploadProcessingService uploadProcessingService;

    @PostMapping("/risk-profile")
    public ResponseEntity<RiskProfileResponse> updateRiskProfile(@Valid @RequestBody RiskProfileRequest request) {
        try {
            log.info("Processing risk profile update request");

            User user = onboardingService.updateRiskProfile(request);
            log.info("Risk profile updated successfully for user: {}", user.getEmail());

            RiskProfileResponse response = new RiskProfileResponse();
            if (user.getUserType() == UserType.new_investor && recommendationService != null) {
                log.debug("Generating starter plan for new investor: {}", user.getEmail());
                StarterPlanDTO plan = recommendationService.generateStarterPlan(user);
                response.setStarterPlan(plan);
                log.info("Starter plan generated for user: {}", user.getEmail());
            } else {
                response.setNextStep("portfolio_upload");
                log.info("Existing investor onboarding completed for user: {}", user.getEmail());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing risk profile update", e);
            throw e;
        }
    }

    /**
     * Upload portfolio file as MultipartFile
     * Accepts Excel files (.xlsx, .xls)
     * 
     * @param userId User ID for the upload
     * @param file Excel file (MultipartFile)
     * @return UploadResponse with upload ID and status
     */
    @PostMapping("/uploads")
    public ResponseEntity<UploadResponse> uploadPortfolio(
            @RequestParam UUID userId,
            @RequestParam(required = false) String portfolioName,
            @RequestPart("file") MultipartFile file) {
        try {
            log.info("Processing portfolio upload request for user: {} with file: {}", userId, file.getOriginalFilename());
            
            // Validate file
            if (file.isEmpty()) {
                log.warn("Empty file uploaded");
                throw new IllegalArgumentException("File cannot be empty");
            }
            
            String filename = file.getOriginalFilename();
            String fileType = extractFileExtension(filename);
            
            // Validate file type (Excel only)
            if (!isValidExcelFile(fileType)) {
                log.warn("Invalid file type: {}", fileType);
                throw new IllegalArgumentException("Only Excel files (.xlsx, .xls) are supported");
            }
            
            // Create portfolio upload record
            PortfolioUpload saved = onboardingService.createPortfolioUpload(
                    userId,
                    filename,
                    fileType,
                    file.getSize()
            );
            log.info("Portfolio upload record created with ID: {}", saved.getUploadId());

            // Extract file bytes and start async processing
            byte[] fileBytes = file.getBytes();
            uploadProcessingService.processUploadAsync(saved.getUploadId(), fileBytes, fileType);
            log.info("Async processing started for upload ID: {}", saved.getUploadId());

            return ResponseEntity.ok(new UploadResponse(saved.getUploadId(), saved.getStatus()));
            
        } catch (IOException e) {
            log.error("Error reading file content", e);
            throw new RuntimeException("Failed to process file: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error processing portfolio upload for user: {}", userId, e);
            throw e;
        }
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
        try {
            log.info("Checking upload status for upload ID: {}", uploadId);

            PortfolioUpload upload = onboardingService.getUploadById(uploadId);

            UploadResponse response = new UploadResponse();
            response.setUploadId(upload.getUploadId());
            response.setStatus(upload.getStatus());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving upload status for upload ID: {}", uploadId, e);
            throw e;
        }
    }
}