package com.mutualfunds.api.mutual_fund.controller;

import com.mutualfunds.api.mutual_fund.dto.analytics.CovarianceRequest;
import com.mutualfunds.api.mutual_fund.dto.analytics.PortfolioCovarianceDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.RiskInsightsDTO;
import com.mutualfunds.api.mutual_fund.dto.analytics.RollingReturnsDTO;
import com.mutualfunds.api.mutual_fund.service.analytics.FundAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FundAnalyticsController {

    private final FundAnalyticsService fundAnalyticsService;

    /**
     * GET /api/v1/funds/{fundId}/rolling-returns
     * 
     * Calculate rolling returns (1M, 3M, 6M, 1Y, 3Y, 5Y) from NAV history.
     * No database changes required - uses existing fundMetadataJson.nav_history
     */
    @GetMapping("/funds/{fundId}/rolling-returns")
    public ResponseEntity<RollingReturnsDTO> getRollingReturns(@PathVariable UUID fundId) {
        RollingReturnsDTO result = fundAnalyticsService.calculateRollingReturns(fundId);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/funds/{fundId}/risk-insights
     * 
     * Generate plain-language risk insights from alpha, beta, and standard
     * deviation.
     * No database changes required - uses existing fundMetadataJson
     */
    @GetMapping("/funds/{fundId}/risk-insights")
    public ResponseEntity<RiskInsightsDTO> getRiskInsights(@PathVariable UUID fundId) {
        RiskInsightsDTO result = fundAnalyticsService.calculateRiskInsights(fundId);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/portfolio/covariance
     * 
     * Calculate portfolio covariance matrix and diversification metrics.
     * Accepts list of fund IDs and weights in request body.
     * No database changes required - uses existing fundMetadataJson.nav_history
     * 
     * Request body example:
     * {
     * "funds": [
     * { "fundId": "uuid-1", "weight": 0.4 },
     * { "fundId": "uuid-2", "weight": 0.3 },
     * { "fundId": "uuid-3", "weight": 0.3 }
     * ]
     * }
     */
    @PostMapping("/portfolio/covariance")
    public ResponseEntity<PortfolioCovarianceDTO> getPortfolioCovariance(
            @RequestBody CovarianceRequest request) {

        PortfolioCovarianceDTO result = fundAnalyticsService.calculatePortfolioCovariance(
                request.getFundIds(),
                request.toWeightMap());
        return ResponseEntity.ok(result);
    }
}
