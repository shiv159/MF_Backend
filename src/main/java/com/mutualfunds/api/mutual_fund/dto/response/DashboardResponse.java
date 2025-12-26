package com.mutualfunds.api.mutual_fund.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private UserProfileDTO userProfile;
    private PortfolioSummaryDTO portfolioSummary;
    private List<HoldingDTO> holdings;
    private List<UploadHistoryDTO> uploadHistory;
    private List<AIInsightDTO> aiInsights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileDTO {
        private UUID userId;
        private String email;
        private String fullName;
        private String phone;
        private String userType;
        private String riskTolerance;
        private Integer investmentHorizonYears;
        private Double monthlySipAmount; // Fixed typo: was monthlySkipAmount
        private String primaryGoal;
        private Boolean isActive; // Added: missing field
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt; // Added: missing field
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioSummaryDTO {
        private Integer totalHoldings;
        private Double totalInvestmentAmount;
        private Double totalCurrentValue;
        private Double totalUnits;
        private Double gainLoss;
        private Double gainLossPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingDTO {
        private UUID holdingId;
        private UUID fundId;
        private String fundName;
        private Integer weightPct;
        private String isin;
        private String amcName;
        private String fundCategory;
        private String fundType; // Added: missing field
        private Double expenseRatio; // Added: missing field
        private Double minSipAmount; // Added: missing field
        private Boolean directPlan; // Added: missing field
        private com.fasterxml.jackson.databind.JsonNode sectorAllocation; // Added: missing field
        private com.fasterxml.jackson.databind.JsonNode topHoldings; // Added: missing field
        private com.fasterxml.jackson.databind.JsonNode fundMetadata; // Added: missing field
        private Double unitsHeld;
        private Double currentNav;
        private java.sql.Date navAsOf; // Added: missing field
        private Double investmentAmount;
        private Double currentValue;
        private Double gainLoss;
        private Double gainLossPercentage;
        private java.sql.Date purchaseDate; // Added: missing field
        private LocalDateTime lastNavUpdate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadHistoryDTO {
        private UUID uploadId;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private LocalDateTime uploadDate;
        private String status;
        private Integer parsedHoldingsCount;
        private Integer enrichedFundCount;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIInsightDTO {
        private UUID insightId;
        private String question;
        private String aiResponse;
        private String insightType;
        private LocalDateTime createdAt;
    }
}
