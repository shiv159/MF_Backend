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
import com.mutualfunds.api.mutual_fund.service.RecommendationService;
import com.mutualfunds.api.mutual_fund.service.ETLEnrichmentService;
import com.mutualfunds.api.mutual_fund.service.FileParsingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Slf4j
public class OnboardingController {

    private final UserRepository userRepository;
    private final PortfolioUploadRepository portfolioUploadRepository;
    
    @Autowired
    private RecommendationService recommendationService;
    
    @Autowired
    private FileParsingService fileParsingService;
    
    @Autowired
    private ETLEnrichmentService etlEnrichmentService;

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

            // Start async processing with Base64 content
            startUploadJob(saved.getUploadId(), request.getFileContent(), request.getFileType());
            log.info("Async processing started for upload ID: {}", saved.getUploadId());

            return ResponseEntity.ok(new UploadResponse(saved.getUploadId(), saved.getStatus()));
        } catch (Exception e) {
            log.error("Error processing portfolio upload", e);
            throw e;
        }
    }

    @Async
    public void startUploadJob(UUID uploadId, String fileContentBase64, String fileType) {
        log.info("Starting async upload processing for upload ID: {}", uploadId);
        try {
            PortfolioUpload upload = portfolioUploadRepository.findById(uploadId)
                    .orElseThrow(() -> new RuntimeException("Upload not found"));
            
            // Step 1: Decode Base64 and parse the file directly
            log.debug("Decoding and parsing file for upload ID: {}", uploadId);
            // Remove whitespace from base64 string (spaces, newlines, tabs)
            String cleanBase64 = fileContentBase64.replaceAll("\\s+", "");
            byte[] fileBytes = Base64.getDecoder().decode(cleanBase64);
            List<Map<String, Object>> parsedData = fileParsingService.parseFileFromBytes(fileBytes, fileType);
            log.info("File parsed successfully. Extracted {} records for upload ID: {}", parsedData.size(), uploadId);
            
            // Step 2: Enrich the data using ETL service
            log.debug("Starting ETL enrichment for {} records", parsedData.size());
            com.mutualfunds.api.mutual_fund.dto.EnrichmentResult enrichmentResult = etlEnrichmentService.enrichPortfolioData(
                    parsedData,
                    upload.getUser().getUserId(),
                    fileType
            );
            log.info("ETL enrichment completed. Enriched {} records for upload ID: {}", enrichmentResult.getEnrichedFundCount(), uploadId);
            
            // Step 3: Update upload status with enrichment metrics
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