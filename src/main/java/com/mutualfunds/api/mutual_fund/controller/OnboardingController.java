package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.request.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.dto.request.UploadRequest;
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

import java.util.UUID;

/**
 * Onboarding controller
 * Handles risk profile updates and portfolio file uploads
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

    @PostMapping("/uploads")
    public ResponseEntity<UploadResponse> uploadPortfolio(@Valid @RequestBody UploadRequest request) {
        try {
            log.info("Processing portfolio upload request for user: {}", request.getUserId());

            // Create portfolio upload record via service layer
            PortfolioUpload saved = onboardingService.createPortfolioUpload(
                    request.getUserId(),
                    request.getFileName(),
                    request.getFileType(),
                    (long) request.getFileContent().length()
            );
            log.info("Portfolio upload record created with ID: {}", saved.getUploadId());

            // Start async processing via service layer
            uploadProcessingService.processUploadAsync(saved.getUploadId(), request.getFileContent(), request.getFileType());
            log.info("Async processing started for upload ID: {}", saved.getUploadId());

            return ResponseEntity.ok(new UploadResponse(saved.getUploadId(), saved.getStatus()));
        } catch (Exception e) {
            log.error("Error processing portfolio upload", e);
            throw e;
        }
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