package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.request.RiskProfileRequest;
import com.mutualfunds.api.mutual_fund.dto.request.UploadRequest;
import com.mutualfunds.api.mutual_fund.dto.response.RiskProfileResponse;
import com.mutualfunds.api.mutual_fund.dto.response.StarterPlanDTO;
import com.mutualfunds.api.mutual_fund.dto.response.UploadResponse;
import com.mutualfunds.api.mutual_fund.entity.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.enums.UploadStatus;
import com.mutualfunds.api.mutual_fund.enums.UserType;
import com.mutualfunds.api.mutual_fund.repository.PortfolioUploadRepository;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import com.mutualfunds.api.mutual_fund.service.contract.IRecommendationService;
import com.mutualfunds.api.mutual_fund.service.contract.IUploadProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Onboarding controller
 * Handles risk profile updates and portfolio file uploads
 * Delegates business logic to service layer
 */
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Slf4j
public class OnboardingController {

    private final UserRepository userRepository;
    private final PortfolioUploadRepository portfolioUploadRepository;
    private final IRecommendationService recommendationService;
    private final IUploadProcessingService uploadProcessingService;

    @PostMapping("/risk-profile")
    public ResponseEntity<RiskProfileResponse> updateRiskProfile(@Valid @RequestBody RiskProfileRequest request) {
        try {
            log.info("Processing risk profile update request");

            User user = getCurrentUser();
            log.debug("Updating risk profile for user: {}", user.getEmail());

            user.setInvestmentHorizonYears(request.getHorizon());
            user.setRiskTolerance(request.getRisk());
            user.setMonthlySipAmount(request.getSip());
            user.setPrimaryGoal(request.getGoal());

            userRepository.save(user);
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

            // Create portfolio upload record
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            log.debug("Creating portfolio upload record for user: {}", user.getEmail());

            PortfolioUpload upload = PortfolioUpload.builder()
                    .user(user)
                    .fileName(request.getFileName())
                    .fileType(request.getFileType())
                    .fileSize((long) request.getFileContent().length())
                    .uploadDate(LocalDateTime.now())
                    .status(UploadStatus.parsing)
                    .build();

            PortfolioUpload saved = portfolioUploadRepository.save(upload);
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

            PortfolioUpload upload = portfolioUploadRepository.findById(uploadId)
                    .orElseThrow(() -> new RuntimeException("Upload not found"));

            UploadResponse response = new UploadResponse();
            response.setUploadId(upload.getUploadId());
            response.setStatus(upload.getStatus());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving upload status for upload ID: {}", uploadId, e);
            throw e;
        }
    }

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            log.debug("Getting current user for email: {}", email);
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        } catch (Exception e) {
            log.error("Error getting current user", e);
            throw e;
        }
    }
}