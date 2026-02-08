package com.mutualfunds.api.mutual_fund.dto.analytics;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PortfolioCovarianceDTO {
    private List<String> fundIds;
    private List<String> fundNames;
    
    // N x N covariance matrix
    private double[][] covarianceMatrix;
    
    // N x N correlation matrix (for display)
    private double[][] correlationMatrix;
    
    // Portfolio-level metrics
    private Double portfolioVariance;
    private Double portfolioStdDev;
    
    // Diversification benefit
    private Double weightedAvgStdDev;        // Naive calculation
    private Double diversificationBenefit;   // % reduction due to correlation < 1
    
    private String calculationMethod;        // "HISTORICAL_MONTHLY"
    private Integer monthsUsed;              // e.g., 36 months
}
