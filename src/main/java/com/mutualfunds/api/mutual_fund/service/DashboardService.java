package com.mutualfunds.api.mutual_fund.service;

import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse;
import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse.AIInsightDTO;
import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse.HoldingDTO;
import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse.PortfolioSummaryDTO;
import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse.UploadHistoryDTO;
import com.mutualfunds.api.mutual_fund.dto.response.DashboardResponse.UserProfileDTO;
import com.mutualfunds.api.mutual_fund.entity.AIInsight;
import com.mutualfunds.api.mutual_fund.entity.PortfolioUpload;
import com.mutualfunds.api.mutual_fund.entity.User;
import com.mutualfunds.api.mutual_fund.entity.UserHolding;
import com.mutualfunds.api.mutual_fund.exception.BadRequestException;
import com.mutualfunds.api.mutual_fund.repository.AIInsightRepository;
import com.mutualfunds.api.mutual_fund.repository.PortfolioUploadRepository;
import com.mutualfunds.api.mutual_fund.repository.UserHoldingRepository;
import com.mutualfunds.api.mutual_fund.repository.UserRepository;
import com.mutualfunds.api.mutual_fund.service.contract.IDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for dashboard operations
 * Retrieves and aggregates all user-related data for dashboard display
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService implements IDashboardService {

    private final UserRepository userRepository;
    private final UserHoldingRepository userHoldingRepository;
    private final PortfolioUploadRepository portfolioUploadRepository;
    private final AIInsightRepository aiInsightRepository;

    @Override
    public DashboardResponse getDashboardData(UUID userId) {
        log.info("Fetching dashboard data for user: {}", userId);

        // Fetch user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found with ID: {}", userId);
                    return new BadRequestException("User not found");
                });

        // Build user profile DTO
        UserProfileDTO userProfile = buildUserProfile(user);

        // Fetch all holdings for the user
        List<UserHolding> holdings = userHoldingRepository.findByUserIdWithFund(userId);
        log.debug("Found {} holdings for user: {}", holdings.size(), userId);

        // Build portfolio summary
        PortfolioSummaryDTO portfolioSummary = buildPortfolioSummary(holdings);

        // Build holdings DTOs
        List<HoldingDTO> holdingDTOs = holdings.stream()
                .map(this::buildHoldingDTO)
                .collect(Collectors.toList());

        // Fetch upload history
        List<PortfolioUpload> uploads = portfolioUploadRepository.findByUser_UserId(userId);
        log.debug("Found {} uploads for user: {}", uploads.size(), userId);

        List<UploadHistoryDTO> uploadHistory = uploads.stream()
                .map(this::buildUploadHistoryDTO)
                .collect(Collectors.toList());

        // Fetch AI insights
        List<AIInsight> insights = aiInsightRepository.findByUser_UserIdOrderByCreatedAtDesc(userId);
        log.debug("Found {} AI insights for user: {}", insights.size(), userId);

        List<AIInsightDTO> aiInsights = insights.stream()
                .map(this::buildAIInsightDTO)
                .collect(Collectors.toList());

        log.info("Dashboard data successfully retrieved for user: {}", userId);

        return DashboardResponse.builder()
                .userProfile(userProfile)
                .portfolioSummary(portfolioSummary)
                .holdings(holdingDTOs)
                .uploadHistory(uploadHistory)
                .aiInsights(aiInsights)
                .build();
    }

    private UserProfileDTO buildUserProfile(User user) {
        return UserProfileDTO.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .riskTolerance(user.getRiskTolerance() != null ? user.getRiskTolerance().name() : null)
                .investmentHorizonYears(user.getInvestmentHorizonYears())
                .monthlySkipAmount(user.getMonthlySipAmount())
                .primaryGoal(user.getPrimaryGoal())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private PortfolioSummaryDTO buildPortfolioSummary(List<UserHolding> holdings) {
        Double totalInvestmentAmount = 0.0;
        Double totalCurrentValue = 0.0;
        Double totalUnits = 0.0;

        for (UserHolding holding : holdings) {
            if (holding.getInvestmentAmount() != null) {
                totalInvestmentAmount += holding.getInvestmentAmount();
            }
            if (holding.getCurrentValue() != null) {
                totalCurrentValue += holding.getCurrentValue();
            }
            if (holding.getUnitsHeld() != null) {
                totalUnits += holding.getUnitsHeld();
            }
        }

        Double gainLoss = totalCurrentValue - totalInvestmentAmount;
        Double gainLossPercentage = totalInvestmentAmount > 0
                ? (gainLoss / totalInvestmentAmount) * 100
                : 0.0;

        return PortfolioSummaryDTO.builder()
                .totalHoldings(holdings.size())
                .totalInvestmentAmount(totalInvestmentAmount)
                .totalCurrentValue(totalCurrentValue)
                .totalUnits(totalUnits)
                .gainLoss(gainLoss)
                .gainLossPercentage(gainLossPercentage)
                .build();
    }

    private HoldingDTO buildHoldingDTO(UserHolding holding) {
        Double gainLoss = (holding.getCurrentValue() != null && holding.getInvestmentAmount() != null)
                ? holding.getCurrentValue() - holding.getInvestmentAmount()
                : 0.0;

        Double gainLossPercentage = (holding.getInvestmentAmount() != null && holding.getInvestmentAmount() > 0)
                ? (gainLoss / holding.getInvestmentAmount()) * 100
                : 0.0;

        return HoldingDTO.builder()
                .holdingId(holding.getUserHoldingId())
                .fundId(holding.getFund().getFundId())
                .fundName(holding.getFund().getFundName())
                .isin(holding.getFund().getIsin())
                .amcName(holding.getFund().getAmcName())
                .fundCategory(holding.getFund().getFundCategory())
                .unitsHeld(holding.getUnitsHeld())
                .currentNav(holding.getCurrentNav())
                .investmentAmount(holding.getInvestmentAmount())
                .currentValue(holding.getCurrentValue())
                .gainLoss(gainLoss)
                .gainLossPercentage(gainLossPercentage)
                .lastNavUpdate(holding.getLastNavUpdate())
                .build();
    }

    private UploadHistoryDTO buildUploadHistoryDTO(PortfolioUpload upload) {
        return UploadHistoryDTO.builder()
                .uploadId(upload.getUploadId())
                .fileName(upload.getFileName())
                .fileType(upload.getFileType())
                .fileSize(upload.getFileSize())
                .uploadDate(upload.getUploadDate())
                .status(upload.getStatus() != null ? upload.getStatus().name() : null)
                .parsedHoldingsCount(upload.getParsedHoldingsCount())
                .enrichedFundCount(upload.getEnrichedFundCount())
                .errorMessage(upload.getErrorMessage())
                .build();
    }

    private AIInsightDTO buildAIInsightDTO(AIInsight insight) {
        return AIInsightDTO.builder()
                .insightId(insight.getInsightId())
                .question(insight.getQuestion())
                .aiResponse(insight.getAiResponse())
                .insightType(insight.getInsightType())
                .createdAt(insight.getCreatedAt())
                .build();
    }
}
